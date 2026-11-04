package com.deepline.fishing.core

/**
 * Every tunable number of the game lives here so balancing can be done in one place.
 * Distances are in meters, speeds in meters per second, weights in kilograms.
 */
object GameConfig {
    /** Width of the playable water column (and of the screen). */
    const val WORLD_WIDTH = 20f

    /**
     * Fish are drawn and collide at this multiple of their catalog length, so they are big
     * enough on a phone screen to aim at their heads.
     */
    const val FISH_SIZE_SCALE = 2f

    /** On the way down, the hook reverses after touching this many objects (fish or obstacles). */
    const val DESCENT_TOUCH_LIMIT = 4

    /**
     * Touches in the first meters of the descent don't count, so a cast that lands right on a
     * fish doesn't cost a bump. Anything the hook overlaps in this zone is let through for the
     * rest of the descent, even if the hook is still touching it when it leaves the zone.
     */
    const val BUMP_FREE_DEPTH = 50f

    const val DESCENT_SPEED = 18f

    /**
     * How eagerly the hook closes the gap to the player's finger: its sideways speed is the gap
     * times this (per second), capped by the rope's agility. It glides onto the finger and then
     * tracks it instead of moving at a fixed speed and stopping dead.
     */
    const val STEER_RESPONSIVENESS = 12f
    const val ASCENT_SPEED = 15f

    /**
     * Fast reel: once nothing more can fit on the line, the player may haul the hook up so it
     * reaches the boat in at most this long, wherever it is (shallow hooks take less).
     */
    const val FAST_REEL_MAX_SECONDS = 1.5f

    /** How long the fast reel takes to reach full speed (it accelerates from the reeling speed). */
    const val FAST_REEL_RAMP_SECONDS = 0.25f

    /** Collision radius of the hook. */
    const val HOOK_RADIUS = 0.6f

    /** Below this depth the water is dark; a Lantern widens what the player can see. */
    const val DARK_DEPTH = 300f
    /** Depth range over which darkness fades in after [DARK_DEPTH]. */
    const val DARK_FADE_RANGE = 25f
    const val VISIBLE_RADIUS_DARK = 6f
    /** 30% further than the first Lantern (14 m): with 200 species the deep needs it. */
    const val VISIBLE_RADIUS_LANTERN = 18.2f

    /** Chance that a spawned fish is Sparkling, and how much more a Sparkling fish is worth. */
    const val SPARKLING_CHANCE = 0.06f
    const val SPARKLING_MULTIPLIER = 3

    /** Each 100 m of depth adds this much to the score multiplier (1.0 at the surface). */
    const val DEPTH_MULTIPLIER_PER_100M = 1f

    /** Average vertical spacing between spawned fish / obstacles. */
    const val FISH_SPACING = 4.5f
    const val FIRST_FISH_DEPTH = 6f
    const val OBSTACLE_SPACING = 22f
    const val FIRST_OBSTACLE_DEPTH = 25f

    /** A fish bumped on the way down darts away this much faster, for this long. */
    const val FLEE_SPEED_MULTIPLIER = 3f
    const val FLEE_DURATION = 1.2f

    /**
     * The legendary 200th fish lives only at the very bottom: the last meters of a fully upgraded
     * rope. It appears in this share of the casts that can reach it.
     */
    const val SOVEREIGN_MIN_DEPTH = 600f
    const val SOVEREIGN_MAX_DEPTH = 620f
    const val SOVEREIGN_SPAWN_CHANCE = 0.05f

    /** A fish counts as "just turned" at the edge for this long (Corner pocket). */
    const val WALL_TURN_WINDOW = 0.3f

    /**
     * Dex: a fish is "seen" (name and depths shown in the Dex) when it comes this close to the
     * hook. In dark water it must also be inside the lit circle around the hook.
     */
    const val SEEN_DISTANCE_M = 10f

    /** Dex rarity stars: a species gets one extra star per value its spawn weight is at or below. */
    val RARITY_STAR_WEIGHTS = listOf(17, 9, 4, 0)

    const val LANTERN_PRICE = 600
    const val STARTING_LANTERNS = 1

    /** Simulation never advances more than this per frame, and sub-steps at this size. */
    const val MAX_FRAME_DT = 1f / 20f
    const val SUBSTEP_DT = 1f / 120f
}
