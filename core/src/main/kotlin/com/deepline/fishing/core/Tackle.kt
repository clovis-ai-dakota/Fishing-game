package com.deepline.fishing.core

/**
 * One-cast consumables, bought in the Shop (singly with points, or in packs for euros or points)
 * and switched on before a cast. Their points price grows with the rope's reach, because deeper
 * casts earn more. The Lantern and the detector change only what you see, never the water, so
 * they're allowed in battles too; the charm and the lure change the rules or the water, so they're
 * solo only.
 */
enum class Tackle(
    val displayName: String,
    val description: String,
    private val priceFactor: Float,
    /** Can be switched on for a battle cast (it only changes what the player sees). */
    val usableInBattles: Boolean = false,
) {
    LANTERN(
        "Lantern",
        "Lights up much more of the dark water below ${GameConfig.DARK_DEPTH.toInt()} m for one cast.",
        // A key item for progress: a little dearer than the others (see GameConfig.LANTERN_MINUTES_PER_EURO).
        0.8f,
        usableInBattles = true,
    ),
    LUCKY_CHARM(
        "Lucky charm",
        "One extra bump on the way down for one cast (${GameConfig.DESCENT_TOUCH_LIMIT + GameConfig.LUCKY_CHARM_EXTRA_BUMPS} instead of ${GameConfig.DESCENT_TOUCH_LIMIT}).",
        0.8f,
    ),
    SPARKLE_LURE(
        "Sparkle lure",
        "${GameConfig.SPARKLE_LURE_MULTIPLIER.toInt()}× as many Sparkling fish in the water for one cast.",
        0.5f,
    ),
    DETECTOR(
        "Rare fish detector",
        "For one cast, marks species you haven't caught yet within ${GameConfig.DETECTOR_RADIUS_M.toInt()} m of the hook, even in the dark.",
        0.5f,
        usableInBattles = true,
    );

    /** The price with a rope at Length level [lengthLevel]. */
    fun price(lengthLevel: Int): Int {
        val base = GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL[lengthLevel.coerceIn(0, GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL.size - 1)]
        return ((base * priceFactor) / 50f).toInt().coerceAtLeast(1) * 50
    }
}
