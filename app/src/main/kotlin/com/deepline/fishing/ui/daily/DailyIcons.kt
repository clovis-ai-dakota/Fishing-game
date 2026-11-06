package com.deepline.fishing.ui.daily

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The daily loop's icons, drawn in code on a 24×24 grid like `GameIcons` (single colour, tinted). */
object DailyIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.filled(block: PathBuilder.() -> Unit) = path(fill = ink, pathBuilder = block)

    private fun ImageVector.Builder.stroked(block: PathBuilder.() -> Unit) =
        path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    /** The streak: a flame. */
    val Flame: ImageVector = icon("Flame") {
        filled {
            moveTo(12f, 2f)
            curveTo(13f, 6f, 18.5f, 8.5f, 18.5f, 14f)
            curveTo(18.5f, 18f, 15.5f, 21.5f, 12f, 21.5f)
            curveTo(8.5f, 21.5f, 5.5f, 18.5f, 5.5f, 14.5f)
            curveTo(5.5f, 11.5f, 7f, 9.5f, 8.5f, 8f)
            curveTo(8.5f, 10.5f, 9.5f, 12f, 10.5f, 12.5f)
            curveTo(10f, 8.5f, 11f, 5f, 12f, 2f)
            close()
        }
    }

    /** A streak shield. */
    val Shield: ImageVector = icon("Shield") {
        filled {
            moveTo(12f, 2.5f)
            lineTo(19.5f, 5.5f)
            verticalLineTo(11f)
            curveTo(19.5f, 15.5f, 16.5f, 19.5f, 12f, 21.5f)
            curveTo(7.5f, 19.5f, 4.5f, 15.5f, 4.5f, 11f)
            verticalLineTo(5.5f)
            close()
        }
    }

    /** Reminders: a bell. */
    val Bell: ImageVector = icon("Bell") {
        filled {
            moveTo(12f, 3f)
            curveTo(15.5f, 3f, 17.5f, 5.5f, 17.5f, 9f)
            verticalLineTo(13.5f)
            lineTo(19.5f, 17f)
            horizontalLineTo(4.5f)
            lineTo(6.5f, 13.5f)
            verticalLineTo(9f)
            curveTo(6.5f, 5.5f, 8.5f, 3f, 12f, 3f)
            close()
            moveTo(9.5f, 18.5f)
            horizontalLineTo(14.5f)
            curveTo(14.5f, 20f, 13.4f, 21f, 12f, 21f)
            curveTo(10.6f, 21f, 9.5f, 20f, 9.5f, 18.5f)
            close()
        }
    }

    /** A reroll: two arrows chasing each other. */
    val Swap: ImageVector = icon("Swap") {
        stroked {
            moveTo(5f, 9f)
            horizontalLineTo(18f)
            moveTo(15f, 6f)
            lineTo(18f, 9f)
            lineTo(15f, 12f)
            moveTo(19f, 15f)
            horizontalLineTo(6f)
            moveTo(9f, 12f)
            lineTo(6f, 15f)
            lineTo(9f, 18f)
        }
    }

    /** Migrations: three fish swimming together. */
    val School: ImageVector = icon("School") {
        filled {
            for ((x, y, s) in listOf(Triple(3f, 8f, 1f), Triple(10f, 13f, 1f), Triple(4f, 17f, 0.8f))) {
                moveTo(x, y)
                curveTo(x + 2.5f * s, y - 3.5f * s, x + 6.5f * s, y - 3.5f * s, x + 8.5f * s, y)
                curveTo(x + 6.5f * s, y + 3.5f * s, x + 2.5f * s, y + 3.5f * s, x, y)
                close()
                moveTo(x + 8f * s, y)
                lineTo(x + 11f * s, y - 2.5f * s)
                lineTo(x + 11f * s, y + 2.5f * s)
                close()
            }
        }
    }
}
