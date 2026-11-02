package com.deepline.fishing.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class CastPhase { DESCENDING, ASCENDING, SURFACED }

enum class ReverseReason { TOUCH_LIMIT, ROPE_END }

data class CatchRecord(
    val species: FishSpecies,
    val sparkling: Boolean,
    val depth: Float,
    val points: Int,
)

data class CastResult(
    val catches: List<CatchRecord>,
    val maxDepth: Float,
    val touchesUsed: Int,
    val reverseReason: ReverseReason?,
    val lanternUsed: Boolean,
) {
    val totalPoints: Int get() = catches.sumOf { it.points }
    val totalWeightKg: Float get() = catches.sumOf { it.species.weightKg.toDouble() }.toFloat()
}

sealed interface CastEvent {
    data class Touched(val touchesUsed: Int) : CastEvent
    data class Reversed(val reason: ReverseReason) : CastEvent
    data class Caught(val record: CatchRecord) : CastEvent
    data class TooHeavy(val species: FishSpecies) : CastEvent
    data object Surfaced : CastEvent
}

/**
 * One cast of the line.
 *
 * Descent: the hook sinks until it has touched [GameConfig.DESCENT_TOUCH_LIMIT] objects
 * (fish or obstacles), or reaches the end of the rope, then reverses. Touching fish on the
 * way down therefore pulls the line up early.
 *
 * Ascent: the hook rises to the surface. Touching a fish's head hooks it, as long as the
 * total weight stays within the rope's limit. Touching the body or tail does nothing.
 */
class CastSession(
    val specs: RopeSpecs,
    val lanternActive: Boolean,
    val world: World,
    startX: Float = GameConfig.WORLD_WIDTH / 2f,
) {
    var phase: CastPhase = CastPhase.DESCENDING
        private set
    var hookX: Float = startX
        private set
    var hookDepth: Float = 0f
        private set
    var maxDepthReached: Float = 0f
        private set
    var touches: Int = 0
        private set
    var reverseReason: ReverseReason? = null
        private set
    var elapsedSeconds: Float = 0f
        private set

    private val _catches = ArrayList<CatchRecord>()
    val catches: List<CatchRecord> get() = _catches
    private val _caughtFish = ArrayList<FishEntity>()
    /** Fish currently hanging on the hook, in the order they were caught. */
    val caughtFish: List<FishEntity> get() = _caughtFish

    val carriedWeightKg: Float get() = _catches.sumOf { it.species.weightKg.toDouble() }.toFloat()
    val pointsSoFar: Int get() = _catches.sumOf { it.points }
    val touchesLeft: Int get() = (GameConfig.DESCENT_TOUCH_LIMIT - touches).coerceAtLeast(0)

    private val pendingEvents = ArrayList<CastEvent>()

    var result: CastResult? = null
        private set

    /** Returns and clears the events produced since the last call. */
    fun drainEvents(): List<CastEvent> {
        if (pendingEvents.isEmpty()) return emptyList()
        val copy = pendingEvents.toList()
        pendingEvents.clear()
        return copy
    }

    /**
     * Advances the simulation by [dt] seconds. [steerTargetX] is where the player wants the
     * hook to be horizontally (in world meters), or null to keep it where it is.
     */
    fun update(dt: Float, steerTargetX: Float?) {
        var remaining = dt.coerceIn(0f, GameConfig.MAX_FRAME_DT)
        while (remaining > 0f && phase != CastPhase.SURFACED) {
            val step = min(remaining, GameConfig.SUBSTEP_DT)
            step(step, steerTargetX)
            remaining -= step
        }
    }

    private fun step(dt: Float, steerTargetX: Float?) {
        elapsedSeconds += dt
        steer(dt, steerTargetX)
        for (fish in world.fish) fish.swim(dt)
        when (phase) {
            CastPhase.DESCENDING -> descend(dt)
            CastPhase.ASCENDING -> ascend(dt)
            CastPhase.SURFACED -> Unit
        }
    }

    private fun steer(dt: Float, targetX: Float?) {
        if (targetX == null) return
        val target = targetX.coerceIn(GameConfig.HOOK_RADIUS, GameConfig.WORLD_WIDTH - GameConfig.HOOK_RADIUS)
        val delta = target - hookX
        val maxMove = specs.steerSpeed * dt
        hookX = if (abs(delta) <= maxMove) target else hookX + maxMove * if (delta > 0) 1 else -1
    }

    private fun descend(dt: Float) {
        hookDepth += GameConfig.DESCENT_SPEED * dt
        if (hookDepth >= specs.lengthM) {
            hookDepth = specs.lengthM
            maxDepthReached = hookDepth
            reverse(ReverseReason.ROPE_END)
            return
        }
        maxDepthReached = hookDepth
        val r = GameConfig.HOOK_RADIUS
        for (fish in world.fish) {
            if (fish.touchedOnDescent || fish.caught) continue
            if (abs(fish.depth - hookDepth) > fish.halfLength + r) continue
            if (Collision.overlapsBody(fish, hookX, hookDepth, r)) {
                fish.touchedOnDescent = true
                fish.fleeTimer = GameConfig.FLEE_DURATION
                if (registerTouch()) return
            }
        }
        for (obstacle in world.obstacles) {
            if (obstacle.touched) continue
            if (abs(obstacle.depth - hookDepth) > obstacle.radius + r) continue
            if (Collision.overlapsObstacle(obstacle, hookX, hookDepth, r)) {
                obstacle.touched = true
                if (registerTouch()) return
            }
        }
    }

    /** Returns true when this touch made the line reverse. */
    private fun registerTouch(): Boolean {
        touches++
        pendingEvents += CastEvent.Touched(touches)
        if (touches >= GameConfig.DESCENT_TOUCH_LIMIT) {
            reverse(ReverseReason.TOUCH_LIMIT)
            return true
        }
        return false
    }

    private fun reverse(reason: ReverseReason) {
        phase = CastPhase.ASCENDING
        reverseReason = reason
        pendingEvents += CastEvent.Reversed(reason)
    }

    private fun ascend(dt: Float) {
        hookDepth -= GameConfig.ASCENT_SPEED * dt
        if (hookDepth <= 0f) {
            hookDepth = 0f
            surface()
            return
        }
        val r = GameConfig.HOOK_RADIUS
        for (fish in world.fish) {
            if (fish.caught) continue
            if (abs(fish.depth - hookDepth) > fish.halfLength + r) continue
            if (!Collision.touchesHead(fish, hookX, hookDepth, r)) continue
            if (carriedWeightKg + fish.species.weightKg <= specs.maxWeightKg + 1e-4f) {
                fish.caught = true
                val record = CatchRecord(
                    species = fish.species,
                    sparkling = fish.sparkling,
                    depth = fish.depth,
                    points = Scoring.points(fish.species, fish.depth, fish.sparkling),
                )
                _catches += record
                _caughtFish += fish
                pendingEvents += CastEvent.Caught(record)
            } else if (fish.tooHeavyCooldown <= 0f) {
                fish.tooHeavyCooldown = 1f
                pendingEvents += CastEvent.TooHeavy(fish.species)
            }
        }
    }

    private fun surface() {
        phase = CastPhase.SURFACED
        result = CastResult(
            catches = _catches.toList(),
            maxDepth = maxDepthReached,
            touchesUsed = touches,
            reverseReason = reverseReason,
            lanternUsed = lanternActive,
        )
        pendingEvents += CastEvent.Surfaced
    }

    companion object {
        fun create(specs: RopeSpecs, lanternActive: Boolean, random: Random = Random.Default): CastSession =
            CastSession(specs, lanternActive, WorldGenerator.generate(specs.lengthM, random))
    }
}
