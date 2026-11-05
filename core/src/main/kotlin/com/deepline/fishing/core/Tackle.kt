package com.deepline.fishing.core

/**
 * One-cast consumables bought with points in Upgrades and switched on before a solo cast (never
 * in battles). Their price grows with the rope's reach, because deeper casts earn more: they stay
 * a real decision all game long instead of becoming pocket change.
 */
enum class Tackle(val displayName: String, val description: String, private val priceFactor: Float) {
    LANTERN(
        "Lantern",
        "Lights up much more of the dark water below ${GameConfig.DARK_DEPTH.toInt()} m for one cast.",
        0.6f,
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
    );

    /** The price with a rope at Length level [lengthLevel]. */
    fun price(lengthLevel: Int): Int {
        val base = GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL[lengthLevel.coerceIn(0, GameConfig.TACKLE_PRICE_BY_LENGTH_LEVEL.size - 1)]
        return ((base * priceFactor) / 50f).toInt().coerceAtLeast(1) * 50
    }
}
