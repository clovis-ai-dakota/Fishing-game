package com.deepline.fishing.core

/**
 * A fish swimming horizontally at a fixed depth. [direction] is +1 when facing/swimming
 * right and -1 when facing left; the head is always at the front.
 */
class FishEntity(
    val id: Int,
    val species: FishSpecies,
    val sparkling: Boolean,
    x: Float,
    val depth: Float,
    direction: Int,
    val baseSpeed: Float,
) {
    var x: Float = x
        internal set
    var direction: Int = direction
        internal set

    /** True once the hook bumped into this fish on the way down. */
    var touchedOnDescent: Boolean = false
        internal set

    /** The hook passed through this fish in the bump-free zone: it can't cost a bump on this descent. */
    var passedInBumpFreeZone: Boolean = false
        internal set
    var caught: Boolean = false
        internal set

    internal var fleeTimer = 0f
    internal var tooHeavyCooldown = 0f

    /** Seconds since this fish last turned around at the edge of the water. */
    var secondsSinceTurn: Float = Float.POSITIVE_INFINITY
        internal set

    val halfLength: Float get() = species.bodyLengthM / 2f
    val halfHeight: Float get() = species.bodyLengthM * species.heightRatio / 2f
    val isFleeing: Boolean get() = fleeTimer > 0f

    internal fun swim(dt: Float) {
        if (caught) return
        if (fleeTimer > 0f) fleeTimer -= dt
        if (tooHeavyCooldown > 0f) tooHeavyCooldown -= dt
        secondsSinceTurn += dt
        val speed = if (fleeTimer > 0f) baseSpeed * GameConfig.FLEE_SPEED_MULTIPLIER else baseSpeed
        x += direction * speed * dt
        val minX = halfLength
        val maxX = GameConfig.WORLD_WIDTH - halfLength
        if (x < minX) {
            x = minX
            direction = 1
            secondsSinceTurn = 0f
        } else if (x > maxX) {
            x = maxX
            direction = -1
            secondsSinceTurn = 0f
        }
    }
}

class Obstacle(
    val id: Int,
    val kind: ObstacleKind,
    val x: Float,
    val depth: Float,
    val radius: Float,
) {
    var touched: Boolean = false
        internal set

    /** The hook passed through this obstacle in the bump-free zone. */
    var passedInBumpFreeZone: Boolean = false
        internal set

    /** Hooked by the last bump: it rides up on the line. */
    var snagged: Boolean = false
        internal set
}

class World(
    val fish: List<FishEntity>,
    val obstacles: List<Obstacle>,
)

object Collision {
    /** Whether a circle of [radius] at ([x], [depth]) overlaps the fish's elliptical body. */
    fun overlapsBody(fish: FishEntity, x: Float, depth: Float, radius: Float): Boolean {
        val dx = x - fish.x
        val dy = depth - fish.depth
        val a = fish.halfLength + radius
        val b = fish.halfHeight + radius
        return (dx * dx) / (a * a) + (dy * dy) / (b * b) <= 1f
    }

    /**
     * Whether the hook is touching the fish's head: it must overlap the body and its center
     * must be within the front third of the fish (in the direction the fish faces).
     */
    fun touchesHead(fish: FishEntity, x: Float, depth: Float, radius: Float): Boolean =
        overlapsBody(fish, x, depth, radius) && (x - fish.x) * fish.direction >= fish.halfLength / 3f

    fun overlapsObstacle(obstacle: Obstacle, x: Float, depth: Float, radius: Float): Boolean {
        val dx = x - obstacle.x
        val dy = depth - obstacle.depth
        val r = obstacle.radius + radius
        return dx * dx + dy * dy <= r * r
    }
}
