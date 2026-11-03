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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Lighting
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val waterStops = listOf(
    0f to Color(0xFF4FC3F7),
    100f to Color(0xFF0288D1),
    300f to Color(0xFF01396B),
    500f to Color(0xFF061A3A),
    700f to Color(0xFF02060F),
)

private fun waterColor(depth: Float): Color {
    if (depth <= waterStops.first().first) return waterStops.first().second
    for (i in 1 until waterStops.size) {
        val (d1, c1) = waterStops[i]
        if (depth <= d1) {
            val (d0, c0) = waterStops[i - 1]
            return lerp(c0, c1, (depth - d0) / (d1 - d0))
        }
    }
    return waterStops.last().second
}

/** Stable pseudo-random number in 0..1, so particles stay put from frame to frame. */
private fun hash01(i: Int, k: Int, salt: Int): Float {
    var x = i * 374761393 + k * 668265263 + salt * 1274126177
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

private val LineColor = Color(0xFFE8F4FA)
private val BumpRed = Color(0xFFFF5252)
private val BumpFreeGreen = Color(0xFF9CFFB0)

/** Draws the whole underwater scene for [session], with the camera following the hook. */
fun DrawScope.drawCastScene(session: CastSession, timeSeconds: Float, textMeasurer: TextMeasurer) {
    val scale = size.width / GameConfig.WORLD_WIDTH
    val viewHeight = size.height / scale
    val top = max(session.hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f)
    val bottom = top + viewHeight
    fun sx(x: Float) = x * scale
    fun sy(depth: Float) = (depth - top) * scale

    val surfaceY = sy(0f)
    val elapsed = session.elapsedSeconds

    // Sky
    if (surfaceY > 0f) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFD7F1FF)), startY = 0f, endY = surfaceY),
            size = Size(size.width, surfaceY),
        )
    }

    // Water
    val waterTop = surfaceY.coerceAtLeast(0f)
    drawRect(
        Brush.verticalGradient(
            listOf(waterColor(max(top, 0f)), waterColor(bottom)),
            startY = waterTop,
            endY = size.height,
        ),
        topLeft = Offset(0f, waterTop),
        size = Size(size.width, size.height - waterTop),
    )

    // Sun rays near the surface, fading with depth.
    val rayStrength = (1f - max(top, 0f) / 80f).coerceIn(0f, 1f)
    if (rayStrength > 0f) {
        val rayBottom = sy(45f)
        for (i in 0 until 4) {
            val x0 = size.width * (0.12f + i * 0.26f) + sin(timeSeconds * 0.3f + i * 1.7f) * 0.6f * scale
            val ray = Path().apply {
                moveTo(x0 - 0.4f * scale, surfaceY)
                lineTo(x0 + 0.4f * scale, surfaceY)
                lineTo(x0 + 3.5f * scale, rayBottom)
                lineTo(x0 + 0.5f * scale, rayBottom)
                close()
            }
            drawPath(
                ray,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f * rayStrength), Color.Transparent),
                    startY = surfaceY,
                    endY = rayBottom,
                ),
            )
        }
    }

    // Drifting particles ("marine snow") fixed in the water, so they scroll with the camera.
    val tile = 30f
    for (k in floor(top / tile).toInt()..floor(bottom / tile).toInt()) {
        for (i in 0 until 14) {
            val wy = k * tile + hash01(i, k, 2) * tile
            if (wy < 0.5f) continue
            val wx = hash01(i, k, 1) * GameConfig.WORLD_WIDTH + sin(timeSeconds * 0.5f + i + k) * 0.15f
            drawCircle(
                Color.White.copy(alpha = 0.08f + 0.10f * hash01(i, k, 3)),
                radius = (0.03f + 0.04f * hash01(i, k, 4)) * scale,
                center = Offset(sx(wx), sy(wy)),
            )
        }
    }

    // Surface waves
    if (surfaceY > -20f && surfaceY < size.height) {
        val wave = Path()
        var x = 0f
        wave.moveTo(0f, surfaceY)
        while (x <= size.width) {
            wave.lineTo(x, surfaceY + sin(x / 28f + timeSeconds * 2f) * 3f)
            x += 8f
        }
        drawPath(wave, Color.White.copy(alpha = 0.6f), style = Stroke(width = 3f))
    }

    // Depth markers
    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    var marker = ceil(max(top, 1f) / 50f) * 50f
    while (marker < bottom) {
        val y = sy(marker)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        drawLabel(textMeasurer, "${marker.toInt()} m", labelStyle, x = 8f, y = y + 4f)
        marker += 50f
    }

    // Where bumps start to count.
    val bumpFreeEnd = GameConfig.BUMP_FREE_DEPTH
    if (bumpFreeEnd in top..bottom) {
        val y = sy(bumpFreeEnd)
        drawLine(
            BumpFreeGreen.copy(alpha = 0.45f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )
        drawLabel(
            textMeasurer,
            "Bump-free above ${bumpFreeEnd.toInt()} m",
            TextStyle(color = BumpFreeGreen.copy(alpha = 0.8f), fontSize = 11.sp),
            x = null,
            y = y - 20f,
        )
    }

    // Rope limit
    val ropeEnd = session.specs.lengthM
    if (ropeEnd in top..bottom) {
        val y = sy(ropeEnd + GameConfig.HOOK_RADIUS)
        drawLine(
            Color(0xFFFFB74D).copy(alpha = 0.7f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
        )
        drawLabel(
            textMeasurer,
            "Rope limit",
            TextStyle(color = Color(0xFFFFB74D), fontSize = 11.sp),
            x = null, // right-aligned
            y = y + 4f,
        )
    }

    // Boat and rod. The rod bends more when reeling up a heavy line.
    val cx = sx(GameConfig.WORLD_WIDTH / 2f)
    val load = (session.carriedWeightKg / session.specs.maxWeightKg).coerceIn(0f, 1f)
    val bend = if (session.phase == CastPhase.ASCENDING) 0.25f + 0.6f * load else 0.1f
    val rodBase = Offset(cx + 0.9f * scale, surfaceY - 1.0f * scale)
    val rodTip = Offset(cx + (1.9f + bend * 0.6f) * scale, surfaceY - (2.9f - bend) * scale)
    if (surfaceY > -6f * scale) drawBoatAndRod(cx, surfaceY, scale, rodBase, rodTip)

    // Obstacles
    for (o in session.world.obstacles) {
        if (o.depth < top - 4f || o.depth > bottom + 4f) continue
        drawObstacle(o, Offset(sx(o.x), sy(o.depth)), o.radius * scale, timeSeconds)
    }

    // Free fish
    for (f in session.world.fish) {
        if (f.caught) continue
        if (f.depth < top - 5f || f.depth > bottom + 5f) continue
        drawFish(
            species = f.species,
            center = Offset(sx(f.x), sy(f.depth)),
            lengthPx = f.species.bodyLengthM * scale,
            direction = f.direction,
            sparkling = f.sparkling,
            timeSeconds = timeSeconds,
            phase = f.id * 1.7f,
            wag = if (f.isFleeing) 2f else 1f,
        )
    }

    // Line: from the rod tip into the water, curving down to the hook.
    val hook = Offset(sx(session.hookX), sy(session.hookDepth))
    val s = GameConfig.HOOK_RADIUS * scale
    val hookEye = Offset(hook.x, hook.y - 1.4f * s)
    val bumpFlash = (1f - (elapsed - session.lastTouchAtSeconds) / 0.5f).coerceIn(0f, 1f)
    val entry = Offset(rodTip.x + (hookEye.x - rodTip.x) * 0.1f, surfaceY)
    val sway = sin(timeSeconds * 1.1f) * 0.3f * scale * (if (session.phase == CastPhase.DESCENDING) 1f else 0.35f)
    val control = Offset(entry.x + (hookEye.x - entry.x) * 0.2f + sway, entry.y + (hookEye.y - entry.y) * 0.55f)
    val line = Path().apply {
        moveTo(rodTip.x, rodTip.y)
        lineTo(entry.x, entry.y)
        quadraticTo(control.x, control.y, hookEye.x, hookEye.y)
    }
    drawPath(line, Color.Black.copy(alpha = 0.22f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
    drawPath(line, lerp(LineColor, BumpRed, bumpFlash).copy(alpha = 0.9f), style = Stroke(width = 1.7f, cap = StrokeCap.Round))
    if (surfaceY > -10f && surfaceY < size.height + 10f) {
        for (k in 0 until 2) {
            val p = (timeSeconds * 0.8f + k * 0.5f) % 1f
            val r = (0.2f + p) * scale
            drawOval(
                Color.White.copy(alpha = 0.5f * (1f - p)),
                topLeft = Offset(entry.x - r, entry.y - r * 0.25f),
                size = Size(r * 2f, r * 0.5f),
                style = Stroke(width = 1.5f),
            )
        }
    }
    // Sinker a little above the hook.
    val sinker = quadPoint(entry, control, hookEye, 0.9f)
    val sinkerR = max(0.13f * scale, 3f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFB0BEC5), Color(0xFF455A64)), center = sinker - Offset(sinkerR * 0.3f, sinkerR * 0.3f), radius = sinkerR * 1.4f),
        radius = sinkerR,
        center = sinker,
    )

    // Bubbles trailing from the hook.
    for (i in 0 until 7) {
        val p = (timeSeconds * 0.9f + i / 7f) % 1f
        val b = Offset(
            hook.x + sin(p * 9f + i * 2.3f) * 0.25f * scale + (i % 3 - 1) * 0.12f * scale,
            hookEye.y - p * 2.4f * scale,
        )
        val br = (0.04f + 0.06f * p) * scale
        drawCircle(Color.White.copy(alpha = 0.45f * (1f - p)), radius = br, center = b, style = Stroke(width = 1.2f))
        drawCircle(Color.White.copy(alpha = 0.2f * (1f - p)), radius = br * 0.35f, center = b - Offset(br * 0.3f, br * 0.3f))
    }

    // Bump-free shield around the hook near the surface.
    if (session.isBumpFree) {
        val pulse = 0.5f + 0.5f * sin(timeSeconds * 4f)
        drawCircle(BumpFreeGreen.copy(alpha = 0.10f + 0.06f * pulse), radius = 2.0f * s, center = hook)
        drawCircle(BumpFreeGreen.copy(alpha = 0.55f), radius = 2.0f * s, center = hook, style = Stroke(width = 2f))
    }

    drawHook(hook, s, timeSeconds)

    // Catches hanging from the hook, heads up and struggling.
    val n = session.caughtFish.size
    session.caughtFish.forEachIndexed { i, f ->
        val len = f.species.bodyLengthM * scale
        val ox = (i - (n - 1) / 2f) * 0.35f * scale
        drawFish(
            species = f.species,
            center = Offset(hook.x + ox, hook.y + s * 0.9f + len / 2f),
            lengthPx = len,
            direction = 1,
            sparkling = f.sparkling,
            timeSeconds = timeSeconds,
            rotationDegrees = -90f,
            phase = f.id * 1.7f,
            wag = 2.2f,
        )
    }

    // Feedback: a red ring on a bump, a golden burst on a catch.
    if (bumpFlash > 0f) {
        drawCircle(BumpRed.copy(alpha = 0.8f * bumpFlash), radius = s * (1.4f + 2.6f * (1f - bumpFlash)), center = hook, style = Stroke(width = 3f))
    }
    val catchFlash = (1f - (elapsed - session.lastCatchAtSeconds) / 0.6f).coerceIn(0f, 1f)
    if (catchFlash > 0f) {
        val inner = s * (1.2f + 2f * (1f - catchFlash))
        for (k in 0 until 8) {
            val a = (k / 8f) * 2f * PI.toFloat()
            val dir = Offset(cos(a), sin(a))
            drawLine(
                SparkleGold.copy(alpha = catchFlash),
                hook + dir * inner,
                hook + dir * (inner + s * 0.9f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Darkness below 300 m; a lantern widens the lit circle around the hook.
    val darkness = Lighting.darkness(session.hookDepth)
    if (darkness > 0f) {
        val radius = Lighting.visibleRadius(session.lanternActive) * scale
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.6f to Color.Black.copy(alpha = 0.45f * darkness),
                1f to Color.Black.copy(alpha = 0.97f * darkness),
                center = hook,
                radius = radius,
            ),
        )
        if (session.lanternActive) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x40FFE082), Color.Transparent),
                    center = hook,
                    radius = radius,
                ),
                radius = radius,
                center = hook,
            )
        }
    }

    // Things that glow in the dark (anglerfish lures) shine through the darkness.
    for (f in session.world.fish) {
        if (f.caught || f.depth < top - 5f || f.depth > bottom + 5f) continue
        drawFishGlow(f.species, Offset(sx(f.x), sy(f.depth)), f.species.bodyLengthM * scale, f.direction, 0.35f + 0.65f * darkness)
    }
}

private fun DrawScope.drawBoatAndRod(cx: Float, surfaceY: Float, scale: Float, rodBase: Offset, rodTip: Offset) {
    val hull = Path().apply {
        moveTo(cx - 2.5f * scale, surfaceY - 1.0f * scale)
        lineTo(cx + 2.5f * scale, surfaceY - 1.0f * scale)
        quadraticTo(cx + 2.2f * scale, surfaceY + 0.2f * scale, cx + 1.5f * scale, surfaceY + 0.4f * scale)
        lineTo(cx - 1.5f * scale, surfaceY + 0.4f * scale)
        quadraticTo(cx - 2.2f * scale, surfaceY + 0.2f * scale, cx - 2.5f * scale, surfaceY - 1.0f * scale)
        close()
    }
    drawPath(
        hull,
        Brush.verticalGradient(listOf(Color(0xFFA1662F), Color(0xFF5D3A1A)), startY = surfaceY - 1f * scale, endY = surfaceY + 0.4f * scale),
    )
    drawLine(Color(0xFFE8D5B5), Offset(cx - 2.4f * scale, surfaceY - 0.9f * scale), Offset(cx + 2.4f * scale, surfaceY - 0.9f * scale), strokeWidth = max(2f, 0.08f * scale))
    // Cabin with a window.
    drawRoundRect(
        Color(0xFFF5E6CC),
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
    // Rod: a tapering curve from the reel to the tip.
    val control = Offset(rodBase.x + (rodTip.x - rodBase.x) * 0.3f, rodBase.y + (rodTip.y - rodBase.y) * 0.8f)
    val rod = Path().apply {
        moveTo(rodBase.x, rodBase.y)
        quadraticTo(control.x, control.y, rodTip.x, rodTip.y)
    }
    drawPath(
        rod,
        Brush.linearGradient(listOf(Color(0xFF3E2723), Color(0xFFBCAAA4)), start = rodBase, end = rodTip),
        style = Stroke(width = max(3f, 0.09f * scale), cap = StrokeCap.Round),
    )
    val reel = quadPoint(rodBase, control, rodTip, 0.12f)
    drawCircle(Color(0xFFB0BEC5), radius = max(3f, 0.16f * scale), center = reel)
    drawCircle(Color(0xFF546E7A), radius = max(1.5f, 0.07f * scale), center = reel)
}

/**
 * A barbed J-hook with a wriggling worm. [bend] is the bottom of the hook (where it catches
 * fish) and [s] its size in pixels; the line ties on at the eye, 1.4 s above the bend.
 */
fun DrawScope.drawHook(bend: Offset, s: Float, timeSeconds: Float, withBait: Boolean = true) {
    val w = max(2.2f, s * 0.16f)
    val metal = Brush.linearGradient(
        listOf(Color(0xFFF5F7F8), Color(0xFF90A4AE), Color(0xFFECEFF1)),
        start = Offset(bend.x - s, bend.y - s),
        end = Offset(bend.x + s, bend.y + s),
    )
    val pointTip = Offset(bend.x + 0.85f * s, bend.y - 0.10f * s)
    val shankAndBend = Path().apply {
        moveTo(bend.x, bend.y - 1.23f * s)
        lineTo(bend.x, bend.y + 0.15f * s)
        cubicTo(bend.x, bend.y + 0.95f * s, bend.x + 0.95f * s, bend.y + 0.95f * s, pointTip.x, pointTip.y)
    }
    if (withBait) {
        // Worm threaded on the shank, with its tail dangling below the bend.
        val pink = Color(0xFFE57373)
        drawLine(pink, Offset(bend.x, bend.y - 0.65f * s), Offset(bend.x, bend.y + 0.35f * s), strokeWidth = s * 0.32f, cap = StrokeCap.Round)
        val tail = Path()
        for (i in 0..8) {
            val u = i / 8f
            val x = bend.x + 0.30f * s + sin(timeSeconds * 6f + u * 6f) * 0.16f * s * u
            val y = bend.y + (0.55f + u * 1.0f) * s
            if (i == 0) tail.moveTo(bend.x + 0.05f * s, bend.y + 0.45f * s)
            tail.lineTo(x, y)
        }
        drawPath(tail, pink, style = Stroke(width = s * 0.26f, cap = StrokeCap.Round))
        for (k in 0 until 3) {
            val y = bend.y + (-0.45f + k * 0.3f) * s
            drawLine(Color(0xFFC62828).copy(alpha = 0.6f), Offset(bend.x - s * 0.14f, y), Offset(bend.x + s * 0.14f, y), strokeWidth = 1.5f)
        }
    }
    drawCircle(metal, radius = s * 0.17f, center = Offset(bend.x, bend.y - 1.4f * s), style = Stroke(width = w * 0.8f))
    drawPath(shankAndBend, metal, style = Stroke(width = w, cap = StrokeCap.Round))
    drawLine(metal, pointTip, Offset(bend.x + 0.62f * s, bend.y + 0.20f * s), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
}

/**
 * Draws a one-line label at ([x], [y]); a null [x] right-aligns it. The text is measured without
 * size limits and then drawn, so a label partly or fully off-screen is simply clipped.
 * (`drawText(textMeasurer, text, topLeft)` sizes the text to the space between [y] and the bottom
 * edge, and throws when [y] is below the edge: that crashed the game whenever a depth or
 * "Rope limit" label scrolled in at the bottom of the screen.)
 */
private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, style: TextStyle, x: Float?, y: Float) {
    if (y > size.height || y < -200f) return
    val layout = textMeasurer.measure(text, style, softWrap = false, maxLines = 1)
    val left = x ?: (size.width - layout.size.width - 8f)
    drawText(layout, topLeft = Offset(left, y))
}

private fun DrawScope.drawObstacle(o: Obstacle, c: Offset, r: Float, timeSeconds: Float) {
    when (o.kind) {
        ObstacleKind.ROCK -> {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF78909C), Color(0xFF37474F)), center = c - Offset(r * 0.35f, r * 0.35f), radius = r * 1.5f),
                radius = r,
                center = c,
            )
            drawCircle(Color(0xFF263238).copy(alpha = 0.35f), radius = r * 0.25f, center = c + Offset(r * 0.3f, r * 0.2f))
            drawCircle(Color(0xFF263238).copy(alpha = 0.3f), radius = r * 0.15f, center = c + Offset(-r * 0.25f, r * 0.45f))
        }
        ObstacleKind.DRIFTWOOD -> {
            rotate(degrees = (o.id * 37 % 40 - 20).toFloat(), pivot = c) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF8D6E63), Color(0xFF4E342E)), startY = c.y - r * 0.3f, endY = c.y + r * 0.3f),
                    topLeft = c - Offset(r, r * 0.3f),
                    size = Size(r * 2f, r * 0.6f),
                    cornerRadius = CornerRadius(r * 0.3f),
                )
                for (k in 0 until 3) {
                    val y = c.y + (k - 1) * r * 0.14f
                    drawLine(Color(0xFF3E2723).copy(alpha = 0.5f), Offset(c.x - r * 0.8f, y), Offset(c.x + r * 0.6f, y), strokeWidth = 1.5f)
                }
            }
        }
        ObstacleKind.JELLY -> {
            val bob = sin(timeSeconds * 2f + o.id) * r * 0.08f
            val center = c + Offset(0f, bob)
            for (k in 0 until 4) {
                val x = center.x - r * 0.6f + k * r * 0.4f
                val sway = sin(timeSeconds * 3f + k) * r * 0.12f
                drawLine(
                    Color(0x99F8BBD0),
                    Offset(x, center.y),
                    Offset(x + sway, center.y + r * 0.9f),
                    strokeWidth = 2f,
                )
            }
            drawArc(
                Brush.radialGradient(listOf(Color(0xDDF8BBD0), Color(0x99EC407A)), center = center, radius = r),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
            )
        }
    }
    if (o.touched) {
        drawCircle(Color(0xFFFF5252).copy(alpha = 0.5f), radius = r * 1.1f, center = c, style = Stroke(width = 2f))
    }
}
