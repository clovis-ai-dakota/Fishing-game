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
     * Obstacles thin out in the dark (below [DARK_DEPTH]: meters between them, on average),
     * where only a few meters ahead can be seen. Tuned with a human-like "good player" model
     * (sees only the screen or the lit circle, reacts in 0.25 s, fully upgraded rope) so the end
     * of a full rope is reached in about a third of casts without a Lantern (DifficultyTest).
     */
    const val DARK_OBSTACLE_SPACING = 30f

    fun obstacleSpacingAt(depth: Float): Float = if (depth < DARK_DEPTH) OBSTACLE_SPACING else DARK_OBSTACLE_SPACING

    /** The hook also sinks a little slower in the dark, for a fairer reaction time (same tuning). */
    const val DARK_DESCENT_SPEED = 17f

    fun descentSpeedAt(depth: Float): Float {
        val k = ((depth - DARK_DEPTH) / 40f).coerceIn(0f, 1f)
        return DESCENT_SPEED + (DARK_DESCENT_SPEED - DESCENT_SPEED) * k
    }

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

    /**
     * The reference price of tackle (Lanterns, charms, lures) by Rope Length level, about the
     * points a good player earns per cast at that reach (measured by the career simulation in
     * EconomyTest). Each item costs a fraction of it, see [Tackle].
     */
    val TACKLE_PRICE_BY_LENGTH_LEVEL = intArrayOf(700, 900, 1_400, 2_200, 4_000, 6_000, 7_500, 9_500, 13_000, 15_000, 18_000)

    /** The Lucky charm's extra bump, and how many more Sparkling fish the Sparkle lure brings. */
    const val LUCKY_CHARM_EXTRA_BUMPS = 1
    const val SPARKLE_LURE_MULTIPLIER = 3f

    /** Wagers on battles against the computer: the stakes offered, and what a win pays back. */
    val WAGER_STAKES = listOf(1_000L, 5_000L, 25_000L, 100_000L)

    /** A win pays back the stake times this (a draw refunds it, a loss or forfeit keeps it). */
    fun wagerPayout(difficulty: BotDifficulty): Float = when (difficulty) {
        BotDifficulty.EASY -> 1.2f
        BotDifficulty.NORMAL -> 1.6f
        BotDifficulty.HARD -> 2.4f
    }

    /** How far from the hook the rare fish detector marks uncaught species (meters). */
    const val DETECTOR_RADIUS_M = 25f

    /**
     * Game feel (app/.../feel): screen shake, slow motion and flashes. They are presentation only:
     * shake moves the drawing, never the hook, the fish or what counts as a hit. Slow motion only
     * plays in solo casts (battles and online casts always run in real time, so nobody gets more
     * time to react than their opponent), and the Reduce motion setting turns all three off.
     */
    object Feel {
        /** The largest shake offset, as a share of the screen width (at full trauma). */
        const val SHAKE_MAX_OFFSET = 0.018f
        /** Trauma lost per second (trauma runs from 0 to 1; the shake is trauma squared). */
        const val SHAKE_DECAY_PER_SEC = 1.6f
        /** How fast the shake wobbles (noise samples per second). */
        const val SHAKE_FREQUENCY = 22f
        /** Trauma added by a bump, the last bump, a snag and a big catch. */
        const val TRAUMA_BUMP = 0.32f
        const val TRAUMA_LAST_BUMP = 0.55f
        const val TRAUMA_SNAG = 0.6f
        const val TRAUMA_RARE_CATCH = 0.3f

        /** Hit-stop on a rare catch: the game nearly freezes for this long (wall-clock seconds)... */
        const val HIT_STOP_SECONDS = 0.06f
        const val HIT_STOP_SCALE = 0.05f
        /** ...then runs at this speed and eases back to normal over this long. Solo casts only. */
        const val SLOW_MO_SCALE = 0.35f
        const val SLOW_MO_SECONDS = 0.45f

        /** A catch of this many rarity stars or more counts as rare (bigger sound, slow motion). */
        const val RARE_CATCH_STARS = 3
        /** How bright a rare catch's flash starts (0..1, drawn under the darkness) and how long it lasts. */
        const val FLASH_ALPHA = 0.45f
        const val FLASH_SECONDS = 0.3f
    }

    /**
     * The shop's exchange rate between euros and points: an item costs, in points, as much as a
     * good player earns in this many minutes of fishing per euro of its price, at their rope's
     * reach ([POINTS_PER_MINUTE_BY_LENGTH_LEVEL]). Paying saves a typical player about a day and
     * a half of play per euro, while free players still reach everything.
     */
    const val MINUTES_PER_EURO = 45f

    /** Lanterns are a key item for progress: their packs cost a full hour per euro in points. */
    const val LANTERN_MINUTES_PER_EURO = 60f

    /** Bigger purchases give more value per euro (the usual tier bonus). */
    fun tierBonus(euroCents: Int): Float = when {
        euroCents >= 999 -> 1.4f
        euroCents >= 499 -> 1.2f
        else -> 1f
    }

    /**
     * Points a good player earns per minute (casts plus menus) by Rope Length level, measured by
     * the career simulation (EconomyTest checks it still holds).
     */
    val POINTS_PER_MINUTE_BY_LENGTH_LEVEL = intArrayOf(380, 870, 1_700, 2_800, 5_200, 8_100, 8_800, 10_100, 12_100, 13_900, 17_100)

    /** The starter pack is offered from this app session on, for this long, once. */
    const val STARTER_OFFER_FROM_SESSION = 3
    const val STARTER_OFFER_HOURS = 72
    const val STARTER_PACK_LANTERNS = 20

    /** Seasons: their length, the XP a solo cast earns, and the track. */
    const val SEASON_WEEKS = 6
    const val SEASON_XP_PER_CAST = 6
    const val SEASON_XP_PER_FISH = 1
    const val SEASON_XP_PER_SECRET = 10
    const val SEASON_XP_PER_TIER = 100
    const val SEASON_TIERS = 20
    /** Tiers the collector's edition opens straight away. */
    const val COLLECTOR_TIER_SKIP = 10

    /** One Captain's Club month. */
    const val CLUB_DAYS = 30

    /** How long an active store subscription keeps the Club on after the store last confirmed it. */
    const val CLUB_RENEWAL_GRACE_DAYS = 3

    /** Boat options bought with points cost their table price times this. */
    const val BOAT_PART_PRICE_SCALE = 6
    const val STARTING_LANTERNS = 1

    /** Simulation never advances more than this per frame, and sub-steps at this size. */
    const val MAX_FRAME_DT = 1f / 20f
    const val SUBSTEP_DT = 1f / 120f
}
