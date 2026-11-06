package com.deepline.fishing.feel

import com.deepline.fishing.core.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScreenShakeTest {
    @Test
    fun noTraumaNoShake() {
        val s = ScreenShake()
        repeat(30) { s.update(1f / 60f) }
        assertEquals(0f to 0f, s.offset())
        assertEquals(0f, s.intensity, 0f)
    }

    @Test
    fun traumaIsCappedAndTheShakeIsItsSquare() {
        val s = ScreenShake()
        s.add(0.5f)
        assertEquals(0.25f, s.intensity, 1e-6f)
        s.add(0.9f)
        assertEquals(1f, s.trauma, 0f)
        s.add(-3f) // negative trauma is ignored
        assertEquals(1f, s.trauma, 0f)
    }

    @Test
    fun offsetsStayWithinTheirBound() {
        val max = GameConfig.Feel.SHAKE_MAX_OFFSET
        for (trauma in listOf(0.2f, 0.6f, 1f)) {
            val s = ScreenShake()
            s.add(trauma)
            var peak = 0f
            repeat(240) {
                val (x, y) = s.offset()
                // Within ±max × trauma² at the trauma it had when sampled (it only decays).
                val bound = max * s.intensity + 1e-6f
                assertTrue("x=$x > $bound", abs(x) <= bound)
                assertTrue("y=$y > $bound", abs(y) <= bound)
                peak = maxOf(peak, abs(x), abs(y))
                s.update(1f / 240f)
            }
            assertTrue("A shake of trauma $trauma actually moves", peak > 0f)
        }
    }

    @Test
    fun traumaDecaysLinearlyToZero() {
        val s = ScreenShake(decayPerSec = 2f)
        s.add(1f)
        s.update(0.25f)
        assertEquals(0.5f, s.trauma, 1e-5f)
        s.update(0.3f)
        assertEquals(0f, s.trauma, 0f)
        assertEquals(0f to 0f, s.offset())
    }

    @Test
    fun theWobbleIsSmoothAndRepeatable() {
        // Smooth: consecutive samples at 120 Hz are close (no jitter between frames).
        for (ch in 0 until 4) {
            var prev = ScreenShake.noise(0f, ch)
            var t = 0f
            while (t < 5f) {
                t += 1f / 120f * GameConfig.Feel.SHAKE_FREQUENCY
                val v = ScreenShake.noise(t, ch)
                assertTrue(v in -1f..1f)
                assertTrue("noise jumped from $prev to $v", abs(v - prev) < 0.6f)
                prev = v
            }
        }
        // Repeatable: the same inputs give the same shake.
        val a = ScreenShake(seed = 3).apply { add(0.8f); update(0.1f) }
        val b = ScreenShake(seed = 3).apply { add(0.8f); update(0.1f) }
        assertEquals(a.offset(), b.offset())
    }
}
