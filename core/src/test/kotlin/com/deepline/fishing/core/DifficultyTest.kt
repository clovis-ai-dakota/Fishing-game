package com.deepline.fishing.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** A human-like good player: sees only what's on screen / lit, reacts in ~0.25 s. */
class GoodPlayer(private val random: Random, private val reaction: Float = 0.25f, private val noise: Float = 0.4f) {
    private var target: Float? = null
    private var timer = 0f
    fun steer(s: CastSession, dt: Float): Float? {
        timer -= dt
        if (timer > 0f) return target
        timer = reaction
        if (s.phase != CastPhase.DESCENDING) return null
        val dark = Lighting.darkness(s.hookDepth)
        val radius = Lighting.visibleRadius(s.lanternActive)
        // On a phone the hook sits 40% down a ~44 m tall view: ~26 m visible below it in the light.
        val look = if (dark > 0.5f) radius * 0.8f else 26f
        val r = GameConfig.HOOK_RADIUS
        data class H(val x: Float, val hw: Float, val eta: Float)
        val hz = ArrayList<H>()
        for (f in s.world.fish) {
            if (f.touchedOnDescent || f.passedInBumpFreeZone || f.caught || f.depth < GameConfig.BUMP_FREE_DEPTH) continue
            val dy = f.depth - s.hookDepth
            if (dy < -f.halfHeight || dy > look) continue
            if (dark > 0.5f && abs(f.x - s.hookX) > radius) continue
            val eta = dy.coerceAtLeast(0f) / GameConfig.descentSpeedAt(s.hookDepth)
            val fx = (f.x + f.direction * f.baseSpeed * eta).coerceIn(f.halfLength, GameConfig.WORLD_WIDTH - f.halfLength)
            val back = f.halfLength / 3f; val front = f.halfLength + r
            hz += H(fx + f.direction * (back + front) / 2f, (front - back) / 2f + 0.3f, eta)
        }
        for (o in s.world.obstacles) {
            if (o.touched || o.passedInBumpFreeZone || o.depth < GameConfig.BUMP_FREE_DEPTH) continue
            val dy = o.depth - s.hookDepth
            if (dy < -o.radius || dy > look) continue
            if (dark > 0.5f && abs(o.x - s.hookX) > radius) continue
            hz += H(o.x, o.radius + r + 0.3f, dy.coerceAtLeast(0f) / GameConfig.descentSpeedAt(s.hookDepth))
        }
        if (hz.isEmpty()) return target
        var best = s.hookX; var bestCost = Float.MAX_VALUE; var x = r
        while (x <= GameConfig.WORLD_WIDTH - r) {
            var c = abs(x - s.hookX) * 0.01f
            for (h in hz) {
                val reach = s.specs.steerSpeed * h.eta
                val at = s.hookX + (x - s.hookX).coerceIn(-reach, reach)
                if (abs(at - h.x) < h.hw) c += 1f / (1f + h.eta)
            }
            if (c < bestCost) { bestCost = c; best = x }
            x += 0.5f
        }
        target = best + (random.nextFloat() * 2f - 1f) * noise
        return target
    }
}

/**
 * Keeps the deep fair: a good player (the model above) with a fully upgraded rope reaches the end
 * of the rope in about a third of casts without a Lantern, a casual one much less, and a Lantern
 * makes it likely. If a change to the water, the hook or the rules moves these numbers, re-tune
 * [GameConfig.DARK_OBSTACLE_SPACING].
 */
class DifficultyTest {
    private fun rate(levels: Int, lantern: Boolean, n: Int, player: (Random) -> GoodPlayer): Double {
        val specs = RopeSpecs.fromLevels(RopeStat.entries.associateWith { levels })
        var bottom = 0
        repeat(n) { seed ->
            val s = CastSession(specs, lantern, WorldGenerator.generate(specs.lengthM, Random(seed + 1000L)))
            val p = player(Random(seed))
            var guard = 0
            while (s.phase == CastPhase.DESCENDING && guard++ < 100_000) s.update(1f / 60f, p.steer(s, 1f / 60f))
            if (s.reverseReason == ReverseReason.ROPE_END) bottom++
        }
        return bottom * 100.0 / n
    }

    @Test
    fun aGoodPlayerReachesTheBottomAboutOneCastInThree() {
        val good = rate(10, lantern = false, n = 400) { GoodPlayer(it) }
        val casual = rate(10, lantern = false, n = 200) { GoodPlayer(it, reaction = 0.35f, noise = 0.6f) }
        val withLantern = rate(10, lantern = true, n = 150) { GoodPlayer(it) }
        println("DIFFICULTY good $good%, casual $casual%, good with a Lantern $withLantern%")
        assertTrue(good in 27.0..40.0, "good player reached the bottom in $good% of casts")
        assertTrue(casual < good, "casual $casual% vs good $good%")
        assertTrue(withLantern > good + 20.0, "a Lantern should clearly help ($withLantern%)")
    }
}
