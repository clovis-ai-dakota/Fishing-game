package com.deepline.fishing.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.ClothPattern
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.SailStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The boat in 2D: the classic one, the legendary boat skins (see SkinArt) and the boats players
 * design themselves (BoatDesign). Coordinates are meters from the boat's centre [cx] and the
 * waterline [surfaceY], [scale] pixels per meter; the boat spans 5 m and its deck is 1 m up.
 */

/**
 * The classic boat's and line's colours, shared by the 3D renderer and the 2D drawing: lagoon-blue
 * topsides, a white boot stripe and rails, red bottom paint, a cream wheelhouse, and a pale
 * hi-vis line that reads against the water.
 */
object BoatLook {
    const val CLASSIC_HULL = 0xFF23708CL
    const val CLASSIC_BOTTOM = 0xFFB83B2EL
    const val CLASSIC_TRIM = 0xFFF4F1E8L
    const val CLASSIC_CABIN = 0xFFF8F3EAL
    const val LINE_R = 0.98f
    const val LINE_G = 0.97f
    const val LINE_B = 0.80f
}

/**
 * The hull's side, as the 3D boat (PropModels): a raked bow to the left rising to 1.44 m, the
 * sheer dipping to the deck (1 m) amidships, a flat transom to the right, the keel 0.45 m down.
 */
internal fun hullPath(cx: Float, surfaceY: Float, scale: Float) = Path().apply {
    fun x(v: Float) = cx + v * scale
    fun y(v: Float) = surfaceY - v * scale
    moveTo(x(-2.72f), y(1.44f))
    quadraticTo(x(-1.6f), y(1.06f), x(-0.2f), y(1.0f))
    quadraticTo(x(1.4f), y(1.0f), x(2.45f), y(1.06f))
    lineTo(x(2.45f), y(-0.08f))
    quadraticTo(x(2.42f), y(-0.4f), x(1.9f), y(-0.45f))
    lineTo(x(-1.35f), y(-0.45f))
    quadraticTo(x(-2.3f), y(-0.42f), x(-2.46f), y(0.2f))
    quadraticTo(x(-2.6f), y(0.9f), x(-2.72f), y(1.44f))
    close()
}

/** The sheer line (top edge of the hull), bow to stern, for the rails. */
private fun sheerPath(cx: Float, surfaceY: Float, scale: Float, drop: Float = 0f) = Path().apply {
    fun x(v: Float) = cx + v * scale
    fun y(v: Float) = surfaceY - (v - drop) * scale
    moveTo(x(-2.66f + drop * 0.5f), y(1.42f))
    quadraticTo(x(-1.6f), y(1.06f), x(-0.2f), y(1.0f))
    quadraticTo(x(1.4f), y(1.0f), x(2.42f), y(1.06f))
}

/** The sheer's height at [x] (boat metres), matching [hullPath]. */
internal fun sheer2d(x: Float): Float = 1.0f + 0.42f * smooth(0.2f, 2.6f, -x).let { it * kotlin.math.sqrt(it) } + 0.05f * smooth(1.6f, 2.45f, x)

private fun smooth(a: Float, b: Float, v: Float): Float {
    val t = ((v - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The whole boat (rig, hull, pattern, trim, wheelhouse and fittings), without the rod. A boat
 * [skin] wins over a hand-made [design]; with neither it's the classic boat.
 */
internal fun DrawScope.drawBoat(cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float, skin: Cosmetic?, design: BoatDesign? = null) {
    val d = design.takeIf { skin == null }
    drawBoatRig(cx, surfaceY, scale, timeSeconds, skin, d)
    val hull = hullPath(cx, surfaceY, scale)
    val left = cx - 2.8f * scale
    val right = cx + 2.5f * scale
    drawPath(hull, hullBrush(skin, surfaceY - 1.44f * scale, surfaceY + 0.45f * scale, timeSeconds, d))
    val trim = trimBrush(skin, cx - 2.6f * scale, cx + 2.4f * scale, timeSeconds, d)
    clipPath(hull) {
        // Bottom paint below the waterline and a boot stripe in the trim colour (not on two-tone hulls).
        if (d?.pattern != HullPattern.TWO_TONE) {
            drawRect(Color(bottomPaint(skin, d)), topLeft = Offset(left, surfaceY - 0.07f * scale), size = Size(right - left, 0.6f * scale))
            drawRect(trim, topLeft = Offset(left, surfaceY - 0.21f * scale), size = Size(right - left, 0.13f * scale))
        }
        // Plank seams, faint over any paint.
        for (k in 0 until 5) {
            val yy = surfaceY - (0.47f + 0.25f * k) * scale
            drawLine(Color.Black.copy(alpha = 0.13f), Offset(left, yy), Offset(right, yy), strokeWidth = max(1f, 0.025f * scale))
        }
    }
    if (d != null) drawHullPattern(d, hull, cx, surfaceY, scale, timeSeconds)
    // Rails along the sheer and a rubbing strake below them.
    val railWidth = if (skin == null && d == null) max(2.5f, 0.12f * scale) else max(3f, 0.16f * scale)
    drawPath(sheerPath(cx, surfaceY, scale), trim, style = Stroke(width = railWidth, cap = StrokeCap.Round))
    drawPath(sheerPath(cx, surfaceY, scale, drop = 0.36f), trim, style = Stroke(width = railWidth * 0.55f, cap = StrokeCap.Round))
    drawPath(hull, Color.Black.copy(alpha = 0.3f), style = Stroke(width = max(1.5f, 0.05f * scale)))
    drawWheelhouse(cx, surfaceY, scale, skin, d, timeSeconds)
    drawFittings(cx, surfaceY, scale, trim, rigged = skin != null || (d != null && d.sailStyle != SailStyle.NONE))
    // With neither sail nor flag, the emblem goes on the hull.
    if (d != null && d.emblem != Emblem.NONE && d.sailStyle == SailStyle.NONE && d.flagStyle == FlagStyle.NONE) {
        drawEmblem(d.emblem, Offset(cx + 1.2f * scale, surfaceY - 0.55f * scale), 0.34f * scale, Color(d.trimColor()), timeSeconds)
    }
    // The hull below the waterline seen through the water, and foam lapping along it.
    clipPath(hull) {
        drawRect(Color(ColorScript.water(1.5f)).copy(alpha = 0.32f), topLeft = Offset(left, surfaceY), size = Size(right - left, 0.6f * scale))
    }
    for (k in 0 until 22) {
        val fx = cx + (-2.5f + 4.95f * k / 21f) * scale
        val wob = sin(timeSeconds * 2.3f + k * 1.7f)
        val rw = (0.1f + 0.06f * ((k * 7 % 5) / 4f) + 0.03f * wob) * scale
        drawOval(Color.White.copy(alpha = 0.45f + 0.2f * wob), topLeft = Offset(fx - rw, surfaceY - 0.02f * scale - rw * 0.22f), size = Size(rw * 2f, rw * 0.44f))
    }
}

/** Bottom paint: the design's bottom colour, the skin's dark tone, or the classic red. */
private fun bottomPaint(skin: Cosmetic?, d: BoatDesign?): Long = when {
    d != null -> d.hullBottomColor()
    skin == Cosmetic.GIANT_SLAYER_PENNANT -> 0xFF102027
    skin == Cosmetic.PRISM_SAIL -> 0xFFB0BEC5
    skin == Cosmetic.ROYAL_BOAT -> 0xFF38006B
    else -> BoatLook.CLASSIC_BOTTOM
}

/** The wheelhouse: a raked windscreen, framed windows, a door, a life ring and a roof. */
private fun DrawScope.drawWheelhouse(cx: Float, surfaceY: Float, scale: Float, skin: Cosmetic?, d: BoatDesign?, t: Float) {
    fun o(x: Float, y: Float) = Offset(cx + x * scale, surfaceY - y * scale)
    val wall = when {
        d != null -> Color(d.cabinColor())
        skin == Cosmetic.ROYAL_BOAT -> Color(0xFFFFF8E1)
        else -> Color(BoatLook.CLASSIC_CABIN)
    }
    val roof = when {
        d != null -> lerp(Color(d.hullColor()), Color.White, 0.1f)
        skin == Cosmetic.GIANT_SLAYER_PENNANT -> Color(0xFF37474F)
        skin == Cosmetic.PRISM_SAIL -> Color(0xFFECEFF1)
        skin == Cosmetic.ROYAL_BOAT -> Color(0xFF7B1FA2)
        else -> lerp(Color(BoatLook.CLASSIC_HULL), Color.White, 0.1f)
    }
    val body = Path().apply {
        val p = listOf(o(-1.35f, 0.95f), o(0.35f, 0.95f), o(0.35f, 2.19f), o(0.29f, 2.25f), o(-1.03f, 2.25f), o(-1.08f, 2.21f), o(-1.35f, 1.72f))
        moveTo(p[0].x, p[0].y)
        for (q in p.drop(1)) lineTo(q.x, q.y)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(wall, wall, lerp(wall, Color.Black, 0.25f)), startY = o(0f, 2.25f).y, endY = o(0f, 0.95f).y))
    // Shade under the roof.
    drawRect(Color.Black.copy(alpha = 0.1f), topLeft = o(-1.05f, 2.25f), size = Size(1.4f * scale, 0.12f * scale))
    drawPath(body, Color.Black.copy(alpha = 0.3f), style = Stroke(width = max(1.2f, 0.035f * scale)))
    fun pane(x0: Float, x1: Float, y0: Float, y1: Float) {
        val tl = o(x0, y1)
        val sz = Size((x1 - x0) * scale, (y1 - y0) * scale)
        drawRect(Brush.verticalGradient(listOf(Color(0xFFD2EEFF), Color(0xFF4A86B8)), startY = tl.y, endY = tl.y + sz.height), topLeft = tl, size = sz)
        // A glint crossing the glass.
        val g = ((t * 0.06f) % 1f)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(tl.x + sz.width * (0.15f + 0.5f * g), tl.y + sz.height), Offset(tl.x + sz.width * (0.45f + 0.5f * g), tl.y), strokeWidth = max(1.5f, 0.05f * scale))
        drawRect(Color(BoatLook.CLASSIC_TRIM), topLeft = tl, size = sz, style = Stroke(width = max(1.5f, 0.06f * scale)))
    }
    pane(-1.02f, -0.62f, 1.62f, 2.05f)
    pane(-0.5f, -0.18f, 1.62f, 2.05f)
    // The windscreen on the raked front.
    drawLine(Color(0xFF4A86B8), o(-1.31f, 1.8f), o(-1.08f, 2.16f), strokeWidth = max(2f, 0.1f * scale), cap = StrokeCap.Round)
    // The door with a porthole and a handle.
    drawRoundRect(roof, topLeft = o(-0.12f, 1.92f), size = Size(0.4f * scale, 0.97f * scale), cornerRadius = CornerRadius(0.05f * scale))
    drawRoundRect(Color(BoatLook.CLASSIC_TRIM), topLeft = o(-0.12f, 1.92f), size = Size(0.4f * scale, 0.97f * scale), cornerRadius = CornerRadius(0.05f * scale), style = Stroke(width = max(1f, 0.04f * scale)))
    drawCircle(Color(0xFF9FD3F5), radius = 0.1f * scale, center = o(0.08f, 1.72f))
    drawCircle(Color(BoatLook.CLASSIC_TRIM), radius = 0.11f * scale, center = o(0.08f, 1.72f), style = Stroke(width = max(1f, 0.04f * scale)))
    drawCircle(Color(0xFFB0BEC5), radius = 0.035f * scale, center = o(0.22f, 1.38f))
    // A life ring, red and white.
    val ring = o(-0.52f, 1.28f)
    for (k in 0 until 8) {
        drawArc(
            if (k % 2 == 0) Color(0xFFE53935) else Color(0xFFF7F6F2),
            startAngle = k * 45f, sweepAngle = 45f, useCenter = false,
            topLeft = ring - Offset(0.17f * scale, 0.17f * scale), size = Size(0.34f * scale, 0.34f * scale),
            style = Stroke(width = 0.1f * scale),
        )
    }
    // The roof: an overhanging slab with a lip, in the hull's colour.
    drawRoundRect(roof, topLeft = o(-1.43f, 2.39f), size = Size(2.03f * scale, 0.14f * scale), cornerRadius = CornerRadius(0.04f * scale))
    drawRect(lerp(roof, Color.Black, 0.2f), topLeft = o(-1.35f, 2.43f), size = Size(1.87f * scale, 0.04f * scale))
    drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft = o(-1.43f, 2.39f), size = Size(2.03f * scale, 0.14f * scale), cornerRadius = CornerRadius(0.04f * scale), style = Stroke(width = max(1f, 0.03f * scale)))
}

/** Antenna and lantern (unless a mast stands there), searchlight, fenders and the aft rails. */
private fun DrawScope.drawFittings(cx: Float, surfaceY: Float, scale: Float, trim: Brush, rigged: Boolean) {
    fun o(x: Float, y: Float) = Offset(cx + x * scale, surfaceY - y * scale)
    val mast = Color(0xFF5D4037)
    if (!rigged) {
        drawLine(mast, o(0.18f, 2.42f), o(0.18f, 3.45f), strokeWidth = max(1.5f, 0.06f * scale), cap = StrokeCap.Round)
        drawLine(mast, o(-0.08f, 3.05f), o(0.44f, 3.05f), strokeWidth = max(1.2f, 0.04f * scale), cap = StrokeCap.Round)
        drawCircle(Color(0x66FFE9A0), radius = 0.16f * scale, center = o(0.18f, 3.5f))
        drawCircle(Color(0xFFFFEDA6), radius = 0.07f * scale, center = o(0.18f, 3.5f))
    }
    // Searchlight on the roof.
    drawRoundRect(Color(0xFF90A4AE), topLeft = o(-1.1f, 2.54f), size = Size(0.2f * scale, 0.15f * scale), cornerRadius = CornerRadius(0.05f * scale))
    drawCircle(Color(0xFFFFF3C4), radius = 0.05f * scale, center = o(-1.1f, 2.47f))
    // Fenders on ropes over the side.
    for (x in listOf(-1.75f, 1.25f)) {
        val top = sheer2d(x) - 0.42f
        drawLine(Color(0xFFD7C9A6), o(x, sheer2d(x)), o(x, top), strokeWidth = max(1f, 0.03f * scale))
        drawRoundRect(Color(0xFFF5F5F2), topLeft = o(x - 0.12f, top), size = Size(0.24f * scale, 0.52f * scale), cornerRadius = CornerRadius(0.12f * scale))
        drawRoundRect(Color(0xFF1E4D8C), topLeft = o(x - 0.08f, top - 0.43f), size = Size(0.16f * scale, 0.09f * scale), cornerRadius = CornerRadius(0.04f * scale))
        drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft = o(x - 0.12f, top), size = Size(0.24f * scale, 0.52f * scale), cornerRadius = CornerRadius(0.12f * scale), style = Stroke(width = max(1f, 0.03f * scale)))
    }
    // Rails along the working deck.
    val posts = listOf(0.55f, 1.05f, 1.55f, 2.05f)
    for (x in posts) drawLine(trim, o(x, sheer2d(x)), o(x, sheer2d(x) + 0.42f), strokeWidth = max(1.2f, 0.045f * scale))
    drawLine(trim, o(posts.first(), sheer2d(posts.first()) + 0.42f), o(posts.last(), sheer2d(posts.last()) + 0.42f), strokeWidth = max(1.5f, 0.05f * scale), cap = StrokeCap.Round)
}

/** Hull colours of a hand-made boat: top colour fading to the bottom colour. */
internal fun customHullBrush(d: BoatDesign, top: Float, bottom: Float): Brush {
    val hull = Color(d.hullColor())
    val low = Color(d.hullBottomColor())
    return if (d.pattern == HullPattern.TWO_TONE) {
        // A crisp split at the waterline stripe.
        Brush.verticalGradient(0f to hull, 0.55f to hull, 0.551f to low, 1f to low, startY = top, endY = bottom)
    } else {
        Brush.verticalGradient(listOf(hull, lerp(hull, low, 0.5f), low), startY = top, endY = bottom)
    }
}

/** The hull pattern, clipped to the hull, in the trim colour. */
private fun DrawScope.drawHullPattern(d: BoatDesign, hull: Path, cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float) {
    val trim = Color(d.trimColor())
    val left = cx - 2.5f * scale
    val right = cx + 2.5f * scale
    val top = surfaceY - 1.0f * scale
    val bottom = surfaceY + 0.4f * scale
    clipPath(hull) {
        when (d.pattern) {
            HullPattern.SOLID, HullPattern.TWO_TONE -> Unit
            HullPattern.STRIPE -> {
                drawRect(trim, topLeft = Offset(left, surfaceY - 0.6f * scale), size = Size(right - left, 0.22f * scale))
                drawRect(trim.copy(alpha = 0.7f), topLeft = Offset(left, surfaceY - 0.3f * scale), size = Size(right - left, 0.07f * scale))
            }
            HullPattern.WAVES -> for (row in 0 until 2) {
                val y0 = surfaceY - (0.6f - row * 0.38f) * scale
                val wave = Path()
                var x = left
                wave.moveTo(x, y0)
                while (x <= right) {
                    wave.lineTo(x, y0 + sin((x - left) / scale * 4f + timeSeconds * 0.8f) * 0.08f * scale)
                    x += max(2f, scale * 0.1f)
                }
                drawPath(wave, trim, style = Stroke(width = max(2f, 0.09f * scale), cap = StrokeCap.Round))
            }
            HullPattern.DOTS -> {
                val step = 0.5f * scale
                var row = 0
                var y = top + 0.3f * scale
                while (y < bottom) {
                    var x = left + (if (row % 2 == 0) 0.25f else 0.5f) * scale
                    while (x < right) {
                        drawCircle(trim, radius = 0.12f * scale, center = Offset(x, y))
                        x += step
                    }
                    y += step * 0.8f
                    row++
                }
            }
            HullPattern.CHECKER -> {
                val cell = 0.35f * scale
                var i = 0
                var y = top
                while (y < bottom) {
                    var j = 0
                    var x = left
                    while (x < right) {
                        if ((i + j) % 2 == 0) drawRect(trim.copy(alpha = 0.85f), topLeft = Offset(x, y), size = Size(cell, cell))
                        x += cell
                        j++
                    }
                    y += cell
                    i++
                }
            }
            HullPattern.FLAMES -> {
                // Flame tongues licking back from the bow.
                val hot = Color(0xFFFFD54F)
                val warm = Color(0xFFFF7043)
                for (k in 0 until 5) {
                    val y = surfaceY - (0.85f - k * 0.27f) * scale
                    val len = (2.6f - abs(k - 2) * 0.45f) * scale
                    val flick = sin(timeSeconds * 6f + k) * 0.12f * scale
                    val flame = Path().apply {
                        moveTo(right, y - 0.13f * scale)
                        quadraticTo(right - len * 0.5f, y - 0.2f * scale + flick, right - len, y + flick)
                        quadraticTo(right - len * 0.5f, y + 0.2f * scale + flick, right, y + 0.13f * scale)
                        close()
                    }
                    drawPath(flame, Brush.horizontalGradient(listOf(warm.copy(alpha = 0.2f), warm, hot), startX = right - len, endX = right))
                }
            }
            HullPattern.STARRY -> for (k in 0 until 14) {
                val x = left + ((k * 0.37f + 0.13f) % 1f) * (right - left)
                val y = top + ((k * 0.61f + 0.21f) % 1f) * (bottom - top)
                val tw = 0.6f + 0.4f * sin(timeSeconds * 2.5f + k * 1.7f)
                twinkle(Offset(x, y), (0.09f + 0.06f * (k % 3)) * scale * tw, trim)
            }
            HullPattern.METALLIC -> {
                // Polished bands sliding along the hull.
                for (k in 0 until 3) {
                    val x0 = left + ((timeSeconds * 0.25f + k / 3f) % 1f) * (right - left + 2f * scale) - scale
                    val band = Path().apply {
                        moveTo(x0, top); lineTo(x0 + 0.35f * scale, top)
                        lineTo(x0 - 0.25f * scale, bottom); lineTo(x0 - 0.6f * scale, bottom); close()
                    }
                    drawPath(band, Color.White.copy(alpha = 0.35f))
                }
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.2f)), startY = top, endY = bottom), topLeft = Offset(left, top), size = Size(right - left, bottom - top))
            }
            HullPattern.NEON -> {
                val pulse = 0.65f + 0.35f * sin(timeSeconds * 3f)
                for ((k, y) in listOf(surfaceY - 0.55f * scale, surfaceY - 0.2f * scale).withIndex()) {
                    val c = if (k == 0) trim else lerp(trim, Color.White, 0.3f)
                    drawLine(c.copy(alpha = 0.35f * pulse), Offset(left, y), Offset(right, y), strokeWidth = 0.28f * scale)
                    drawLine(c.copy(alpha = pulse), Offset(left, y), Offset(right, y), strokeWidth = 0.09f * scale)
                }
            }
            HullPattern.AURORA -> {
                // Members-only: green and violet curtains drifting along the hull.
                for (k in 0 until 4) {
                    val c = if (k % 2 == 0) Color(0xFF64FFDA) else Color(0xFFB388FF)
                    val wave = Path()
                    var x = left
                    val y0 = top + (0.2f + k * 0.28f) * scale
                    wave.moveTo(x, y0)
                    while (x <= right) {
                        wave.lineTo(x, y0 + sin((x - left) / scale * 2.2f + timeSeconds * 1.4f + k) * 0.18f * scale)
                        x += max(2f, scale * 0.1f)
                    }
                    drawPath(wave, c.copy(alpha = 0.55f), style = Stroke(width = 0.22f * scale, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** Masts, sails, flag and emblem of a hand-made boat. */
internal fun DrawScope.drawCustomRig(d: BoatDesign, cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float) {
    val sailColor = Color(d.sailColor())
    val flagColor = Color(d.flagColor())
    val emblemColor = Color(d.trimColor())
    val hasSail = d.sailStyle != SailStyle.NONE
    val mastX = cx - 0.2f * scale
    val mastBottom = if (hasSail) surfaceY - 1.0f * scale else surfaceY - 2.42f * scale
    val mastTop = surfaceY - (if (hasSail) 5.2f else 4.6f) * scale
    val billow = sin(timeSeconds * 2f) * 0.15f * scale
    val edge = Color.Black.copy(alpha = 0.3f)
    var emblemAt: Offset? = null
    var emblemR = 0f
    if (hasSail || d.flagStyle != FlagStyle.NONE) {
        drawLine(Color(0xFF5D4037), Offset(mastX, mastBottom), Offset(mastX, mastTop), strokeWidth = max(2f, 0.08f * scale), cap = StrokeCap.Round)
    }
    when (d.sailStyle) {
        SailStyle.NONE -> Unit
        SailStyle.TRIANGLE, SailStyle.TWIN -> {
            val main = Path().apply {
                moveTo(mastX - 0.1f * scale, mastTop + 0.2f * scale)
                quadraticTo(mastX - 1.6f * scale - billow, mastTop + 2.2f * scale, mastX - 2.2f * scale, surfaceY - 1.5f * scale)
                lineTo(mastX - 0.1f * scale, surfaceY - 1.5f * scale)
                close()
            }
            drawPath(main, Brush.verticalGradient(listOf(lerp(sailColor, Color.White, 0.25f), sailColor), startY = mastTop, endY = surfaceY - 1.5f * scale))
            drawClothPrint(main, d.cloth, emblemColor, mastX - 2.3f * scale, mastTop, mastX, surfaceY - 1.5f * scale, scale, timeSeconds)
            drawPath(main, edge, style = Stroke(width = max(1.5f, 0.04f * scale)))
            emblemAt = Offset(mastX - 0.85f * scale, surfaceY - 2.4f * scale)
            emblemR = 0.42f * scale
            if (d.sailStyle == SailStyle.TWIN) {
                // A jib in front of the mast.
                val jib = Path().apply {
                    moveTo(mastX + 0.1f * scale, mastTop + 0.5f * scale)
                    quadraticTo(mastX + 1.1f * scale + billow * 0.6f, mastTop + 2.2f * scale, mastX + 1.6f * scale, surfaceY - 1.3f * scale)
                    lineTo(mastX + 0.1f * scale, surfaceY - 1.3f * scale)
                    close()
                }
                drawPath(jib, Brush.verticalGradient(listOf(lerp(sailColor, Color.White, 0.4f), lerp(sailColor, Color.White, 0.1f)), startY = mastTop, endY = surfaceY - 1.3f * scale))
                drawClothPrint(jib, d.cloth, emblemColor, mastX, mastTop, mastX + 1.7f * scale, surfaceY - 1.3f * scale, scale, timeSeconds)
                drawPath(jib, edge, style = Stroke(width = max(1.5f, 0.04f * scale)))
            }
        }
        SailStyle.SQUARE -> {
            val sailTop = mastTop + 0.5f * scale
            val sailBottom = surfaceY - 1.6f * scale
            val sail = Path().apply {
                moveTo(mastX - 1.6f * scale, sailTop)
                lineTo(mastX + 1.6f * scale, sailTop)
                quadraticTo(mastX + 1.9f * scale + billow, (sailTop + sailBottom) / 2f, mastX + 1.5f * scale, sailBottom)
                lineTo(mastX - 1.5f * scale, sailBottom)
                quadraticTo(mastX - 1.3f * scale + billow, (sailTop + sailBottom) / 2f, mastX - 1.6f * scale, sailTop)
                close()
            }
            drawPath(sail, Brush.verticalGradient(listOf(lerp(sailColor, Color.White, 0.2f), sailColor), startY = sailTop, endY = sailBottom))
            drawClothPrint(sail, d.cloth, emblemColor, mastX - 1.7f * scale, sailTop, mastX + 2.0f * scale, sailBottom, scale, timeSeconds)
            drawPath(sail, edge, style = Stroke(width = max(1.5f, 0.05f * scale)))
            drawLine(edge, Offset(mastX - 1.6f * scale, sailTop), Offset(mastX + 1.6f * scale, sailTop), strokeWidth = max(2f, 0.07f * scale))
            emblemAt = Offset(mastX, (sailTop + sailBottom) / 2f)
            emblemR = 0.6f * scale
        }
    }
    // Flag at the top of the mast.
    val wave = sin(timeSeconds * 3f) * 0.12f * scale
    val flag: Path? = when (d.flagStyle) {
        FlagStyle.NONE -> null
        FlagStyle.PENNANT -> Path().apply {
            moveTo(mastX, mastTop)
            quadraticTo(mastX + 0.9f * scale, mastTop + 0.25f * scale + wave, mastX + 1.8f * scale, mastTop + 0.45f * scale + wave)
            quadraticTo(mastX + 0.9f * scale, mastTop + 0.7f * scale + wave, mastX, mastTop + 0.9f * scale)
            close()
        }
        FlagStyle.SWALLOWTAIL -> Path().apply {
            moveTo(mastX, mastTop)
            quadraticTo(mastX + 0.8f * scale, mastTop + wave, mastX + 1.6f * scale, mastTop + wave * 0.5f)
            lineTo(mastX + 1.1f * scale, mastTop + 0.45f * scale + wave)
            lineTo(mastX + 1.6f * scale, mastTop + 0.9f * scale + wave * 0.5f)
            quadraticTo(mastX + 0.8f * scale, mastTop + 0.9f * scale + wave, mastX, mastTop + 0.9f * scale)
            close()
        }
        FlagStyle.BANNER -> Path().apply {
            moveTo(mastX, mastTop)
            quadraticTo(mastX + 1.1f * scale, mastTop + wave, mastX + 2.2f * scale, mastTop - wave * 0.4f)
            lineTo(mastX + 2.2f * scale, mastTop + 1.2f * scale - wave * 0.4f)
            quadraticTo(mastX + 1.1f * scale, mastTop + 1.2f * scale + wave, mastX, mastTop + 1.2f * scale)
            close()
        }
    }
    if (flag != null) {
        drawPath(flag, flagColor)
        drawClothPrint(flag, d.cloth, lerp(flagColor, Color.White, 0.6f), mastX, mastTop - 0.3f * scale, mastX + 2.3f * scale, mastTop + 1.4f * scale, scale * 0.5f, timeSeconds)
        drawPath(flag, edge, style = Stroke(width = max(1.2f, 0.04f * scale)))
        if (emblemAt == null) {
            emblemAt = Offset(mastX + 0.75f * scale, mastTop + (if (d.flagStyle == FlagStyle.BANNER) 0.6f else 0.45f) * scale + wave * 0.7f)
            emblemR = 0.32f * scale
        }
    }
    if (d.emblem != Emblem.NONE && emblemAt != null) drawEmblem(d.emblem, emblemAt, emblemR, emblemColor, timeSeconds)
}

/**
 * The print on a sail or flag ([shape], within the given bounds): stripes, stars or waves in [ink].
 * The 3D renderer prints the same patterns (toon.frag, uCloth).
 */
internal fun DrawScope.drawClothPrint(
    shape: Path, cloth: ClothPattern, ink: Color,
    left: Float, top: Float, right: Float, bottom: Float, scale: Float, timeSeconds: Float,
) {
    if (cloth == ClothPattern.PLAIN) return
    val c = ink.copy(alpha = 0.7f)
    clipPath(shape) {
        when (cloth) {
            ClothPattern.PLAIN -> Unit
            ClothPattern.STRIPES -> {
                var y = top + 0.35f * scale
                while (y < bottom) {
                    drawRect(c, topLeft = Offset(left, y), size = Size(right - left, 0.22f * scale))
                    y += 0.6f * scale
                }
            }
            ClothPattern.STARS -> {
                val step = 0.7f * scale
                var row = 0
                var y = top + step * 0.5f
                while (y < bottom) {
                    var x = left + (if (row % 2 == 0) step * 0.3f else step * 0.8f)
                    while (x < right) {
                        drawPath(starPath(Offset(x, y), 0.16f * scale, 0.07f * scale, 5), c)
                        x += step
                    }
                    y += step
                    row++
                }
            }
            ClothPattern.WAVES -> {
                var y = top + 0.4f * scale
                var k = 0
                while (y < bottom) {
                    val w = Path()
                    var x = left
                    w.moveTo(x, y)
                    while (x <= right) {
                        w.lineTo(x, y + sin((x - left) / scale * 4f + timeSeconds * 1.5f + k) * 0.1f * scale)
                        x += max(2f, scale * 0.08f)
                    }
                    drawPath(w, c, style = Stroke(width = 0.1f * scale, cap = StrokeCap.Round))
                    y += 0.55f * scale
                    k++
                }
            }
        }
    }
}

/** A boat emblem of radius [r] at [c], filled with [fill] and outlined for contrast. */
internal fun DrawScope.drawEmblem(emblem: Emblem, c: Offset, r: Float, fill: Color, timeSeconds: Float = 0f) {
    val outline = Stroke(width = max(1.2f, r * 0.12f))
    val ink = Color.Black.copy(alpha = 0.45f)
    fun fillAndInk(p: Path) {
        drawPath(p, fill)
        drawPath(p, ink, style = outline)
    }
    when (emblem) {
        Emblem.NONE -> Unit
        Emblem.STAR -> fillAndInk(starPath(c, r, r * 0.45f, 5))
        Emblem.HEART -> fillAndInk(
            Path().apply {
                moveTo(c.x, c.y + r * 0.85f)
                cubicTo(c.x - r * 1.3f, c.y - r * 0.05f, c.x - r * 0.6f, c.y - r * 1.05f, c.x, c.y - r * 0.4f)
                cubicTo(c.x + r * 0.6f, c.y - r * 1.05f, c.x + r * 1.3f, c.y - r * 0.05f, c.x, c.y + r * 0.85f)
                close()
            },
        )
        Emblem.MOON -> {
            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.45f, -r * 0.2f), r * 0.85f)) }
            fillAndInk(Path().apply { op(disc, bite, PathOperation.Difference) })
        }
        Emblem.FISH -> fillAndInk(
            Path().apply {
                moveTo(c.x + r, c.y)
                quadraticTo(c.x + r * 0.2f, c.y - r * 0.75f, c.x - r * 0.45f, c.y)
                lineTo(c.x - r, c.y - r * 0.5f)
                lineTo(c.x - r, c.y + r * 0.5f)
                lineTo(c.x - r * 0.45f, c.y)
                quadraticTo(c.x + r * 0.2f, c.y + r * 0.75f, c.x + r, c.y)
                close()
            },
        )
        Emblem.ANCHOR -> {
            val w = max(1.5f, r * 0.18f)
            drawCircle(fill, radius = r * 0.2f, center = c + Offset(0f, -r * 0.75f), style = Stroke(width = w))
            drawLine(fill, c + Offset(0f, -r * 0.55f), c + Offset(0f, r * 0.85f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(fill, c + Offset(-r * 0.45f, -r * 0.3f), c + Offset(r * 0.45f, -r * 0.3f), strokeWidth = w, cap = StrokeCap.Round)
            drawArc(fill, 20f, 140f, false, topLeft = c + Offset(-r * 0.8f, -r * 0.3f), size = Size(r * 1.6f, r * 1.15f), style = Stroke(width = w, cap = StrokeCap.Round))
        }
        Emblem.WAVE -> for (k in 0 until 3) {
            val y = c.y + (k - 1) * r * 0.5f
            val p = Path()
            var x = c.x - r
            p.moveTo(x, y)
            while (x <= c.x + r) {
                p.lineTo(x, y + sin((x - c.x) / r * PI.toFloat() * 1.5f + timeSeconds) * r * 0.15f)
                x += max(1.5f, r * 0.1f)
            }
            drawPath(p, fill, style = Stroke(width = max(1.5f, r * 0.16f), cap = StrokeCap.Round))
        }
        Emblem.CROWN -> fillAndInk(
            Path().apply {
                moveTo(c.x - r, c.y + r * 0.5f)
                lineTo(c.x - r, c.y - r * 0.35f)
                lineTo(c.x - r * 0.5f, c.y + r * 0.05f)
                lineTo(c.x, c.y - r * 0.65f)
                lineTo(c.x + r * 0.5f, c.y + r * 0.05f)
                lineTo(c.x + r, c.y - r * 0.35f)
                lineTo(c.x + r, c.y + r * 0.5f)
                close()
            },
        )
        Emblem.SKULL -> {
            val head = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(0f, -r * 0.15f), r * 0.8f)) }
            val jaw = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - r * 0.45f, c.y + r * 0.35f, c.x + r * 0.45f, c.y + r * 0.85f, r * 0.15f, r * 0.15f)) }
            fillAndInk(Path().apply { op(head, jaw, PathOperation.Union) })
            val eye = Color.Black.copy(alpha = 0.75f)
            drawCircle(eye, radius = r * 0.22f, center = c + Offset(-r * 0.3f, -r * 0.15f))
            drawCircle(eye, radius = r * 0.22f, center = c + Offset(r * 0.3f, -r * 0.15f))
            for (k in -1..1) drawLine(eye, c + Offset(k * r * 0.2f, r * 0.45f), c + Offset(k * r * 0.2f, r * 0.8f), strokeWidth = max(1f, r * 0.07f))
        }
        Emblem.SHELL -> {
            val fan = Path().apply {
                moveTo(c.x, c.y + r * 0.85f)
                for (k in 0..10) {
                    val a = PI.toFloat() * (1f + k / 10f)
                    val rr = r * (0.95f + 0.08f * sin(k * PI.toFloat()))
                    lineTo(c.x + cos(a) * rr, c.y + r * 0.35f + sin(a) * rr)
                }
                close()
            }
            fillAndInk(fan)
            for (k in 1 until 6) {
                val a = PI.toFloat() * (1f + k / 6f)
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x, c.y + r * 0.85f), Offset(c.x + cos(a) * r * 0.85f, c.y + r * 0.35f + sin(a) * r * 0.85f), strokeWidth = max(1f, r * 0.06f))
            }
        }
        Emblem.TRIDENT -> {
            val w = max(1.5f, r * 0.16f)
            drawLine(fill, c + Offset(0f, -r * 0.6f), c + Offset(0f, r), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(fill, c + Offset(-r * 0.6f, -r * 0.35f), c + Offset(r * 0.6f, -r * 0.35f), strokeWidth = w, cap = StrokeCap.Round)
            for (dx in listOf(-0.6f, 0f, 0.6f)) {
                fillAndInk(Path().apply {
                    moveTo(c.x + dx * r - w, c.y - r * 0.35f)
                    lineTo(c.x + dx * r, c.y - r)
                    lineTo(c.x + dx * r + w, c.y - r * 0.35f)
                    close()
                })
            }
        }
        Emblem.LIGHTNING -> fillAndInk(
            Path().apply {
                moveTo(c.x + r * 0.25f, c.y - r)
                lineTo(c.x - r * 0.55f, c.y + r * 0.1f)
                lineTo(c.x - r * 0.02f, c.y + r * 0.1f)
                lineTo(c.x - r * 0.3f, c.y + r)
                lineTo(c.x + r * 0.55f, c.y - r * 0.15f)
                lineTo(c.x + r * 0.05f, c.y - r * 0.15f)
                close()
            },
        )
    }
}

private fun starPath(c: Offset, outer: Float, inner: Float, points: Int) = Path().apply {
    for (k in 0 until points * 2) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / points
        val rr = if (k % 2 == 0) outer else inner
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** A small framed picture of a boat (editor and shop tiles): sky, sea and the boat. */
internal fun DrawScope.drawBoatTile(skin: Cosmetic?, design: BoatDesign?, timeSeconds: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFD7F1FF))), size = size.copy(height = h * 0.8f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))), topLeft = Offset(0f, h * 0.8f), size = Size(w, h * 0.2f))
    val scale = min(w / 6f, h / 6.6f)
    drawBoat(w / 2f, h * 0.8f, scale, timeSeconds, skin, design)
}
