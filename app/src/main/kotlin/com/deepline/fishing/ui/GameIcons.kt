package com.deepline.fishing.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's icons, drawn in code on a 24×24 grid like the rest of the art (no icon library).
 * They are single-colour: `Icon` tints them.
 */
object GameIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.filled(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) =
        path(fill = ink, pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block)

    private fun ImageVector.Builder.stroked(width: Float = 2f, block: PathBuilder.() -> Unit) =
        path(
            stroke = ink,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    /** The Fish tab: a fish with an eye. */
    val Fish: ImageVector = icon("Fish") {
        filled(evenOdd = true) {
            moveTo(2.5f, 12f)
            curveTo(5.5f, 6.5f, 12.5f, 6f, 16.5f, 12f)
            curveTo(12.5f, 18f, 5.5f, 17.5f, 2.5f, 12f)
            close()
            circle(7f, 11.2f, 1.2f)
        }
        filled {
            moveTo(15.5f, 12f)
            lineTo(21.5f, 7f)
            lineTo(20f, 12f)
            lineTo(21.5f, 17f)
            close()
        }
    }

    /** The Dex tab: a grid of collected cards. */
    val Dex: ImageVector = icon("Dex") {
        filled {
            for ((x, y) in listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)) {
                moveTo(x + 1.5f, y)
                horizontalLineTo(x + 6.5f)
                quadTo(x + 8f, y, x + 8f, y + 1.5f)
                verticalLineTo(y + 6.5f)
                quadTo(x + 8f, y + 8f, x + 6.5f, y + 8f)
                horizontalLineTo(x + 1.5f)
                quadTo(x, y + 8f, x, y + 6.5f)
                verticalLineTo(y + 1.5f)
                quadTo(x, y, x + 1.5f, y)
                close()
            }
        }
    }

    /** The Upgrades tab: an arrow going up. */
    val Upgrades: ImageVector = icon("Upgrades") {
        filled {
            moveTo(12f, 3f)
            lineTo(20f, 11.5f)
            lineTo(15f, 11.5f)
            lineTo(15f, 20.5f)
            lineTo(9f, 20.5f)
            lineTo(9f, 11.5f)
            lineTo(4f, 11.5f)
            close()
        }
    }

    /** Themes: a painter's palette with four dabs of colour (drawn as holes). */
    val Palette: ImageVector = icon("Palette") {
        filled(evenOdd = true) {
            // The palette's outline: a rounded blob with a thumb hole.
            moveTo(12f, 3f)
            curveTo(17f, 3f, 21f, 6.6f, 21f, 11f)
            curveTo(21f, 14f, 18.8f, 15.5f, 16.5f, 15.2f)
            curveTo(15f, 15f, 14.2f, 16.2f, 14.8f, 17.6f)
            curveTo(15.5f, 19.4f, 14.4f, 21f, 12f, 21f)
            curveTo(7f, 21f, 3f, 17f, 3f, 12f)
            curveTo(3f, 7f, 7f, 3f, 12f, 3f)
            close()
            for ((x, y) in listOf(7.5f to 11.5f, 9.5f to 7.5f, 14f to 6.8f, 17.3f to 9.8f)) {
                moveTo(x + 1.4f, y)
                arcTo(1.4f, 1.4f, 0f, true, false, x - 1.4f, y)
                arcTo(1.4f, 1.4f, 0f, true, false, x + 1.4f, y)
                close()
            }
        }
    }

    /** The Shop tab: a bag with two handles. */
    val Shop: ImageVector = icon("Shop") {
        filled {
            // The bag.
            moveTo(4.5f, 8f)
            horizontalLineTo(19.5f)
            lineTo(18.5f, 20.5f)
            quadTo(18.4f, 21.5f, 17.4f, 21.5f)
            horizontalLineTo(6.6f)
            quadTo(5.6f, 21.5f, 5.5f, 20.5f)
            close()
            // The handle, as an arch over the opening.
            moveTo(8f, 8f)
            verticalLineTo(6.5f)
            curveTo(8f, 4.2f, 9.8f, 2.5f, 12f, 2.5f)
            curveTo(14.2f, 2.5f, 16f, 4.2f, 16f, 6.5f)
            verticalLineTo(8f)
            horizontalLineTo(14.3f)
            verticalLineTo(6.5f)
            curveTo(14.3f, 5.2f, 13.3f, 4.2f, 12f, 4.2f)
            curveTo(10.7f, 4.2f, 9.7f, 5.2f, 9.7f, 6.5f)
            verticalLineTo(8f)
            close()
        }
    }

    /** The Battle tab: a lightning bolt. */
    val Battle: ImageVector = icon("Battle") {
        filled {
            moveTo(13.5f, 2f)
            lineTo(4.5f, 13.5f)
            lineTo(11f, 13.5f)
            lineTo(9.5f, 22f)
            lineTo(19.5f, 9.5f)
            lineTo(13f, 9.5f)
            close()
        }
    }

    /** Milestones: a trophy cup. */
    val Trophy: ImageVector = icon("Trophy") {
        filled {
            moveTo(7f, 3f)
            horizontalLineTo(17f)
            verticalLineTo(8.5f)
            curveTo(17f, 11.5f, 14.8f, 13.8f, 12f, 13.8f)
            curveTo(9.2f, 13.8f, 7f, 11.5f, 7f, 8.5f)
            close()
            moveTo(11f, 13.5f)
            horizontalLineTo(13f)
            verticalLineTo(17.5f)
            horizontalLineTo(11f)
            close()
            moveTo(7.5f, 17.5f)
            horizontalLineTo(16.5f)
            verticalLineTo(21f)
            horizontalLineTo(7.5f)
            close()
        }
        stroked(1.8f) {
            moveTo(7f, 5f)
            horizontalLineTo(4.5f)
            curveTo(4.5f, 8f, 5.5f, 9.5f, 7.5f, 10f)
            moveTo(17f, 5f)
            horizontalLineTo(19.5f)
            curveTo(19.5f, 8f, 18.5f, 9.5f, 16.5f, 10f)
        }
    }

    /** Help / tour: a question mark. */
    val Help: ImageVector = icon("Help") {
        stroked(2.4f) {
            moveTo(8.5f, 8.5f)
            curveTo(8.5f, 6f, 10.2f, 4.5f, 12.2f, 4.5f)
            curveTo(14.4f, 4.5f, 16f, 6f, 16f, 8f)
            curveTo(16f, 10.8f, 12.2f, 11.2f, 12.2f, 14.5f)
        }
        filled { circle(12.2f, 19f, 1.6f) }
    }

    /** Settings: a cog with eight teeth around a hole. */
    val Settings: ImageVector = icon("Settings") {
        filled(evenOdd = true) {
            val teeth = 8
            for (i in 0 until teeth * 2) {
                val outer = i % 2 == 0
                val r = if (outer) 10.2f else 7.6f
                for (k in 0..1) {
                    val a = ((i + k * 0.5f - 0.25f) / (teeth * 2f)) * 2f * Math.PI.toFloat()
                    val x = 12f + r * kotlin.math.cos(a)
                    val y = 12f + r * kotlin.math.sin(a)
                    if (i == 0 && k == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            close()
            circle(12f, 12f, 3.4f)
        }
    }

    /** Locked Dex entries. */
    val Lock: ImageVector = icon("Lock") {
        stroked(2.2f) {
            moveTo(8f, 11f)
            verticalLineTo(8f)
            curveTo(8f, 5.5f, 9.8f, 3.8f, 12f, 3.8f)
            curveTo(14.2f, 3.8f, 16f, 5.5f, 16f, 8f)
            verticalLineTo(11f)
        }
        filled(evenOdd = true) {
            moveTo(6f, 10.5f)
            horizontalLineTo(18f)
            quadTo(19.5f, 10.5f, 19.5f, 12f)
            verticalLineTo(19f)
            quadTo(19.5f, 20.5f, 18f, 20.5f)
            horizontalLineTo(6f)
            quadTo(4.5f, 20.5f, 4.5f, 19f)
            verticalLineTo(12f)
            quadTo(4.5f, 10.5f, 6f, 10.5f)
            close()
            circle(12f, 15.3f, 1.6f)
        }
    }

    /** Rarity. */
    val Star: ImageVector = icon("Star") {
        filled {
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) 10f else 4.2f
                val a = -PI / 2 + k * PI / 5
                val x = 12f + (r * cos(a)).toFloat()
                val y = 12.8f + (r * sin(a)).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Lanterns. */
    val Lantern: ImageVector = icon("Lantern") {
        stroked(1.8f) {
            moveTo(9f, 5f)
            curveTo(9f, 2.8f, 15f, 2.8f, 15f, 5f)
        }
        filled(evenOdd = true) {
            moveTo(7.5f, 5f)
            horizontalLineTo(16.5f)
            verticalLineTo(7f)
            horizontalLineTo(7.5f)
            close()
            moveTo(8f, 7.5f)
            horizontalLineTo(16f)
            lineTo(17f, 18f)
            horizontalLineTo(7f)
            close()
            moveTo(10f, 9.5f)
            horizontalLineTo(14f)
            lineTo(14.6f, 16f)
            horizontalLineTo(9.4f)
            close()
            moveTo(6.5f, 18.5f)
            horizontalLineTo(17.5f)
            verticalLineTo(21f)
            horizontalLineTo(6.5f)
            close()
        }
    }

    /** Points. */
    val Coin: ImageVector = icon("Coin") {
        filled(evenOdd = true) {
            circle(12f, 12f, 9.5f)
            circle(12f, 12f, 6.5f)
        }
        filled {
            moveTo(12f, 8f)
            lineTo(14.5f, 12f)
            lineTo(12f, 16f)
            lineTo(9.5f, 12f)
            close()
        }
    }

    /** Seen in the Dex. */
    val Eye: ImageVector = icon("Eye") {
        stroked(2f) {
            moveTo(2.5f, 12f)
            curveTo(5f, 7f, 9f, 5.5f, 12f, 5.5f)
            curveTo(15f, 5.5f, 19f, 7f, 21.5f, 12f)
            curveTo(19f, 17f, 15f, 18.5f, 12f, 18.5f)
            curveTo(9f, 18.5f, 5f, 17f, 2.5f, 12f)
            close()
        }
        filled { circle(12f, 12f, 3.2f) }
    }

    /** Sparkling. */
    val Sparkle: ImageVector = icon("Sparkle") {
        filled {
            moveTo(12f, 2f)
            quadTo(13f, 11f, 22f, 12f)
            quadTo(13f, 13f, 12f, 22f)
            quadTo(11f, 13f, 2f, 12f)
            quadTo(11f, 11f, 12f, 2f)
            close()
        }
    }

    /** Previous / next in the Dex pager. */
    val ChevronLeft: ImageVector = icon("ChevronLeft") {
        stroked(2.6f) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }

    val ChevronRight: ImageVector = icon("ChevronRight") {
        stroked(2.6f) {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }
}
