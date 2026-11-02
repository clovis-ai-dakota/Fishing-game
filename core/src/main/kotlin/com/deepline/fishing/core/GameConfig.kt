package com.deepline.fishing.core

/**
 * Every tunable number of the game lives here so balancing can be done in one place.
 * Distances are in meters, speeds in meters per second, weights in kilograms.
 */
object GameConfig {
    /** Width of the playable water column. */
    const val WORLD_WIDTH = 30f

    /** On the way down, the hook reverses after touching this many objects (fish or obstacles). */
    const val DESCENT_TOUCH_LIMIT = 4

    const val DESCENT_SPEED = 20f
    const val ASCENT_SPEED = 15f

    /** Collision radius of the hook. */
    const val HOOK_RADIUS = 0.6f

    /** Below this depth the water is dark; a Lantern widens what the player can see. */
    const val DARK_DEPTH = 300f
    /** Depth range over which darkness fades in after [DARK_DEPTH]. */
    const val DARK_FADE_RANGE = 25f
    const val VISIBLE_RADIUS_DARK = 5f
    const val VISIBLE_RADIUS_LANTERN = 14f

    /** Chance that a spawned fish is Sparkling, and how much more a Sparkling fish is worth. */
    const val SPARKLING_CHANCE = 0.06f
    const val SPARKLING_MULTIPLIER = 3

    /** Each 100 m of depth adds this much to the score multiplier (1.0 at the surface). */
    const val DEPTH_MULTIPLIER_PER_100M = 1f

    /** Average vertical spacing between spawned fish / obstacles. */
    const val FISH_SPACING = 3.5f
    const val FIRST_FISH_DEPTH = 6f
    const val OBSTACLE_SPACING = 22f
    const val FIRST_OBSTACLE_DEPTH = 25f

    /** A fish bumped on the way down darts away this much faster, for this long. */
    const val FLEE_SPEED_MULTIPLIER = 3f
    const val FLEE_DURATION = 1.2f

    const val LANTERN_PRICE = 600
    const val STARTING_LANTERNS = 1

    const val EVENT_DURATION_MS = 7L * 24 * 60 * 60 * 1000

    /** Simulation never advances more than this per frame, and sub-steps at this size. */
    const val MAX_FRAME_DT = 1f / 20f
    const val SUBSTEP_DT = 1f / 120f
}
