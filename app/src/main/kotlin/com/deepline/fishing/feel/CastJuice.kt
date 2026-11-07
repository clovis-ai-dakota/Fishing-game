package com.deepline.fishing.feel

import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.ui.ColorScript

/**
 * What the renderers add to one frame of a cast, as plain data (both the 3D view and the 2D
 * fallback draw it the same way):
 *
 * - [shakeX], [shakeY]: how far to move the whole drawing, as a share of the screen width. Only
 *   the picture moves: the hook, the fish and hit detection never do.
 * - [flash] (0..1) in [flashColor]: a tint over the scene, drawn under the darkness layer so it
 *   never lights up dark water.
 * - [particles]: splashes and bubbles, also drawn under the darkness.
 */
class CastFx(
    val shakeX: Float = 0f,
    val shakeY: Float = 0f,
    val flash: Float = 0f,
    val flashColor: Long = ColorScript.CATCH_FLASH,
    val particles: List<Particle> = emptyList(),
) {
    companion object {
        val NONE = CastFx()
    }
}

/**
 * The render-only effects of the cast in progress: shake trauma, the slow-motion moment, the
 * flash and the particle bursts. Reset at every cast. Wall-clock effects (shake, slow motion,
 * flash) advance with [advance]; bursts live in cast time, so they freeze with a pause and slow
 * down with the slow motion like everything else.
 */
class CastJuice {
    val shake = ScreenShake()
    private var wall = 0f
    private var slowMoAt = Float.NEGATIVE_INFINITY
    private var flashAt = Float.NEGATIVE_INFINITY
    private var flashStrength = 0f
    private var flashGold = false
    private val bursts = ArrayList<Burst>()
    private var nextSeed = 1

    /** How fast the cast runs now (1: normal). Below 1 only during a solo slow-motion moment. */
    var timeScale = 1f
        private set

    fun reset() {
        shake.reset()
        wall = 0f
        slowMoAt = Float.NEGATIVE_INFINITY
        flashAt = Float.NEGATIVE_INFINITY
        flashStrength = 0f
        bursts.clear()
        timeScale = 1f
    }

    /**
     * Moves the wall clock on by [dt] seconds and returns how much cast time passes in that frame:
     * [dt] itself, or less during a slow-motion moment. [allowSlowMo] is false in battles (both
     * players must get the same real time) and with Reduce motion.
     */
    fun advance(dt: Float, allowSlowMo: Boolean): Float {
        wall += dt.coerceAtLeast(0f)
        shake.update(dt)
        timeScale = if (allowSlowMo) slowMoScale(wall - slowMoAt) else 1f
        return dt * timeScale
    }

    /** Applies the render side of [f] (sounds and haptics are [GameFeel]'s). */
    fun apply(f: Feedback, x: Float, depth: Float, castSec: Float, reduceMotion: Boolean) {
        if (!reduceMotion) {
            shake.add(f.trauma)
            if (f.slowMo) slowMoAt = wall
            if (f.flash > 0f) {
                flashAt = wall
                flashStrength = f.flash
                flashGold = f.goldFlash
            }
        }
        f.burst?.let { addBurst(it, x, depth, castSec, f.burstStrength, f.glints) }
    }

    fun addBurst(kind: BurstKind, x: Float, depth: Float, castSec: Float, strength: Float = 1f, glints: Boolean = false) {
        bursts.removeAll { castSec - it.startSec > it.kind.life }
        while (bursts.size >= MAX_BURSTS) bursts.removeAt(0)
        bursts += Burst(kind, x, depth, castSec, nextSeed++, strength, glints)
    }

    /** What to draw this frame, at cast time [castSec]. Reduce motion keeps the particles only. */
    fun frame(castSec: Float, reduceMotion: Boolean): CastFx {
        val particles = Particles.of(bursts, castSec)
        if (reduceMotion) return if (particles.isEmpty()) CastFx.NONE else CastFx(particles = particles)
        val (sx, sy) = shake.offset()
        val f = flashAlpha(wall - flashAt) * flashStrength
        return CastFx(sx, sy, f, if (flashGold) ColorScript.GOLD_FLASH else ColorScript.CATCH_FLASH, particles)
    }

    companion object {
        const val MAX_BURSTS = 16

        /** The time scale [since] seconds after a slow-motion moment began: hit-stop, then ease back. */
        fun slowMoScale(since: Float): Float {
            val stop = GameConfig.Feel.HIT_STOP_SECONDS
            val slow = GameConfig.Feel.SLOW_MO_SECONDS
            return when {
                since < 0f || since.isNaN() -> 1f
                since < stop -> GameConfig.Feel.HIT_STOP_SCALE
                since < stop + slow -> {
                    val u = (since - stop) / slow
                    GameConfig.Feel.SLOW_MO_SCALE + (1f - GameConfig.Feel.SLOW_MO_SCALE) * u * u
                }
                else -> 1f
            }
        }

        /** The flash's opacity [since] seconds after it fired (fading out). */
        fun flashAlpha(since: Float): Float {
            if (since < 0f || since.isNaN() || since >= GameConfig.Feel.FLASH_SECONDS) return 0f
            val u = since / GameConfig.Feel.FLASH_SECONDS
            return GameConfig.Feel.FLASH_ALPHA * (1f - u) * (1f - u)
        }
    }
}
