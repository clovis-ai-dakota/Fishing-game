package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleTest {
    private val sprat = FishCatalog.byId("silver_sprat")!!
    private val sturgeon = FishCatalog.byId("iron_sturgeon")!!
    private val hake = FishCatalog.byId("ghost_hake")!!

    private fun players() = listOf(BattlePlayer("A"), BattlePlayer("B"))

    private fun cast(points: Int, duration: Float = 30f, vararg fish: Pair<FishSpecies, Float>): BattleCast {
        val catches = if (fish.isEmpty()) {
            listOf(CatchRecord(sprat, false, 10f, points))
        } else {
            fish.mapIndexed { i, (s, d) -> CatchRecord(s, false, d, if (i == 0) points else 0) }
        }
        return BattleCast(CastResult(catches, 100f, 0, null, false), duration)
    }

    private fun play(b: Battle, c: BattleCast) = b.record(b.nextTurn()!!, c)

    @Test
    fun bestOfFiveAlternatesAndEndsAtThreeRoundWins() {
        val b = Battle(BattleMode.BEST_OF_FIVE, players(), seed = 1)
        val t1 = b.nextTurn()!!
        assertEquals(0, t1.playerIndex)
        b.record(t1, cast(100))
        val t2 = b.nextTurn()!!
        assertEquals(1, t2.playerIndex)
        assertEquals(t1.seed, t2.seed, "both players fish the same water in a round")
        b.record(t2, cast(50))
        assertEquals(1, b.roundWins(0))

        // Tied round counts for nobody.
        play(b, cast(70)); play(b, cast(70))
        assertEquals(listOf(null), b.rounds.drop(1).map { it.winner })

        play(b, cast(10)); play(b, cast(90))
        play(b, cast(80)); play(b, cast(20))
        assertNull(b.outcome())
        play(b, cast(99)); play(b, cast(1))
        val outcome = assertNotNull(b.outcome())
        assertEquals(0, outcome.winner)
        assertEquals(3, b.roundWins(0))
        assertNull(b.nextTurn())
    }

    @Test
    fun bestOfFiveIsCappedWhenRoundsKeepTying() {
        val b = Battle(BattleMode.BEST_OF_FIVE, players(), seed = 2)
        repeat(Battle.MAX_ROUNDS) { play(b, cast(5)); play(b, cast(5)) }
        assertTrue(b.isOver)
        assertNull(assertNotNull(b.outcome()).winner)
    }

    @Test
    fun timeAttackGivesEachPlayerThreeMinutesAndCountsTheBestCast() {
        val b = Battle(BattleMode.TIME_ATTACK, players(), seed = 3)
        val first = b.nextTurn()!!
        assertEquals(180f, first.timeLimitSec)
        b.record(first, cast(40, duration = 100f))
        val second = b.nextTurn()!!
        assertEquals(0, second.playerIndex)
        assertEquals(80f, second.timeLimitSec!!, 0.001f)
        b.record(second, cast(120, duration = 80f))

        val p2 = b.nextTurn()!!
        assertEquals(1, p2.playerIndex)
        assertEquals(first.seed, p2.seed, "cast N uses the same water for both players")
        b.record(p2, cast(100, duration = 180f))

        assertTrue(b.isOver)
        assertEquals("120 pts best cast", b.score(0).label)
        assertEquals(0, b.outcome()!!.winner)
    }

    @Test
    fun heavyHaulCountsKilograms() {
        val b = Battle(BattleMode.HEAVY_HAUL, players(), seed = 4)
        repeat(3) { play(b, cast(500, 30f, sprat to 10f)) }
        repeat(3) { play(b, cast(10, 30f, sturgeon to 400f)) }
        assertEquals(1, b.outcome()!!.winner)
    }

    @Test
    fun speciesHuntCountsDistinctSpeciesThenPoints() {
        val b = Battle(BattleMode.SPECIES_HUNT, players(), seed = 5)
        play(b, cast(10, 30f, sprat to 10f, hake to 300f))
        play(b, cast(10, 30f, sprat to 10f))
        play(b, cast(10, 30f, sprat to 10f))
        play(b, cast(50, 30f, hake to 300f))
        play(b, cast(50, 30f, sprat to 10f))
        play(b, cast(50, 30f, sprat to 10f))
        assertEquals(b.score(0).primary, b.score(1).primary)
        assertEquals(1, b.outcome()!!.winner, "same species count, B has more points")
    }

    @Test
    fun deepDiveUsesTheDeepestCatch() {
        val b = Battle(BattleMode.DEEP_DIVE, players(), seed = 6)
        play(b, cast(999, 30f, sprat to 50f)); play(b, cast(0)); play(b, cast(0))
        play(b, cast(1, 30f, hake to 450f)); play(b, cast(0)); play(b, cast(0))
        assertEquals(1, b.outcome()!!.winner)
    }

    @Test
    fun forfeitHandsTheWinToTheOpponent() {
        val b = Battle(BattleMode.HEAVY_HAUL, players(), seed = 7)
        play(b, cast(10))
        b.forfeit(0)
        assertEquals(BattleOutcome(winner = 1, byForfeit = true), b.outcome())
        assertNull(b.nextTurn())
    }

    @Test
    fun outOfOrderTurnsAreRejected() {
        val b = Battle(BattleMode.HEAVY_HAUL, players(), seed = 8)
        val wrong = b.nextTurn()!!.copy(castNumber = 2)
        assertFailsWith<IllegalStateException> { b.record(wrong, cast(1)) }
    }

    @Test
    fun onlinePlayersCanFishAtTheSameTimeButBestOfFiveWaitsForTheRound() {
        val b = Battle(BattleMode.BEST_OF_FIVE, players(), seed = 9)
        val p1 = b.nextTurnFor(1)!!
        b.record(p1, cast(10)) // player 2 finishes round 1 first
        assertNull(b.nextTurnFor(1), "player 2 waits for player 1 to finish round 1")
        b.record(b.nextTurnFor(0)!!, cast(20))
        assertEquals(0, b.rounds.single().winner)
        assertEquals(2, b.nextTurnFor(1)!!.round)

        val haul = Battle(BattleMode.HEAVY_HAUL, players(), seed = 10)
        haul.record(haul.nextTurnFor(1)!!, cast(1))
        haul.record(haul.nextTurnFor(1)!!, cast(1))
        assertEquals(3, haul.nextTurnFor(1)!!.castNumber)
        assertEquals(1, haul.nextTurnFor(0)!!.castNumber)
    }
}
