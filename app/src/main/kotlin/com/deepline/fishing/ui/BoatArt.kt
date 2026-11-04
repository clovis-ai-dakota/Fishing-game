package com.deepline.fishing.ui

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

/** The hull outline: a flat deck 1 m above the water, rounded down to a keel 0.4 m below. */
internal fun hullPath(cx: Float, surfaceY: Float, scale: Float) = Path().apply {
    moveTo(cx - 2.5f * scale, surfaceY - 1.0f * scale)
    lineTo(cx + 2.5f * scale, surfaceY - 1.0f * scale)
    quadraticTo(cx + 2.2f * scale, surfaceY + 0.2f * scale, cx + 1.5f * scale, surfaceY + 0.4f * scale)
    lineTo(cx - 1.5f * scale, surfaceY + 0.4f * scale)
    quadraticTo(cx - 2.2f * scale, surfaceY + 0.2f * scale, cx - 2.5f * scale, surfaceY - 1.0f * scale)
    close()
}

/**
 * The whole boat (rig, hull, pattern, trim, cabin), without the rod. A boat [skin] wins over a
 * hand-made [design]; with neither it's the classic boat.
 */
internal fun DrawScope.drawBoat(cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float, skin: Cosmetic?, design: BoatDesign? = null) {
    val d = design.takeIf { skin == null }
    drawBoatRig(cx, surfaceY, scale, timeSeconds, skin, d)
    val hull = hullPath(cx, surfaceY, scale)
    drawPath(hull, hullBrush(skin, surfaceY - 1f * scale, surfaceY + 0.4f * scale, timeSeconds, d))
    if (d != null) drawHullPattern(d, hull, cx, surfaceY, scale, timeSeconds)
    val trimWidth = if (skin == null && d == null) max(2f, 0.08f * scale) else max(3f, 0.16f * scale)
    drawLine(
        trimBrush(skin, cx - 2.4f * scale, cx + 2.4f * scale, timeSeconds, d),
        Offset(cx - 2.4f * scale, surfaceY - 0.9f * scale),
        Offset(cx + 2.4f * scale, surfaceY - 0.9f * scale),
        strokeWidth = trimWidth,
    )
    if (skin != null) {
        drawLine(
            trimBrush(skin, cx - 2f * scale, cx + 2f * scale, timeSeconds),
            Offset(cx - 1.9f * scale, surfaceY - 0.15f * scale),
            Offset(cx + 1.9f * scale, surfaceY - 0.15f * scale),
            strokeWidth = trimWidth * 0.6f,
        )
    }
    if (d != null) drawPath(hull, Color.Black.copy(alpha = 0.25f), style = Stroke(width = max(1.5f, 0.05f * scale)))
    drawCabin(cx, surfaceY, scale, skin, d)
    // With neither sail nor flag, the emblem goes on the hull.
    if (d != null && d.emblem != Emblem.NONE && d.sailStyle == SailStyle.NONE && d.flagStyle == FlagStyle.NONE) {
        drawEmblem(d.emblem, Offset(cx + 1.2f * scale, surfaceY - 0.45f * scale), 0.38f * scale, Color(d.trimColor()), timeSeconds)
    }
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
    val mastBottom = if (hasSail) surfaceY - 1.0f * scale else surfaceY - 2.3f * scale
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
        drawPath(flag, edge, style = Stroke(width = max(1.2f, 0.04f * scale)))
        if (emblemAt == null) {
            emblemAt = Offset(mastX + 0.75f * scale, mastTop + (if (d.flagStyle == FlagStyle.BANNER) 0.6f else 0.45f) * scale + wave * 0.7f)
            emblemR = 0.32f * scale
        }
    }
    if (d.emblem != Emblem.NONE && emblemAt != null) drawEmblem(d.emblem, emblemAt, emblemR, emblemColor, timeSeconds)
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
