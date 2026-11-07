package com.deepline.fishing.feel

import com.deepline.fishing.core.GameConfig
import kotlin.math.floor

/**
 * Trauma-based screen shake (after Squirrel Eiserloh, "Math for Game Programmers: Juicing Your
 * Cameras With Math", GDC 2016): events add trauma (0..1), trauma decays linearly, and the shake
 * is trauma squared (small knocks barely move, big ones really do) times smooth noise, so it
 * wobbles instead of jittering. Only the drawing moves (see `CastFx`): never the hook, the fish
 * or what counts as a hit.
 *
 * Offsets are a share of the screen width, so the 3D and 2D views shake the same.
 */
class ScreenShake(
    private val maxOffset: Float = GameConfig.Feel.SHAKE_MAX_OFFSET,
    private val decayPerSec: Float = GameConfig.Feel.SHAKE_DECAY_PER_SEC,
    private val frequency: Float = GameConfig.Feel.SHAKE_FREQUENCY,
    private val seed: Int = 0,
) {
    var trauma: Float = 0f
        private set
    private var time = 0f

    /** Adds [amount] of trauma (capped at 1). */
    fun add(amount: Float) {
        trauma = (trauma + amount.coerceAtLeast(0f)).coerceAtMost(1f)
    }

    fun update(dtSec: Float) {
        val dt = dtSec.coerceAtLeast(0f)
        time += dt
        trauma = (trauma - decayPerSec * dt).coerceAtLeast(0f)
    }

    fun reset() {
        trauma = 0f
        time = 0f
    }

    /** How strong the shake is now: trauma squared. */
    val intensity: Float get() = trauma * trauma

    /** The current offset (x, y) as a share of the screen width; each within ±[maxOffset]. */
    fun offset(): Pair<Float, Float> {
        val k = maxOffset * intensity
        if (k == 0f) return 0f to 0f
        val t = time * frequency
        return k * noise(t, seed * 2 + 1) to k * noise(t, seed * 2 + 2)
    }

    companion object {
        /** Smooth 1D value noise in -1..1: random values at whole numbers, eased in between. */
        fun noise(t: Float, channel: Int): Float {
            val i = floor(t).toInt()
            val f = t - i
            val u = f * f * (3f - 2f * f)
            val a = lattice(i, channel)
            val b = lattice(i + 1, channel)
            return a + (b - a) * u
        }

        private fun lattice(i: Int, channel: Int): Float {
            var x = i * 374761393 + channel * 668265263
            x = (x xor (x ushr 13)) * 1274126177
            x = x xor (x ushr 16)
            return (x and 0xFFFF) / 32767.5f - 1f
        }
    }
}
