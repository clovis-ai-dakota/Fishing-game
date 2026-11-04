package com.deepline.fishing.ui.dex

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.deepline.fishing.core.DepthZone

/** Each depth zone's colour: card frames, chips and the depth gauge. */
fun zoneColor(zone: DepthZone): Color = when (zone) {
    DepthZone.SHALLOWS -> Color(0xFF4FC3F7)
    DepthZone.TWILIGHT -> Color(0xFF7986CB)
    DepthZone.MIDNIGHT -> Color(0xFF5C6BC0).copy(red = 0.25f, green = 0.3f)
    DepthZone.ABYSS -> Color(0xFFAB47BC)
}

/** The water behind a species' card, from its zone's surface colour down to its depths. */
fun zoneBackground(zone: DepthZone): Brush = Brush.verticalGradient(
    when (zone) {
        DepthZone.SHALLOWS -> listOf(Color(0xFF29B6F6), Color(0xFF0277BD))
        DepthZone.TWILIGHT -> listOf(Color(0xFF3949AB), Color(0xFF1A237E))
        DepthZone.MIDNIGHT -> listOf(Color(0xFF1A237E), Color(0xFF0B1033))
        DepthZone.ABYSS -> listOf(Color(0xFF311B92), Color(0xFF07021A))
    },
)

/** Secret tiers, from teal (easy) to gold (legendary). */
fun tierColor(tier: Int): Color = when (tier) {
    1 -> Color(0xFF4DB6AC)
    2 -> Color(0xFF4FC3F7)
    3 -> Color(0xFF9575CD)
    4 -> Color(0xFFFF8A65)
    else -> Color(0xFFFFD54F)
}

fun tierName(tier: Int): String = if (tier >= 5) "Legendary" else "Tier $tier"

/** "#007", "#200". */
fun dexNumberText(number: Int, digits: Int = 3): String = "#" + number.toString().padStart(digits, '0')
