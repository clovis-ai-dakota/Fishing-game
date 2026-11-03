package com.deepline.fishing.server

import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.online.CatchDto
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.LiveStatus
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleServerTest {
    private class Player(val ws: DefaultClientWebSocketSession) {
        suspend fun send(m: ClientMessage) = ws.send(Frame.Text(OnlineProtocol.encode(m)))

        suspend fun receive(): ServerMessage = withTimeout(5_000) {
            while (true) {
                val frame = ws.incoming.receive()
                if (frame is Frame.Text) return@withTimeout OnlineProtocol.decodeServer(frame.readText())!!
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

        suspend inline fun <reified T : ServerMessage> expect(): T {
            val m = receive()
            return assertIs<T>(m, "got $m")
        }
    }

    private suspend fun ApplicationTestBuilder.connect(name: String): Player {
        val client = createClient { install(WebSockets) }
        val p = Player(client.webSocketSession("/battle"))
        p.expect<ServerMessage.Welcome>()
        p.send(ClientMessage.Hello(name))
        return p
    }

    private fun setup(builder: ApplicationTestBuilder) = builder.application { battleServer(Lobby()) }

    @Test
    fun roomCodeMatchPlaysToTheEnd() = testApplication {
        setup(this)
        val host = connect("Host")
        val guest = connect("Guest")

        host.send(ClientMessage.CreateRoom(BattleMode.HEAVY_HAUL))
        val room = host.expect<ServerMessage.RoomCreated>()
        assertEquals(OnlineProtocol.ROOM_CODE_LENGTH, room.code.length)

        guest.send(ClientMessage.JoinRoom(room.code.lowercase()))
        val hostStart = host.expect<ServerMessage.MatchStarted>()
        val guestStart = guest.expect<ServerMessage.MatchStarted>()
        assertEquals(hostStart.seed, guestStart.seed)
        assertEquals(listOf("Host", "Guest"), hostStart.playerNames)
        assertEquals(0, hostStart.yourIndex)
        assertEquals(1, guestStart.yourIndex)

        val hostMatch = OnlineMatch(hostStart)
        val guestMatch = OnlineMatch(guestStart)
        val players = listOf(host to hostMatch, guest to guestMatch)

        // Live status is relayed to the opponent.
        host.send(ClientMessage.Live(LiveStatus(1, 120f, 300, 2, true)))
        assertEquals(120f, guest.expect<ServerMessage.OpponentLive>().status.depth)

        // Both fish simultaneously: alternate casts, each validated and echoed to both.
        var over: ServerMessage.MatchOver? = null
        repeat(3) {
            for ((index, pm) in players.withIndex()) {
                val (player, match) = pm
                val turn = match.myNextTurn()!!
                val cast = BotPlayer.playCast(OnlineProtocol.tournamentSpecs, turn.seed, BotDifficulty.NORMAL)
                player.send(ClientMessage.CastFinished(turn, cast.toDto()))
                match.markSent(turn)
                for ((other, otherMatch) in players) {
                    val recorded = other.expect<ServerMessage.CastRecorded>()
                    assertEquals(index, recorded.turn.playerIndex)
                    otherMatch.apply(recorded)
                }
            }
        }
        for ((player, match) in players) {
            over = player.expect<ServerMessage.MatchOver>()
            match.apply(over!!)
        }
        val expectedOutcome = hostMatch.battle.outcome()
        assertEquals(expectedOutcome?.winner, over!!.winner)
        assertEquals(hostMatch.battle.score(0), guestMatch.battle.score(0), "mirrors agree")
        assertEquals(hostMatch.battle.score(1), guestMatch.battle.score(1))
    }

    @Test
    fun quickMatchPairsPlayersWaitingForTheSameMode() = testApplication {
        setup(this)
        val a = connect("A")
        val b = connect("B")
        val c = connect("C")
        a.send(ClientMessage.QuickMatch(BattleMode.TIME_ATTACK))
        a.expect<ServerMessage.Waiting>()
        c.send(ClientMessage.QuickMatch(BattleMode.BEST_OF_FIVE))
        c.expect<ServerMessage.Waiting>()
        b.send(ClientMessage.QuickMatch(BattleMode.TIME_ATTACK))
        val startA = a.expect<ServerMessage.MatchStarted>()
        val startB = b.expect<ServerMessage.MatchStarted>()
        assertEquals(BattleMode.TIME_ATTACK, startA.mode)
        assertEquals(startA.seed, startB.seed)

        c.send(ClientMessage.CancelWaiting)
        c.expect<ServerMessage.WaitingCancelled>()
    }

    @Test
    fun serverRecomputesPointsAndRejectsImpossibleCasts() = testApplication {
        setup(this)
        val host = connect("Host")
        val guest = connect("Guest")
        host.send(ClientMessage.CreateRoom(BattleMode.SPECIES_HUNT))
        val code = host.expect<ServerMessage.RoomCreated>().code
        guest.send(ClientMessage.JoinRoom(code))
        val start = host.expect<ServerMessage.MatchStarted>()
        guest.expect<ServerMessage.MatchStarted>()
        val turn = OnlineMatch(start).myNextTurn()!!

        // A deep-sea fish caught in the shallows is impossible.
        val impossible = CastDto(listOf(CatchDto("pale_leviathan", false, 20f, 99_999)), 30f, 0, null, 20f)
        host.send(ClientMessage.CastFinished(turn, impossible))
        assertTrue(host.expect<ServerMessage.Error>().message.contains("not possible"))

        // A real catch with inflated points is accepted, with the points recomputed.
        val inflated = CastDto(listOf(CatchDto("silver_sprat", false, 20f, 99_999)), 30f, 0, null, 20f)
        host.send(ClientMessage.CastFinished(turn, inflated))
        val recorded = host.expect<ServerMessage.CastRecorded>()
        assertEquals(6, recorded.cast.catches.single().points) // 5 base × 1.2 depth multiplier

        // Replaying the same turn is rejected.
        guest.expect<ServerMessage.CastRecorded>()
        host.send(ClientMessage.CastFinished(turn, inflated))
        assertNotNull(host.expect<ServerMessage.Error>())
    }

    @Test
    fun leavingMidMatchForfeits() = testApplication {
        setup(this)
        val host = connect("Host")
        val guest = connect("Guest")
        host.send(ClientMessage.CreateRoom(BattleMode.BEST_OF_FIVE))
        val code = host.expect<ServerMessage.RoomCreated>().code
        guest.send(ClientMessage.JoinRoom(code))
        host.expect<ServerMessage.MatchStarted>()
        guest.expect<ServerMessage.MatchStarted>()

        guest.ws.close()
        val over = host.expect<ServerMessage.MatchOver>()
        assertEquals(0, over.winner)
        assertTrue(over.byForfeit)
    }

    @Test
    fun unknownRoomCodeIsAnError() = testApplication {
        setup(this)
        val p = connect("Lost")
        p.send(ClientMessage.JoinRoom("ZZZZZ"))
        assertTrue(p.expect<ServerMessage.Error>().message.contains("ZZZZZ"))
    }
}
