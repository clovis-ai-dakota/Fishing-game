package com.deepline.fishing.ui.dex

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.deepline.fishing.core.DepthZone

/** Each depth zone's colour (card frames, chips, the depth gauge), from the colour script. */
fun zoneColor(zone: DepthZone): Color = Color(com.deepline.fishing.ui.ColorScript.zoneAccent(zone))

/** The water behind a species' card: its zone's water, from the colour script. */
fun zoneBackground(zone: DepthZone): Brush {
    val (top, bottom) = com.deepline.fishing.ui.ColorScript.zoneGradient(zone)
    return Brush.verticalGradient(listOf(Color(top), Color(bottom)))
}

/** Secret tiers, from calm teal (easy) through violet and coral to gold (legendary). */
fun tierColor(tier: Int): Color = when (tier) {
    1 -> Color(0xFF6FD6C8)
    2 -> Color(0xFF7FC8F2)
    3 -> Color(0xFFB39DFF)
    4 -> Color(0xFFFF9E8A)
    else -> Color(0xFFFFD580)
}

fun tierName(tier: Int): String = if (tier >= 5) "Legendary" else "Tier $tier"

/** "#007", "#200". */
fun dexNumberText(number: Int, digits: Int = 3): String = "#" + number.toString().padStart(digits, '0')
