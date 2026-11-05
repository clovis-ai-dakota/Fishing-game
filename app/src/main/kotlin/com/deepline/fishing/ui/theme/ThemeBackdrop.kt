package com.deepline.fishing.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The sea scene behind the menus, in the theme's colours: a sky band with the sun or the moon, a
 * moving waterline, light (rays by day, moonlight by night), bubbles or plankton, a shoal passing
 * far away, and the theme's own decor. Only the draw phase reads the clock, so the menus above it
 * never recompose for the animation. Looks only: nothing here is part of the game.
 */
@Composable
fun ThemeBackdrop(palette: MenuPalette, modifier: Modifier = Modifier, animate: Boolean = true) {
    val clock = rememberInfiniteTransition(label = "backdrop")
    val t = clock.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "backdrop time",
    )
    Canvas(modifier) { drawBackdrop(palette, if (animate) t.value else 12f) }
}

/** Deterministic noise in 0..1 for the scene's placements. */
private fun h(k: Int, salt: Int): Float {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777215f
}

fun DrawScope.drawBackdrop(p: MenuPalette, t: Float) {
    val w = size.width
    val hgt = size.height
    if (w <= 1f || hgt <= 1f) return
    val horizon = hgt * 0.15f
    val u = min(w, hgt) / 400f // scene unit, about a dp on a phone

    // Sky.
    drawRect(Brush.verticalGradient(p.sky, startY = 0f, endY = horizon), size = Size(w, horizon + 2f))
    drawSkyDetails(p, t, horizon, u)

    // Sea, below a moving waterline.
    val line = Path().apply {
        moveTo(0f, horizon)
        var x = 0f
        while (x <= w) {
            lineTo(x, horizon + sin(x / (60f * u) + t * 0.8f) * 3f * u + sin(x / (23f * u) - t * 1.3f) * 1.5f * u)
            x += 6f * u
        }
        lineTo(w, hgt)
        lineTo(0f, hgt)
        close()
    }
    drawPath(line, Brush.verticalGradient(p.sea, startY = horizon, endY = hgt))
    // The foam along the waterline.
    val foam = Path().apply {
        var x = 0f
        moveTo(0f, horizon)
        while (x <= w) {
            lineTo(x, horizon + sin(x / (60f * u) + t * 0.8f) * 3f * u + sin(x / (23f * u) - t * 1.3f) * 1.5f * u)
            x += 6f * u
        }
    }
    drawPath(foam, Color.White.copy(alpha = if (p.night) 0.18f else 0.55f), style = Stroke(width = 2f * u, cap = StrokeCap.Round))

    drawLight(p, t, horizon, u)
    drawShoal(p, t, horizon, u)
    when (p.decor) {
        SceneDecor.LAGOON -> drawSeabed(p, t, u, grass = true)
        SceneDecor.KELP -> drawKelp(p, t, horizon, u)
        SceneDecor.SAND -> drawSandbar(p, t, horizon, u)
        SceneDecor.CORAL -> drawCoral(p, t, u)
        SceneDecor.ICE -> drawIce(p, t, horizon, u)
        SceneDecor.HARBOR -> drawSeabed(p, t, u, grass = false)
        SceneDecor.KOI -> drawKoi(p, t, horizon, u)
        SceneDecor.TREASURE -> drawTreasure(p, t, u)
        SceneDecor.GLOW -> drawGlow(p, t, horizon, u)
        SceneDecor.AURORA -> drawSeabed(p, t, u, grass = false)
    }
    drawParticles(p, t, horizon, u)
}

// ---- Sky -------------------------------------------------------------------------------------------

private fun DrawScope.drawSkyDetails(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    if (p.night || p.decor == SceneDecor.GLOW) {
        // Stars, twinkling.
        for (k in 0 until 40) {
            val x = h(k, 1) * w
            val y = h(k, 2) * horizon * 0.92f
            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + h(k, 3) * 2f) + k))
            drawCircle(Color.White.copy(alpha = 0.75f * tw), radius = (0.6f + h(k, 4) * 1.2f) * u, center = Offset(x, y))
        }
    }
    when {
        p.decor == SceneDecor.AURORA -> drawAuroraCurtains(p, t, horizon, u)
        p.decor == SceneDecor.HARBOR -> drawHarborSky(p, t, horizon, u)
        // The sun and the moon sit low in the sky band, under the header's buttons.
        p.night -> {
            // The moon, with a soft halo.
            val c = Offset(w * 0.6f, horizon * 0.74f)
            drawCircle(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.3f), Color.Transparent), center = c, radius = 30f * u), radius = 30f * u, center = c)
            drawCircle(p.glow, radius = 9f * u, center = c)
            drawCircle(p.sky.last(), radius = 7.5f * u, center = c + Offset(4f * u, -2.5f * u))
        }
        p.decor != SceneDecor.GLOW -> {
            // A sun low on the right, and a couple of clouds drifting under the header.
            val c = Offset(w * 0.6f, horizon * 0.78f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), p.accentA.copy(alpha = 0.22f), Color.Transparent), center = c, radius = 38f * u), radius = 38f * u, center = c)
            for (k in 0 until 2) {
                val cx = ((h(k, 7) * w + t * (6f + 4f * k) * u) % (w + 120f * u)) - 60f * u
                val cy = horizon * (0.7f + 0.15f * k)
                val cloud = Color.White.copy(alpha = 0.55f)
                drawOval(cloud, topLeft = Offset(cx - 34f * u, cy - 8f * u), size = Size(68f * u, 16f * u))
                drawCircle(cloud, radius = 11f * u, center = Offset(cx - 8f * u, cy - 6f * u))
                drawCircle(cloud, radius = 8f * u, center = Offset(cx + 12f * u, cy - 4f * u))
            }
        }
    }
}

private fun DrawScope.drawAuroraCurtains(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    for (k in 0 until 3) {
        val col = if (k % 2 == 0) p.accentA else p.accentB
        val base = horizon * (0.12f + 0.12f * k)
        val path = Path()
        var x = -10f
        path.moveTo(x, base)
        while (x <= w + 10f) {
            path.lineTo(x, base + sin(x / (70f * u) + t * 0.35f + k * 1.7f) * 10f * u)
            x += 8f * u
        }
        drawPath(
            path,
            Brush.verticalGradient(listOf(col.copy(alpha = if (p.night) 0.4f else 0.22f), Color.Transparent), startY = base - 10f * u, endY = base + 18f * u),
            style = Stroke(width = 12f * u, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawHarborSky(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    if (!p.night) {
        // A low sun on the horizon, with its glow.
        val c = Offset(w * 0.5f, horizon - 2f * u)
        drawCircle(Brush.radialGradient(listOf(p.accentA.copy(alpha = 0.7f), Color.Transparent), center = c, radius = 90f * u), radius = 90f * u, center = c)
        drawCircle(p.accentA, radius = 22f * u, center = c)
        // Gulls.
        for (k in 0 until 3) {
            val gx = ((h(k, 11) * w + t * 10f * u * (1f + k * 0.3f)) % (w + 40f * u)) - 20f * u
            val gy = horizon * (0.66f + 0.1f * k) + sin(t * 2f + k) * 3f * u
            val flap = 3f * u * (0.6f + 0.4f * sin(t * 6f + k))
            drawLine(p.silhouette, Offset(gx - 6f * u, gy - flap), Offset(gx, gy), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            drawLine(p.silhouette, Offset(gx, gy), Offset(gx + 6f * u, gy - flap), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
        }
    } else {
        val c = Offset(w * 0.78f, horizon * 0.35f)
        drawCircle(p.glow.copy(alpha = 0.9f), radius = 10f * u, center = c)
    }
    // Boats and the pier on the horizon (lights at night).
    for (k in 0 until 3) {
        val bx = w * (0.15f + 0.3f * k) + sin(t * 0.2f + k) * 6f * u
        val by = horizon
        val hull = Path().apply {
            moveTo(bx - 14f * u, by - 4f * u); lineTo(bx + 14f * u, by - 4f * u); lineTo(bx + 10f * u, by + 2f * u); lineTo(bx - 10f * u, by + 2f * u); close()
        }
        drawPath(hull, p.silhouette)
        drawLine(p.silhouette, Offset(bx, by - 4f * u), Offset(bx, by - 26f * u), strokeWidth = 1.4f * u)
        val sail = Path().apply { moveTo(bx + 1f * u, by - 25f * u); lineTo(bx + 12f * u, by - 6f * u); lineTo(bx + 1f * u, by - 6f * u); close() }
        drawPath(sail, p.silhouette.copy(alpha = 0.85f))
        if (p.night) {
            drawCircle(p.accentA, radius = 1.8f * u, center = Offset(bx, by - 27f * u))
            // The light's reflection, shimmering.
            for (r in 0 until 4) drawLine(p.accentA.copy(alpha = 0.4f - r * 0.08f), Offset(bx - 4f * u, by + (6f + r * 6f) * u), Offset(bx + 4f * u + sin(t * 3f + r) * 2f * u, by + (6f + r * 6f) * u), strokeWidth = 1.4f * u)
        }
    }
}

// ---- Water -----------------------------------------------------------------------------------------

private fun DrawScope.drawLight(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    if (!p.night && p.decor != SceneDecor.GLOW) {
        // Sun rays slanting down into the water.
        for (k in 0 until 5) {
            val x0 = w * (0.08f + 0.22f * k) + sin(t * 0.25f + k * 1.9f) * 12f * u
            val spread = (24f + 18f * h(k, 21)) * u
            val ray = Path().apply {
                moveTo(x0, horizon)
                lineTo(x0 + spread, horizon)
                lineTo(x0 + spread * 2.6f + 40f * u, hgt * 0.75f)
                lineTo(x0 + 40f * u, hgt * 0.75f)
                close()
            }
            drawPath(ray, Brush.verticalGradient(listOf(p.glow.copy(alpha = 0.22f), Color.Transparent), startY = horizon, endY = hgt * 0.75f))
        }
        // Caustics just under the surface.
        for (k in 0 until 6) {
            val y = horizon + (12f + 14f * k) * u
            val path = Path()
            var x = 0f
            path.moveTo(x, y)
            while (x <= w) {
                path.lineTo(x, y + sin(x / (17f * u) + t * (1.1f + k * 0.2f) + k) * 2.2f * u)
                x += 5f * u
            }
            drawPath(path, Color.White.copy(alpha = 0.16f - k * 0.02f), style = Stroke(width = 1.2f * u))
        }
    } else {
        // Moonlight (or the glow of the deep) fading down.
        val c = Offset(w * 0.8f, horizon)
        drawRect(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.16f), Color.Transparent), center = c, radius = hgt * 0.6f))
        for (k in 0 until 8) {
            val y = horizon + (6f + k * 7f) * u
            val half = (30f - k * 3f) * u * (0.8f + 0.2f * sin(t * 2f + k))
            drawLine(p.glow.copy(alpha = 0.35f - k * 0.04f), Offset(c.x - half, y), Offset(c.x + half, y), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }
    }
}

/** A far shoal crossing slowly, faint enough never to compete with the text. */
private fun DrawScope.drawShoal(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    val col = p.silhouette.copy(alpha = if (p.night) 0.45f else 0.18f)
    for (group in 0 until 2) {
        val dir = if (group == 0) 1f else -1f
        val speed = (9f + 5f * group) * u
        val span = w + 160f * u
        val cx = ((t * speed + h(group, 31) * span) % span) - 80f * u
        val x0 = if (dir > 0) cx else w - cx
        val y0 = horizon + (hgt - horizon) * (0.35f + 0.25f * group)
        for (k in 0 until 6) {
            val fx = x0 + (h(k, 32 + group) - 0.5f) * 70f * u
            val fy = y0 + (h(k, 34 + group) - 0.5f) * 40f * u + sin(t * 1.5f + k) * 3f * u
            val len = (9f + 4f * h(k, 36)) * u
            drawFishShape(Offset(fx, fy), len, dir, col)
        }
    }
}

/** A simple fish: an oval body and a tail. */
private fun DrawScope.drawFishShape(c: Offset, len: Float, dir: Float, col: Color) {
    drawOval(col, topLeft = Offset(c.x - len / 2f, c.y - len * 0.2f), size = Size(len, len * 0.4f))
    val tail = Path().apply {
        val tx = c.x - dir * len * 0.45f
        moveTo(tx, c.y)
        lineTo(tx - dir * len * 0.3f, c.y - len * 0.22f)
        lineTo(tx - dir * len * 0.3f, c.y + len * 0.22f)
        close()
    }
    drawPath(tail, col)
}

private fun DrawScope.drawParticles(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    val n = when (p.decor) {
        SceneDecor.GLOW -> 46
        else -> 22
    }
    for (k in 0 until n) {
        val speed = (0.03f + 0.04f * h(k, 41))
        val phase = (t * speed + h(k, 42)) % 1f
        val x = h(k, 43) * w + sin(phase * 9f + k) * 6f * u
        val y = hgt - phase * (hgt - horizon)
        val fade = min(1f, (1f - phase) * 3f) * min(1f, phase * 6f)
        if (p.night || p.decor == SceneDecor.GLOW) {
            val tw = 0.5f + 0.5f * sin(t * 3f + k * 1.3f)
            drawCircle(p.particle.copy(alpha = 0.55f * fade * tw), radius = (1.1f + 1.6f * h(k, 44)) * u, center = Offset(x, y))
        } else {
            val r = (2f + 3.5f * h(k, 44)) * u
            drawCircle(Color.White.copy(alpha = 0.5f * fade), radius = r, center = Offset(x, y), style = Stroke(width = 1.1f * u))
            drawCircle(Color.White.copy(alpha = 0.35f * fade), radius = r * 0.3f, center = Offset(x - r * 0.35f, y - r * 0.35f))
        }
    }
}

// ---- Decor -----------------------------------------------------------------------------------------

private fun DrawScope.drawSeabed(p: MenuPalette, t: Float, u: Float, grass: Boolean) {
    val w = size.width
    val hgt = size.height
    val floor = hgt - 70f * u
    val sand = if (p.light) lerp(p.sea.last(), Color(0xFFF2DFB4), 0.55f) else lerp(p.sea.last(), p.silhouette, 0.35f)
    val dunes = Path().apply {
        moveTo(0f, hgt)
        var x = 0f
        lineTo(0f, floor)
        while (x <= w) {
            lineTo(x, floor + sin(x / (80f * u)) * 10f * u + sin(x / (31f * u)) * 4f * u)
            x += 8f * u
        }
        lineTo(w, hgt)
        close()
    }
    drawPath(dunes, sand)
    if (grass) {
        for (k in 0 until 9) {
            val x = h(k, 51) * w
            val tall = (24f + 26f * h(k, 52)) * u
            for (b in 0 until 3) {
                val sway = sin(t * 1.4f + k + b) * 5f * u
                val base = Offset(x + b * 4f * u, floor + 4f * u)
                val blade = Path().apply {
                    moveTo(base.x - 2f * u, base.y)
                    quadraticTo(base.x + sway * 0.5f, base.y - tall * 0.6f, base.x + sway, base.y - tall)
                    quadraticTo(base.x + 2f * u + sway * 0.5f, base.y - tall * 0.6f, base.x + 2f * u, base.y)
                    close()
                }
                drawPath(blade, p.silhouette.copy(alpha = if (p.night) 0.7f else 0.45f))
            }
        }
        // A starfish and a shell or two.
        drawStar(Offset(w * 0.72f, floor + 16f * u), 9f * u, p.accentB.copy(alpha = if (p.night) 0.5f else 0.85f), t * 0.1f)
        drawStar(Offset(w * 0.18f, floor + 22f * u), 6f * u, p.accentA.copy(alpha = if (p.night) 0.45f else 0.8f), 0.4f)
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, col: Color, spin: Float) {
    val path = Path()
    for (k in 0 until 10) {
        val a = spin + k * PI.toFloat() / 5f - PI.toFloat() / 2f
        val rr = if (k % 2 == 0) r else r * 0.45f
        val pt = c + Offset(cos(a), sin(a)) * rr
        if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    drawPath(path, col)
}

private fun DrawScope.drawKelp(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    drawSeabed(p, t, u, grass = false)
    // Tall kelp along both edges, swaying; the middle stays clear for the menus.
    for (k in 0 until 8) {
        val side = k % 2
        val x = if (side == 0) w * (0.02f + 0.07f * (k / 2)) else w * (0.98f - 0.07f * (k / 2))
        val tall = (hgt - horizon) * (0.55f + 0.3f * h(k, 61))
        val col = (if (k % 3 == 0) p.accentB else p.silhouette).copy(alpha = if (p.night) 0.6f else 0.4f)
        val stalk = Path()
        var y = hgt
        stalk.moveTo(x, y)
        var i = 0
        while (y > hgt - tall) {
            y -= 8f * u
            val sway = sin(t * 0.9f + y / (60f * u) + k) * 10f * u * ((hgt - y) / tall)
            stalk.lineTo(x + sway, y)
            // Leaves along the stalk.
            if (i % 4 == 0) {
                val dir = if (i % 8 == 0) 1f else -1f
                drawOval(col, topLeft = Offset(x + sway + (if (dir > 0) 0f else -16f * u), y - 4f * u), size = Size(16f * u, 7f * u))
            }
            i++
        }
        drawPath(stalk, col, style = Stroke(width = 3f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawSandbar(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    // A light net of caustics over the sandy shallows.
    for (k in 0 until 7) {
        val y = hgt * (0.55f + 0.06f * k)
        val path = Path()
        var x = 0f
        path.moveTo(x, y)
        while (x <= w) {
            path.lineTo(x, y + sin(x / (26f * u) + t * 0.9f + k * 1.3f) * 5f * u)
            x += 6f * u
        }
        drawPath(path, Color.White.copy(alpha = if (p.night) 0.06f else 0.28f), style = Stroke(width = 1.6f * u))
    }
    // Ripples in the sand and a few shells.
    for (k in 0 until 5) {
        val y = hgt - (18f + 12f * k) * u
        drawLine(p.silhouette.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y + sin(k.toFloat()) * 6f * u), strokeWidth = 1.5f * u)
    }
    for (k in 0 until 4) {
        val c = Offset(h(k, 71) * w, hgt - (14f + 30f * h(k, 72)) * u)
        drawArc(p.accentA.copy(alpha = 0.9f), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = c - Offset(7f * u, 7f * u), size = Size(14f * u, 14f * u))
    }
}

private fun DrawScope.drawCoral(p: MenuPalette, t: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    drawSeabed(p, t, u, grass = false)
    val floor = hgt - 64f * u
    val colours = listOf(p.silhouette, p.accentA, p.accentB)
    for (k in 0 until 7) {
        val base = Offset(w * (0.04f + 0.15f * k), floor + 8f * u)
        val col = colours[k % 3].copy(alpha = if (p.night) 0.75f else 0.85f)
        if (k % 2 == 0) {
            // Branching coral.
            for (b in 0 until 5) {
                val a = -PI.toFloat() / 2f + (b - 2f) * 0.32f + sin(t * 0.6f + k) * 0.04f
                val len = (30f + 14f * h(k * 5 + b, 81)) * u
                val end = base + Offset(cos(a), sin(a)) * len
                drawLine(col, base, end, strokeWidth = 4f * u, cap = StrokeCap.Round)
                drawCircle(col, radius = 3.5f * u, center = end)
            }
        } else {
            // A sea fan.
            val fan = Path().apply {
                moveTo(base.x, base.y)
                for (s in 0..10) {
                    val a = PI.toFloat() * (1.15f + s * 0.07f)
                    lineTo(base.x + cos(a) * 34f * u, base.y + sin(a) * 34f * u)
                }
                close()
            }
            drawPath(fan, col.copy(alpha = col.alpha * 0.8f))
        }
    }
    // Small reef fish darting about.
    for (k in 0 until 5) {
        val x = ((t * (20f + 8f * k) * u + h(k, 82) * w) % (w + 40f * u)) - 20f * u
        val y = floor - (20f + 40f * h(k, 83)) * u + sin(t * 2f + k) * 4f * u
        drawFishShape(Offset(x, y), 10f * u, 1f, colours[(k + 1) % 3])
    }
}

private fun DrawScope.drawIce(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    // Floes on the water, the ice below the waterline pale and see-through.
    for (k in 0 until 4) {
        val cx = w * (0.1f + 0.27f * k) + sin(t * 0.15f + k) * 8f * u
        val width = (40f + 30f * h(k, 91)) * u
        val above = Path().apply {
            moveTo(cx - width / 2f, horizon); lineTo(cx - width * 0.3f, horizon - 10f * u); lineTo(cx + width * 0.2f, horizon - 14f * u); lineTo(cx + width / 2f, horizon); close()
        }
        drawPath(above, p.accentA.copy(alpha = 0.95f))
        val below = Path().apply {
            moveTo(cx - width / 2f, horizon); lineTo(cx + width / 2f, horizon); lineTo(cx + width * 0.3f, horizon + 30f * u); lineTo(cx - width * 0.25f, horizon + 24f * u); close()
        }
        drawPath(below, p.accentB.copy(alpha = if (p.night) 0.2f else 0.4f))
    }
    // Snow over everything.
    for (k in 0 until 30) {
        val phase = (t * (0.02f + 0.02f * h(k, 92)) + h(k, 93)) % 1f
        val x = h(k, 94) * size.width + sin(phase * 12f + k) * 10f * u
        val y = phase * size.height
        drawCircle(Color.White.copy(alpha = if (p.night) 0.7f else 0.9f), radius = (1.2f + 1.4f * h(k, 95)) * u, center = Offset(x, y))
    }
}

private fun DrawScope.drawKoi(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    // Koi gliding in slow loops.
    for (k in 0 until 4) {
        val a = t * (0.12f + 0.03f * k) + k * 1.6f
        val c = Offset(w * (0.5f + 0.36f * cos(a)), horizon + (hgt - horizon) * (0.45f + 0.28f * sin(a * 1.3f + k)))
        val heading = atan2Deg(cos(a * 1.3f + k) * 1.3f * 0.28f * (hgt - horizon), -sin(a) * 0.36f * w)
        val len = (34f + 10f * h(k, 101)) * u
        rotate(heading, pivot = c) {
            val body = if (k % 2 == 0) p.accentA else Color.White
            val wag = sin(t * 6f + k) * 0.25f
            val tail = Path().apply {
                moveTo(c.x - len * 0.42f, c.y)
                lineTo(c.x - len * 0.7f, c.y - len * (0.18f + wag * 0.3f))
                lineTo(c.x - len * 0.62f, c.y)
                lineTo(c.x - len * 0.7f, c.y + len * (0.18f - wag * 0.3f))
                close()
            }
            drawPath(tail, body.copy(alpha = 0.85f))
            drawOval(body.copy(alpha = 0.9f), topLeft = Offset(c.x - len * 0.45f, c.y - len * 0.14f), size = Size(len * 0.9f, len * 0.28f))
            drawOval((if (k % 2 == 0) Color.White else p.accentA).copy(alpha = 0.8f), topLeft = Offset(c.x - len * 0.05f, c.y - len * 0.1f), size = Size(len * 0.25f, len * 0.16f))
            drawCircle(Color(0xFF1A1A1A).copy(alpha = 0.7f), radius = len * 0.035f, center = Offset(c.x + len * 0.33f, c.y - len * 0.05f))
        }
    }
    // Lily pads floating at the surface, and a few petals.
    for (k in 0 until 5) {
        val c = Offset(h(k, 102) * w + sin(t * 0.2f + k) * 6f * u, horizon + (8f + 30f * h(k, 103)) * u)
        val r = (12f + 8f * h(k, 104)) * u
        drawArc(p.silhouette.copy(alpha = 0.85f), startAngle = 20f + k * 40f, sweepAngle = 320f, useCenter = true, topLeft = c - Offset(r, r * 0.55f), size = Size(2f * r, 1.1f * r))
        if (k % 2 == 0) drawCircle(p.accentB, radius = 4f * u, center = c + Offset(r * 0.2f, -r * 0.1f))
    }
}

private fun atan2Deg(y: Float, x: Float) = (kotlin.math.atan2(y, x) * 180f / PI.toFloat())

private fun DrawScope.drawTreasure(p: MenuPalette, t: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    drawSeabed(p, t, u, grass = false)
    val floor = hgt - 66f * u
    // A chest, half open, and an anchor.
    val chest = Offset(w * 0.66f, floor - 4f * u)
    drawRect(p.silhouette, topLeft = chest, size = Size(40f * u, 22f * u))
    drawRect(lerp(p.silhouette, Color.Black, 0.2f), topLeft = chest - Offset(0f, 12f * u), size = Size(40f * u, 10f * u))
    drawRect(p.accentA.copy(alpha = 0.9f), topLeft = chest + Offset(3f * u, -2f * u), size = Size(34f * u, 4f * u))
    val ax = w * 0.22f
    drawLine(p.silhouette, Offset(ax, floor - 40f * u), Offset(ax, floor + 6f * u), strokeWidth = 4f * u, cap = StrokeCap.Round)
    drawArc(p.silhouette, 20f, 140f, useCenter = false, topLeft = Offset(ax - 16f * u, floor - 20f * u), size = Size(32f * u, 28f * u), style = Stroke(width = 4f * u, cap = StrokeCap.Round))
    drawCircle(p.silhouette, radius = 5f * u, center = Offset(ax, floor - 44f * u), style = Stroke(width = 3f * u))
    // Coins on the sand, glinting in turn, and gold sparks rising.
    for (k in 0 until 12) {
        val c = Offset(w * (0.5f + 0.4f * (h(k, 111) - 0.5f)), floor + (6f + 14f * h(k, 112)) * u)
        drawOval(p.accentA, topLeft = c - Offset(4f * u, 2f * u), size = Size(8f * u, 4f * u))
        val glint = max(0f, sin(t * 2f + k * 1.7f))
        if (glint > 0.8f) drawStar(c - Offset(0f, 2f * u), 5f * u * (glint - 0.8f) * 5f, p.accentB, 0f)
    }
    for (k in 0 until 10) {
        val phase = (t * 0.05f + h(k, 113)) % 1f
        val c = Offset(w * (0.6f + 0.25f * (h(k, 114) - 0.5f)) + sin(phase * 8f) * 4f * u, floor - phase * hgt * 0.4f)
        drawCircle(p.accentB.copy(alpha = 0.7f * (1f - phase)), radius = 1.6f * u, center = c)
    }
}

private fun DrawScope.drawGlow(p: MenuPalette, t: Float, horizon: Float, u: Float) {
    val w = size.width
    val hgt = size.height
    // A deep glow rising from below.
    drawRect(Brush.radialGradient(listOf(p.accentA.copy(alpha = 0.18f), Color.Transparent), center = Offset(w * 0.5f, hgt * 1.05f), radius = hgt * 0.55f))
    // Jellyfish drifting up and down, pulsing, with glowing rims and waving tentacles.
    for (k in 0 until 6) {
        val col = if (k % 2 == 0) p.accentA else p.accentB
        val drift = (t * (0.012f + 0.006f * h(k, 121)) + h(k, 122)) % 1f
        val c = Offset(w * (0.1f + 0.8f * h(k, 123)) + sin(t * 0.3f + k) * 14f * u, hgt - drift * (hgt - horizon) * 1.1f)
        val pulse = 0.85f + 0.15f * sin(t * 2.2f + k)
        val r = (16f + 12f * h(k, 124)) * u
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.3f), Color.Transparent), center = c, radius = r * 2.6f), radius = r * 2.6f, center = c)
        drawArc(col.copy(alpha = 0.35f), 180f, 180f, useCenter = true, topLeft = c - Offset(r * pulse, r * 0.8f), size = Size(2f * r * pulse, 1.6f * r))
        drawArc(col.copy(alpha = 0.95f), 180f, 180f, useCenter = false, topLeft = c - Offset(r * pulse, r * 0.8f), size = Size(2f * r * pulse, 1.6f * r), style = Stroke(width = 1.8f * u))
        for (tent in 0 until 5) {
            val tx = c.x + (tent - 2f) * r * 0.35f * pulse
            val path = Path()
            path.moveTo(tx, c.y)
            for (s in 1..8) {
                val yy = c.y + s * r * 0.22f
                path.lineTo(tx + sin(t * 3f + s * 0.8f + tent) * 3f * u, yy)
            }
            drawPath(path, col.copy(alpha = 0.55f), style = Stroke(width = 1.2f * u, cap = StrokeCap.Round))
        }
    }
    drawSeabed(p, t, u, grass = false)
    // Glowing specks on the floor.
    for (k in 0 until 14) {
        val c = Offset(h(k, 125) * w, hgt - (10f + 40f * h(k, 126)) * u)
        val tw = 0.4f + 0.6f * abs(sin(t * 1.5f + k))
        drawCircle((if (k % 2 == 0) p.accentA else p.accentB).copy(alpha = 0.8f * tw), radius = 2f * u, center = c)
    }
}

/** For tests that draw the scene on their own canvas. */
internal fun drawBackdropForTest(scope: DrawScope, palette: MenuPalette, t: Float) = scope.drawBackdrop(palette, t)
