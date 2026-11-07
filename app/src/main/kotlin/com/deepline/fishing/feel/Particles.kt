package com.deepline.fishing.feel

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** A kind of particle burst, and how long its particles live (cast seconds). */
enum class BurstKind(val life: Float) {
    /** The hook hitting the water at the start of a cast: spray, foam and a trail of bubbles. */
    ENTRY_SPLASH(1.1f),
    /** The line breaking the surface on the way back with fish on it. */
    SURFACE_SPLASH(1.1f),
    /** A bump: bubbles knocked out of the water around the hook. */
    BUMP_PUFF(0.9f),
    /** A catch: a stream of bubbles rising from the hook (glints too for a Sparkling fish). */
    CATCH_BUBBLES(1.3f),
}

/** A burst of particles at ([x], [depth]) in the water, started at [startSec] (cast seconds). */
data class Burst(
    val kind: BurstKind,
    val x: Float,
    val depth: Float,
    val startSec: Float,
    val seed: Int,
    /** 1 for a normal burst; more particles and bigger for stronger moments. */
    val strength: Float = 1f,
    /** Throws gold glints too (a Sparkling or legendary catch). */
    val glints: Boolean = false,
)

enum class ParticleStyle { DROPLET, FOAM, BUBBLE, GLINT }

/** One particle to draw: a position in the water (meters; depth < 0 is above the surface). */
data class Particle(val x: Float, val depth: Float, val radius: Float, val alpha: Float, val style: ParticleStyle)

/**
 * Where a burst's particles are at a given time: plain physics from a seeded random start, so the
 * same burst always looks the same and nothing needs storing per particle. Spray flies up and
 * falls back (gravity), bubbles are thrown out, slowed by the water and float up. Positions are in
 * world meters and the renderers draw them before the darkness layer, so they never light up
 * dark water. Purely visual: nothing here touches the game.
 */
object Particles {
    private const val GRAVITY = 18f
    private const val WATER_DRAG = 3.5f
    private const val BUOYANCY = 1.3f

    /** Adds [burst]'s live particles at cast time [nowSec] to [out]. */
    fun addTo(out: MutableList<Particle>, burst: Burst, nowSec: Float) {
        val t = nowSec - burst.startSec
        if (t < 0f || t > burst.kind.life) return
        val u = t / burst.kind.life
        val s = burst.strength.coerceIn(0.5f, 3f)
        when (burst.kind) {
            BurstKind.ENTRY_SPLASH, BurstKind.SURFACE_SPLASH -> splash(out, burst, t, u, s)
            BurstKind.BUMP_PUFF -> puff(out, burst, t, u, s)
            BurstKind.CATCH_BUBBLES -> rising(out, burst, t, u, s)
        }
    }

    fun of(bursts: List<Burst>, nowSec: Float): List<Particle> {
        val out = ArrayList<Particle>()
        for (b in bursts) addTo(out, b, nowSec)
        return out
    }

    private fun splash(out: MutableList<Particle>, b: Burst, t: Float, u: Float, s: Float) {
        val big = if (b.kind == BurstKind.SURFACE_SPLASH) 1.3f else 1f
        // Spray: droplets thrown up in a fan, falling back into the water.
        val drops = (12 * s * big).toInt()
        for (i in 0 until drops) {
            val a = (-PI / 2 + (rand(b.seed, i, 1) - 0.5f) * 2.1f).toFloat()
            val speed = (3f + 4.5f * rand(b.seed, i, 2)) * big
            val x = b.x + cos(a) * speed * t
            val depth = sin(a) * speed * t + 0.5f * GRAVITY * t * t
            if (depth > 0.15f) continue // back in the water
            out += Particle(x, depth, (0.07f + 0.09f * rand(b.seed, i, 3)) * big, 1f - u * 0.6f, ParticleStyle.DROPLET)
        }
        // Foam spreading along the surface.
        for (i in 0 until (8 * big).toInt()) {
            val side = if (i % 2 == 0) 1f else -1f
            val spread = (0.3f + 0.6f * rand(b.seed, i, 4)) * (1f - exp(-3f * t)) * 2.2f * big
            out += Particle(b.x + side * spread, 0.05f, (0.22f + 0.15f * rand(b.seed, i, 5)) * (1f - 0.5f * u), (1f - u) * 0.8f, ParticleStyle.FOAM)
        }
        // A trail of bubbles under the surface, floating back up.
        for (i in 0 until (7 * s).toInt()) {
            val start = 0.6f + 3.4f * rand(b.seed, i, 6)
            val depth = start - (BUOYANCY + 0.6f * rand(b.seed, i, 7)) * t
            if (depth < 0.2f) continue
            val x = b.x + (rand(b.seed, i, 8) - 0.5f) * 1.2f + sin(t * 6f + i) * 0.08f
            out += Particle(x, depth, 0.08f + 0.08f * rand(b.seed, i, 9), 1f - u, ParticleStyle.BUBBLE)
        }
    }

    private fun puff(out: MutableList<Particle>, b: Burst, t: Float, u: Float, s: Float) {
        val n = (10 * s).toInt()
        val travel = (1f - exp(-WATER_DRAG * t)) / WATER_DRAG
        for (i in 0 until n) {
            val a = (2 * PI * (i + rand(b.seed, i, 1)) / n).toFloat()
            val speed = (1.6f + 2.4f * rand(b.seed, i, 2)) * (0.8f + 0.2f * s)
            val x = b.x + cos(a) * speed * travel
            val depth = b.depth + sin(a) * speed * travel - BUOYANCY * t
            val style = if (i % 3 == 0) ParticleStyle.FOAM else ParticleStyle.BUBBLE
            out += Particle(x, depth, (0.06f + 0.08f * rand(b.seed, i, 3)) * (1f + 0.5f * u), 1f - u, style)
        }
    }

    private fun rising(out: MutableList<Particle>, b: Burst, t: Float, u: Float, s: Float) {
        val n = (12 * s).toInt()
        for (i in 0 until n) {
            val delay = 0.35f * rand(b.seed, i, 1)
            val tt = t - delay
            if (tt < 0f) continue
            val x = b.x + (rand(b.seed, i, 2) - 0.5f) * 1.6f + sin(tt * 7f + i) * 0.12f
            val depth = b.depth - (1.2f + 1.6f * rand(b.seed, i, 3)) * tt
            out += Particle(x, depth, 0.06f + 0.1f * rand(b.seed, i, 4), (1f - u) * (0.6f + 0.4f * rand(b.seed, i, 5)), ParticleStyle.BUBBLE)
        }
        if (b.glints) {
            val g = (8 * s).toInt()
            val travel = (1f - exp(-WATER_DRAG * t)) / WATER_DRAG
            for (i in 0 until g) {
                val a = (2 * PI * i / g + rand(b.seed, i, 6)).toFloat()
                val speed = 3f + 2f * rand(b.seed, i, 7)
                val twinkle = 0.6f + 0.4f * sin(t * 18f + i * 1.7f)
                out += Particle(b.x + cos(a) * speed * travel, b.depth + sin(a) * speed * travel, 0.18f + 0.12f * rand(b.seed, i, 8), (1f - u) * twinkle, ParticleStyle.GLINT)
            }
        }
    }

    /** A stable random number in 0..1 for particle [i] of a burst. */
    private fun rand(seed: Int, i: Int, k: Int): Float {
        var x = seed * 1274126177 + i * 374761393 + k * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777215f
    }
}
