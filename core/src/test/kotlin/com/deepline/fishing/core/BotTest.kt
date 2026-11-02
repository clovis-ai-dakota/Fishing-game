package com.deepline.fishing.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotTest {
    private val specs = RopeSpecs.fromLevels(mapOf(RopeStat.LENGTH to 4, RopeStat.STRENGTH to 4, RopeStat.AGILITY to 4))

    private fun average(difficulty: BotDifficulty): Double =
        (1L..30L).map { BotPlayer.playCast(specs, it, difficulty).points }.average()

    @Test
    fun botIsDeterministicForAGivenSeed() {
        val a = BotPlayer.playCast(specs, 42, BotDifficulty.NORMAL)
        val b = BotPlayer.playCast(specs, 42, BotDifficulty.NORMAL)
        assertEquals(a, b)
    }

    @Test
    fun botCatchesFishAndHarderBotsScoreMore() {
        val easy = average(BotDifficulty.EASY)
        val normal = average(BotDifficulty.NORMAL)
        val hard = average(BotDifficulty.HARD)
        println("bot averages: easy=$easy normal=$normal hard=$hard")
        assertTrue(easy > 0.0)
        assertTrue(hard > easy, "hard=$hard easy=$easy")
    }

    @Test
    fun botRespectsTheTimeLimitAndWeightLimit() {
        val cast = BotPlayer.playCast(specs, 7, BotDifficulty.HARD, timeLimitSec = 5f)
        assertTrue(cast.durationSec <= 5f)
        for (seed in 1L..10L) {
            val c = BotPlayer.playCast(specs, seed, BotDifficulty.HARD)
            assertTrue(c.result.totalWeightKg <= specs.maxWeightKg + 1e-3f)
        }
    }
}
