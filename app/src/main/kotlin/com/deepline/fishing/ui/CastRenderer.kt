package com.deepline.fishing.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.deepline.fishing.R
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Lighting
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.feel.CastFx
import com.deepline.fishing.feel.Particle
import com.deepline.fishing.feel.ParticleStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Water colour by depth, from the shared colour script (the same as the 3D renderer). */
internal fun waterColor(depth: Float, atmosphere: com.deepline.fishing.core.Atmosphere = com.deepline.fishing.core.Atmosphere.FAMILY): Color =
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
 * The words drawn on the water (depth markers, the bump-free and rope-limit lines), in the game's
 * language. A DrawScope can't read string resources, so the composable that draws a cast resolves
 * them ([rememberCastLabels]) and passes them down. [NONE] draws no words.
 */
class CastLabels(
    /** "Bump-free above 50 m", on the line where bumps start to count. */
    val bumpFree: String,
    /** "Rope limit", on the line at the end of the rope. */
    val ropeLimit: String,
    /** "150 m", a depth marker. */
    private val depthLabel: (meters: Int) -> String,
) {
    private val depths = HashMap<Int, String>()

    /** A depth marker's label (cached: the same few are drawn every frame). */
    fun depth(meters: Int): String = depths.getOrPut(meters) { depthLabel(meters) }

    companion object {
        val NONE = CastLabels(bumpFree = "", ropeLimit = "", depthLabel = { "" })
    }
}

/** The cast's labels in the language of [resources]. */
fun castLabels(resources: Resources): CastLabels = CastLabels(
    bumpFree = resources.getString(R.string.cast_canvas_bump_free_above, GameConfig.BUMP_FREE_DEPTH.toInt()),
    ropeLimit = resources.getString(R.string.cast_canvas_rope_limit),
    depthLabel = { meters -> metersText(meters.toFloat()).resolve(resources) },
)

/** The cast's labels in the language shown now. */
@Composable
fun rememberCastLabels(): CastLabels {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(configuration, resources) { castLabels(resources) }
}

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
    /** Species a rare fish detector marks (not caught yet), or null when it's off. */
    detect: Set<String>? = null,
    /** Game feel: screen shake, a flash and particles (render only, see `CastFx`). */
    fx: CastFx = CastFx.NONE,
    /** The words drawn on the water ([rememberCastLabels]); none by default (the skins preview). */
    labels: CastLabels = CastLabels.NONE,
) {
    // The shake moves the whole picture; the background and the darkness bleed past the edges
    // so no unlit strip ever shows.
    val dx = fx.shakeX * size.width
    val dy = fx.shakeY * size.width
    if (dx == 0f && dy == 0f) {
        drawCastSceneContent(session, timeSeconds, textMeasurer, labels, aimX, skins, cameraTop, detect, fx, bleed = 0f)
    } else {
        translate(dx, dy) {
            drawCastSceneContent(session, timeSeconds, textMeasurer, labels, aimX, skins, cameraTop, detect, fx, bleed = ceil(max(abs(dx), abs(dy))) + 2f)
        }
    }
}

private fun DrawScope.drawCastSceneContent(
    session: CastSession,
    timeSeconds: Float,
    textMeasurer: TextMeasurer,
    labels: CastLabels,
    aimX: Float?,
    skins: Skins,
    cameraTop: Float?,
    detect: Set<String>?,
    fx: CastFx,
    bleed: Float,
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
    if (surfaceY > -bleed) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFD7F1FF)), startY = 0f, endY = surfaceY),
            topLeft = Offset(-bleed, -bleed),
            size = Size(size.width + 2f * bleed, surfaceY + bleed),
        )
    }

    // Water
    val waterTop = surfaceY.coerceAtLeast(-bleed)
    drawRect(
        Brush.verticalGradient(
            listOf(waterColor(max(top, 0f), skins.atmosphere), waterColor(bottom, skins.atmosphere)),
            startY = waterTop,
            endY = size.height,
        ),
        topLeft = Offset(-bleed, waterTop),
        size = Size(size.width + 2f * bleed, size.height + bleed - waterTop),
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
        drawLabel(textMeasurer, labels.depth(marker.toInt()), labelStyle, x = 8f, y = y + 4f)
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
            labels.bumpFree,
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
            labels.ropeLimit,
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

    // Game feel: splashes and bubbles, and a rare catch's flash, under the darkness.
    if (fx.particles.isNotEmpty()) drawParticles(fx.particles, top, scale, skins.atmosphere)
    if (fx.flash > 0f) {
        drawRect(Color(fx.flashColor).copy(alpha = fx.flash.coerceIn(0f, 1f)), topLeft = Offset(-bleed, -bleed), size = Size(size.width + 2f * bleed, size.height + 2f * bleed))
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
            topLeft = Offset(-bleed, -bleed),
            size = Size(size.width + 2f * bleed, size.height + 2f * bleed),
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
    if (detect != null) drawDetectorMarks(session, detect, top, scale, timeSeconds)
}

/**
 * The rare fish detector: a pulsing ring and a marker over every species not caught yet within
 * [GameConfig.DETECTOR_RADIUS_M] of the hook, drawn over the darkness (that's what it's for).
 * It only marks: the fish themselves stay as dark as ever.
 */
internal fun DrawScope.drawDetectorMarks(session: CastSession, detect: Set<String>, top: Float, scale: Float, timeSeconds: Float) {
    val r2 = GameConfig.DETECTOR_RADIUS_M * GameConfig.DETECTOR_RADIUS_M
    val hook = Offset(session.hookX * scale, (session.hookDepth - top) * scale)
    // The detector's range, faint around the hook.
    drawCircle(DetectorCyan.copy(alpha = 0.12f), radius = GameConfig.DETECTOR_RADIUS_M * scale, center = hook, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
    val pulse = 0.5f + 0.5f * sin(timeSeconds * 5f)
    for (f in session.world.fish) {
        if (f.caught || f.species.id !in detect) continue
        val dx = f.x - session.hookX
        val dy = f.depth - session.hookDepth
        if (dx * dx + dy * dy > r2) continue
        val c = Offset(f.x * scale, (f.depth - top) * scale)
        val r = (f.halfLength + 0.4f) * scale
        drawCircle(DetectorCyan.copy(alpha = 0.35f + 0.4f * pulse), radius = r * (1f + 0.12f * pulse), center = c, style = Stroke(width = 3f))
        // A small diamond above the fish.
        val m = c + Offset(0f, -r - 10f)
        val d = 7f
        val diamond = Path().apply {
            moveTo(m.x, m.y - d); lineTo(m.x + d, m.y); lineTo(m.x, m.y + d); lineTo(m.x - d, m.y); close()
        }
        drawPath(diamond, DetectorCyan)
    }
}

private val DetectorCyan = Color(0xFF4DD0E1)

/**
 * Game feel particles (splashes, foam, bubbles, glints) at their world positions, in the colour
 * script's splash colour for their depth. Drawn before the darkness, like the 3D view's.
 */
internal fun DrawScope.drawParticles(particles: List<Particle>, top: Float, scale: Float, atmosphere: com.deepline.fishing.core.Atmosphere) {
    for (p in particles) {
        val c = Offset(p.x * scale, (p.depth - top) * scale)
        val r = max(p.radius * scale, 1f)
        if (c.y < -r * 4f || c.y > size.height + r * 4f) continue
        val a = p.alpha.coerceIn(0f, 1f)
        when (p.style) {
            ParticleStyle.DROPLET -> drawCircle(Color(ColorScript.splash(p.depth, atmosphere)).copy(alpha = 0.9f * a), radius = r, center = c)
            ParticleStyle.FOAM -> drawCircle(Color(ColorScript.splash(p.depth, atmosphere)).copy(alpha = 0.5f * a), radius = r, center = c)
            ParticleStyle.BUBBLE -> {
                val col = Color(ColorScript.splash(p.depth, atmosphere))
                drawCircle(col.copy(alpha = 0.7f * a), radius = r, center = c, style = Stroke(width = max(1f, r * 0.3f)))
                drawCircle(col.copy(alpha = 0.6f * a), radius = r * 0.25f, center = c + Offset(-r * 0.35f, -r * 0.35f))
            }
            ParticleStyle.GLINT -> {
                val col = Color(ColorScript.GLINT).copy(alpha = a)
                val w = max(1.5f, r * 0.25f)
                drawLine(col, c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(col, c - Offset(0f, r), c + Offset(0f, r), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
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
    // Rod (as in 3D): a navy graphite blank tapering to the tip, a cork grip, a reel hanging
    // below with a turning crank, and guides the line runs through.
    val control = Offset(rodBase.x + (rodTip.x - rodBase.x) * 0.3f, rodBase.y + (rodTip.y - rodBase.y) * 0.8f)
    fun at(u: Float) = quadPoint(rodBase, control, rodTip, u)
    fun below(u: Float): Offset {
        val d = at(min(1f, u + 0.01f)) - at(max(0f, u - 0.01f))
        val len = max(1e-3f, d.getDistance())
        val n = Offset(d.y / len, -d.x / len)
        return if (n.y < 0f) -n else n // screen y grows downwards
    }
    val ink = Color(0xFF14202E)
    val steps = 12
    fun width(u: Float) = max(2f, (0.11f - 0.075f * u) * scale)
    // Outline first, then the blank, then its highlight, so the segments join seamlessly.
    for (pass in 0 until 3) for (i in 0 until steps) {
        val u0 = i / steps.toFloat()
        val u1 = (i + 1) / steps.toFloat()
        val w = width(u0)
        when (pass) {
            0 -> drawLine(ink, at(u0), at(u1), strokeWidth = w + 2f, cap = StrokeCap.Round)
            1 -> drawLine(Color(0xFF243B5A), at(u0), at(u1), strokeWidth = w, cap = StrokeCap.Round)
            else -> drawLine(Color(0xFF7FA7D1).copy(alpha = 0.7f), at(u0) - below(u0) * (w * 0.22f), at(u1) - below(u1) * (w * 0.22f), strokeWidth = max(1f, w * 0.25f), cap = StrokeCap.Round)
        }
    }
    // Cork grip and butt cap.
    for (i in 0 until 4) {
        val u0 = i / 4f * 0.17f
        val u1 = (i + 1) / 4f * 0.17f
        drawLine(ink, at(u0), at(u1), strokeWidth = max(3f, 0.17f * scale) + 2f, cap = StrokeCap.Butt)
        drawLine(if (i % 2 == 0) Color(0xFFC9A26B) else Color(0xFFB88D55), at(u0), at(u1), strokeWidth = max(3f, 0.17f * scale), cap = StrokeCap.Butt)
    }
    drawCircle(Color(0xFF90A4AE), radius = max(2f, 0.085f * scale), center = rodBase)
    // Guides, smaller towards the tip.
    for (k in 0 until 5) {
        val u = floatArrayOf(0.36f, 0.52f, 0.67f, 0.8f, 0.92f)[k]
        val c = at(u) + below(u) * ((0.11f - 0.012f * k) * scale)
        drawLine(Color(0xFFB0BEC5), at(u), c, strokeWidth = max(1f, 0.025f * scale))
        drawCircle(Color(0xFFCFD8DC), radius = max(1.5f, (0.055f - 0.007f * k) * scale), center = c, style = Stroke(width = max(1f, 0.022f * scale)))
    }
    // The reel below the blank, its spool showing line, and a crank that turns.
    val seat = at(0.2f)
    val reel = seat + below(0.2f) * (0.22f * scale)
    drawLine(Color(0xFF90A4AE), seat, reel, strokeWidth = max(1.5f, 0.056f * scale))
    drawCircle(ink, radius = max(3f, 0.14f * scale), center = reel)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE3EBF0), Color(0xFF78909C)), center = reel - Offset(0.04f * scale, 0.04f * scale), radius = 0.16f * scale), radius = max(2.5f, 0.13f * scale), center = reel)
    drawCircle(Color(BoatLook.LINE_R, BoatLook.LINE_G, BoatLook.LINE_B), radius = max(1.5f, 0.08f * scale), center = reel)
    drawCircle(Color(0xFF546E7A), radius = max(1f, 0.035f * scale), center = reel)
    val a = timeSeconds * 2.2f
    val knob = reel + Offset(cos(a), sin(a)) * (0.15f * scale)
    drawLine(Color(0xFF546E7A), reel, knob, strokeWidth = max(1.2f, 0.04f * scale), cap = StrokeCap.Round)
    drawCircle(Color(0xFFC9A26B), radius = max(1.5f, 0.045f * scale), center = knob)
    drawCircle(Color(0xFFCFD8DC), radius = max(1.5f, 0.03f * scale), center = rodTip)
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
        // Worm threaded on the shank, with its tail dangling below the bend: a darker back,
        // segment rings, the saddle band and a wet highlight (as in 3D).
        val pink = Color(0xFFE57373)
        val dark = Color(0xFFB4485A)
        val tail = Path()
        for (i in 0..8) {
            val u = i / 8f
            val x = bend.x + 0.30f * s + sin(timeSeconds * 6f + u * 6f) * 0.16f * s * u
            val y = bend.y + (0.55f + u * 1.0f) * s
            if (i == 0) tail.moveTo(bend.x + 0.05f * s, bend.y + 0.45f * s)
            tail.lineTo(x, y)
        }
        drawPath(tail, dark, style = Stroke(width = s * 0.3f, cap = StrokeCap.Round))
        drawPath(tail, pink, style = Stroke(width = s * 0.2f, cap = StrokeCap.Round))
        drawLine(dark, Offset(bend.x, bend.y - 0.65f * s), Offset(bend.x, bend.y + 0.35f * s), strokeWidth = s * 0.36f, cap = StrokeCap.Round)
        drawLine(pink, Offset(bend.x - s * 0.03f, bend.y - 0.65f * s), Offset(bend.x - s * 0.03f, bend.y + 0.35f * s), strokeWidth = s * 0.26f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFFD9707E), topLeft = Offset(bend.x - s * 0.19f, bend.y - 0.42f * s), size = Size(s * 0.38f, s * 0.2f), cornerRadius = CornerRadius(s * 0.08f))
        for (k in 0 until 6) {
            val y = bend.y + (-0.55f + k * 0.17f) * s
            drawLine(Color(0xFF8E2A3C).copy(alpha = 0.45f), Offset(bend.x - s * 0.15f, y), Offset(bend.x + s * 0.15f, y), strokeWidth = max(1f, s * 0.035f))
        }
        drawLine(Color.White.copy(alpha = 0.55f), Offset(bend.x - s * 0.09f, bend.y - 0.58f * s), Offset(bend.x - s * 0.09f, bend.y + 0.2f * s), strokeWidth = max(1f, s * 0.05f), cap = StrokeCap.Round)
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
    if (text.isEmpty() || y > size.height || y < -200f) return
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
    /** Species a rare fish detector marks, or null when it's off (see [drawDetectorMarks]). */
    detect: Set<String>? = null,
    /** The shake moves the overlay with the 3D view (its flash and particles are drawn in 3D). */
    fx: CastFx = CastFx.NONE,
    /** The words drawn on the water ([rememberCastLabels]). */
    labels: CastLabels = CastLabels.NONE,
) {
    val dx = fx.shakeX * size.width
    val dy = fx.shakeY * size.width
    if (dx == 0f && dy == 0f) drawCastOverlayContent(session, timeSeconds, textMeasurer, labels, aimX, cameraTop, detect)
    else translate(dx, dy) { drawCastOverlayContent(session, timeSeconds, textMeasurer, labels, aimX, cameraTop, detect) }
}

private fun DrawScope.drawCastOverlayContent(
    session: CastSession,
    timeSeconds: Float,
    textMeasurer: TextMeasurer,
    labels: CastLabels,
    aimX: Float?,
    cameraTop: Float?,
    detect: Set<String>?,
) {
    val scale = size.width / GameConfig.WORLD_WIDTH
    val viewHeight = size.height / scale
    val top = cameraTop ?: max(session.hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f)
    val bottom = top + viewHeight
    fun sx(x: Float) = x * scale
    fun sy(depth: Float) = (depth - top) * scale
    val elapsed = session.elapsedSeconds
    if (detect != null) drawDetectorMarks(session, detect, top, scale, timeSeconds)

    val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    var marker = ceil(max(top, 1f) / 50f) * 50f
    while (marker < bottom) {
        val y = sy(marker)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        drawLabel(textMeasurer, labels.depth(marker.toInt()), labelStyle, x = 8f, y = y + 4f)
        marker += 50f
    }
    val bumpFreeEnd = GameConfig.BUMP_FREE_DEPTH
    if (bumpFreeEnd in top..bottom) {
        val y = sy(bumpFreeEnd)
        drawLine(BumpFreeGreen.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        drawLabel(textMeasurer, labels.bumpFree, TextStyle(color = BumpFreeGreen.copy(alpha = 0.8f), fontSize = 11.sp), x = null, y = y - 20f)
    }
    val ropeEnd = session.specs.lengthM
    if (ropeEnd in top..bottom) {
        val y = sy(ropeEnd + GameConfig.HOOK_RADIUS)
        drawLine(Color(0xFFFFB74D).copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
        drawLabel(textMeasurer, labels.ropeLimit, TextStyle(color = Color(0xFFFFB74D), fontSize = 11.sp), x = null, y = y + 4f)
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
