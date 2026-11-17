package com.deepline.fishing.ui

import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The still scene of the Fish menu and the Skins previews: the boat at the surface and the hook
 * hanging 5 m down under it, never steered. The 2D and 3D renderers both draw this session, so
 * the boat, line and hook are at the same place in either (and where [FishMenuHitAreas] says).
 */
fun boatPreviewSession(): CastSession =
    CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList())).also {
        while (it.hookDepth < 5f) it.update(1f / 60f, null)
    }

/** What a tap on the Fish menu's scene touched. The sea is nothing. */
enum class FishMenuTarget { BOAT, LINE, HOOK }

/** A rectangle in pixels, from the top-left of the scene. */
data class HitRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
    operator fun contains(p: Pair<Float, Float>) = p.first in left..right && p.second in top..bottom

    /** Grown around its centre to at least [min] on each side (a finger-sized target). */
    fun atLeast(min: Float): HitRect {
        val gx = max(0f, (min - width) / 2f)
        val gy = max(0f, (min - height) / 2f)
        return HitRect(left - gx, top - gy, right + gx, bottom + gy)
    }
}

/**
 * Where the boat, the line and the hook of [boatPreviewSession] are on a scene of [widthPx] x
 * [heightPx] pixels whose view starts [cameraTop] metres above the surface: the same formulas as
 * the 2D renderer (`drawCastSceneContent`, `drawBoatAndRod`, `hullPath`, `drawHook`), which the 3D
 * camera matches on the play plane. [minTouchPx] is the smallest target (48 dp).
 */
class FishMenuHitAreas(
    val boat: HitRect,
    val hook: HitRect,
    /** The line from the rod tip, through where it enters the water, down to the hook's eye. */
    val line: List<Pair<Float, Float>>,
    private val lineHalfWidth: Float,
) {
    /** A point of the line half-way down the water, for its accessibility node. */
    val lineMiddle: Pair<Float, Float> get() = line[line.size * 2 / 3]

    /** What a tap at ([x], [y]) touches: the hook first (it is small and sits on the line), then the boat. */
    fun targetAt(x: Float, y: Float): FishMenuTarget? = when {
        (x to y) in hook -> FishMenuTarget.HOOK
        (x to y) in boat -> FishMenuTarget.BOAT
        distanceToLine(x, y) <= lineHalfWidth -> FishMenuTarget.LINE
        else -> null
    }

    private fun distanceToLine(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val (ax, ay) = line[i]
            val (bx, by) = line[i + 1]
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
            val px = ax + t * dx - x
            val py = ay + t * dy - y
            best = min(best, sqrt(px * px + py * py))
        }
        return best
    }

    companion object {
        fun of(widthPx: Float, heightPx: Float, cameraTop: Float, minTouchPx: Float, session: CastSession = SESSION): FishMenuHitAreas {
            val scale = widthPx / GameConfig.WORLD_WIDTH
            fun sx(x: Float) = x * scale
            fun sy(depth: Float) = (depth - cameraTop) * scale
            val surfaceY = sy(0f)
            val cx = sx(GameConfig.WORLD_WIDTH / 2f)
            // The rod (still: no load on the line), as drawBoatAndRod.
            val rodTipX = cx + (1.9f + 0.1f * 0.6f) * scale
            val rodTipY = surfaceY - (2.9f - 0.1f) * scale
            // The boat: hull from the bow to the transom, keel to the top of a custom mast or
            // sail (5.2 m), and out to the rod's tip.
            val boat = HitRect(cx - 2.8f * scale, surfaceY - 5.2f * scale, max(cx + 2.5f * scale, rodTipX + 0.2f * scale), surfaceY + 0.45f * scale)
            // The hook: the bend at the hook's position, the eye 1.4 s above it (drawHook).
            val s = GameConfig.HOOK_RADIUS * scale
            val bendX = sx(session.hookX)
            val bendY = sy(session.hookDepth)
            val hook = HitRect(bendX - 0.2f * s, bendY - 1.57f * s, bendX + 0.95f * s, bendY + 0.75f * s).atLeast(minTouchPx)
            // The line: rod tip, into the water a little towards the hook, then a curve to the eye.
            val eyeX = bendX
            val eyeY = bendY - 1.4f * s
            val entryX = rodTipX + (eyeX - rodTipX) * 0.1f
            val entryY = surfaceY
            val trail = (-session.hookVelocityX * 0.12f).coerceIn(-1.5f, 1.5f) * scale
            val ctrlX = eyeX + trail
            val ctrlY = entryY + (eyeY - entryY) * 0.6f
            val line = ArrayList<Pair<Float, Float>>()
            line += rodTipX to rodTipY
            line += entryX to entryY
            for (i in 1..24) {
                val t = i / 24f
                val u = 1f - t
                line += (u * u * entryX + 2 * u * t * ctrlX + t * t * eyeX) to (u * u * entryY + 2 * u * t * ctrlY + t * t * eyeY)
            }
            return FishMenuHitAreas(boat, hook, line, minTouchPx / 2f)
        }

        private val SESSION by lazy { boatPreviewSession() }
    }
}
