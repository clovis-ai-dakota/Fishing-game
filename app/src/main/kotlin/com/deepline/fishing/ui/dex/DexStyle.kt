package com.deepline.fishing.ui.dex

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.deepline.fishing.core.DepthZone

/** Each depth zone's colour (card frames, chips, the depth gauge), from the colour script. */
fun zoneColor(zone: DepthZone): Color = Color(com.deepline.fishing.ui.ColorScript.zoneAccent(zone))

/** A zone's colour for text on the theme's cards: deeper on light themes so it stays readable. */
@androidx.compose.runtime.Composable
fun zoneTextColor(zone: DepthZone): Color {
    val c = zoneColor(zone)
    return if (com.deepline.fishing.ui.theme.LocalMenuPalette.current.light) androidx.compose.ui.graphics.lerp(c, Color.Black, 0.5f) else c
}

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

/**
 * A caught card's look ([com.deepline.fishing.core.CardStyle]): its frame and a sheen over the
 * card. It never changes the fish or the water: only the card around them.
 */
fun cardFrame(style: com.deepline.fishing.core.CardStyle, zone: DepthZone, t: Float): Brush = when (style) {
    com.deepline.fishing.core.CardStyle.CLASSIC -> androidx.compose.ui.graphics.SolidColor(zoneColor(zone))
    com.deepline.fishing.core.CardStyle.HOLO -> Brush.sweepGradient(
        List(7) { Color.hsv(((t * 360f + it * 60f) % 360f), 0.55f, 1f) },
    )
    com.deepline.fishing.core.CardStyle.GOLD -> Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFFA000), Color(0xFFFFF8E1), Color(0xFFFFA000)))
    com.deepline.fishing.core.CardStyle.NIGHT -> Brush.linearGradient(listOf(Color(0xFF9FA8DA), Color(0xFF1A237E), Color(0xFFE8EAF6)))
    com.deepline.fishing.core.CardStyle.CORAL -> Brush.linearGradient(listOf(Color(0xFFFF8A80), Color(0xFFFFCCBC), Color(0xFFFF7043)))
}

/** The sheen drawn over a caught card (transparent in the middle, so the fish stays clear). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardSheen(style: com.deepline.fishing.core.CardStyle, t: Float) {
    val w = size.width
    val h = size.height
    when (style) {
        com.deepline.fishing.core.CardStyle.CLASSIC -> Unit
        com.deepline.fishing.core.CardStyle.HOLO -> {
            val x = (t % 1f) * (w + h) - h
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + h * 0.6f, h)))
        }
        com.deepline.fishing.core.CardStyle.GOLD -> drawRect(Brush.verticalGradient(listOf(Color(0x33FFD54F), Color.Transparent, Color(0x33FFA000))))
        com.deepline.fishing.core.CardStyle.NIGHT -> for (k in 0 until 14) {
            val sx = ((k * 0.37f + 0.11f) % 1f) * w
            val sy = ((k * 0.61f + 0.07f) % 1f) * h * 0.35f
            drawCircle(Color.White.copy(alpha = 0.4f + 0.4f * kotlin.math.sin(t * 6f + k)), radius = 1.5f + (k % 3), center = androidx.compose.ui.geometry.Offset(sx, sy))
        }
        com.deepline.fishing.core.CardStyle.CORAL -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33FF8A80))))
    }
}

/** A blank sample card in [style], for the Shop and the Skins section. */
@androidx.compose.runtime.Composable
fun CardStyleSample(style: com.deepline.fishing.core.CardStyle, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "card").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "card time",
    )
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.Canvas(
            androidx.compose.ui.Modifier
                .fillMaxHeight(0.8f)
                .aspectRatio(0.72f)
                .clip(shape)
                .background(zoneBackground(DepthZone.TWILIGHT))
                .border(3.dp, cardFrame(style, DepthZone.TWILIGHT, t), shape),
        ) { drawCardSheen(style, t) }
    }
}
