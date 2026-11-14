package com.deepline.fishing.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Pictograms that stand in for sentences in the menus (see docs/ui-design.md): drawn in code like
 * `GameIcons`, in one style (round caps, 2 px strokes on a 24 px grid), tinted by whoever shows
 * them. Never system emoji: those change with the phone's maker and ignore the theme.
 */
object KitIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.filled(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) =
        path(fill = ink, pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block)

    private fun ImageVector.Builder.stroked(width: Float = 2.2f, block: PathBuilder.() -> Unit) =
        path(stroke = ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); horizontalLineTo(r); verticalLineTo(b); horizontalLineTo(l); close()
    }

    /** A fishing hook with its eye and barb: casting, casts left, attempts. */
    val Hook = icon("Hook") {
        stroked(2.6f) {
            moveTo(15.5f, 8f)
            verticalLineTo(15f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4.5f, y1 = 15f)
            verticalLineTo(10.5f)
            lineTo(8.5f, 14f)
        }
        stroked(2.2f) { circle(15.5f, 5f, 2.4f) }
    }

    /** Rope length: how deep. */
    val Depth = icon("Depth") {
        stroked { moveTo(5f, 4f); horizontalLineTo(19f) }
        stroked(2.4f) { moveTo(12f, 8f); verticalLineTo(20f); moveTo(7.5f, 15.5f); lineTo(12f, 20f); lineTo(16.5f, 15.5f) }
    }

    /** Rope strength: how heavy. */
    val Weight = icon("Weight") {
        filled { moveTo(8f, 9.5f); lineTo(16f, 9.5f); lineTo(19.5f, 20.5f); lineTo(4.5f, 20.5f); close() }
        stroked { circle(12f, 6.3f, 2.5f) }
    }

    /** Rope agility: how fast sideways. */
    val Speed = icon("Speed") {
        stroked(2.4f) {
            moveTo(3.5f, 12f); horizontalLineTo(20.5f)
            moveTo(7.5f, 8f); lineTo(3.5f, 12f); lineTo(7.5f, 16f)
            moveTo(16.5f, 8f); lineTo(20.5f, 12f); lineTo(16.5f, 16f)
        }
    }

    /** The rare fish detector. */
    val Radar = icon("Radar") {
        stroked { circle(12f, 12f, 8.6f) }
        stroked { circle(12f, 12f, 4.2f) }
        stroked(2.4f) { moveTo(12f, 12f); lineTo(18f, 6f) }
        filled { circle(16.6f, 15.4f, 1.5f) }
    }

    /** The Lucky charm. */
    val Clover = icon("Clover") {
        filled { circle(8.8f, 8.8f, 3.5f); circle(15.2f, 8.8f, 3.5f); circle(8.8f, 15.2f, 3.5f); circle(15.2f, 15.2f, 3.5f) }
        stroked { moveTo(12f, 13f); quadTo(13.5f, 19f, 17.5f, 21.5f) }
    }

    /** More about this: opens the explanation the screen no longer spells out. */
    val Info = icon("Info") {
        stroked { circle(12f, 12f, 9f) }
        filled { circle(12f, 7.7f, 1.35f) }
        stroked(2.5f) { moveTo(12f, 11.2f); verticalLineTo(16.8f) }
    }

    val Gift = icon("Gift") {
        stroked { rect(4.5f, 11f, 19.5f, 20.5f); rect(3f, 7.5f, 21f, 11f); moveTo(12f, 7.5f); verticalLineTo(20.5f) }
        stroked { moveTo(12f, 7.5f); lineTo(8.5f, 3.5f); moveTo(12f, 7.5f); lineTo(15.5f, 3.5f) }
    }

    /** Step back a level (with a refund). */
    val Undo = icon("Undo") {
        stroked(2.4f) {
            moveTo(8.5f, 5f); lineTo(4.5f, 9f); lineTo(8.5f, 13f)
            moveTo(4.5f, 9f); horizontalLineTo(14.5f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14.5f, y1 = 19f)
            horizontalLineTo(9f)
        }
    }

    /** Time left, time limits. */
    val Clock = icon("Clock") {
        stroked { circle(12f, 12f, 9f) }
        stroked(2.4f) { moveTo(12f, 7f); verticalLineTo(12f); lineTo(15.5f, 14.2f) }
    }

    val Friends = icon("Friends") {
        filled { circle(8.5f, 8f, 3.1f); circle(16.8f, 9f, 2.5f) }
        filled { moveTo(2.5f, 20f); curveTo(2.5f, 15.5f, 5f, 13f, 8.5f, 13f); curveTo(12f, 13f, 14.5f, 15.5f, 14.5f, 20f); close() }
        filled { moveTo(16f, 20f); curveTo(16f, 17.5f, 15.5f, 15.8f, 14.6f, 14.4f); curveTo(18.5f, 13f, 21.5f, 15.5f, 21.5f, 20f); close() }
    }

    /** Leaderboards. */
    val Podium = icon("Podium") {
        filled { rect(9f, 8f, 15f, 21f); rect(3f, 13f, 8f, 21f); rect(16f, 15.5f, 21f, 21f) }
        filled { circle(12f, 4.3f, 1.8f) }
    }

    /** Clubs. */
    val Flag = icon("Flag") {
        stroked(2.4f) { moveTo(6f, 3.5f); verticalLineTo(21f) }
        filled { moveTo(7f, 4f); lineTo(20f, 8f); lineTo(7f, 12.5f); close() }
    }

    /** The computer rival. */
    val Robot = icon("Robot") {
        stroked { rect(5f, 8.5f, 19f, 19.5f); moveTo(12f, 8.5f); verticalLineTo(5f); moveTo(9f, 16.5f); horizontalLineTo(15f) }
        filled { circle(12f, 4f, 1.4f); circle(9.2f, 12.8f, 1.5f); circle(14.8f, 12.8f, 1.5f) }
    }

    /** A friend on this phone. */
    val Phone = icon("Phone") {
        stroked { rect(7f, 3f, 17f, 21f) }
        filled { circle(12f, 17.8f, 1.1f) }
    }

    /** Online. */
    val Globe = icon("Globe") {
        stroked { circle(12f, 12f, 9f); moveTo(3f, 12f); horizontalLineTo(21f) }
        stroked { moveTo(12f, 3f); curveTo(7.5f, 7f, 7.5f, 17f, 12f, 21f); moveTo(12f, 3f); curveTo(16.5f, 7f, 16.5f, 17f, 12f, 21f) }
    }

    /** Premium: euro purchases, passes, the Captain's Club. */
    val Crown = icon("Crown") {
        filled { moveTo(3.5f, 18.5f); lineTo(4.5f, 7.5f); lineTo(9f, 12f); lineTo(12f, 5f); lineTo(15f, 12f); lineTo(19.5f, 7.5f); lineTo(20.5f, 18.5f); close() }
    }

    val Check = icon("Check") {
        stroked(2.8f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }

    /** The rope (the Shop's upgrades page). */
    val Rope = icon("Rope") {
        stroked(2.4f) { circle(11f, 11f, 7f) }
        stroked { circle(11f, 11f, 3f); moveTo(16.2f, 16.2f); lineTo(21f, 21f) }
    }

    /** Passes. */
    val Ticket = icon("Ticket") {
        stroked { rect(3f, 7f, 21f, 17f); moveTo(15f, 9.5f); verticalLineTo(10.5f); moveTo(15f, 13.5f); verticalLineTo(14.5f) }
    }

    /** The aquarium. */
    val Tank = icon("Tank") {
        stroked { rect(3f, 6f, 21f, 19.5f); moveTo(3f, 10f); quadTo(6f, 8f, 9f, 10f); quadTo(12f, 12f, 15f, 10f); quadTo(18f, 8f, 21f, 10f) }
        filled { circle(10f, 15f, 1.7f) }
    }

    val Boat = icon("Boat") {
        filled { moveTo(2.5f, 14.5f); horizontalLineTo(21.5f); lineTo(18.5f, 20.5f); horizontalLineTo(5.5f); close() }
        filled { rect(7.5f, 9.5f, 13.5f, 13.5f) }
        stroked { moveTo(16f, 4f); verticalLineTo(13.5f) }
    }

    val Plus = icon("Plus") {
        stroked(2.6f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
    }
}
