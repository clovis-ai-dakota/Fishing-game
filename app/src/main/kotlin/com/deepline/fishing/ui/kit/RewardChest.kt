package com.deepline.fishing.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A treasure chest drawn as a solid (a small box model projected by hand, no 3D view): closed and
 * restless while a reward is [ready], and its lid swings back on its hinge when [open], with the
 * gold glowing inside. The daily reward's picture: it says "something to open" without a word.
 */
@Composable
fun RewardChest(open: Boolean, ready: Boolean, modifier: Modifier = Modifier) {
    val calm = LocalCalmMotion.current
    val lid by animateFloatAsState(
        if (open) 1f else 0f,
        if (calm) snap() else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "chest lid",
    )
    val time = if (calm) {
        0f
    } else {
        val t by rememberInfiniteTransition(label = "chest").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "chest time",
        )
        t
    }
    Canvas(modifier) { drawChest(lid.coerceIn(0f, 1.15f), ready && !open, time) }
}

private val Wood = Color(0xFFB8742F)
private val WoodDark = Color(0xFF7D4A1E)
private val WoodLight = Color(0xFFD6954A)
private val Gold = Color(0xFFFFC83D)
private val GoldDark = Color(0xFFB98300)
private val Lining = Color(0xFF7A1F2B)

/** The lid fully open, in radians: past upright, leaning back on its hinge. */
private const val LID_OPEN = 1.85f

/** How much of the top of things the viewer sees (the chest is seen from a little above). */
private const val VIEW_FROM_ABOVE = 0.38f

/**
 * [open] 0 (shut) to 1 (lid back); [restless] makes the shut chest jump a little now and then;
 * [time] turns once per loop (0 to 2 pi).
 */
internal fun DrawScope.drawChest(open: Float, restless: Boolean, time: Float) {
    val unit = minOf(size.width / 2.6f, size.height / 2.5f)
    val cx = size.width / 2f
    val base = size.height * 0.92f
    // A point of the model: x across, y up, z away from the viewer.
    fun at(x: Float, y: Float, z: Float) = Offset(cx + x * unit * (1f - 0.1f * z), base - (y + VIEW_FROM_ABOVE * z) * unit)

    fun quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }, color)
    }

    fun outline(a: Offset, b: Offset, c: Offset, d: Offset) {
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }, WoodDark, style = Stroke(width = unit * 0.035f))
    }

    val bodyTop = 0.85f
    val lidHeight = 0.42f
    // A shut chest with a reward inside can't keep still: two quick shakes each loop.
    val shake = if (restless) sin(time * 6f) * (if (sin(time) > 0.55f) 4f else 0f) else 0f
    rotate(shake, pivot = Offset(cx, base)) {
        // The ground shadow.
        drawOval(Color.Black.copy(alpha = 0.22f), topLeft = Offset(cx - unit * 1.15f, base - unit * 0.12f), size = androidx.compose.ui.geometry.Size(unit * 2.3f, unit * 0.34f))
        if (restless) {
            val pulse = 0.5f + 0.5f * sin(time * 2f)
            val c = at(0f, bodyTop, 0.5f)
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f + 0.25f * pulse), Color.Transparent), center = c, radius = unit * 1.7f), radius = unit * 1.7f, center = c)
        }

        // Inside: the back wall and the opening, dark until the gold shows.
        quad(at(-1f, 0f, 1f), at(1f, 0f, 1f), at(1f, bodyTop, 1f), at(-1f, bodyTop, 1f), WoodDark)
        quad(at(-1f, bodyTop, 0f), at(1f, bodyTop, 0f), at(1f, bodyTop, 1f), at(-1f, bodyTop, 1f), lerp(Color(0xFF3A2010), Gold, 0.75f * open.coerceIn(0f, 1f)))

        // The lid: a box turning on the hinge along the back of the opening.
        val angle = open * LID_OPEN
        val ca = cos(angle)
        val sa = sin(angle)
        // A point of the lid, (dy above the opening, dz from the hinge towards the front).
        fun lid(x: Float, dy: Float, dz: Float): Offset = at(x, bodyTop + dy * ca + dz * sa, 1f - dz * ca + dy * sa)
        val f0l = lid(-1f, 0f, 1f); val f0r = lid(1f, 0f, 1f) // front edge, underside
        val f1l = lid(-1f, lidHeight, 1f); val f1r = lid(1f, lidHeight, 1f) // front edge, top
        val b0l = lid(-1f, 0f, 0f); val b0r = lid(1f, 0f, 0f) // hinge
        val b1l = lid(-1f, lidHeight, 0f); val b1r = lid(1f, lidHeight, 0f)
        // Which faces look at the viewer (who sits in front, a little above).
        val topSeen = VIEW_FROM_ABOVE * ca - sa > 0f
        val frontSeen = VIEW_FROM_ABOVE * sa + ca > 0f
        val undersideSeen = sa - VIEW_FROM_ABOVE * ca > 0f
        if (!frontSeen) {
            quad(b0l, b0r, b1r, b1l, Wood)
            outline(b0l, b0r, b1r, b1l)
        }
        if (undersideSeen) {
            quad(b0l, b0r, f0r, f0l, Lining)
            outline(b0l, b0r, f0r, f0l)
        }
        if (topSeen) {
            quad(f1l, f1r, b1r, b1l, WoodLight)
            outline(f1l, f1r, b1r, b1l)
        }
        if (frontSeen) {
            quad(f0l, f0r, f1r, f1l, Wood)
            // The straps go on over the lid.
            for (x in listOf(-0.6f, 0.6f)) quad(lid(x - 0.1f, 0f, 1f), lid(x + 0.1f, 0f, 1f), lid(x + 0.1f, lidHeight, 1f), lid(x - 0.1f, lidHeight, 1f), Gold)
            outline(f0l, f0r, f1r, f1l)
        }

        // The gold's light rising out of the open chest.
        if (open > 0.05f) {
            val o = open.coerceIn(0f, 1f)
            val c = at(0f, bodyTop, 0.5f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0).copy(alpha = 0.85f * o), Gold.copy(alpha = 0.35f * o), Color.Transparent), center = c, radius = unit * 1.25f), radius = unit * 1.25f, center = c)
            // A heap of coins over the rim.
            for (k in 0 until 7) {
                val x = -0.72f + k * 0.24f
                val y = bodyTop + (0.1f + 0.1f * sin(k * 2.1f)) * o
                val p = at(x, y, 0.35f + 0.08f * (k % 3))
                drawCircle(GoldDark, radius = unit * 0.15f, center = p + Offset(0f, unit * 0.025f))
                drawCircle(Gold, radius = unit * 0.15f, center = p)
                drawCircle(Color.White.copy(alpha = 0.55f), radius = unit * 0.045f, center = p + Offset(-unit * 0.05f, -unit * 0.05f))
            }
        }

        // The front of the body, its straps and the lock.
        val a = at(-1f, 0f, 0f); val b = at(1f, 0f, 0f); val c = at(1f, bodyTop, 0f); val d = at(-1f, bodyTop, 0f)
        drawPath(
            Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() },
            Brush.verticalGradient(listOf(Wood, lerp(Wood, WoodDark, 0.45f)), startY = d.y, endY = a.y),
        )
        // Planks.
        for (k in 1..2) {
            val y = bodyTop * k / 3f
            drawLine(WoodDark.copy(alpha = 0.55f), at(-1f, y, 0f), at(1f, y, 0f), strokeWidth = unit * 0.025f)
        }
        for (x in listOf(-0.6f, 0.6f)) quad(at(x - 0.1f, 0f, 0f), at(x + 0.1f, 0f, 0f), at(x + 0.1f, bodyTop, 0f), at(x - 0.1f, bodyTop, 0f), Gold)
        quad(at(-1f, bodyTop - 0.1f, 0f), at(1f, bodyTop - 0.1f, 0f), c, d, GoldDark)
        outline(a, b, c, d)
        val lock = at(0f, bodyTop - 0.18f, 0f)
        drawCircle(GoldDark, radius = unit * 0.2f, center = lock + Offset(0f, unit * 0.03f))
        drawCircle(Gold, radius = unit * 0.2f, center = lock)
        drawCircle(WoodDark, radius = unit * 0.06f, center = lock)
        drawLine(WoodDark, lock, lock + Offset(0f, unit * 0.1f), strokeWidth = unit * 0.045f)
    }
}
