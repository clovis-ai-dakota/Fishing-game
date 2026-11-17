package com.deepline.fishing.ui.village

import com.deepline.fishing.Building
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where everything stands in the village, in metres: x to the east, z towards the viewer (the sea
 * is to the south, at the bottom of the screen), y up, the sea's surface at y = 0. One source for
 * the 3D scene, the 2D map (the fallback, and what the UI tests see) and the tests.
 *
 * Laid out for a phone held upright: the buildings climb from the shore to the hill rather than
 * spreading along the coast, so the six of them fit across the screen's width.
 */
object VillageLayout {
    /** A building's place: its footprint's centre, its half sizes, and where its name plate floats. */
    class Spot(val center: Vec3, val half: Vec3, val plate: Vec3) {
        val corners: List<Vec3>
            get() = buildList {
                for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
                    add(Vec3(center.x + sx * half.x, center.y + sy * half.y, center.z + sz * half.z))
                }
            }
    }

    /** Where the land meets the beach (z), and the beach meets the sea. */
    const val SHORE_Z = 4f
    const val BEACH_Z = 7.5f

    /** How far the camera may wander: the village's own grounds. */
    const val MIN_X = -18f
    const val MAX_X = 18f
    const val MIN_Z = -38f
    const val MAX_Z = 24f

    val spots: Map<Building, Spot> = mapOf(
        // The species hall on the hill, the tallest roof in the village.
        Building.DEX to Spot(Vec3(0f, 6.5f, -32f), Vec3(7f, 6.5f, 5f), Vec3(0f, 15.5f, -32f)),
        // The market hall and the rope workshop, either side of the high street.
        Building.SHOP to Spot(Vec3(-12f, 4f, -15f), Vec3(5.5f, 4f, 4f), Vec3(-12f, 10f, -15f)),
        Building.ROPE to Spot(Vec3(12f, 3.5f, -15f), Vec3(6.5f, 3.5f, 4f), Vec3(12f, 9f, -15f)),
        // The glass pavilion by the beach.
        Building.AQUARIUM to Spot(Vec3(-11f, 4f, -1f), Vec3(5.5f, 4f, 5.5f), Vec3(-11f, 10f, -1f)),
        // The pier and your boat moored at its end.
        Building.FISHING to Spot(Vec3(7f, 2.5f, 15f), Vec3(4.5f, 3.5f, 10f), Vec3(9f, 8.5f, 20f)),
        // The round duelling platform out on the water.
        Building.DUEL to Spot(Vec3(-8f, 2.5f, 19f), Vec3(7f, 3f, 7f), Vec3(-8f, 9.5f, 19f)),
    )

    /** The pier, from the beach out to sea (x, z of its two ends), and where the boat lies. */
    val pierFrom = Vec3(6f, 0.9f, SHORE_Z)
    val pierTo = Vec3(6f, 0.9f, 24f)
    val boatAt = Vec3(9.2f, 0f, 20f)

    /** The duelling platform's jetty from the beach. */
    val jettyFrom = Vec3(-8.5f, 0.9f, SHORE_Z)
    val jettyTo = Vec3(-8.5f, 0.9f, 12.5f)
}

/**
 * The village's camera: looking down at the ground at [pitch], from [distance] metres away, at
 * the ground point [targetX], [targetZ]. Dragging moves the target, pinching the distance, both
 * within the village's bounds. [toScreen] and [pick] use the same matrices as the 3D view.
 */
class VillageCamera(
    var targetX: Float = 0f,
    var targetZ: Float = -6f,
    var distance: Float = 130f,
) {
    val pitch = 52f * PI.toFloat() / 180f
    val fovY = 34f * PI.toFloat() / 180f

    /** How close and how far the camera may be (the far limit is set by [fit]). */
    var minDistance = 45f
    var maxDistance = 190f

    val eye: Vec3 get() = Vec3(targetX, distance * sin(pitch), targetZ + distance * cos(pitch))

    fun view(): FloatArray = Mat4.lookAt(eye, Vec3(targetX, 0f, targetZ), Vec3.UP)

    fun projection(aspect: Float): FloatArray = Mat4.perspective(fovY, aspect, max(1f, distance * 0.35f), distance * 3.2f + 120f)

    fun viewProj(widthPx: Float, heightPx: Float): FloatArray = Mat4.multiply(projection(widthPx / heightPx), view())

    /** Where a world point lands, in pixels from the top-left, or null when it is behind the camera. */
    fun toScreen(p: Vec3, widthPx: Float, heightPx: Float, viewProj: FloatArray = viewProj(widthPx, heightPx)): Pair<Float, Float>? {
        // transformPoint divides by w: check w first (behind the eye it would flip).
        val w = viewProj[3] * p.x + viewProj[7] * p.y + viewProj[11] * p.z + viewProj[15]
        if (w <= 0.01f) return null
        val c = Mat4.transformPoint(viewProj, p)
        return (c.x * 0.5f + 0.5f) * widthPx to (0.5f - c.y * 0.5f) * heightPx
    }

    /** The screen rectangle a building covers (its box's corners), or null when it's behind the camera. */
    fun screenRect(b: Building, widthPx: Float, heightPx: Float, viewProj: FloatArray = viewProj(widthPx, heightPx)): FloatArray? {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var bot = -Float.MAX_VALUE
        for (c in VillageLayout.spots.getValue(b).corners) {
            val (x, y) = toScreen(c, widthPx, heightPx, viewProj) ?: return null
            l = min(l, x); r = max(r, x); t = min(t, y); bot = max(bot, y)
        }
        return floatArrayOf(l, t, r, bot)
    }

    /** The building under a tap at ([x], [y]), the nearest one when several overlap there. */
    fun pick(x: Float, y: Float, widthPx: Float, heightPx: Float): Building? {
        val vp = viewProj(widthPx, heightPx)
        val eye = eye
        return Building.entries
            .filter { b -> screenRect(b, widthPx, heightPx, vp)?.let { x in it[0]..it[2] && y in it[1]..it[3] } == true }
            .minByOrNull { b ->
                val c = VillageLayout.spots.getValue(b).center
                (c - eye).length()
            }
    }

    /** How many metres one pixel covers at the ground target (drag speed). */
    fun metresPerPixel(heightPx: Float): Float = 2f * distance * tan(fovY / 2f) / heightPx

    /** A one-finger drag by ([dx], [dy]) pixels: the ground follows the finger. */
    fun drag(dx: Float, dy: Float, heightPx: Float) {
        val m = metresPerPixel(heightPx)
        targetX = (targetX - dx * m).coerceIn(VillageLayout.MIN_X, VillageLayout.MAX_X)
        // Up and down the screen covers more ground: the ground is seen at an angle.
        targetZ = (targetZ - dy * m / sin(pitch)).coerceIn(VillageLayout.MIN_Z, VillageLayout.MAX_Z)
    }

    /** A pinch by [factor] (above 1 zooms in). */
    fun zoom(factor: Float) {
        if (factor <= 0f) return
        distance = (distance / factor).coerceIn(minDistance, maxDistance)
    }

    /**
     * Frames every building: the target at their middle, the distance the closest that keeps all
     * of them inside the screen less [insets] (left, top, right, bottom, in pixels: the HUD). The
     * furthest the player may zoom out is a little beyond it.
     */
    fun fit(widthPx: Float, heightPx: Float, insets: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)) {
        val all = VillageLayout.spots.values.flatMap { it.corners }
        targetX = (all.minOf { it.x } + all.maxOf { it.x }) / 2f
        targetZ = (all.minOf { it.z } + all.maxOf { it.z }) / 2f
        fun fits(d: Float): Boolean {
            distance = d
            val vp = viewProj(widthPx, heightPx)
            return all.all { p ->
                val s = toScreen(p, widthPx, heightPx, vp) ?: return@all false
                s.first >= insets[0] && s.first <= widthPx - insets[2] && s.second >= insets[1] && s.second <= heightPx - insets[3]
            }
        }
        fun closest(): Float {
            var lo = 20f
            var hi = 600f
            repeat(40) {
                val mid = (lo + hi) / 2f
                if (fits(mid)) hi = mid else lo = mid
            }
            return hi
        }
        // The closest distance that fits, then the target slid so the framing is centred between
        // the insets (which moves things in perspective: fit again, a few times).
        var d = closest()
        repeat(4) {
            distance = d
            val vp = viewProj(widthPx, heightPx)
            val ys = all.mapNotNull { toScreen(it, widthPx, heightPx, vp)?.second }
            val freeMid = (insets[1] + heightPx - insets[3]) / 2f
            val usedMid = (ys.min() + ys.max()) / 2f
            if (abs(usedMid - freeMid) > 1f) targetZ += (usedMid - freeMid) * metresPerPixel(heightPx) / sin(pitch)
            d = closest()
        }
        maxDistance = max(d * 1.12f, minDistance + 1f)
        distance = d.coerceIn(minDistance, maxDistance)
    }

    fun copy() = VillageCamera(targetX, targetZ, distance).also {
        it.minDistance = minDistance
        it.maxDistance = maxDistance
    }
}

/** Sine and cosine of an angle in degrees (layout helpers). */
internal fun sinD(deg: Float) = sin(deg * PI.toFloat() / 180f)
internal fun cosD(deg: Float) = cos(deg * PI.toFloat() / 180f)
