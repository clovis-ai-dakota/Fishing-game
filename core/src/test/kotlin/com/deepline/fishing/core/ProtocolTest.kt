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
    fun snagsAreValidatedAndRescored() {
        val specs = OnlineProtocol.tournamentSpecs
        val turn = BattleTurn(0, 1, 0, 1L, null)
        val snag = com.deepline.fishing.core.online.SnagDto(ObstacleKind.ROCK, 120f, points = 999_999)
        val bumps = listOf(BumpKind.DRIFTWOOD, BumpKind.DRIFTWOOD, BumpKind.ROCK, BumpKind.ROCK)
        val dto = com.deepline.fishing.core.online.CastDto(emptyList(), 120f, GameConfig.DESCENT_TOUCH_LIMIT, ReverseReason.TOUCH_LIMIT, 20f, listOf(snag), bumps)
        assertEquals(dto, OnlineProtocol.decodeClient(OnlineProtocol.encode(ClientMessage.CastFinished(turn, dto)))!!.let { (it as ClientMessage.CastFinished).cast })

        val cast = assertNotNull(CastValidation.toBattleCast(dto, turn, specs))
        assertEquals(Scoring.snagPoints(ObstacleKind.ROCK, 120f), cast.points)

        assertNull(CastValidation.toBattleCast(dto.copy(touchesUsed = 2, bumps = bumps.take(2)), turn, specs), "only the last bump snags")
        assertNull(CastValidation.toBattleCast(dto.copy(bumps = bumps.take(3) + BumpKind.JELLY), turn, specs), "last bump didn't hit a rock")
        assertNull(CastValidation.toBattleCast(dto.copy(snags = listOf(snag, snag)), turn, specs), "one hook per cast")
        assertNull(CastValidation.toBattleCast(dto.copy(snags = listOf(snag.copy(depth = 10f))), turn, specs), "bump-free zone")
    }

    @Test
    fun secretBonusesAreRecomputedAndFakedFactsRejected() {
        val specs = OnlineProtocol.tournamentSpecs
        val turn = BattleTurn(0, 1, 0, 1L, null)
        fun c(id: String, depth: Float, bumped: Boolean = false) = com.deepline.fishing.core.online.CatchDto(id, false, depth, 0, false, bumped)
        // A genuine ladder: caught on the way up (deepest first), lightest first.
        val ladder = listOf(c("silver_sprat", 80f), c("coral_darter", 60f), c("reed_perch", 40f), c("sun_bream", 35f))
        val dto = com.deepline.fishing.core.online.CastDto(ladder, 370f, 0, ReverseReason.ROPE_END, 40f)
        val cast = assertNotNull(CastValidation.toBattleCast(dto, turn, specs))
        assertEquals(setOf(Secrets.LADDER, Secrets.CLEAN_DROP), cast.result.secretBonuses.map { it.secretId }.toSet())
        assertTrue(cast.points > cast.result.basePoints)

        // Reordering catches against the ascent to fake a ladder is rejected.
        val faked = listOf(c("silver_sprat", 30f), c("coral_darter", 50f), c("reed_perch", 80f), c("sun_bream", 120f))
        assertNull(CastValidation.toBattleCast(dto.copy(catches = faked), turn, specs))
        // A "bumped then caught" fish needs a fish-head bump.
        assertNull(CastValidation.toBattleCast(dto.copy(catches = listOf(c("silver_sprat", 50f, bumped = true))), turn, specs))
        // Bump facts must match the bumps used.
        assertNull(CastValidation.toBattleCast(dto.copy(touchesUsed = 1), turn, specs))
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
