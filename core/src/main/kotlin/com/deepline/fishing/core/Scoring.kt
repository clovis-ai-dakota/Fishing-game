package com.deepline.fishing.core

import kotlin.math.roundToInt

object Scoring {
    fun depthMultiplier(depth: Float): Float =
        1f + depth.coerceAtLeast(0f) / 100f * GameConfig.DEPTH_MULTIPLIER_PER_100M

    /** Deeper catches are worth more; Sparkling fish are worth [GameConfig.SPARKLING_MULTIPLIER] times more. */
    fun points(species: FishSpecies, depth: Float, sparkling: Boolean): Int {
        val base = species.basePoints * depthMultiplier(depth)
        val sparkle = if (sparkling) GameConfig.SPARKLING_MULTIPLIER else 1
        return (base * sparkle).roundToInt()
    }
}

object Lighting {
    /** 0 = fully lit, 1 = fully dark. Darkness only starts below [GameConfig.DARK_DEPTH]. */
    fun darkness(depth: Float): Float =
        ((depth - GameConfig.DARK_DEPTH) / GameConfig.DARK_FADE_RANGE).coerceIn(0f, 1f)

    fun visibleRadius(lanternActive: Boolean): Float =
        if (lanternActive) GameConfig.VISIBLE_RADIUS_LANTERN else GameConfig.VISIBLE_RADIUS_DARK
}
