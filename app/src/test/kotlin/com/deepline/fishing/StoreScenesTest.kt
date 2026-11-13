package com.deepline.fishing

import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.Secrets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store screenshots' casts show what docs/store/screenshots.md says, and give away no secret. */
class StoreScenesTest {
    @Test
    fun descentIsAFreshCastInTheShallows() {
        val s = StoreScenes.descent.play()
        assertEquals(CastPhase.DESCENDING, s.phase)
        assertTrue(s.hookDepth in 15f..25f)
        assertEquals(GameConfig.DESCENT_TOUCH_LIMIT, s.touchesLeft)
    }

    @Test
    fun bumpIsTheSecondBumpOnAJellyInTheTwilightReef() {
        val s = StoreScenes.bump.play()
        assertEquals(CastPhase.DESCENDING, s.phase)
        assertTrue("${s.hookDepth}", s.hookDepth in 120f..180f)
        assertEquals(2, s.touchesLeft)
        assertTrue(s.world.obstacles.single { it.touched && it.kind == ObstacleKind.JELLY }.depth in 120f..180f)
    }

    @Test
    fun ascentHooksAThirdFishWithTheLineHalfFull() {
        val s = StoreScenes.ascent.play()
        assertEquals(CastPhase.ASCENDING, s.phase)
        assertEquals(ReverseReason.ROPE_END, s.reverseReason)
        assertEquals(0, s.touches)
        assertEquals(3, s.catches.size)
        assertEquals(DepthZone.TWILIGHT, DepthZone.of(s.hookDepth))
        val load = s.carriedWeightKg / s.specs.maxWeightKg
        assertTrue("load $load", load in 0.4f..0.6f)
    }

    @Test
    fun darkIsTheMidnightTrenchWithALantern() {
        val s = StoreScenes.dark.play()
        assertEquals(CastPhase.DESCENDING, s.phase)
        assertTrue(s.lanternActive)
        assertTrue(s.hookDepth in 340f..380f && s.hookDepth > GameConfig.DARK_DEPTH)
        assertEquals(0, s.touches)
        assertTrue(s.world.fish.any { it.species.glowing })
    }

    @Test
    fun resultsHaveASparklingCatchAndNoSecret() {
        val s = StoreScenes.results.play()
        val result = s.result!!
        assertTrue(result.catches.any { it.sparkling })
        assertTrue(result.catches.size >= 4)
        assertTrue("secrets: ${Secrets.revealedBy(result)}", Secrets.revealedBy(result).isEmpty())
        assertTrue(result.secretBonuses.isEmpty())
    }
}
