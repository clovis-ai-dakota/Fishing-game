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
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.FishCatalog
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

/** Water colour by depth, from the shared colour script (the same as the 3D renderer). */
private fun waterColor(depth: Float, atmosphere: com.deepline.fishing.core.Atmosphere = com.deepline.fishing.core.Atmosphere.FAMILY): Color =
    Color(ColorScript.water(depth, atmosphere))

/** The darkness colour: black, or blood-black in the Nightmare atmosphere (same visibility). */
private fun darkTint(skins: Skins) =
    if (skins.atmosphere == com.deepline.fishing.core.Atmosphere.NIGHTMARE) Color(0xFF0F0003) else Color.Black

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

/**
 * Draws the whole underwater scene for [session], with the camera following the hook.
 * [aimX] is where the player's finger is (world meters), shown as a marker the hook glides to.
 */
fun DrawScope.drawCastScene(
    session: CastSession,
    timeSeconds: Float,
    textMeasurer: TextMeasurer,
    aimX: Float? = null,
    skins: Skins = Skins.CLASSIC,
    /** Overrides the camera: the depth (m) at the top of the screen. Used by the skins preview. */
    cameraTop: Float? = null,
) {
    val scale = size.width / GameConfig.WORLD_WIDTH
    val viewHeight = size.height / scale
    val top = cameraTop ?: max(session.hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f)
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
            listOf(waterColor(max(top, 0f), skins.atmosphere), waterColor(bottom, skins.atmosphere)),
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
    if (surfaceY > -6f * scale) drawBoatAndRod(cx, surfaceY, scale, rodBase, rodTip, skins, timeSeconds)

    // Obstacles
    for (o in session.world.obstacles) {
        if (o.snagged) continue // it rides on the hook, drawn below
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
    // The line runs from where it enters the water to the hook, arriving from above the hook and
    // trailing slightly behind its sideways movement (no idle sway: it only moves with the hook).
    val entry = Offset(rodTip.x + (hookEye.x - rodTip.x) * 0.1f, surfaceY)
    val trail = (-session.hookVelocityX * 0.12f).coerceIn(-1.5f, 1.5f) * scale
    val control = Offset(hookEye.x + trail, entry.y + (hookEye.y - entry.y) * 0.6f)
    val linePoints = buildList {
        add(rodTip)
        add(entry)
        for (i in 1..24) add(quadPoint(entry, control, hookEye, i / 24f))
    }
    drawSkinnedLine(linePoints, timeSeconds, bumpFlash, skins.line)
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

    // Bubbles (or the skin's trail) rising from the hook.
    drawHookTrail(hook.x, hookEye.y, scale, timeSeconds, skins.trail)

    // Bump-free shield around the hook near the surface.
    if (session.isBumpFree) {
        val pulse = 0.5f + 0.5f * sin(timeSeconds * 4f)
        drawCircle(BumpFreeGreen.copy(alpha = 0.10f + 0.06f * pulse), radius = 2.0f * s, center = hook)
        drawCircle(BumpFreeGreen.copy(alpha = 0.55f), radius = 2.0f * s, center = hook, style = Stroke(width = 2f))
    }

    // Where the finger is aiming: the hook glides there (faster with more Rope Agility).
    if (aimX != null) {
        val aim = Offset(sx(aimX.coerceIn(0f, GameConfig.WORLD_WIDTH)), hook.y)
        if (kotlin.math.abs(aim.x - hook.x) > s * 0.5f) {
            drawLine(Color.White.copy(alpha = 0.25f), hook, aim, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        drawCircle(Color.White.copy(alpha = 0.55f), radius = s * 0.55f, center = aim, style = Stroke(width = 2.5f))
        drawCircle(Color.White.copy(alpha = 0.35f), radius = s * 0.15f, center = aim)
    }

    drawHookAura(hook, s, timeSeconds, skins.hook)
    drawHook(hook, s, timeSeconds, skin = skins.hook)

    // An object snagged by the last bump rides up under the hook.
    session.snaggedObstacle?.let { o ->
        val r = o.radius * scale
        drawObstacleIcon(o.kind, Offset(hook.x, hook.y + s * 0.8f + r), r, timeSeconds, o.id)
    }

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
                0.6f to darkTint(skins).copy(alpha = 0.45f * darkness),
                1f to darkTint(skins).copy(alpha = 0.97f * darkness),
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

private fun DrawScope.drawBoatAndRod(
    cx: Float,
    surfaceY: Float,
    scale: Float,
    rodBase: Offset,
    rodTip: Offset,
    skins: Skins,
    timeSeconds: Float,
) {
    // The boat itself: classic, a boat skin, or the player's own design (BoatArt.kt).
    drawBoat(cx, surfaceY, scale, timeSeconds, skins.boat, skins.customBoat)
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
fun DrawScope.drawHook(bend: Offset, s: Float, timeSeconds: Float, withBait: Boolean = true, skin: Cosmetic? = null) {
    val w = max(2.2f, s * 0.16f)
    val metal = hookMetal(skin, bend, s, timeSeconds)
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
    // Same shape and size as the classic hook whatever the skin: ornaments are purely visual.
    drawHookOrnament(bend, s, w, timeSeconds, skin)
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
    drawObstacleIcon(o.kind, c, r, timeSeconds, o.id)
    if (o.touched) {
        drawCircle(Color(0xFFFF5252).copy(alpha = 0.5f), radius = r * 1.1f, center = c, style = Stroke(width = 2f))
    }
}

/** Draws a rock, driftwood or jelly of radius [r] at [c] (also used for icons). */
fun DrawScope.drawObstacleIcon(kind: ObstacleKind, c: Offset, r: Float, timeSeconds: Float = 0f, id: Int = 0) {
    when (kind) {
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
            rotate(degrees = (id * 37 % 40 - 20).toFloat(), pivot = c) {
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
            val bob = sin(timeSeconds * 2f + id) * r * 0.08f
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
}

/**
 * What the 3D renderer leaves to 2D, drawn over it: depth markers and labels, the bump-free and
 * rope-limit lines, the aim marker, the bump-free shield and the bump and catch flashes. The 3D
 * camera maps the play plane exactly like [drawCastScene], so these line up with the scene.
 */
fun DrawScope.drawCastOverlay(
    session: CastSession,
    timeSeconds: Float,
    textMeasurer: TextMeasurer,
    aimX: Float? = null,
    cameraTop: Float? = null,
) {
    val scale = size.width / GameConfig.WORLD_WIDTH
    val viewHeight = size.height / scale
    val top = cameraTop ?: max(session.hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f)
    val bottom = top + viewHeight
    fun sx(x: Float) = x * scale
    fun sy(depth: Float) = (depth - top) * scale
    val elapsed = session.elapsedSeconds

    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    var marker = ceil(max(top, 1f) / 50f) * 50f
    while (marker < bottom) {
        val y = sy(marker)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        drawLabel(textMeasurer, "${marker.toInt()} m", labelStyle, x = 8f, y = y + 4f)
        marker += 50f
    }
    val bumpFreeEnd = GameConfig.BUMP_FREE_DEPTH
    if (bumpFreeEnd in top..bottom) {
        val y = sy(bumpFreeEnd)
        drawLine(BumpFreeGreen.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        drawLabel(textMeasurer, "Bump-free above ${bumpFreeEnd.toInt()} m", TextStyle(color = BumpFreeGreen.copy(alpha = 0.8f), fontSize = 11.sp), x = null, y = y - 20f)
    }
    val ropeEnd = session.specs.lengthM
    if (ropeEnd in top..bottom) {
        val y = sy(ropeEnd + GameConfig.HOOK_RADIUS)
        drawLine(Color(0xFFFFB74D).copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
        drawLabel(textMeasurer, "Rope limit", TextStyle(color = Color(0xFFFFB74D), fontSize = 11.sp), x = null, y = y + 4f)
    }

    val hook = Offset(sx(session.hookX), sy(session.hookDepth))
    val s = GameConfig.HOOK_RADIUS * scale
    if (session.isFastReeling) {
        // Speed streaks rushing past while the full line is hauled up.
        for (k in 0 until 14) {
            val x = size.width * hash01(k, 3, 9)
            val len = size.height * (0.08f + 0.12f * hash01(k, 4, 9))
            val y = ((timeSeconds * 2.2f + hash01(k, 5, 9)) % 1f) * (size.height + len) - len
            drawLine(Color.White.copy(alpha = 0.25f + 0.25f * hash01(k, 6, 9)), Offset(x, y), Offset(x, y + len), strokeWidth = 2f + 2f * hash01(k, 7, 9), cap = StrokeCap.Round)
        }
    }
    if (session.isBumpFree) {
        val pulse = 0.5f + 0.5f * sin(timeSeconds * 4f)
        drawCircle(BumpFreeGreen.copy(alpha = 0.10f + 0.06f * pulse), radius = 2.0f * s, center = hook)
        drawCircle(BumpFreeGreen.copy(alpha = 0.55f), radius = 2.0f * s, center = hook, style = Stroke(width = 2f))
    }
    if (aimX != null) {
        val aim = Offset(sx(aimX.coerceIn(0f, GameConfig.WORLD_WIDTH)), hook.y)
        if (kotlin.math.abs(aim.x - hook.x) > s * 0.5f) {
            drawLine(Color.White.copy(alpha = 0.25f), hook, aim, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        drawCircle(Color.White.copy(alpha = 0.55f), radius = s * 0.55f, center = aim, style = Stroke(width = 2.5f))
        drawCircle(Color.White.copy(alpha = 0.35f), radius = s * 0.15f, center = aim)
    }
    val bumpFlash = (1f - (elapsed - session.lastTouchAtSeconds) / 0.5f).coerceIn(0f, 1f)
    if (bumpFlash > 0f) {
        drawCircle(BumpRed.copy(alpha = 0.8f * bumpFlash), radius = s * (1.4f + 2.6f * (1f - bumpFlash)), center = hook, style = Stroke(width = 3f))
    }
    val catchFlash = (1f - (elapsed - session.lastCatchAtSeconds) / 0.6f).coerceIn(0f, 1f)
    if (catchFlash > 0f) {
        val inner = s * (1.2f + 2f * (1f - catchFlash))
        for (k in 0 until 8) {
            val a = (k / 8f) * 2f * PI.toFloat()
            val dir = Offset(cos(a), sin(a))
            drawLine(SparkleGold.copy(alpha = catchFlash), hook + dir * inner, hook + dir * (inner + s * 0.9f), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}
