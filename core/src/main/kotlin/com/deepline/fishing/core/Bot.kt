package com.deepline.fishing.core

import kotlin.math.abs
import kotlin.random.Random

/** How well the computer rival plays. */
enum class BotDifficulty(
    val displayName: String,
    /** How far ahead (in meters) the bot looks for things to dodge or catch. */
    val lookaheadM: Float,
    /** Random aiming error, in meters. */
    val aimNoiseM: Float,
    /** How often the bot re-thinks its target, in seconds. */
    val replanSeconds: Float,
) {
    EASY("Easy", 6f, 1.3f, 0.5f),
    NORMAL("Normal", 8f, 0.9f, 0.3f),
    HARD("Hard", 16f, 0.15f, 0.1f),
}

/** Steering logic for the computer rival. It plays by exactly the same rules as a human. */
class BotBrain(private val difficulty: BotDifficulty, private val random: Random) {
    private var target: Float? = null
    private var replanTimer = 0f

    fun steer(session: CastSession, dt: Float): Float? {
        replanTimer -= dt
        if (replanTimer <= 0f) {
            replanTimer = difficulty.replanSeconds
            target = when (session.phase) {
                CastPhase.DESCENDING -> planDodge(session)
                CastPhase.ASCENDING -> planCatch(session)
                CastPhase.SURFACED -> null
            }?.let { it + (random.nextFloat() * 2f - 1f) * difficulty.aimNoiseM }
        }
        return target
    }

    private class Hazard(val x: Float, val halfWidth: Float, val eta: Float)

    /** Going down: pick the x that avoids the most upcoming fish heads and objects. */
    private fun planDodge(s: CastSession): Float? {
        // With one bump left, a Hard rival aims it at something worth hooking instead.
        if (difficulty == BotDifficulty.HARD && s.touchesLeft == 1) planLastBump(s)?.let { return it }
        val r = GameConfig.HOOK_RADIUS
        val margin = 0.3f
        val hazards = ArrayList<Hazard>()
        for (f in s.world.fish) {
            if (f.touchedOnDescent || f.passedInBumpFreeZone || f.caught) continue
            if (f.depth < GameConfig.BUMP_FREE_DEPTH) continue
            val dy = f.depth - s.hookDepth
            if (dy < -f.halfHeight || dy > difficulty.lookaheadM) continue
            val eta = dy.coerceAtLeast(0f) / GameConfig.DESCENT_SPEED
            // Only the head counts as a bump: it spans from a third of the way forward to the nose.
            val fishX = predictX(f, eta)
            val back = f.halfLength / 3f
            val front = f.halfLength + r
            hazards += Hazard(fishX + f.direction * (back + front) / 2f, (front - back) / 2f + margin, eta)
        }
        for (o in s.world.obstacles) {
            if (o.touched || o.passedInBumpFreeZone) continue
            if (o.depth < GameConfig.BUMP_FREE_DEPTH) continue
            val dy = o.depth - s.hookDepth
            if (dy < -o.radius || dy > difficulty.lookaheadM) continue
            hazards += Hazard(o.x, o.radius + r + margin, dy.coerceAtLeast(0f) / GameConfig.DESCENT_SPEED)
        }
        if (hazards.isEmpty()) return null

        var bestX = s.hookX
        var bestCost = Float.MAX_VALUE
        var x = r
        while (x <= GameConfig.WORLD_WIDTH - r) {
            var cost = abs(x - s.hookX) * 0.01f
            for (h in hazards) {
                val reach = s.specs.steerSpeed * h.eta
                val posAtArrival = s.hookX + (x - s.hookX).coerceIn(-reach, reach)
                if (abs(posAtArrival - h.x) < h.halfWidth) cost += 1f / (1f + h.eta)
            }
            if (cost < bestCost) {
                bestCost = cost
                bestX = x
            }
            x += 0.5f
        }
        return bestX
    }

    /** The x of the most valuable object or fish head ahead that the hook can still reach. */
    private fun planLastBump(s: CastSession): Float? {
        var best: Float? = null
        var bestValue = 0f
        for (o in s.world.obstacles) {
            if (o.touched || o.passedInBumpFreeZone || o.depth < GameConfig.BUMP_FREE_DEPTH) continue
            val dy = o.depth - s.hookDepth
            if (dy < 0.5f || dy > difficulty.lookaheadM) continue
            val eta = dy / GameConfig.DESCENT_SPEED
            if (abs(o.x - s.hookX) > s.specs.steerSpeed * eta) continue
            val value = Scoring.snagPoints(o.kind, o.depth).toFloat()
            if (value > bestValue) {
                bestValue = value
                best = o.x
            }
        }
        return best
    }

    /** Coming up: aim for the head of the most valuable reachable fish that still fits on the line. */
    private fun planCatch(s: CastSession): Float? {
        val capacity = s.specs.maxWeightKg - s.carriedWeightKg
        var best: Float? = null
        var bestValue = 0f
        for (f in s.world.fish) {
            if (f.caught || f.species.weightKg > capacity + 1e-4f) continue
            val dy = s.hookDepth - f.depth
            if (dy < 0.2f || dy > difficulty.lookaheadM) continue
            val eta = dy / GameConfig.ASCENT_SPEED
            val fishX = predictX(f, eta)
            val headX = fishX + f.direction * f.halfLength * 0.7f
            if (abs(headX - s.hookX) > s.specs.steerSpeed * eta + 0.3f) continue
            val value = Scoring.points(f.species, f.depth, f.sparkling) / (1f + eta * 0.3f)
            if (value > bestValue) {
                bestValue = value
                best = headX
            }
        }
        return best
    }

    private fun predictX(f: FishEntity, seconds: Float): Float {
        val speed = if (f.isFleeing) f.baseSpeed * GameConfig.FLEE_SPEED_MULTIPLIER else f.baseSpeed
        return (f.x + f.direction * speed * seconds).coerceIn(f.halfLength, GameConfig.WORLD_WIDTH - f.halfLength)
    }
}

object BotPlayer {
    private const val DT = 1f / 60f
    private const val MAX_SIM_SECONDS = 600f

    /** Plays a whole cast instantly. Stops early when [timeLimitSec] runs out. */
    fun playCast(
        specs: RopeSpecs,
        seed: Long,
        difficulty: BotDifficulty,
        timeLimitSec: Float? = null,
        random: Random = Random(seed xor 0x5DEECE66DL),
    ): BattleCast {
        val session = CastSession.seeded(specs, seed)
        val brain = BotBrain(difficulty, random)
        while (session.phase != CastPhase.SURFACED) {
            if (timeLimitSec != null && session.elapsedSeconds >= timeLimitSec) break
            if (session.elapsedSeconds >= MAX_SIM_SECONDS) break
            session.update(DT, brain.steer(session, DT))
        }
        session.endNow()
        val duration = timeLimitSec?.let { minOf(it, session.elapsedSeconds) } ?: session.elapsedSeconds
        return BattleCast(session.result!!, duration)
    }
}
