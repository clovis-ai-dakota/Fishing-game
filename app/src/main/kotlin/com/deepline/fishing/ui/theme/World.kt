package com.deepline.fishing.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.Screen
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The menus are places along one coast, in the tabs' order: the open [SEA] where the boat floats
 * (the Fish tab and what opens from it), the water [DEEP] under it (the Dex), the [PORT] to the
 * right (the Shop), then the [ARENA] out at sea at sunset (battles) and the [LIGHTHOUSE] past it
 * (friends, leaderboards, the tournament, clubs). [x] and [y] are the camera's position in screens:
 * moving between menus moves the camera, and the scene slides by with parallax (see
 * [drawBackdrop]). Whole numbers, so a still view never draws a layer twice.
 */
enum class Place(val x: Float, val y: Float) {
    SEA(0f, 0f),
    DEEP(0f, 0.6f),
    PORT(1f, 0f),
    ARENA(2f, 0f),
    LIGHTHOUSE(3f, 0f),
    ;

    companion object {
        fun of(screen: Screen): Place = when (screen) {
            Screen.DEX, Screen.FISH_DETAIL, Screen.AQUARIUM -> DEEP
            Screen.SHOP -> PORT
            Screen.BATTLE_SETUP, Screen.BATTLE, Screen.ONLINE_LOBBY -> ARENA
            Screen.SOCIAL, Screen.FRIENDS, Screen.LEADERBOARDS, Screen.TOURNAMENT, Screen.CLUBS -> LIGHTHOUSE
            else -> SEA
        }
    }
}

/** Draws [draw] shifted by the camera, [factor] of its speed (far things move less), wrapping around. */
internal inline fun DrawScope.parallax(camX: Float, factor: Float, draw: DrawScope.() -> Unit) {
    val shift = camX * factor
    val frac = shift - floor(shift)
    if (frac < 0.002f || frac > 0.998f) {
        draw()
        return
    }
    val w = size.width
    clipRect {
        translate(left = -frac * w) { draw() }
        translate(left = (1f - frac) * w) { draw() }
    }
}

/** How much of [place] the camera at [camX] sees: 1 on it, 0 a screen away. */
private fun near(camX: Float, place: Place) = (1f - abs(camX - place.x)).coerceIn(0f, 1f)

/**
 * The light of each place over the theme's sky and sea: a warm morning on the port, a sunset
 * over the arena, the beam's night blue by the lighthouse, and the dark of deeper water as the
 * camera sinks. Tints, so every theme keeps its own colours underneath.
 */
internal fun DrawScope.drawPlaceLight(p: MenuPalette, horizon: Float, camX: Float, camY: Float) {
    val w = size.width
    val h = size.height
    val strength = if (p.night) 0.55f else 1f
    fun wash(color: Color, amount: Float, reach: Float) {
        if (amount <= 0.01f) return
        drawRect(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.62f * amount * strength), color.copy(alpha = 0f)), startY = 0f, endY = horizon + h * reach),
            size = Size(w, horizon + h * reach),
        )
    }
    wash(Color(0xFFFFC56B), near(camX, Place.PORT), 0.22f)
    wash(Color(0xFFFF6A3D), near(camX, Place.ARENA), 0.42f)
    wash(Color(0xFF5B4BC4), near(camX, Place.LIGHTHOUSE), 0.3f)
    // Under the surface: darker and bluer the deeper the camera goes.
    val deep = (camY / Place.DEEP.y).coerceIn(0f, 1f)
    if (deep > 0.01f) drawRect(lerp(p.sea.last(), Color(0xFF031B3A), 0.7f).copy(alpha = 0.5f * deep))
}

/**
 * What stands on the coast, drawn where the camera is: the port's pier and market stall, the
 * rival's boat against the setting sun, the lighthouse and its beam. Silhouettes and flat shapes
 * in the theme's colours: they sit behind the menus and never look like something to tap.
 */
internal fun DrawScope.drawPlaces(p: MenuPalette, t: Float, horizon: Float, u: Float, camX: Float) {
    val w = size.width
    for (place in listOf(Place.PORT, Place.ARENA, Place.LIGHTHOUSE)) {
        if (abs(camX - place.x) >= 1f) continue
        translate(left = (place.x - camX) * w) {
            when (place) {
                Place.PORT -> drawPort(p, t, horizon, u)
                Place.ARENA -> drawArena(p, t, horizon, u)
                else -> drawLighthouse(p, t, horizon, u)
            }
        }
    }
}

/**
 * A colour seen from far across the water: washed towards the sky, so the coast reads as distant
 * and the menus' text stays readable over it.
 */
private fun far(c: Color, p: MenuPalette, amount: Float = 0.45f) = lerp(c, p.sky.last(), amount)

private fun DrawScope.drawPort(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val wood = far(if (p.night) Color(0xFF4A3524) else Color(0xFF9A6A3C), p)
    val woodDark = far(if (p.night) Color(0xFF2E2117) else Color(0xFF6B4826), p)
    // Just under the horizon, where a tab's first title ends and its first card starts.
    val deck = horizon + 7f * u
    // The quay: a line of planks on short posts, mirrored in the water.
    var x = 6f * u
    while (x < w) {
        drawRect(woodDark, topLeft = Offset(x, deck), size = Size(4f * u, 12f * u))
        drawRect(woodDark.copy(alpha = 0.3f), topLeft = Offset(x + sin(t * 1.5f + x) * u, deck + 12f * u), size = Size(4f * u, 10f * u))
        x += 46f * u
    }
    drawRect(wood, topLeft = Offset(0f, deck - 4f * u), size = Size(w, 5f * u))
    // The market stall: a counter under a striped awning, crates beside it, a string of lights.
    val sx = w * 0.6f
    val counter = far(if (p.night) Color(0xFF6A5A46) else Color(0xFFF1DFBE), p, 0.25f)
    drawRect(counter, topLeft = Offset(sx, deck - 22f * u), size = Size(44f * u, 18f * u))
    val awning = Path().apply {
        moveTo(sx - 6f * u, deck - 22f * u); lineTo(sx + 50f * u, deck - 22f * u); lineTo(sx + 44f * u, deck - 33f * u); lineTo(sx, deck - 33f * u); close()
    }
    drawPath(awning, far(if (p.night) Color(0xFF8A3A30) else Color(0xFFE2573F), p, 0.3f))
    for (k in 0 until 4) {
        val a = sx + 3f * u + k * 12.5f * u
        val stripe = Path().apply {
            moveTo(a - 4f * u, deck - 22f * u); lineTo(a + 2f * u, deck - 22f * u); lineTo(a + 3.5f * u, deck - 33f * u); lineTo(a - 1.5f * u, deck - 33f * u); close()
        }
        drawPath(stripe, Color.White.copy(alpha = if (p.night) 0.4f else 0.8f))
    }
    for (k in 0 until 3) {
        val c = Offset(w * 0.3f + k * 15f * u, deck - 4f * u - 10f * u)
        drawRoundRect(lerp(wood, Color.White, 0.12f * k), topLeft = c, size = Size(13f * u, 10f * u), cornerRadius = CornerRadius(1.5f * u))
        drawLine(woodDark, c + Offset(0f, 5f * u), c + Offset(13f * u, 5f * u), strokeWidth = 0.8f * u)
    }
    // A moored mast or two, far along the quay.
    for (mx in listOf(0.1f, 0.17f, 0.9f)) {
        val bob = sin(t * 0.9f + mx * 20f) * u
        drawLine(woodDark, Offset(w * mx, deck - 4f * u + bob), Offset(w * mx, deck - 34f * u + bob), strokeWidth = 1.4f * u)
        drawLine(woodDark, Offset(w * mx - 7f * u, deck - 8f * u + bob), Offset(w * mx + 7f * u, deck - 8f * u + bob), strokeWidth = 3f * u, cap = StrokeCap.Round)
    }
    for (k in 0..5) {
        val lx = sx - 8f * u + k * 12f * u
        val ly = deck - 38f * u + 4f * u * (1f - abs(k - 2.5f) / 2.5f)
        val on = 0.6f + 0.4f * sin(t * 2f + k * 1.3f)
        drawCircle(p.accentA.copy(alpha = if (p.night) on else 0.8f), radius = 1.8f * u, center = Offset(lx, ly))
    }
}

private fun DrawScope.drawArena(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    // The sun going down, and its path on the water.
    val sun = Offset(w * 0.5f, horizon - 4f * u)
    val glow = if (p.night) Color(0xFFFFB199) else Color(0xFFFFE08A)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.9f), glow.copy(alpha = 0f)), center = sun, radius = 90f * u), radius = 90f * u, center = sun)
    clipRect(bottom = horizon) { drawCircle(glow, radius = 26f * u, center = sun) }
    for (k in 0 until 6) {
        val y = horizon + (10f + k * 13f) * u
        val half = (34f - k * 4f + sin(t * 1.4f + k) * 4f) * u
        drawLine(glow.copy(alpha = 0.5f - k * 0.07f), Offset(sun.x - half, y), Offset(sun.x + half, y), strokeWidth = 3f * u, cap = StrokeCap.Round)
    }
    // The rival's boat, facing yours across the water.
    val ink = far(lerp(p.silhouette, Color.Black, 0.55f), p, 0.3f)
    fun boat(cx: Float, flip: Float, flag: Color) {
        val bob = sin(t * 1.1f + cx) * 1.5f * u
        val y = horizon + bob
        val hull = Path().apply {
            moveTo(cx - 30f * u * flip, y - 8f * u); lineTo(cx + 26f * u * flip, y - 8f * u); lineTo(cx + 20f * u * flip, y + 5f * u); lineTo(cx - 22f * u * flip, y + 5f * u); close()
        }
        drawPath(hull, ink)
        drawRect(ink, topLeft = Offset(cx - 8f * u, y - 22f * u), size = Size(16f * u, 14f * u))
        drawLine(ink, Offset(cx + 12f * u * flip, y - 8f * u), Offset(cx + 12f * u * flip, y - 44f * u), strokeWidth = 2f * u)
        val wave = sin(t * 3f + cx) * 2f * u
        val pennant = Path().apply {
            moveTo(cx + 12f * u * flip, y - 44f * u); lineTo(cx + (12f + 18f) * u * flip, y - 39f * u + wave); lineTo(cx + 12f * u * flip, y - 34f * u); close()
        }
        drawPath(pennant, flag)
    }
    scale(0.7f, 0.7f, pivot = Offset(w * 0.5f, horizon)) {
        boat(w * 0.14f, 1f, Color(0xFF4FC3F7))
        boat(w * 0.86f, -1f, Color(0xFFFF5A4A))
    }
}

private fun DrawScope.drawLighthouse(p: MenuPalette, t: Float, horizon: Float, u0: Float) {
    val w = size.width
    // Far out on its rock: small, so it stays under the screens' titles.
    val u = u0 * 0.6f
    val cx = w * 0.8f
    val rock = far(lerp(p.silhouette, Color.Black, 0.4f), p, 0.3f)
    val base = Path().apply {
        moveTo(cx - 60f * u, horizon + 4f * u); quadraticTo(cx - 20f * u, horizon - 22f * u, cx + 10f * u, horizon - 14f * u); quadraticTo(cx + 46f * u, horizon - 16f * u, cx + 70f * u, horizon + 4f * u); close()
    }
    // The beam sweeps over the water.
    val angle = sin(t * 0.5f) * 0.9f
    val lamp = Offset(cx, horizon - 96f * u)
    val beam = Path().apply {
        moveTo(lamp.x, lamp.y)
        lineTo(lamp.x - cos(angle - 0.12f) * w, lamp.y + sin(angle - 0.12f) * w * 0.3f)
        lineTo(lamp.x - cos(angle + 0.12f) * w, lamp.y + sin(angle + 0.12f) * w * 0.3f)
        close()
    }
    drawPath(beam, Brush.radialGradient(listOf(Color(0xFFFFF3B0).copy(alpha = if (p.night) 0.5f else 0.32f), Color.Transparent), center = lamp, radius = w * 0.9f))
    drawPath(base, rock)
    val tower = Path().apply {
        moveTo(cx - 11f * u, horizon - 14f * u); lineTo(cx + 11f * u, horizon - 14f * u); lineTo(cx + 7f * u, horizon - 88f * u); lineTo(cx - 7f * u, horizon - 88f * u); close()
    }
    drawPath(tower, if (p.night) Color(0xFFB9C2CC) else Color(0xFFF6F1E7))
    clipRect(top = horizon - 62f * u, bottom = horizon - 44f * u) { drawPath(tower, Color(0xFFC9412F)) }
    drawRect(Color(0xFF2B3A46), topLeft = Offset(cx - 10f * u, horizon - 92f * u), size = Size(20f * u, 4f * u))
    drawRect(Color(0xFFFFE27A), topLeft = Offset(cx - 6f * u, horizon - 104f * u), size = Size(12f * u, 12f * u))
    val cap = Path().apply { moveTo(cx - 9f * u, horizon - 104f * u); lineTo(cx + 9f * u, horizon - 104f * u); lineTo(cx, horizon - 116f * u); close() }
    drawPath(cap, Color(0xFFC9412F))
}
