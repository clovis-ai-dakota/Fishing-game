package com.deepline.fishing.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TackleTest {
    private val center = GameConfig.WORLD_WIDTH / 2f

    @Test
    fun tackleIsBoughtUsedAndCostsMoreWithALongerRope() {
        var p = PlayerProgress(pointsBalance = 100_000)
        for (item in Tackle.entries) {
            val before = Progression.tackleCount(p, item)
            val price = Progression.tacklePrice(p, item)
            p = Progression.buyTackle(p, item)!!
            assertEquals(before + 1, Progression.tackleCount(p, item))
            p = Progression.consumeTackle(p, item)!!
            assertEquals(before, Progression.tackleCount(p, item))
            assertTrue(price > 0)
        }
        assertNull(Progression.buyTackle(PlayerProgress(pointsBalance = 10), Tackle.LUCKY_CHARM))
        assertNull(Progression.consumeTackle(PlayerProgress(), Tackle.SPARKLE_LURE))
        for (item in Tackle.entries) assertTrue(item.price(10) > item.price(4) && item.price(4) > item.price(0))
        // Saved and loaded.
        val saved = PlayerProgress(luckyCharms = 3, sparkleLures = 2)
        val back = ProgressCodec.decode(ProgressCodec.encode(saved))
        assertEquals(3, back.luckyCharms)
        assertEquals(2, back.sparkleLures)
    }

    @Test
    fun aLuckyCharmGivesOneMoreBump() {
        val rocks = (0 until 6).map { i -> Obstacle(i, ObstacleKind.ROCK, center, 60f + i * 12f, 1f) }
        val s = CastSession(RopeSpecs(200f, 6f, 8f), false, World(emptyList(), rocks))
        s.bonusBumps = GameConfig.LUCKY_CHARM_EXTRA_BUMPS
        while (s.phase == CastPhase.DESCENDING) s.update(1f / 60f, null)
        assertEquals(GameConfig.DESCENT_TOUCH_LIMIT + 1, s.touches)
        assertTrue(rocks[GameConfig.DESCENT_TOUCH_LIMIT].snagged, "the extra bump is the last one, and it snags")
        while (s.phase != CastPhase.SURFACED) s.update(1f / 60f, null)
        assertEquals(GameConfig.DESCENT_TOUCH_LIMIT + 1, s.result!!.touchLimit)
    }

    @Test
    fun aSparkleLureBringsMoreSparklingFish() {
        fun sparkling(chance: Float) = (0 until 40).sumOf { seed ->
            WorldGenerator.generate(600f, Random(seed), chance).fish.count { it.sparkling }
        }
        val normal = sparkling(GameConfig.SPARKLING_CHANCE)
        val lured = sparkling(GameConfig.SPARKLING_CHANCE * GameConfig.SPARKLE_LURE_MULTIPLIER)
        assertTrue(lured > normal * 2, "$lured vs $normal")
    }

    @Test
    fun wagersPayByRivalAndRefundDraws() {
        val p = PlayerProgress(pointsBalance = 10_000)
        assertNull(Progression.placeWager(p, 20_000))
        val staked = Progression.placeWager(p, 5_000)!!
        assertEquals(5_000, staked.pointsBalance)
        val won = Progression.settleWager(staked, 5_000, BotDifficulty.HARD, won = true)
        assertEquals(5_000 + (5_000 * GameConfig.wagerPayout(BotDifficulty.HARD)).toLong(), won.pointsBalance)
        assertEquals(10_000, Progression.settleWager(staked, 5_000, BotDifficulty.EASY, won = null).pointsBalance)
        assertEquals(5_000, Progression.settleWager(staked, 5_000, BotDifficulty.NORMAL, won = false).pointsBalance)
        assertEquals(p.pointsEarned, won.pointsEarned, "winnings don't count towards milestones")
        assertTrue(GameConfig.wagerPayout(BotDifficulty.HARD) > GameConfig.wagerPayout(BotDifficulty.EASY))
    }
}
