package com.deepline.fishing.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.FishCatalog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Skins: rewards for legendary secrets, made to be shown off. Everything here is drawn BEFORE the
 * darkness layer and only around the hook, the line and the boat, so a skin never lights up dark
 * water or shows a fish the classic look wouldn't. The hook keeps exactly the same shape and size.
 */

/** Which cosmetic is worn in each drawn slot (null = classic). Titles are shown as text, not drawn. */
data class Skins(
    val hook: Cosmetic? = null,
    val line: Cosmetic? = null,
    val boat: Cosmetic? = null,
    val trail: Cosmetic? = null,
    /** A hand-made boat, worn when no boat cosmetic is ([boat] wins when both are set). */
    val customBoat: com.deepline.fishing.core.BoatDesign? = null,
    val atmosphere: com.deepline.fishing.core.Atmosphere = com.deepline.fishing.core.Atmosphere.FAMILY,
) {
    companion object {
        val CLASSIC = Skins()

        fun of(
            equipped: Map<CosmeticSlot, Cosmetic>,
            customBoat: com.deepline.fishing.core.BoatDesign? = null,
            atmosphere: com.deepline.fishing.core.Atmosphere = com.deepline.fishing.core.Atmosphere.FAMILY,
        ) = Skins(
            hook = equipped[CosmeticSlot.HOOK],
            line = equipped[CosmeticSlot.LINE],
            boat = equipped[CosmeticSlot.BOAT],
            trail = equipped[CosmeticSlot.TRAIL],
            customBoat = customBoat,
            atmosphere = atmosphere,
        )

        /** One full set, as unlocked by a legendary secret. */
        fun ofSet(setName: String) = of(Cosmetic.entries.filter { it.setName == setName }.associateBy { it.slot })
    }
}

internal val Gold = Color(0xFFFFD54F)
private val GoldDeep = Color(0xFFFFA000)
private val GoldPale = Color(0xFFFFF8E1)
private val Cyan = Color(0xFF4DD0E1)
private val CyanPale = Color(0xFFE0F7FA)
private val RoyalPurple = Color(0xFF6A1B9A)
private val BoneColors = listOf(Color(0xFFFFF8E1), Color(0xFFD7CCC8), Color(0xFFFFFDE7))

/** A colour of the rainbow, [hue] in degrees (any value). */
private fun rainbow(hue: Float, alpha: Float = 1f, saturation: Float = 0.75f, value: Float = 1f) =
    Color.hsv(((hue % 360f) + 360f) % 360f, saturation, value, alpha.coerceIn(0f, 1f))

private fun prismColors(t: Float, n: Int = 7) = List(n) { rainbow(t * 90f + it * 360f / (n - 1)) }

/** The hook's metal, by skin. */
internal fun hookMetal(skin: Cosmetic?, bend: Offset, s: Float, timeSeconds: Float): Brush {
    val start = Offset(bend.x - s, bend.y - s)
    val end = Offset(bend.x + s, bend.y + s)
    return when (skin) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> Brush.linearGradient(BoneColors, start, end)
        Cosmetic.PRISM_HOOK -> Brush.linearGradient(prismColors(timeSeconds), start, end)
        Cosmetic.CROWN_HOOK -> Brush.linearGradient(listOf(GoldPale, Gold, GoldDeep, Gold), start, end)
        else -> Brush.linearGradient(listOf(Color(0xFFF5F7F8), Color(0xFF90A4AE), Color(0xFFECEFF1)), start, end)
    }
}

/** A small four-pointed twinkle. */
internal fun DrawScope.twinkle(c: Offset, r: Float, color: Color) {
    if (r <= 0f) return
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/** Glow and ornaments around the hook, drawn under it. [s] is the hook size in pixels. */
fun DrawScope.drawHookAura(bend: Offset, s: Float, timeSeconds: Float, skin: Cosmetic?) {
    val center = Offset(bend.x + 0.3f * s, bend.y - 0.4f * s)
    when (skin) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> {
            val pulse = 0.5f + 0.5f * sin(timeSeconds * 3f)
            drawCircle(
                Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f + 0.25f * pulse), Color.Transparent), center = center, radius = 1.9f * s),
                radius = 1.9f * s,
                center = center,
            )
            // Three rune sparks circling the hook.
            for (k in 0 until 3) {
                val a = timeSeconds * 2.2f + k * 2f * PI.toFloat() / 3f
                val p = center + Offset(cos(a), sin(a) * 0.8f) * (1.45f * s)
                drawCircle(CyanPale.copy(alpha = 0.9f), radius = max(1.5f, 0.12f * s), center = p)
                drawCircle(Cyan.copy(alpha = 0.5f), radius = max(2.5f, 0.24f * s), center = p, style = Stroke(width = 1.2f))
            }
        }
        Cosmetic.PRISM_HOOK -> {
            drawCircle(
                Brush.sweepGradient(prismColors(timeSeconds).map { it.copy(alpha = 0.45f) } + rainbow(timeSeconds * 90f, 0.45f), center = center),
                radius = 1.7f * s,
                center = center,
                style = Stroke(width = max(2f, 0.22f * s)),
            )
            for (k in 0 until 4) {
                val a = -timeSeconds * 1.6f + k * PI.toFloat() / 2f
                val p = center + Offset(cos(a), sin(a)) * (1.7f * s)
                twinkle(p, max(3f, 0.32f * s) * (0.7f + 0.3f * sin(timeSeconds * 5f + k)), rainbow(timeSeconds * 90f + k * 90f, 0.95f, 0.45f))
            }
        }
        Cosmetic.CROWN_HOOK -> {
            drawCircle(
                Brush.radialGradient(listOf(Gold.copy(alpha = 0.45f), Color.Transparent), center = center, radius = 1.8f * s),
                radius = 1.8f * s,
                center = center,
            )
            // Slow rotating rays.
            for (k in 0 until 8) {
                val a = timeSeconds * 0.6f + k * PI.toFloat() / 4f
                val d = Offset(cos(a), sin(a))
                drawLine(Gold.copy(alpha = 0.35f), center + d * (1.15f * s), center + d * (1.8f * s), strokeWidth = max(1.5f, 0.1f * s), cap = StrokeCap.Round)
            }
        }
        else -> Unit
    }
}

/** Ornaments drawn on top of the hook (a crown at the eye, a glint on the bend). */
internal fun DrawScope.drawHookOrnament(bend: Offset, s: Float, w: Float, timeSeconds: Float, skin: Cosmetic?) {
    when (skin) {
        Cosmetic.LEVIATHAN_BONE_HOOK -> {
            // Carved rings on the shank that pulse cyan.
            val pulse = 0.5f + 0.5f * sin(timeSeconds * 3f)
            for (k in 0 until 3) {
                val y = bend.y + (-1.05f + k * 0.32f) * s
                drawLine(lerp(Color(0xFF8D6E63), Cyan, pulse), Offset(bend.x - w * 0.6f, y), Offset(bend.x + w * 0.6f, y), strokeWidth = max(1.2f, w * 0.35f))
            }
        }
        Cosmetic.PRISM_HOOK -> {
            val g = (timeSeconds * 0.8f) % 1f
            twinkle(Offset(bend.x + 0.45f * s, bend.y + 0.55f * s), max(2f, 0.3f * s) * sin(g * PI.toFloat()), Color.White)
        }
        Cosmetic.CROWN_HOOK -> {
            // A little crown sitting on the eye.
            val eye = Offset(bend.x, bend.y - 1.4f * s)
            val cw = 0.55f * s
            val base = eye.y - 0.2f * s
            val crown = Path().apply {
                moveTo(eye.x - cw, base)
                lineTo(eye.x - cw, base - 0.35f * s)
                lineTo(eye.x - cw * 0.5f, base - 0.15f * s)
                lineTo(eye.x, base - 0.5f * s)
                lineTo(eye.x + cw * 0.5f, base - 0.15f * s)
                lineTo(eye.x + cw, base - 0.35f * s)
                lineTo(eye.x + cw, base)
                close()
            }
            drawPath(crown, Brush.verticalGradient(listOf(GoldPale, GoldDeep), startY = base - 0.5f * s, endY = base))
            drawCircle(Color(0xFFE53935), radius = max(1.2f, 0.07f * s), center = Offset(eye.x, base - 0.12f * s))
            val g = (timeSeconds * 0.7f) % 1f
            twinkle(Offset(bend.x + 0.5f * s, bend.y + 0.5f * s), max(2f, 0.3f * s) * sin(g * PI.toFloat()), Color.White)
        }
        else -> Unit
    }
}

/**
 * The fishing line along [points] (rod tip first, hook eye last). [bumpFlash] (0..1) tints it red
 * right after a bump, whatever the skin.
 */
fun DrawScope.drawSkinnedLine(points: List<Offset>, timeSeconds: Float, bumpFlash: Float, skin: Cosmetic?) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    val flash = { c: Color -> lerp(c, Color(0xFFFF5252), bumpFlash) }
    val u = 1.dp.toPx() // skinned lines are bold on any screen
    when (skin) {
        Cosmetic.GIANTS_BRAID_LINE -> {
            drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 3.2f * u, cap = StrokeCap.Round))
            drawPath(path, flash(CyanPale), style = Stroke(width = 2.2f * u, cap = StrokeCap.Round))
            drawPath(path, flash(Cyan), style = Stroke(width = 2.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 2.5f * u), -timeSeconds * 15f * u)))
            // A bright shimmer running down the braid.
            drawPath(
                path,
                Color.White.copy(alpha = 0.95f * (1f - bumpFlash)),
                style = Stroke(width = 1.6f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 50f * u), -timeSeconds * 60f * u)),
            )
        }
        Cosmetic.RAINBOW_LINE -> {
            drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 3f * u, cap = StrokeCap.Round))
            // Colour flows down from the rod to the hook.
            for (i in 1 until points.size) {
                val hue = i * 360f / points.size * 1.5f - timeSeconds * 160f
                drawLine(flash(rainbow(hue, saturation = 0.85f)), points[i - 1], points[i], strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
        }
        Cosmetic.GOLDEN_LINE -> {
            drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 3f * u, cap = StrokeCap.Round))
            drawPath(path, flash(GoldDeep), style = Stroke(width = 2f * u, cap = StrokeCap.Round))
            drawPath(path, flash(Gold), style = Stroke(width = 1.1f * u, cap = StrokeCap.Round))
            drawPath(
                path,
                Color.White.copy(alpha = 0.95f * (1f - bumpFlash)),
                style = Stroke(width = 1.5f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * u, 28f * u), -timeSeconds * 45f * u)),
            )
        }
        else -> {
            drawPath(path, Color.Black.copy(alpha = 0.22f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            drawPath(path, flash(Color(0xFFE8F4FA)).copy(alpha = 0.9f), style = Stroke(width = 1.7f, cap = StrokeCap.Round))
        }
    }
}

/** Bubbles (or the skin's trail) rising from the hook. [scale] is pixels per meter. */
fun DrawScope.drawHookTrail(hookX: Float, eyeY: Float, scale: Float, timeSeconds: Float, skin: Cosmetic?) {
    val count = if (skin == null) 7 else 12
    val rise = if (skin == null) 2.4f else 3.4f
    val grow = if (skin == null) 1f else 1.7f
    for (i in 0 until count) {
        val p = (timeSeconds * 0.9f + i / count.toFloat()) % 1f
        val b = Offset(
            hookX + sin(p * 9f + i * 2.3f) * 0.3f * grow * scale + (i % 3 - 1) * 0.14f * grow * scale,
            eyeY - p * rise * scale,
        )
        val fade = 1f - p
        val br = (0.04f + 0.06f * p) * grow * scale
        when (skin) {
            Cosmetic.TIDE_TRAIL -> {
                drawCircle(Cyan.copy(alpha = 0.45f * fade), radius = br * 1.2f, center = b)
                drawCircle(CyanPale.copy(alpha = 0.95f * fade), radius = br, center = b, style = Stroke(width = max(1.6f, br * 0.25f)))
                if (i % 3 == 0) {
                    // Little ripples spreading out.
                    val rr = br * (1.5f + 2f * p)
                    drawOval(Cyan.copy(alpha = 0.5f * fade), topLeft = b - Offset(rr, rr * 0.4f), size = Size(rr * 2f, rr * 0.8f), style = Stroke(width = 1.2f))
                }
            }
            Cosmetic.PRISM_TRAIL -> {
                val c = rainbow(i * 360f / count + timeSeconds * 120f, 0.9f * fade)
                if (i % 2 == 0) twinkle(b, br * 1.8f, c) else drawCircle(c, radius = br, center = b, style = Stroke(width = max(1.8f, br * 0.3f)))
            }
            Cosmetic.GOLDEN_TRAIL -> {
                if (i % 3 == 0) {
                    // Tumbling coins.
                    val flip = kotlin.math.abs(cos(timeSeconds * 4f + i))
                    drawOval(Gold.copy(alpha = fade), topLeft = b - Offset(br * flip, br), size = Size(2f * br * flip + 1f, 2f * br))
                    drawOval(GoldDeep.copy(alpha = fade), topLeft = b - Offset(br * flip, br), size = Size(2f * br * flip + 1f, 2f * br), style = Stroke(width = 1.2f))
                } else {
                    twinkle(b, br * 1.6f, Gold.copy(alpha = 0.95f * fade))
                }
            }
            else -> {
                drawCircle(Color.White.copy(alpha = 0.45f * fade), radius = br, center = b, style = Stroke(width = 1.2f))
                drawCircle(Color.White.copy(alpha = 0.2f * fade), radius = br * 0.35f, center = b - Offset(br * 0.3f, br * 0.3f))
            }
        }
    }
}

/** The hull's colour and the trim stripe along it, by boat skin (or hand-made [design]). */
internal fun hullBrush(skin: Cosmetic?, top: Float, bottom: Float, timeSeconds: Float, design: com.deepline.fishing.core.BoatDesign? = null): Brush =
    if (skin == null && design != null) customHullBrush(design, top, bottom) else when (skin) {
    Cosmetic.GIANT_SLAYER_PENNANT -> Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF102027)), startY = top, endY = bottom)
    Cosmetic.PRISM_SAIL -> Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFB0BEC5)), startY = top, endY = bottom)
    Cosmetic.ROYAL_BOAT -> Brush.verticalGradient(listOf(Color(0xFF7B1FA2), Color(0xFF38006B)), startY = top, endY = bottom)
    else -> Brush.verticalGradient(listOf(Color(0xFFA1662F), Color(0xFF5D3A1A)), startY = top, endY = bottom)
}

internal fun trimBrush(skin: Cosmetic?, left: Float, right: Float, timeSeconds: Float, design: com.deepline.fishing.core.BoatDesign? = null): Brush =
    if (skin == null && design != null) Brush.horizontalGradient(listOf(Color(design.trimColor()), lerp(Color(design.trimColor()), Color.White, 0.35f), Color(design.trimColor())), startX = left, endX = right)
    else when (skin) {
    Cosmetic.GIANT_SLAYER_PENNANT -> Brush.horizontalGradient(listOf(Cyan, CyanPale, Cyan), startX = left, endX = right)
    Cosmetic.PRISM_SAIL -> Brush.horizontalGradient(prismColors(timeSeconds), startX = left, endX = right)
    Cosmetic.ROYAL_BOAT -> Brush.horizontalGradient(listOf(GoldDeep, GoldPale, Gold, GoldDeep), startX = left, endX = right)
    else -> Brush.horizontalGradient(listOf(Color(0xFFE8D5B5), Color(0xFFE8D5B5)), startX = left, endX = right)
}

/** Masts, sails and flags behind the cabin. */
internal fun DrawScope.drawBoatRig(cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float, skin: Cosmetic?, design: com.deepline.fishing.core.BoatDesign? = null) {
    if (skin == null && design != null) return drawCustomRig(design, cx, surfaceY, scale, timeSeconds)
    when (skin) {
        Cosmetic.GIANT_SLAYER_PENNANT -> drawPennant(cx, surfaceY, scale, timeSeconds)
        Cosmetic.PRISM_SAIL -> {
            val mastBase = Offset(cx - 0.2f * scale, surfaceY - 1.0f * scale)
            val mastTop = Offset(mastBase.x, surfaceY - 5.2f * scale)
            drawLine(Color(0xFFECEFF1), mastBase, mastTop, strokeWidth = max(2f, 0.08f * scale), cap = StrokeCap.Round)
            val billow = sin(timeSeconds * 2f) * 0.15f * scale
            val sail = Path().apply {
                moveTo(mastTop.x - 0.1f * scale, mastTop.y + 0.2f * scale)
                quadraticTo(mastTop.x - 1.6f * scale - billow, mastTop.y + 2.2f * scale, mastTop.x - 2.2f * scale, surfaceY - 1.5f * scale)
                lineTo(mastTop.x - 0.1f * scale, surfaceY - 1.5f * scale)
                close()
            }
            drawPath(sail, Brush.verticalGradient(prismColors(timeSeconds), startY = mastTop.y, endY = surfaceY - 1.5f * scale))
            drawPath(sail, Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.5f))
            twinkle(Offset(mastTop.x, mastTop.y - 0.1f * scale), 0.35f * scale * (0.7f + 0.3f * sin(timeSeconds * 4f)), Color.White)
        }
        Cosmetic.ROYAL_BOAT -> {
            val mastBase = Offset(cx - 0.2f * scale, surfaceY - 1.0f * scale)
            val mastTop = Offset(mastBase.x, surfaceY - 5.4f * scale)
            drawLine(GoldDeep, mastBase, mastTop, strokeWidth = max(2.5f, 0.1f * scale), cap = StrokeCap.Round)
            val billow = sin(timeSeconds * 2f) * 0.15f * scale
            val sailTop = mastTop.y + 0.5f * scale
            val sailBottom = surfaceY - 1.6f * scale
            val sail = Path().apply {
                moveTo(mastTop.x - 1.6f * scale, sailTop)
                lineTo(mastTop.x + 1.6f * scale, sailTop)
                quadraticTo(mastTop.x + 1.9f * scale + billow, (sailTop + sailBottom) / 2f, mastTop.x + 1.5f * scale, sailBottom)
                lineTo(mastTop.x - 1.5f * scale, sailBottom)
                quadraticTo(mastTop.x - 1.3f * scale + billow, (sailTop + sailBottom) / 2f, mastTop.x - 1.6f * scale, sailTop)
                close()
            }
            drawPath(sail, Brush.verticalGradient(listOf(Color(0xFF8E24AA), RoyalPurple), startY = sailTop, endY = sailBottom))
            drawPath(sail, Gold, style = Stroke(width = max(1.5f, 0.06f * scale)))
            // Crown emblem on the sail.
            val c = Offset(mastTop.x, (sailTop + sailBottom) / 2f)
            val w = 0.7f * scale
            val crown = Path().apply {
                moveTo(c.x - w, c.y + 0.35f * scale)
                lineTo(c.x - w, c.y - 0.25f * scale)
                lineTo(c.x - w * 0.5f, c.y + 0.05f * scale)
                lineTo(c.x, c.y - 0.45f * scale)
                lineTo(c.x + w * 0.5f, c.y + 0.05f * scale)
                lineTo(c.x + w, c.y - 0.25f * scale)
                lineTo(c.x + w, c.y + 0.35f * scale)
                close()
            }
            drawPath(crown, Brush.verticalGradient(listOf(GoldPale, GoldDeep), startY = c.y - 0.45f * scale, endY = c.y + 0.35f * scale))
            // A gold streamer at the top.
            val wave = sin(timeSeconds * 3f) * 0.1f * scale
            val flag = Path().apply {
                moveTo(mastTop.x, mastTop.y)
                quadraticTo(mastTop.x + 0.6f * scale, mastTop.y + 0.1f * scale + wave, mastTop.x + 1.2f * scale, mastTop.y + 0.2f * scale + wave)
                lineTo(mastTop.x, mastTop.y + 0.4f * scale)
                close()
            }
            drawPath(flag, Gold)
            twinkle(Offset(c.x + 0.4f * scale, c.y - 0.3f * scale), 0.3f * scale * kotlin.math.abs(sin(timeSeconds * 1.5f)), Color.White)
        }
        else -> Unit
    }
}

/** The Giant-slayer pennant: a mast on the cabin with a waving flag showing a leviathan. */
private fun DrawScope.drawPennant(cx: Float, surfaceY: Float, scale: Float, timeSeconds: Float) {
    val mastBase = Offset(cx - 0.4f * scale, surfaceY - 2.3f * scale)
    val mastTop = Offset(mastBase.x, surfaceY - 5.0f * scale)
    drawLine(Color(0xFF5D4037), mastBase, mastTop, strokeWidth = max(2f, 0.08f * scale), cap = StrokeCap.Round)
    val wave = sin(timeSeconds * 3f) * 0.15f * scale
    val flag = Path().apply {
        moveTo(mastTop.x, mastTop.y)
        quadraticTo(mastTop.x + 1.2f * scale, mastTop.y + 0.25f * scale + wave, mastTop.x + 2.4f * scale, mastTop.y + 0.6f * scale + wave)
        quadraticTo(mastTop.x + 1.2f * scale, mastTop.y + 1.0f * scale + wave, mastTop.x, mastTop.y + 1.3f * scale)
        close()
    }
    drawPath(flag, Color(0xFF102027))
    drawPath(flag, Cyan, style = Stroke(width = 2f))
    drawFish(
        FishCatalog.byId("pale_leviathan")!!,
        Offset(mastTop.x + 0.9f * scale, mastTop.y + 0.65f * scale + wave * 0.6f),
        1.2f * scale,
        1,
        sparkling = false,
        timeSeconds = timeSeconds,
    )
}

/** A small badge drawn for a set, used on the Skins screen and the secret details. */
internal fun DrawScope.drawCabin(cx: Float, surfaceY: Float, scale: Float, skin: Cosmetic?, design: com.deepline.fishing.core.BoatDesign? = null) {
    drawRoundRect(
        when {
            skin == null && design != null -> Color(design.cabinColor())
            skin == Cosmetic.ROYAL_BOAT -> GoldPale
            else -> Color(0xFFF5E6CC)
        },
        topLeft = Offset(cx - 1.3f * scale, surfaceY - 2.3f * scale),
        size = Size(1.7f * scale, 1.3f * scale),
        cornerRadius = CornerRadius(0.15f * scale),
    )
    drawRoundRect(
        Color(0xFF81D4FA),
        topLeft = Offset(cx - 1.0f * scale, surfaceY - 2.05f * scale),
        size = Size(0.6f * scale, 0.5f * scale),
        cornerRadius = CornerRadius(0.08f * scale),
    )
}
