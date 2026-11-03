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
    /** Hooked by the last bump on the way down rather than on the way up. */
    val byLastBump: Boolean = false,
    /** Caught on the way up after being bumped on the way down. */
    val bumpedOnDescent: Boolean = false,
)

/** An object (not a fish) hooked by the last bump. */
data class SnagRecord(
    val kind: ObstacleKind,
    val depth: Float,
    val points: Int,
)

data class CastResult(
    val catches: List<CatchRecord>,
    val maxDepth: Float,
    val touchesUsed: Int,
    val reverseReason: ReverseReason?,
    val lanternUsed: Boolean,
    val snags: List<SnagRecord> = emptyList(),
    /** What each bump on the way down hit, in order. */
    val bumps: List<BumpKind> = emptyList(),
    /** The rope's weight limit for this cast. */
    val lineStrengthKg: Float = Float.POSITIVE_INFINITY,
) {
    /** Points from catches and snags, before fishing-secret bonuses. */
    val basePoints: Int get() = catches.sumOf { it.points } + snags.sumOf { it.points }

    /** Bonuses from fishing secrets pulled off in this cast. */
    val secretBonuses: List<SecretBonus> = SecretRules.bonuses(this)

    val totalPoints: Int get() = basePoints + secretBonuses.sumOf { it.points }
    val totalWeightKg: Float
        get() = (catches.sumOf { it.species.weightKg.toDouble() } + snags.sumOf { it.kind.weightKg.toDouble() }).toFloat()

    /** True when the last bump hooked something (a fish or an object). */
    val lastBumpHooked: Boolean get() = snags.isNotEmpty() || catches.any { it.byLastBump }
}

sealed interface CastEvent {
    data class Touched(val touchesUsed: Int) : CastEvent
    data class Reversed(val reason: ReverseReason) : CastEvent
    data class Caught(val record: CatchRecord) : CastEvent
    data class TooHeavy(val species: FishSpecies) : CastEvent
    /** The last bump hooked what it hit: a fish ([fish]) or an object ([snag]). */
    data class LastBumpHooked(val fish: CatchRecord?, val snag: SnagRecord?) : CastEvent {
        val name: String get() = fish?.species?.name ?: snag!!.kind.displayName
        val points: Int get() = fish?.points ?: snag!!.points
    }
    data object Surfaced : CastEvent
}

/**
 * One cast of the line.
 *
 * Descent: the hook sinks until it has bumped [GameConfig.DESCENT_TOUCH_LIMIT] times, or reaches
 * the end of the rope, then reverses. A bump is touching a fish's head (the same spot that
 * catches it on the way up; bodies and tails don't count) or touching an obstacle anywhere.
 * Nothing counts in the first [GameConfig.BUMP_FREE_DEPTH] m. The last bump hooks what it hit:
 * the fish goes on the line, or the object is snagged (the only way to land one).
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

    /** When the last bump / catch happened ([elapsedSeconds]), for visual feedback. */
    var lastTouchAtSeconds: Float = Float.NEGATIVE_INFINITY
        private set
    var lastCatchAtSeconds: Float = Float.NEGATIVE_INFINITY
        private set

    /** Sideways speed of the hook (m/s, smoothed), e.g. to make the line trail behind it. */
    var hookVelocityX: Float = 0f
        private set

    private val _bumps = ArrayList<BumpKind>()
    private val _snags = ArrayList<SnagRecord>()
    val snags: List<SnagRecord> get() = _snags
    /** The object hooked by the last bump, if any, riding up on the line. */
    var snaggedObstacle: Obstacle? = null
        private set

    /** True while the hook is still in the bump-free zone on the way down. */
    val isBumpFree: Boolean get() = phase == CastPhase.DESCENDING && hookDepth < GameConfig.BUMP_FREE_DEPTH

    private val _catches = ArrayList<CatchRecord>()
    val catches: List<CatchRecord> get() = _catches
    private val _caughtFish = ArrayList<FishEntity>()
    /** Fish currently hanging on the hook, in the order they were caught. */
    val caughtFish: List<FishEntity> get() = _caughtFish

    val carriedWeightKg: Float
        get() = (_catches.sumOf { it.species.weightKg.toDouble() } + _snags.sumOf { it.kind.weightKg.toDouble() }).toFloat()
    val pointsSoFar: Int get() = _catches.sumOf { it.points } + _snags.sumOf { it.points }
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

    /**
     * Moves the hook towards the finger: speed proportional to the gap (so it glides onto the
     * finger and then tracks it), capped by the rope's agility, never overshooting.
     */
    private fun steer(dt: Float, targetX: Float?) {
        val before = hookX
        if (targetX != null) {
            val target = targetX.coerceIn(GameConfig.HOOK_RADIUS, GameConfig.WORLD_WIDTH - GameConfig.HOOK_RADIUS)
            val delta = target - hookX
            val speed = (abs(delta) * GameConfig.STEER_RESPONSIVENESS).coerceAtMost(specs.steerSpeed)
            val move = speed * dt
            hookX = if (abs(delta) <= move) target else hookX + move * if (delta > 0) 1 else -1
        }
        val velocity = if (dt > 0f) (hookX - before) / dt else 0f
        hookVelocityX += (velocity - hookVelocityX) * 0.15f
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
        val bumpFree = hookDepth < GameConfig.BUMP_FREE_DEPTH
        for (fish in world.fish) {
            if (fish.touchedOnDescent || fish.passedInBumpFreeZone || fish.caught) continue
            if (abs(fish.depth - hookDepth) > fish.halfLength + r) continue
            // Only the head counts: brushing a body or tail on the way down is free.
            if (!Collision.touchesHead(fish, hookX, hookDepth, r)) continue
            if (bumpFree) {
                fish.passedInBumpFreeZone = true
                continue
            }
            fish.touchedOnDescent = true
            _bumps += BumpKind.FISH_HEAD
            if (isLastBump()) {
                hookFishWithLastBump(fish)
                registerTouch()
                return
            }
            fish.fleeTimer = GameConfig.FLEE_DURATION
            registerTouch()
        }
        for (obstacle in world.obstacles) {
            if (obstacle.touched || obstacle.passedInBumpFreeZone) continue
            if (abs(obstacle.depth - hookDepth) > obstacle.radius + r) continue
            if (Collision.overlapsObstacle(obstacle, hookX, hookDepth, r)) {
                if (bumpFree) {
                    obstacle.passedInBumpFreeZone = true
                    continue
                }
                obstacle.touched = true
                _bumps += BumpKind.of(obstacle.kind)
                if (isLastBump()) {
                    snagWithLastBump(obstacle)
                    registerTouch()
                    return
                }
                registerTouch()
            }
        }
    }

    private fun isLastBump() = touches + 1 >= GameConfig.DESCENT_TOUCH_LIMIT

    /** The last bump hooks the fish it hit, whatever the weight limit: the line holds this one. */
    private fun hookFishWithLastBump(fish: FishEntity) {
        fish.caught = true
        val record = CatchRecord(
            species = fish.species,
            sparkling = fish.sparkling,
            depth = fish.depth,
            points = Scoring.points(fish.species, fish.depth, fish.sparkling),
            byLastBump = true,
        )
        _catches += record
        _caughtFish += fish
        lastCatchAtSeconds = elapsedSeconds
        pendingEvents += CastEvent.LastBumpHooked(fish = record, snag = null)
    }

    private fun snagWithLastBump(obstacle: Obstacle) {
        obstacle.snagged = true
        snaggedObstacle = obstacle
        val record = SnagRecord(obstacle.kind, obstacle.depth, Scoring.snagPoints(obstacle.kind, obstacle.depth))
        _snags += record
        lastCatchAtSeconds = elapsedSeconds
        pendingEvents += CastEvent.LastBumpHooked(fish = null, snag = record)
    }

    /** Counts a bump; returns true when it made the line reverse. */
    private fun registerTouch(): Boolean {
        touches++
        lastTouchAtSeconds = elapsedSeconds
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
                    bumpedOnDescent = fish.touchedOnDescent,
                )
                _catches += record
                _caughtFish += fish
                lastCatchAtSeconds = elapsedSeconds
                pendingEvents += CastEvent.Caught(record)
            } else if (fish.tooHeavyCooldown <= 0f) {
                fish.tooHeavyCooldown = 1f
                pendingEvents += CastEvent.TooHeavy(fish.species)
            }
        }
    }

    /**
     * Ends the cast immediately (e.g. when a battle timer runs out). Fish already on the line
     * are kept and scored.
     */
    fun endNow() {
        if (phase != CastPhase.SURFACED) surface()
    }

    private fun surface() {
        phase = CastPhase.SURFACED
        result = CastResult(
            catches = _catches.toList(),
            maxDepth = maxDepthReached,
            touchesUsed = touches,
            reverseReason = reverseReason,
            lanternUsed = lanternActive,
            snags = _snags.toList(),
            bumps = _bumps.toList(),
            lineStrengthKg = specs.maxWeightKg,
        )
        pendingEvents += CastEvent.Surfaced
    }

    companion object {
        fun create(specs: RopeSpecs, lanternActive: Boolean, random: Random = Random.Default): CastSession =
            CastSession(specs, lanternActive, WorldGenerator.generate(specs.lengthM, random))

        /** A cast on the water generated from [seed]: the same seed always gives the same fish. */
        fun seeded(specs: RopeSpecs, seed: Long, lanternActive: Boolean = false): CastSession =
            create(specs, lanternActive, Random(seed))
    }
}
