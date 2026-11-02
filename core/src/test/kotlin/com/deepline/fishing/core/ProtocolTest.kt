package com.deepline.fishing.core

import com.deepline.fishing.core.online.CastValidation
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {
    @Test
    fun messagesRoundTripThroughJson() {
        val turn = BattleTurn(0, 1, 1, 42L, null)
        val cast = BotPlayer.playCast(OnlineProtocol.tournamentSpecs, 42L, BotDifficulty.HARD).toDto()
        val messages = listOf(
            ClientMessage.Hello("Ana"),
            ClientMessage.QuickMatch(BattleMode.DEEP_DIVE),
            ClientMessage.CancelWaiting,
            ClientMessage.CastFinished(turn, cast),
            ClientMessage.Forfeit,
        )
        for (m in messages) assertEquals(m, OnlineProtocol.decodeClient(OnlineProtocol.encode(m)))
        val start = ServerMessage.MatchStarted(BattleMode.TIME_ATTACK, 7L, 1, listOf("A", "B"))
        assertEquals(start, OnlineProtocol.decodeServer(OnlineProtocol.encode(start)))
        assertTrue(OnlineProtocol.encode(ClientMessage.Forfeit).contains("\"type\":\"forfeit\""))
        assertNull(OnlineProtocol.decodeClient("{not json"))
    }

    @Test
    fun validationRecomputesPointsAndRejectsImpossibleCatches() {
        val specs = OnlineProtocol.tournamentSpecs
        val turn = BattleTurn(0, 1, 0, 1L, 180f)
        val real = BotPlayer.playCast(specs, 3L, BotDifficulty.HARD)
        val rebuilt = assertNotNull(CastValidation.toBattleCast(real.toDto(), turn, specs))
        assertEquals(real.points, rebuilt.points)

        val dto = real.toDto().copy(durationSec = 9_999f)
        assertEquals(180f, CastValidation.toBattleCast(dto, turn, specs)!!.durationSec)
        val tooDeep = dto.copy(catches = listOf(com.deepline.fishing.core.online.CatchDto("pale_leviathan", false, 790f)))
        assertNull(CastValidation.toBattleCast(tooDeep, turn, specs), "beyond the tournament rope")
    }

    @Test
    fun onlineMirrorWaitsForServerConfirmation() {
        val match = OnlineMatch(ServerMessage.MatchStarted(BattleMode.HEAVY_HAUL, 5L, 1, listOf("A", "B")))
        val turn = assertNotNull(match.myNextTurn())
        assertEquals(1, turn.playerIndex)
        match.markSent(turn)
        assertNull(match.myNextTurn())
        val cast = BotPlayer.playCast(OnlineProtocol.tournamentSpecs, turn.seed, BotDifficulty.EASY).toDto()
        match.apply(ServerMessage.CastRecorded(turn, cast))
        assertEquals(2, match.myNextTurn()!!.castNumber)

        match.apply(ServerMessage.MatchOver(winner = 1, byForfeit = true, reason = "A left"))
        assertNull(match.myNextTurn())
        assertEquals(1, match.outcome!!.winner)
    }
}
