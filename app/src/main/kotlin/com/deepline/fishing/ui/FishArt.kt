package com.deepline.fishing.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.core.FishSpecies
import kotlin.math.max
import kotlin.math.sin

/** Body plan used to draw a species (purely visual). */
enum class FishShape { STANDARD, DEEP_BODY, EEL, ANGLER, CUTTLE }

/** Markings drawn on the body. */
enum class FishPattern { PLAIN, STRIPES, LATERAL_BAND, SPOTS }

data class FishStyle(val shape: FishShape, val pattern: FishPattern)

object FishStyles {
    private val styles = mapOf(
        "silver_sprat" to FishStyle(FishShape.STANDARD, FishPattern.LATERAL_BAND),
        "coral_darter" to FishStyle(FishShape.STANDARD, FishPattern.SPOTS),
        "reed_perch" to FishStyle(FishShape.STANDARD, FishPattern.STRIPES),
        "sun_bream" to FishStyle(FishShape.DEEP_BODY, FishPattern.PLAIN),
        "dusk_snapper" to FishStyle(FishShape.STANDARD, FishPattern.LATERAL_BAND),
        "ribbon_eel" to FishStyle(FishShape.EEL, FishPattern.PLAIN),
        "reef_grouper" to FishStyle(FishShape.DEEP_BODY, FishPattern.SPOTS),
        "veil_ray" to FishStyle(FishShape.DEEP_BODY, FishPattern.STRIPES),
        "ghost_hake" to FishStyle(FishShape.STANDARD, FishPattern.PLAIN),
        "needle_viper" to FishStyle(FishShape.STANDARD, FishPattern.LATERAL_BAND),
        "ink_cuttle" to FishStyle(FishShape.CUTTLE, FishPattern.SPOTS),
        "iron_sturgeon" to FishStyle(FishShape.STANDARD, FishPattern.SPOTS),
        "glowfin_angler" to FishStyle(FishShape.ANGLER, FishPattern.SPOTS),
        "star_drifter" to FishStyle(FishShape.DEEP_BODY, FishPattern.SPOTS),
        "abyss_wyrm" to FishStyle(FishShape.EEL, FishPattern.STRIPES),
        "pale_leviathan" to FishStyle(FishShape.STANDARD, FishPattern.LATERAL_BAND),
        "deep_sovereign" to FishStyle(FishShape.ANGLER, FishPattern.STRIPES),
    )

    /** Body plans for generated species, by the kind of fish their name ends with. */
    private val shapeByKind = listOf(
        "Cuttle" to FishShape.CUTTLE,
        "Grouper" to FishShape.DEEP_BODY,
        "Batfish" to FishShape.DEEP_BODY,
        "Angelfish" to FishShape.DEEP_BODY,
        "Gulper" to FishShape.DEEP_BODY,
        "Ray" to FishShape.DEEP_BODY,
    )

    /** Markings that match a name ("Banded", "Speckled"...); otherwise one picked from the id. */
    private val patternByWord = mapOf(
        "Banded" to FishPattern.STRIPES,
        "Tidal" to FishPattern.STRIPES,
        "Speckled" to FishPattern.SPOTS,
        "Pebble" to FishPattern.SPOTS,
        "Starlit" to FishPattern.SPOTS,
        "Nebula" to FishPattern.SPOTS,
    )

    fun of(species: FishSpecies): FishStyle {
        styles[species.id]?.let { return it }
        // Glowing fish carry a lure, which is what shines in dark water.
        val shape = when {
            species.glowing -> FishShape.ANGLER
            species.heightRatio < 0.2f -> FishShape.EEL
            else -> shapeByKind.firstOrNull { species.name.endsWith(it.first) }?.second ?: FishShape.STANDARD
        }
        val pattern = patternByWord[species.name.substringBefore(' ')]
            ?: FishPattern.entries[(species.id.hashCode() and 0x7FFFFFFF) % FishPattern.entries.size]
        return FishStyle(shape, pattern)
    }
}

/**
 * Paths for one species in "unit" space: the fish is 1 long (x from -0.5 tail to +0.5 nose,
 * head pointing to +x) and h = heightRatio tall. They are built once and scaled when drawn.
 */
private class FishGeometry(species: FishSpecies) {
    val style = FishStyles.of(species)
    val h = species.heightRatio
    val body = bodyPath(style.shape, h)
    val tail = tailPath(style.shape, h)
    val dorsal = dorsalPath(style.shape, h)
    val ventral = ventralPath(style.shape, h)
    val tailPivot = Offset(if (style.shape == FishShape.EEL) -0.45f else -0.37f, 0f)

    // Colours from the colour script (bright near the surface, dark and harsh in the deep).
    val look = FishLooks.of(species)
    val bodyColor = Color(look.body)
    val accent = Color(look.accent)
    val bodyBrush = Brush.verticalGradient(
        0f to lerp(bodyColor, accent, 0.45f),
        0.55f to bodyColor,
        1f to lerp(bodyColor, Color.White, 0.45f),
        startY = -0.5f * h,
        endY = 0.5f * h,
    )
    val finColor = lerp(accent, bodyColor, 0.25f).copy(alpha = 0.9f)

    /** Where the eye sits, and its radius, in unit space. */
    val eye: Offset = when (style.shape) {
        FishShape.EEL -> Offset(0.40f, -0.14f * h)
        FishShape.ANGLER -> Offset(0.28f, -0.24f * h)
        FishShape.CUTTLE -> Offset(0.35f, -0.08f * h)
        else -> Offset(0.36f, -0.12f * h)
    }
    val eyeRadius = when (style.shape) {
        FishShape.EEL -> 0.22f * h
        FishShape.ANGLER -> 0.10f * h
        else -> 0.11f * h
    }

    /** Tip of the anglerfish lure, in unit space. */
    val lureTip = Offset(0.64f, -0.78f * h)

    companion object {
        private fun bodyPath(shape: FishShape, h: Float) = Path().apply {
            when (shape) {
                FishShape.STANDARD -> {
                    moveTo(0.50f, 0.02f * h)
                    cubicTo(0.47f, -0.30f * h, 0.30f, -0.50f * h, 0.08f, -0.50f * h)
                    cubicTo(-0.12f, -0.50f * h, -0.28f, -0.25f * h, -0.37f, -0.11f * h)
                    lineTo(-0.37f, 0.11f * h)
                    cubicTo(-0.28f, 0.25f * h, -0.12f, 0.48f * h, 0.08f, 0.48f * h)
                    cubicTo(0.30f, 0.48f * h, 0.46f, 0.30f * h, 0.50f, 0.02f * h)
                }
                FishShape.DEEP_BODY -> {
                    moveTo(0.50f, 0.04f * h)
                    cubicTo(0.48f, -0.38f * h, 0.25f, -0.56f * h, 0.02f, -0.52f * h)
                    cubicTo(-0.18f, -0.48f * h, -0.30f, -0.22f * h, -0.37f, -0.09f * h)
                    lineTo(-0.37f, 0.09f * h)
                    cubicTo(-0.30f, 0.22f * h, -0.18f, 0.50f * h, 0.02f, 0.50f * h)
                    cubicTo(0.25f, 0.52f * h, 0.48f, 0.36f * h, 0.50f, 0.04f * h)
                }
                FishShape.EEL -> {
                    moveTo(0.50f, 0f)
                    cubicTo(0.48f, -0.45f * h, 0.38f, -0.50f * h, 0.25f, -0.50f * h)
                    cubicTo(0.0f, -0.50f * h, -0.30f, -0.40f * h, -0.50f, -0.05f * h)
                    lineTo(-0.50f, 0.05f * h)
                    cubicTo(-0.30f, 0.40f * h, 0.0f, 0.50f * h, 0.25f, 0.50f * h)
                    cubicTo(0.38f, 0.50f * h, 0.48f, 0.45f * h, 0.50f, 0f)
                }
                FishShape.ANGLER -> {
                    moveTo(0.46f, 0.15f * h)
                    cubicTo(0.50f, -0.30f * h, 0.30f, -0.55f * h, 0.05f, -0.50f * h)
                    cubicTo(-0.15f, -0.46f * h, -0.30f, -0.20f * h, -0.37f, -0.10f * h)
                    lineTo(-0.37f, 0.10f * h)
                    cubicTo(-0.28f, 0.30f * h, -0.10f, 0.52f * h, 0.12f, 0.50f * h)
                    cubicTo(0.32f, 0.48f * h, 0.50f, 0.38f * h, 0.46f, 0.15f * h)
                }
                FishShape.CUTTLE -> {
                    addOval(Rect(-0.44f, -0.48f * h, 0.36f, 0.48f * h))
                    addOval(Rect(0.24f, -0.34f * h, 0.47f, 0.34f * h))
                }
            }
            close()
        }

        private fun tailPath(shape: FishShape, h: Float) = Path().apply {
            when (shape) {
                FishShape.EEL -> {
                    moveTo(-0.44f, -0.10f * h)
                    lineTo(-0.57f, 0f)
                    lineTo(-0.44f, 0.10f * h)
                }
                FishShape.CUTTLE -> {
                    // The fin "skirt" around the mantle.
                    addOval(Rect(-0.49f, -0.60f * h, 0.38f, 0.60f * h))
                }
                else -> {
                    moveTo(-0.34f, -0.08f * h)
                    cubicTo(-0.40f, -0.18f * h, -0.46f, -0.40f * h, -0.53f, -0.56f * h)
                    quadraticTo(-0.46f, 0f, -0.53f, 0.56f * h)
                    cubicTo(-0.46f, 0.40f * h, -0.40f, 0.18f * h, -0.34f, 0.08f * h)
                }
            }
            close()
        }

        private fun dorsalPath(shape: FishShape, h: Float) = Path().apply {
            when (shape) {
                FishShape.EEL -> {
                    moveTo(0.22f, -0.40f * h)
                    cubicTo(0.0f, -0.90f * h, -0.30f, -0.80f * h, -0.52f, -0.05f * h)
                    lineTo(-0.30f, -0.10f * h)
                    lineTo(0.0f, -0.20f * h)
                }
                FishShape.DEEP_BODY -> {
                    moveTo(-0.20f, -0.44f * h)
                    cubicTo(-0.12f, -0.90f * h, 0.12f, -1.00f * h, 0.22f, -0.50f * h)
                }
                FishShape.ANGLER -> {
                    moveTo(-0.22f, -0.42f * h)
                    quadraticTo(-0.15f, -0.78f * h, -0.04f, -0.48f * h)
                }
                FishShape.CUTTLE -> Unit
                FishShape.STANDARD -> {
                    moveTo(-0.14f, -0.46f * h)
                    cubicTo(-0.08f, -0.85f * h, 0.08f, -0.95f * h, 0.16f, -0.48f * h)
                }
            }
            close()
        }

        private fun ventralPath(shape: FishShape, h: Float) = Path().apply {
            when (shape) {
                FishShape.EEL -> {
                    moveTo(0.10f, 0.40f * h)
                    cubicTo(-0.05f, 0.85f * h, -0.30f, 0.75f * h, -0.52f, 0.05f * h)
                    lineTo(-0.30f, 0.10f * h)
                    lineTo(0.0f, 0.20f * h)
                }
                FishShape.CUTTLE -> Unit
                else -> {
                    moveTo(-0.14f, 0.44f * h)
                    quadraticTo(-0.07f, 0.80f * h, 0.05f, 0.46f * h)
                }
            }
            close()
        }
    }
}

private val geometryCache = HashMap<String, FishGeometry>()

private fun geometryOf(species: FishSpecies): FishGeometry =
    geometryCache.getOrPut(species.id) { FishGeometry(species) }

private val SilhouetteBody = Color(0xFF1C2E3E)
private val SilhouetteFin = Color(0xFF142331)
private val SpotOffsets = listOf(
    Offset(0.18f, -0.22f), Offset(0.02f, -0.30f), Offset(-0.12f, -0.12f), Offset(0.06f, 0.06f),
    Offset(-0.24f, -0.26f), Offset(-0.06f, 0.22f), Offset(0.22f, 0.10f), Offset(-0.20f, 0.08f),
)

/**
 * Draws a fish centered on [center], [lengthPx] long, with its head pointing right when
 * [direction] is 1 and left when it is -1. [rotationDegrees] rotates the whole fish (e.g. -90 to
 * hang it head-up from the hook). [phase] offsets the swimming animation between fish, and
 * [wag] scales how hard the tail swishes (a hooked fish struggles).
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
    phase: Float = 0f,
    wag: Float = 1f,
) {
    if (lengthPx <= 0.5f) return
    val g = geometryOf(species)
    val h = g.h
    val l = lengthPx
    val detailed = lengthPx >= 14f
    val px = 1f / l // one screen pixel, in unit space
    val swish = sin(timeSeconds * (7f + 5f * (wag - 1f)) + phase)
    val tailAngle = swish * 14f * wag

    withTransform({
        translate(center.x, center.y)
        rotate(rotationDegrees, pivot = Offset.Zero)
        scale(direction.toFloat() * l, l, pivot = Offset.Zero)
    }) {
        val finColor = if (silhouette) SilhouetteFin else g.finColor

        // Tail (swishing), then fins, then the body on top.
        if (g.style.shape == FishShape.CUTTLE) {
            val ripple = 1f + 0.03f * swish
            withTransform({ scale(1f, ripple, pivot = Offset.Zero) }) { drawPath(g.tail, finColor.copy(alpha = 0.7f)) }
        } else {
            rotate(tailAngle, pivot = g.tailPivot) { drawPath(g.tail, finColor) }
        }
        drawPath(g.dorsal, finColor)
        drawPath(g.ventral, finColor)

        if (silhouette) {
            drawPath(g.body, SilhouetteBody)
            return@withTransform
        }
        drawPath(g.body, g.bodyBrush)

        if (detailed) {
            clipPath(g.body) {
                when (g.style.pattern) {
                    FishPattern.STRIPES -> for (x in listOf(0.20f, 0.06f, -0.08f, -0.22f)) {
                        drawRect(g.accent.copy(alpha = 0.30f), topLeft = Offset(x - 0.03f, -0.6f * h), size = Size(0.05f, 1.2f * h))
                    }
                    FishPattern.LATERAL_BAND -> {
                        drawRect(g.accent.copy(alpha = 0.35f), topLeft = Offset(-0.45f, -0.07f * h), size = Size(0.85f, 0.11f * h))
                    }
                    FishPattern.SPOTS -> for (o in SpotOffsets) {
                        drawCircle(g.accent.copy(alpha = 0.38f), radius = 0.07f * h, center = Offset(o.x, o.y * h))
                    }
                    FishPattern.PLAIN -> Unit
                }
                // Sheen on the upper flank.
                drawOval(Color.White.copy(alpha = 0.16f), topLeft = Offset(-0.18f, -0.44f * h), size = Size(0.52f, 0.24f * h))
            }

            // The pale "face" marks the head: that is the part to nudge with the hook.
            if (g.style.shape != FishShape.CUTTLE) {
                drawOval(Color.White.copy(alpha = 0.16f), topLeft = Offset(0.24f, -0.30f * h), size = Size(0.25f, 0.60f * h))
                val gill = Path().apply {
                    moveTo(0.26f, -0.30f * h)
                    quadraticTo(0.19f, 0f, 0.26f, 0.30f * h)
                }
                drawPath(gill, g.accent.copy(alpha = 0.55f), style = Stroke(width = 1.4f * px, cap = StrokeCap.Round))
            }

            // Pectoral fin, flapping.
            val flap = sin(timeSeconds * 9f + phase * 1.3f) * 0.10f * h
            val pectoral = Path().apply {
                moveTo(0.21f, 0.08f * h)
                quadraticTo(0.10f, 0.30f * h + flap, 0.04f, 0.30f * h + flap)
                quadraticTo(0.12f, 0.16f * h, 0.21f, 0.08f * h)
                close()
            }
            drawPath(pectoral, finColor.copy(alpha = 0.75f))

            when (g.style.shape) {
                FishShape.ANGLER -> {
                    // Toothy mouth and the lure on its stalk.
                    val mouth = Path().apply {
                        moveTo(0.48f, 0.10f * h)
                        quadraticTo(0.36f, 0.24f * h, 0.24f, 0.14f * h)
                    }
                    drawPath(mouth, Color(0xFF0D1B2A), style = Stroke(width = max(1.5f * px, 0.025f), cap = StrokeCap.Round))
                    for (i in 0 until 4) {
                        val tx = 0.44f - i * 0.05f
                        val tooth = Path().apply {
                            moveTo(tx, 0.12f * h)
                            lineTo(tx - 0.012f, 0.22f * h)
                            lineTo(tx - 0.024f, 0.13f * h)
                            close()
                        }
                        drawPath(tooth, Color.White.copy(alpha = 0.9f))
                    }
                    val stalk = Path().apply {
                        moveTo(0.30f, -0.45f * h)
                        quadraticTo(0.50f, -1.10f * h, g.lureTip.x, g.lureTip.y)
                    }
                    drawPath(stalk, g.accent, style = Stroke(width = max(1.2f * px, 0.012f), cap = StrokeCap.Round))
                    drawCircle(g.accent, radius = 0.045f, center = g.lureTip)
                }
                FishShape.CUTTLE -> {
                    for (i in 0 until 5) {
                        val y0 = (-0.22f + i * 0.11f) * h
                        val wiggle = sin(timeSeconds * 5f + i + phase) * 0.06f * h
                        val arm = Path().apply {
                            moveTo(0.44f, y0)
                            quadraticTo(0.52f, y0 + wiggle, 0.58f, y0 * 1.3f + wiggle)
                        }
                        drawPath(arm, lerp(g.bodyColor, g.accent, 0.3f), style = Stroke(width = max(2f * px, 0.06f * h), cap = StrokeCap.Round))
                    }
                }
                else -> Unit
            }
        }

        // Eye: white, iris, pupil and a glint.
        val r = max(g.eyeRadius, 1.6f * px)
        drawCircle(Color.White, radius = r, center = g.eye)
        if (detailed) {
            drawCircle(lerp(g.accent, Color(0xFF0D1B2A), 0.35f), radius = r * 0.72f, center = g.eye)
        }
        drawCircle(Color(0xFF0D1B2A), radius = r * 0.45f, center = g.eye)
        if (detailed) {
            drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.18f, center = g.eye + Offset(-r * 0.25f, -r * 0.25f))
        }

        if (sparkling) {
            drawPath(g.body, SparkleGold, style = Stroke(width = 2.5f * px))
        }
    }

    if (sparkling && !silhouette) {
        // Twinkling glints around the fish (in screen space, so they stay crisp).
        val hPx = l * h
        val glints = listOf(Offset(-0.18f * l, -0.85f * hPx), Offset(0.30f * l, 0.80f * hPx), Offset(0.48f * l, -0.70f * hPx))
        glints.forEachIndexed { i, p ->
            val a = 0.5f + 0.5f * sin(timeSeconds * 5f + i * 2.1f + phase)
            val rr = max(l * 0.07f, 3f) * (0.6f + 0.4f * a)
            val c = SparkleGold.copy(alpha = a)
            val q = Offset(center.x + p.x * direction, center.y + p.y)
            drawLine(c, Offset(q.x - rr, q.y), Offset(q.x + rr, q.y), strokeWidth = 2f)
            drawLine(c, Offset(q.x, q.y - rr), Offset(q.x, q.y + rr), strokeWidth = 2f)
        }
    }
}

/**
 * The parts of a fish that glow in the dark (the anglerfish lure), drawn after the darkness
 * overlay so they shine through it. [strength] is 0..1.
 */
fun DrawScope.drawFishGlow(species: FishSpecies, center: Offset, lengthPx: Float, direction: Int, strength: Float) {
    if (strength <= 0f) return
    val g = geometryOf(species)
    if (g.style.shape != FishShape.ANGLER) return
    val tip = Offset(center.x + direction * g.lureTip.x * lengthPx, center.y + g.lureTip.y * lengthPx)
    val radius = max(lengthPx * 0.35f, 12f)
    drawCircle(
        Brush.radialGradient(
            listOf(g.accent.copy(alpha = 0.85f * strength), g.accent.copy(alpha = 0.25f * strength), Color.Transparent),
            center = tip,
            radius = radius,
        ),
        radius = radius,
        center = tip,
    )
    drawCircle(Color.White.copy(alpha = 0.9f * strength), radius = max(lengthPx * 0.03f, 2f), center = tip)
}
