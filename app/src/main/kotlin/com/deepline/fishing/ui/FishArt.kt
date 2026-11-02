package com.deepline.fishing.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.deepline.fishing.core.FishSpecies
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a fish centered on [center], [lengthPx] long, with its head pointing right when
 * [direction] is 1 and left when it is -1. [rotationDegrees] rotates the whole fish
 * (e.g. -90 to hang it head-up from the hook).
 */
fun DrawScope.drawFish(
    species: FishSpecies,
    center: Offset,
    lengthPx: Float,
    direction: Int,
    sparkling: Boolean,
    timeSeconds: Float,
    rotationDegrees: Float = 0f,
    silhouette: Boolean = false,
) {
    val l = lengthPx
    val h = lengthPx * species.heightRatio
    val body = if (silhouette) Color(0xFF1C2E3E) else Color(species.bodyColor)
    val accent = if (silhouette) Color(0xFF142331) else Color(species.accentColor)

    withTransform({
        translate(center.x, center.y)
        rotate(rotationDegrees, pivot = Offset.Zero)
        scale(direction.toFloat(), 1f, pivot = Offset.Zero)
    }) {
        val tail = Path().apply {
            moveTo(-0.30f * l, 0f)
            lineTo(-0.5f * l, -0.5f * h)
            lineTo(-0.5f * l, 0.5f * h)
            close()
        }
        drawPath(tail, accent)

        val fin = Path().apply {
            moveTo(-0.12f * l, -0.40f * h)
            lineTo(0.06f * l, -0.78f * h)
            lineTo(0.18f * l, -0.38f * h)
            close()
        }
        drawPath(fin, accent)

        drawOval(body, topLeft = Offset(-0.40f * l, -0.5f * h), size = Size(0.90f * l, h))

        if (!silhouette) {
            // A lighter "face" marks the head: that is the part to nudge with the hook.
            drawOval(
                Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0.18f * l, -0.34f * h),
                size = Size(0.28f * l, 0.68f * h),
            )
            val eyeRadius = max(h * 0.11f, 1.6f)
            val eye = Offset(0.34f * l, -0.10f * h)
            drawCircle(Color.White, radius = eyeRadius, center = eye)
            drawCircle(Color(0xFF0D1B2A), radius = eyeRadius * 0.55f, center = eye)
        }

        if (sparkling && !silhouette) {
            drawOval(
                SparkleGold,
                topLeft = Offset(-0.40f * l, -0.5f * h),
                size = Size(0.90f * l, h),
                style = Stroke(width = max(1.5f, h * 0.08f)),
            )
            val glints = listOf(
                Offset(-0.18f * l, -0.85f * h),
                Offset(0.30f * l, 0.80f * h),
                Offset(0.48f * l, -0.70f * h),
            )
            glints.forEachIndexed { i, p ->
                val a = 0.5f + 0.5f * sin(timeSeconds * 5f + i * 2.1f)
                val r = max(l * 0.07f, 3f) * (0.6f + 0.4f * a)
                val c = SparkleGold.copy(alpha = a)
                drawLine(c, Offset(p.x - r, p.y), Offset(p.x + r, p.y), strokeWidth = 2f)
                drawLine(c, Offset(p.x, p.y - r), Offset(p.x, p.y + r), strokeWidth = 2f)
            }
        }
    }
}
