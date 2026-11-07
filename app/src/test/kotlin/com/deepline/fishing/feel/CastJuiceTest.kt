package com.deepline.fishing.feel

import com.deepline.fishing.core.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastJuiceTest {
    private val f = GameConfig.Feel

    @Test
    fun slowMotionIsAHitStopThenAnEaseBack() {
        assertEquals(1f, CastJuice.slowMoScale(-1f), 0f)
        assertEquals(f.HIT_STOP_SCALE, CastJuice.slowMoScale(0f), 0f)
        assertEquals(f.SLOW_MO_SCALE, CastJuice.slowMoScale(f.HIT_STOP_SECONDS), 1e-6f)
        assertEquals(1f, CastJuice.slowMoScale(f.HIT_STOP_SECONDS + f.SLOW_MO_SECONDS), 0f)
        assertEquals(1f, CastJuice.slowMoScale(Float.POSITIVE_INFINITY), 0f)
        var last = 0f
        var t = f.HIT_STOP_SECONDS
        while (t < f.HIT_STOP_SECONDS + f.SLOW_MO_SECONDS) {
            val k = CastJuice.slowMoScale(t)
            assertTrue(k >= last && k <= 1f)
            last = k
            t += 0.01f
        }
    }

    @Test
    fun slowMotionIsShortAndOnlyWhenAllowed() {
        val j = CastJuice()
        j.apply(Feedback(slowMo = true), 10f, 50f, 0f, reduceMotion = false)
        // A battle frame: always real time.
        assertEquals(1f / 60f, j.advance(1f / 60f, allowSlowMo = false), 0f)
        val k = CastJuice()
        k.apply(Feedback(slowMo = true), 10f, 50f, 0f, reduceMotion = false)
        var wall = 0f
        var cast = 0f
        while (wall < 2f) {
            cast += k.advance(1f / 60f, allowSlowMo = true)
            wall += 1f / 60f
        }
        // The whole moment costs a fraction of a second of game time, then the cast runs normally.
        val lost = wall - cast
        assertTrue("lost $lost s", lost > 0.1f && lost < f.HIT_STOP_SECONDS + f.SLOW_MO_SECONDS)
        assertEquals(1f, k.timeScale, 0f)
    }

    @Test
    fun theFlashFadesQuickly() {
        assertEquals(f.FLASH_ALPHA, CastJuice.flashAlpha(0f), 0f)
        assertTrue(CastJuice.flashAlpha(f.FLASH_SECONDS / 2f) < f.FLASH_ALPHA / 2f)
        assertEquals(0f, CastJuice.flashAlpha(f.FLASH_SECONDS), 0f)
        assertTrue(f.FLASH_ALPHA <= 0.5f)
        // Under three flashes a second (photosensitivity guidance): one per catch, and short.
        assertTrue(f.FLASH_SECONDS <= 0.34f)
    }

    @Test
    fun burstsMakeFiniteParticlesThatExpire() {
        for (kind in BurstKind.entries) {
            val burst = Burst(kind, 10f, if (kind == BurstKind.ENTRY_SPLASH || kind == BurstKind.SURFACE_SPLASH) 0f else 120f, 5f, 3, strength = 2f, glints = true)
            assertTrue("$kind before it starts", Particles.of(listOf(burst), 4.9f).isEmpty())
            var t = 5f
            var any = false
            while (t <= 5f + kind.life) {
                for (p in Particles.of(listOf(burst), t)) {
                    any = true
                    assertTrue(p.x.isFinite() && p.depth.isFinite() && p.radius > 0f && p.radius < 2f)
                    assertTrue(p.alpha in 0f..1f)
                    // Close to where they started: these are small effects around the hook.
                    assertTrue("$kind particle at ${p.x}", kotlin.math.abs(p.x - 10f) < 8f)
                    assertTrue("$kind particle at ${p.depth}", kotlin.math.abs(p.depth - burst.depth) < 8f)
                }
                t += 0.05f
            }
            assertTrue("$kind shows something", any)
            assertTrue("$kind is over", Particles.of(listOf(burst), 5f + kind.life + 0.01f).isEmpty())
        }
    }

    @Test
    fun splashesStayAboveTheWaterAndBubblesBelow() {
        val splash = Burst(BurstKind.ENTRY_SPLASH, 10f, 0f, 0f, 1)
        for (t in listOf(0.1f, 0.3f, 0.6f)) {
            for (p in Particles.of(listOf(splash), t)) {
                when (p.style) {
                    ParticleStyle.DROPLET -> assertTrue(p.depth <= 0.15f)
                    ParticleStyle.BUBBLE -> assertTrue(p.depth > 0f)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun reduceMotionKeepsOnlyTheParticles() {
        val j = CastJuice()
        j.apply(Feedback(trauma = 1f, flash = 1f, burst = BurstKind.BUMP_PUFF), 10f, 80f, 1f, reduceMotion = false)
        j.advance(0.01f, allowSlowMo = true)
        val full = j.frame(1.1f, reduceMotion = false)
        assertTrue(full.flash > 0f && (full.shakeX != 0f || full.shakeY != 0f) && full.particles.isNotEmpty())
        val calm = j.frame(1.1f, reduceMotion = true)
        assertEquals(0f, calm.flash, 0f)
        assertEquals(0f, calm.shakeX, 0f)
        assertEquals(0f, calm.shakeY, 0f)
        assertTrue(calm.particles.isNotEmpty())
        // Feedback applied while reduce motion is on adds no trauma or flash at all.
        val k = CastJuice()
        k.apply(Feedback(trauma = 1f, flash = 1f, slowMo = true), 10f, 80f, 1f, reduceMotion = true)
        assertEquals(0f, k.shake.trauma, 0f)
        assertEquals(0.5f, k.advance(0.5f, allowSlowMo = true), 0f)
    }

    @Test
    fun burstsAreCapped() {
        val j = CastJuice()
        repeat(100) { j.addBurst(BurstKind.BUMP_PUFF, 10f, 80f, 1f) }
        val n = j.frame(1.2f, reduceMotion = false).particles.size
        assertTrue(n <= CastJuice.MAX_BURSTS * 40)
    }
}
