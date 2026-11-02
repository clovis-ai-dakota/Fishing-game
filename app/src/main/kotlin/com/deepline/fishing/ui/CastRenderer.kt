package com.deepline.fishing.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Lighting
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import kotlin.math.ceil
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

/** Draws the whole underwater scene for [session], with the camera following the hook. */
fun DrawScope.drawCastScene(session: CastSession, timeSeconds: Float, textMeasurer: TextMeasurer) {
    val scale = size.width / GameConfig.WORLD_WIDTH
    val viewHeight = size.height / scale
    val top = max(session.hookDepth - viewHeight * 0.4f, -viewHeight * 0.18f)
    val bottom = top + viewHeight
    fun sx(x: Float) = x * scale
    fun sy(depth: Float) = (depth - top) * scale

    val surfaceY = sy(0f)

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
        drawText(textMeasurer, "${marker.toInt()} m", topLeft = Offset(8f, y + 4f), style = labelStyle)
        marker += 50f
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
        drawText(
            textMeasurer,
            "Rope limit",
            topLeft = Offset(size.width - 90f, y + 4f),
            style = TextStyle(color = Color(0xFFFFB74D), fontSize = 11.sp),
        )
    }

    // Boat
    if (surfaceY > -60f) {
        val cx = sx(GameConfig.WORLD_WIDTH / 2f)
        val hull = Path().apply {
            moveTo(cx - 2.4f * scale, surfaceY - 1.0f * scale)
            lineTo(cx + 2.4f * scale, surfaceY - 1.0f * scale)
            lineTo(cx + 1.7f * scale, surfaceY + 0.35f * scale)
            lineTo(cx - 1.7f * scale, surfaceY + 0.35f * scale)
            close()
        }
        drawPath(hull, Color(0xFF8D5524))
        drawRect(
            Color(0xFFF5E6CC),
            topLeft = Offset(cx - 1.2f * scale, surfaceY - 2.2f * scale),
            size = Size(1.6f * scale, 1.2f * scale),
        )
        drawLine(
            Color(0xFF5D4037),
            Offset(cx + 0.9f * scale, surfaceY - 1.0f * scale),
            Offset(cx + 1.8f * scale, surfaceY - 3.4f * scale),
            strokeWidth = 3f,
        )
    }

    // Obstacles
    for (o in session.world.obstacles) {
        if (o.depth < top - 4f || o.depth > bottom + 4f) continue
        drawObstacle(o, Offset(sx(o.x), sy(o.depth)), o.radius * scale, timeSeconds)
    }

    // Free fish
    for (f in session.world.fish) {
        if (f.caught) continue
        if (f.depth < top - 3f || f.depth > bottom + 3f) continue
        drawFish(
            species = f.species,
            center = Offset(sx(f.x), sy(f.depth)),
            lengthPx = f.species.bodyLengthM * scale,
            direction = f.direction,
            sparkling = f.sparkling,
            timeSeconds = timeSeconds,
        )
    }

    // Line and hook
    val hook = Offset(sx(session.hookX), sy(session.hookDepth))
    val rodTip = Offset(sx(GameConfig.WORLD_WIDTH / 2f + 1.8f), sy(-3.4f))
    drawLine(Color.White.copy(alpha = 0.8f), rodTip, hook, strokeWidth = 2f)
    val hookR = GameConfig.HOOK_RADIUS * scale
    drawArc(
        Color(0xFFCFD8DC),
        startAngle = 0f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(hook.x - hookR * 0.8f, hook.y - hookR * 0.2f),
        size = Size(hookR * 1.6f, hookR * 1.6f),
        style = Stroke(width = 3.5f),
    )
    drawCircle(Color(0xFFFF7043), radius = hookR * 0.45f, center = hook)

    // Catches hanging from the hook, heads up
    val n = session.caughtFish.size
    session.caughtFish.forEachIndexed { i, f ->
        val len = f.species.bodyLengthM * scale
        val ox = (i - (n - 1) / 2f) * 0.35f * scale
        drawFish(
            species = f.species,
            center = Offset(hook.x + ox, hook.y + hookR + len / 2f),
            lengthPx = len,
            direction = 1,
            sparkling = f.sparkling,
            timeSeconds = timeSeconds,
            rotationDegrees = -90f,
        )
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
}

private fun DrawScope.drawObstacle(o: Obstacle, c: Offset, r: Float, timeSeconds: Float) {
    when (o.kind) {
        ObstacleKind.ROCK -> {
            drawCircle(Color(0xFF455A64), radius = r, center = c)
            drawCircle(Color(0xFF607D8B), radius = r * 0.55f, center = c + Offset(-r * 0.25f, -r * 0.25f))
        }
        ObstacleKind.DRIFTWOOD -> {
            rotate(degrees = (o.id * 37 % 40 - 20).toFloat(), pivot = c) {
                drawRoundRect(
                    Color(0xFF6D4C41),
                    topLeft = c - Offset(r, r * 0.3f),
                    size = Size(r * 2f, r * 0.6f),
                    cornerRadius = CornerRadius(r * 0.3f),
                )
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
                Color(0xBBF48FB1),
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
