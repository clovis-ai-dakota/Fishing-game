package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.Showcase
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The aquarium in 3D, seen from the front: a tank of water over sand, the decor the player owns
 * where they put it, the fish they picked swimming lengths of it and turning at the glass, bubbles
 * rising. In a card it fills its view; full screen ([Camera]) it shows the whole tank on its
 * stand in a dark room, and the player zooms, looks around and moves the decor. Looks only, like
 * the 2D tank (`AquariumTank`), which is the fallback.
 */
class AquariumScene {
    /** A piece of decor in the tank, and where along it it stands (0 left to 1 right, see `Showcase.decorSpot`). */
    data class Piece(val decor: Decor, val spot: Float)

    /**
     * The full-screen view: where the player looks (pinch to zoom, drag the water to look around,
     * double-tap to zoom in or out) and the decor they're moving (drag a piece; let go in the water
     * and it sinks to the sand). The gestures write it on the main thread; [build] reads it, clamps
     * it and leaves what the gestures need (the last frame's projection) on the render thread.
     */
    class Camera {
        @Volatile var zoom = 1f
        /** Whether the view was set yet: it opens filling the screen, down at the sand. */
        @Volatile internal var framed = false
        /** The view's centre, in metres from the middle of the tank's back wall. */
        @Volatile var panX = 0f
        @Volatile var panY = 0f

        /** The last frame's view, for turning a finger into a point in the tank. */
        private class View(val eye: Vec3, val halfTan: Float, val aspect: Float, val w: Float, val h: Float, val tankWidth: Float, val coverZoom: Float, val maxZoom: Float)
        @Volatile private var view: View? = null

        /** The piece under the finger and where it is now (world metres; y above the sand). */
        @Volatile var held: Decor? = null
            private set
        @Volatile private var heldX = 0f
        @Volatile private var heldY = 0f
        private var grabDx = 0f
        private var grabDy = 0f
        /** Pieces let go above the sand: their height and speed as they sink. */
        internal val sinking = ConcurrentHashMap<Decor, FloatArray>()
        /** Where pieces were put this session (until the saved spots catch up). */
        internal val placed = ConcurrentHashMap<Decor, Float>()

        /** Metres of back wall per screen pixel: how far a finger moves the view. */
        private val metersPerPx: Float get() = view?.let { v -> 2f * (v.eye.z + DEPTH / 2f) * v.halfTan / v.h } ?: 0f

        fun zoomBy(factor: Float) {
            zoom = (zoom * factor).coerceIn(1f, view?.maxZoom ?: MAX_ZOOM)
        }

        /** A finger moved the water by ([dxPx], [dyPx]): the tank follows it. */
        fun drag(dxPx: Float, dyPx: Float) {
            panX -= dxPx * metersPerPx
            panY += dyPx * metersPerPx
        }

        /** A double tap: the whole tank on its stand, or back to filling the screen where the finger is. */
        fun toggleZoom(xPx: Float, yPx: Float) {
            val v = view ?: return
            if (zoom > 1.2f) {
                zoom = 1f
                panX = 0f
                panY = 0f
            } else {
                val at = unproject(xPx, yPx, 0f) ?: return
                zoom = v.coverZoom
                panX = at.x
                panY = -HEIGHT
            }
        }

        /** Where the finger at ([xPx], [yPx]) meets the plane z = [z] in the tank, or null before the first frame. */
        internal fun unproject(xPx: Float, yPx: Float, z: Float): Vec3? {
            val v = view ?: return null
            val nx = xPx / v.w * 2f - 1f
            val ny = 1f - yPx / v.h * 2f
            // The camera looks straight down -z: the ray's slope is the lens's.
            val k = v.eye.z - z
            return Vec3(v.eye.x + nx * v.halfTan * v.aspect * k, v.eye.y + ny * v.halfTan * k, z)
        }

        /** The movable piece under the finger, the nearest first; null if none. */
        fun pick(xPx: Float, yPx: Float, pieces: List<Piece>): Decor? {
            val v = view ?: return null
            return pieces.filter { Showcase.isMovable(it.decor) }.sortedByDescending { Showcase.decorDepth(it.decor) }.firstOrNull { p ->
                val (lo, hi) = AquariumModels.bounds(p.decor)
                val z = zOf(p.decor)
                val at = unproject(xPx, yPx, z) ?: return@firstOrNull false
                val x = xOf(p, v.tankWidth)
                val y = heightOf(p.decor) + sandAt(x, z)
                at.x in (x + lo.x - 0.15f)..(x + hi.x + 0.15f) && at.y in (y + lo.y - 0.15f)..(y + hi.y + 0.15f)
            }?.decor
        }

        /** Takes hold of [d] where the finger touched it. */
        fun grab(d: Decor, xPx: Float, yPx: Float, pieces: List<Piece>) {
            val v = view ?: return
            val p = pieces.firstOrNull { it.decor == d } ?: return
            val at = unproject(xPx, yPx, zOf(d)) ?: return
            heldX = xOf(p, v.tankWidth)
            heldY = heightOf(d)
            grabDx = heldX - at.x
            grabDy = heldY + sandAt(heldX, zOf(d)) - at.y
            sinking.remove(d)
            held = d
        }

        /** The finger moved: the piece follows it, inside the tank and never below the sand. */
        fun move(xPx: Float, yPx: Float) {
            val d = held ?: return
            val v = view ?: return
            val at = unproject(xPx, yPx, zOf(d)) ?: return
            val (lo, hi) = AquariumModels.bounds(d)
            val half = v.tankWidth / 2f
            heldX = (at.x + grabDx).coerceIn(-half - lo.x + 0.1f, half - hi.x - 0.1f)
            heldY = (at.y + grabDy - sandAt(heldX, zOf(d))).coerceIn(0f, HEIGHT - hi.y - 0.4f)
        }

        /** Lets go: the piece sinks from where it is; returns it and its new spot along the tank. */
        fun release(): Pair<Decor, Float>? {
            val d = held ?: return null
            val v = view ?: return null
            held = null
            if (heldY > 0.01f) sinking[d] = floatArrayOf(heldY, 0f)
            val spot = ((heldX + v.tankWidth / 2f - MARGIN) / (v.tankWidth - 2f * MARGIN)).coerceIn(0f, 1f)
            placed[d] = spot
            return d to spot
        }

        internal fun xOf(p: Piece, tw: Float): Float =
            if (held == p.decor) heldX else -tw / 2f + MARGIN + (tw - 2f * MARGIN) * (placed[p.decor] ?: p.spot)

        internal fun heightOf(d: Decor): Float = if (held == d) heldY else sinking[d]?.get(0) ?: 0f

        internal fun remember(eye: Vec3, halfTan: Float, aspect: Float, w: Int, h: Int, tw: Float, coverZoom: Float, maxZoom: Float) {
            view = View(eye, halfTan, aspect, w.toFloat(), h.toFloat(), tw, coverZoom, maxZoom)
        }

        /** Sinking pieces fall through the water ([dt] seconds), slowed by it, and settle on the sand. */
        internal fun sink(dt: Float) {
            for ((d, s) in sinking) {
                s[1] = max(s[1] - SINK_GRAVITY * dt, -SINK_SPEED)
                s[0] += s[1] * dt
                if (s[0] <= 0f) sinking.remove(d)
            }
        }

        companion object {
            const val MAX_ZOOM = 6f
        }
    }

    /** The tank: [width] by [HEIGHT] by [DEPTH] metres, the sand at y = 0. */
    private fun width(big: Boolean) = if (big) 12f else 8f
    private var lastT = -1f
    /** The fish living together (see [AquariumSchool]): kept from frame to frame. */
    val school = AquariumSchool()

    fun build(widthPx: Int, heightPx: Int, t: Float, fish: List<FishSpecies>, pieces: List<Piece>, big: Boolean, camera: Camera? = null): Frame {
        val tw = width(big)
        val aspect = widthPx / heightPx.toFloat()
        // A long lens: the front of the tank hardly bigger than its back, like looking through glass.
        val baseFov = 18f * PI.toFloat() / 180f
        val baseHalfTan = tan(baseFov / 2f)
        // How far the back wall must be to fill the view across, or up and down.
        val fitWidth = tw * 0.5f / (baseHalfTan * aspect)
        val fitHeight = HEIGHT * 0.5f / baseHalfTan
        // In a card the back wall fills the view (the closest of the two: the water reaches every
        // edge); full screen the whole tank shows, on its stand, with room around it.
        val cover = min(fitWidth, fitHeight) * 0.98f
        val toBack = if (camera == null) cover else max(fitWidth, fitHeight * 1.5f) * 1.06f
        val distance = toBack - DEPTH / 2f
        // Zooming narrows the lens (the tank keeps its shape); looking around slides the camera
        // along the glass, never past the tank's edges once it fills the view.
        val coverZoom = toBack / cover
        val maxZoom = coverZoom * 2.5f
        // Full screen opens filling the screen (some of the tank off the sides), down at the sand.
        if (camera != null && !camera.framed) {
            camera.framed = true
            camera.zoom = coverZoom
            camera.panY = -HEIGHT
        }
        val zoom = (camera?.zoom ?: 1f).coerceIn(1f, maxZoom)
        val halfTan = baseHalfTan / zoom
        val fov = 2f * kotlin.math.atan(halfTan)
        val seenHalfH = toBack * halfTan
        val seenHalfW = seenHalfH * aspect
        val roomX = max(0f, tw / 2f - seenHalfW)
        val panX = (camera?.panX ?: 0f).coerceIn(-roomX, roomX)
        // A little lower than the back wall's foot too, once zoomed in: the sand in front of the
        // decor is nearer the lens and shows below it.
        val roomY = max(0f, HEIGHT / 2f - seenHalfH)
        val panY = (camera?.panY ?: 0f).coerceIn(-roomY - if (roomY > 0f) FLOOR_ROOM else 0f, roomY)
        val center = Vec3(panX, HEIGHT / 2f + panY, 0f)
        val eye = Vec3(panX, HEIGHT * 0.5f + panY, distance)
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.1f)
        if (camera != null) {
            camera.zoom = zoom
            camera.panX = panX
            camera.panY = panY
            camera.remember(eye, halfTan, aspect, widthPx, heightPx, tw, coverZoom, maxZoom)
            camera.sink(dt)
        }
        lastT = t
        val view = Mat4.lookAt(eye, center, Vec3.UP)
        val proj = Mat4.perspective(fov, aspect, 0.3f, distance + DEPTH * 3f)
        val globals = mapOf(
            "uView" to view,
            "uProj" to proj,
            "uTime" to f(t),
            "uResolution" to f(widthPx.toFloat(), heightPx.toFloat()),
            "uCamPos" to f(eye.x, eye.y, eye.z),
            "uLightDir" to f(0.25f, 1f, 0.55f),
            "uLightColor" to f(1f, 1f, 0.97f),
            "uAmbient" to f(0.45f, 0.62f, 0.78f),
            "uFogColor" to f(0f, 0f, 0f),
            "uWaterTint" to f(0f, 0f, 0f),
            "uFogNear" to f(1e5f),
            "uFogFar" to f(2e5f),
            "uDarkness" to f(0f),
        )
        val items = ArrayList<DrawItem>()
        val painted = model(Mat4.identity()) + mapOf("uMaterial" to f(5f))
        items += DrawItem(Program.TOON, if (big) "aquarium:tank:big" else "aquarium:tank", uniforms = painted)
        // Full screen, the tank stands on its cabinet under a lid with a lamp.
        if (camera != null) items += DrawItem(Program.TOON, if (big) "aquarium:stand:big" else "aquarium:stand", uniforms = painted + mapOf("uGlow" to f(0.6f)))
        // The decor, each where the player put it (or its own spot), sinking if let go in the water.
        for ((i, p) in pieces.withIndex()) {
            val d = p.decor
            val at = if (d == Decor.LANTERN_LIGHTS) {
                Vec3(0f, 0f, zOf(d))
            } else {
                // Standing on the sand, sunk into it a little.
                val x = camera?.xOf(p, tw) ?: xOf(p.spot, tw)
                Vec3(x, sandAt(x, zOf(d)) - 0.04f + (camera?.heightOf(d) ?: 0f), zOf(d))
            }
            items += decorItems(d, at, t, i, tw)
        }
        // The fish, living together: schools, chases, meetings, nibbling at the decor.
        val tops = pieces.filter { it.decor != Decor.LANTERN_LIGHTS }.map { p ->
            val x = camera?.xOf(p, tw) ?: xOf(p.spot, tw)
            Vec3(x, sandAt(x, zOf(p.decor)) + AquariumModels.bounds(p.decor).second.y, zOf(p.decor))
        }
        school.sync(fish, tw, tops)
        school.step(dt)
        for ((i, f) in school.swimmers.withIndex()) {
            items += SceneBuilder.fishItem(f.species, Vec3(f.x, f.y, f.z), f.yaw, f.pitch, f.phase, 1f, false, i.toFloat(), len = f.len)
        }
        // Bubbles rising from the diver's helmet (or from the sand), drawn last, see-through.
        val sprites = SpriteBatch()
        val bubbler = pieces.firstOrNull { it.decor == Decor.BUBBLER }
        val from = bubbler?.let { camera?.xOf(it, tw) ?: xOf(it.spot, tw) } ?: (tw * 0.3f)
        val fromY = if (bubbler != null) 1.2f + (camera?.heightOf(Decor.BUBBLER) ?: 0f) else 0.6f
        for (k in 0 until 14) {
            val p = (t * (0.18f + 0.02f * (k % 3)) + k / 14f) % 1f
            val x = from + 0.25f * sin(p * 9f + k) + if (k % 2 == 0) 0f else 0.15f
            val y = fromY + p * (HEIGHT - fromY - 0.2f)
            sprites.quad(Vec3(x, y, zOf(Decor.BUBBLER) + 0.3f), 0.08f + 0.06f * (k % 3), floatArrayOf(0.9f, 0.97f, 1f, 0.75f * (1f - p * 0.6f)), SceneBuilder.SHAPE_RING)
        }
        // Light falling through the water.
        for (k in 0 until 4) {
            val x = -tw / 2f + tw * (k + 0.5f) / 4f + 0.4f * sin(t * 0.2f + k)
            sprites.quad(Vec3(x, HEIGHT * 0.55f, -DEPTH / 2f + 0.05f), 0.9f, HEIGHT * 1.1f, floatArrayOf(1f, 1f, 0.9f, 0.06f), 7f)
        }
        items += sprites.toItem(Blend.ALPHA)
        // A dark room around the tank full screen; the water's own blue in a card.
        val clear = if (camera != null) f(0.035f, 0.05f, 0.08f) else f(0.02f, 0.3f, 0.52f)
        return Frame(widthPx, heightPx, globals, items, clearColor = clear)
    }

    private fun decorItems(d: Decor, at: Vec3, t: Float, i: Int, tw: Float): List<DrawItem> {
        val items = ArrayList<DrawItem>()
        val glow = f(0.7f + 0.3f * sin(t * 2f + i))
        val m = if (d == Decor.LANTERN_LIGHTS) Mat4.trs(at, scale = Vec3(tw / 8f, 1f, 1f)) else Mat4.trs(at)
        items += DrawItem(Program.TOON, "aquarium:${d.name}", uniforms = model(m) + mapOf("uMaterial" to f(5f), "uGlow" to glow), cullBack = d != Decor.PLANTS)
        for (tex in AquariumModels.leafTextures(d)) {
            items += DrawItem(Program.LEAF, "aquarium:leaves:${d.name}:$tex", uniforms = model(m) + mapOf("uMaterial" to f(5f)), cullBack = false, texture = tex)
        }
        // Living parts on top of the painted ones: kelp that sways, corals in the reef's colours.
        when (d) {
            Decor.PLANTS -> for ((k, off) in listOf(Vec3(-0.45f, 0f, -0.1f), Vec3(0.05f, 0f, -0.25f), Vec3(0.5f, 0f, 0.05f)).withIndex()) {
                val km = Mat4.trs(at + off, scale = Vec3(0.2f, 0.16f + 0.035f * k, 0.2f))
                items += DrawItem(Program.TOON, "kelp:${k + i}", uniforms = model(km) + mapOf("uMaterial" to f(4f), "uSwim" to f(0f, 0f, k + i * 1.3f, 0f)), cullBack = false)
            }
            Decor.CORAL_GARDEN -> for ((k, off) in listOf(Vec3(-0.5f, 0.25f, 0f), Vec3(0.1f, 0.35f, -0.2f), Vec3(0.55f, 0.2f, 0.15f)).withIndex()) {
                val colors = listOf(0xFFF78FD0, 0xFFFF9E5E, 0xFFFF6B8A)
                val cm = Mat4.trs(at + off, rotY = k * 1.1f, scale = Vec3(0.3f, 0.3f, 0.3f))
                items += DrawItem(Program.TOON, "coral:${k + i}", uniforms = model(cm) + mapOf("uMaterial" to f(4f), "uBase" to rgb(colors[k])))
            }
            else -> Unit
        }
        return items
    }

    private fun model(m: FloatArray) = mapOf("uModel" to m, "uNormalMatrix" to Mat4.normalMatrix(m))

    companion object {
        const val HEIGHT = 4.5f
        const val DEPTH = 3f
        /** How much lower than the back wall's foot the full-screen view can look (m). */
        private const val FLOOR_ROOM = 0.6f
        /** Decor keeps this far (m) from the side glass. */
        private const val MARGIN = 0.9f
        /** A piece let go in the water sinks: pulled down this much (m/s²), never faster than this (m/s). */
        private const val SINK_GRAVITY = 6f
        private const val SINK_SPEED = 3f

        /** The sand's height at ([x], [z]): gentle ripples, rising a little towards the back glass. */
        fun sandAt(x: Float, z: Float): Float = 0.12f + 0.08f * sin(x * 1.3f) * cos(z * 1.7f) + 0.1f * (0.5f - z / DEPTH)

        /** Where a piece at [spot] (0..1) stands along a tank [tw] wide. */
        fun xOf(spot: Float, tw: Float) = -tw / 2f + MARGIN + (tw - 2f * MARGIN) * spot

        /** How far back [d] stands (m from the middle of the tank's depth). */
        fun zOf(d: Decor) = Showcase.decorDepth(d) * (DEPTH / 2f - 0.6f)

        /** The tank's own meshes: `aquarium:tank`, `aquarium:tank:big`, the stands, and one per piece of decor. */
        fun mesh(key: String): MeshData = AquariumModels.mesh(key)
    }
}

/** The aquarium's painted meshes (colour per vertex), built once. */
internal object AquariumModels {
    private val cache = HashMap<String, MeshData>()

    fun mesh(key: String): MeshData = synchronized(cache) {
        cache.getOrPut(key) {
            when (key) {
                "aquarium:tank" -> tank(8f)
                "aquarium:tank:big" -> tank(12f)
                "aquarium:stand" -> stand(8f)
                "aquarium:stand:big" -> stand(12f)
                else -> if (key.startsWith("aquarium:leaves:")) {
                    val (d, tex) = key.removePrefix("aquarium:leaves:").split(':', limit = 2)
                    leaves(Decor.valueOf(d), tex)
                } else {
                    decor(Decor.valueOf(key.substringAfter("aquarium:")))
                }
            }
        }
    }

    /** A piece's extent around its foot (its mesh's bounds, kelp and corals included). */
    fun bounds(d: Decor): Pair<Vec3, Vec3> {
        val (lo, hi) = mesh("aquarium:${d.name}").bounds()
        return when (d) {
            Decor.PLANTS -> Vec3(min(lo.x, -0.7f), 0f, lo.z) to Vec3(max(hi.x, 0.75f), max(hi.y, 2.4f), hi.z)
            Decor.CORAL_GARDEN -> Vec3(min(lo.x, -0.8f), 0f, lo.z) to Vec3(max(hi.x, 0.85f), max(hi.y, 1.2f), hi.z)
            else -> Vec3(lo.x, 0f, lo.z) to hi
        }
    }

    private fun rgba(argb: Long) = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)

    private inline fun MeshBuilder.paint(color: FloatArray, block: MeshBuilder.() -> Unit) {
        val from = vertexCount
        block()
        recolor(from, color)
    }

    private fun MeshBuilder.cube(c: Vec3, s: Vec3, color: FloatArray, part: Float = Part.PAINTED) = paint(color) { box(c, s, part) }

    /** The water's back and sides, the sand with gentle dunes, and the tank's frame. */
    private fun tank(tw: Float): MeshData {
        val b = MeshBuilder()
        val h = AquariumScene.HEIGHT
        val d = AquariumScene.DEPTH
        // The back wall: deep blue at the bottom to bright turquoise at the top.
        val bottom = floatArrayOf(0.02f, 0.30f, 0.52f, 1f)
        val top = floatArrayOf(0.30f, 0.80f, 0.92f, 1f)
        val n = Vec3(0f, 0f, 1f)
        val a = b.vertex(Vec3(-tw / 2f, 0f, -d / 2f), n, 0f, 0f, Part.BULB, bottom)
        val c = b.vertex(Vec3(tw / 2f, 0f, -d / 2f), n, 1f, 0f, Part.BULB, bottom)
        val e = b.vertex(Vec3(tw / 2f, h, -d / 2f), n, 1f, 1f, Part.BULB, top)
        val g = b.vertex(Vec3(-tw / 2f, h, -d / 2f), n, 0f, 1f, Part.BULB, top)
        b.quad(a, c, e, g)
        // The side walls, a little darker.
        for (side in listOf(-1f, 1f)) {
            val sn = Vec3(-side, 0f, 0f)
            val x = side * tw / 2f
            val i0 = b.vertex(Vec3(x, 0f, -d / 2f), sn, 0f, 0f, Part.PAINTED, floatArrayOf(0.03f, 0.27f, 0.45f, 1f))
            val i1 = b.vertex(Vec3(x, 0f, d / 2f), sn, 1f, 0f, Part.PAINTED, floatArrayOf(0.03f, 0.27f, 0.45f, 1f))
            val i2 = b.vertex(Vec3(x, h, d / 2f), sn, 1f, 1f, Part.PAINTED, floatArrayOf(0.2f, 0.62f, 0.78f, 1f))
            val i3 = b.vertex(Vec3(x, h, -d / 2f), sn, 0f, 1f, Part.PAINTED, floatArrayOf(0.2f, 0.62f, 0.78f, 1f))
            if (side < 0) b.quad(i0, i1, i2, i3) else b.quad(i1, i0, i3, i2)
        }
        // The sand: a gently rippled floor.
        val sand = rgba(0xFFE9D3A0)
        val cols = 24
        val rows = 6
        val first = b.vertexCount
        for (j in 0..rows) for (k in 0..cols) {
            val x = -tw / 2f + tw * k / cols
            val z = -d / 2f + d * j / rows
            val y = AquariumScene.sandAt(x, z)
            val shade = 0.9f + 0.1f * hash01(k, j, 21)
            b.vertex(Vec3(x, y, z), Vec3.UP, 0f, 0f, Part.PAINTED, floatArrayOf(sand[0] * shade, sand[1] * shade, sand[2] * shade, 1f))
        }
        for (j in 0 until rows) for (k in 0 until cols) {
            val i = first + j * (cols + 1) + k
            b.quad(i, i + cols + 1, i + cols + 2, i + 1)
        }
        b.smoothNormals(first)
        // The frame: dark bars along the front edges and a rim at the top.
        val frame = rgba(0xFF263238)
        b.cube(Vec3(0f, h + 0.08f, d / 2f), Vec3(tw + 0.3f, 0.18f, 0.18f), frame)
        b.cube(Vec3(0f, -0.05f, d / 2f), Vec3(tw + 0.3f, 0.3f, 0.2f), frame)
        for (side in listOf(-1f, 1f)) b.cube(Vec3(side * (tw / 2f + 0.06f), h / 2f, d / 2f), Vec3(0.16f, h + 0.3f, 0.18f), frame)
        return b.build()
    }

    /**
     * The sunken ship: a round wooden hull lofted from bow to stern (weathered darker and greener
     * towards the keel), its deck with planks missing, a stern cabin with dark windows, a broken
     * mast with its fallen yard, railing posts, a hole in its side and an anchor in the sand.
     * Built about 3 m long, bow towards +x; the caller tilts it as it settled.
     */
    private fun wreck(): MeshData {
        val b = MeshBuilder()
        val wood = rgba(0xFF7A5A3C)
        val darkWood = rgba(0xFF4E3826)
        val plank = rgba(0xFF8C6A48)
        val iron = rgba(0xFF3C4448)
        // The hull: deep and round, a pointed bow rising at the front, a broad stern.
        val hull = listOf(
            LoftStation(-1.35f, 0.62f, 0.06f, 0.42f, 0.40f),
            LoftStation(-1.15f, 0.6f, 0.07f, 0.55f, 0.52f),
            LoftStation(-0.6f, 0.58f, 0.07f, 0.6f, 0.6f),
            LoftStation(0.1f, 0.58f, 0.07f, 0.6f, 0.6f),
            LoftStation(0.7f, 0.62f, 0.07f, 0.5f, 0.48f),
            LoftStation(1.15f, 0.7f, 0.06f, 0.34f, 0.3f),
            LoftStation(1.45f, 0.8f, 0.04f, 0.16f, 0.12f),
            LoftStation(1.62f, 0.9f, 0.02f, 0.03f, 0.02f),
        )
        val hullFrom = b.vertexCount
        b.loft(hull, 18, Part.PAINTED)
        b.recolor(hullFrom, wood)
        // Weathered: darker towards the keel, a little green where weed grows, planks in bands.
        b.bakeColors(hullFrom) { p, _, _, c ->
            val low = ((0.62f - p.y) / 0.6f).coerceIn(0f, 1f)
            val band = if (((p.y * 9f) % 1f + 1f) % 1f < 0.5f) 1f else 0.9f
            val k = (1f - 0.35f * low) * band
            c[0] *= k * (1f - 0.12f * low); c[1] *= k; c[2] *= k * (1f - 0.1f * low)
        }
        // A wale along each side at the deck line, and the keel.
        for (side in listOf(-1f, 1f)) {
            val path = (0..10).map { i ->
                val x = -1.3f + 2.85f * i / 10f
                val st = hullAt(hull, x)
                Vec3(x, st.centerY + st.top * 0.3f, side * (st.halfWidth + 0.02f))
            }
            b.paint(darkWood) { tube(path, { 0.045f }, 6, Part.PAINTED) }
        }
        b.paint(darkWood) { tube((0..10).map { i -> val x = -1.3f + 2.9f * i / 10f; val st = hullAt(hull, x); Vec3(x, st.centerY - st.bottom + 0.02f, 0f) }, { 0.05f }, 6, Part.PAINTED) }
        // The deck: planks side by side, two of them gone.
        for (k in 0 until 7) {
            if (k == 2 || k == 5) continue
            val z = -0.42f + 0.14f * k
            b.cube(Vec3(-0.05f, 0.67f, z), Vec3(2.2f - 0.25f * kotlin.math.abs(k - 3), 0.04f, 0.12f), if (k % 2 == 0) plank else wood)
        }
        // Railing posts along both sides, a few broken short.
        for (side in listOf(-1f, 1f)) for (i in 0 until 7) {
            val x = -1.15f + 0.38f * i
            val st = hullAt(hull, x)
            val h = if ((i + (if (side > 0) 1 else 0)) % 3 == 0) 0.08f else 0.22f
            b.cube(Vec3(x, st.centerY + st.top + h / 2f, side * (st.halfWidth - 0.04f)), Vec3(0.05f, h, 0.05f), darkWood)
        }
        // The stern cabin, its roof slipping, two dark windows and a door.
        b.cube(Vec3(-0.95f, 0.92f, 0f), Vec3(0.6f, 0.46f, 0.8f), plank)
        b.paint(darkWood) { append(MeshBuilder().apply { box(Vec3.ZERO, Vec3(0.72f, 0.06f, 0.92f), Part.PAINTED) }.build(), Mat4.trs(Vec3(-0.93f, 1.18f, 0.02f), rotX = 0.12f, rotZ = -0.08f)) }
        for (z in listOf(-0.22f, 0.22f)) b.cube(Vec3(-0.64f, 0.98f, z), Vec3(0.03f, 0.14f, 0.16f), rgba(0xFF10181C))
        b.cube(Vec3(-0.95f, 0.9f, 0.41f), Vec3(0.2f, 0.3f, 0.02f), rgba(0xFF1B1410))
        // The broken mast, leaning, and its yard fallen across the deck.
        b.paint(darkWood) { tube(listOf(Vec3(0.25f, 0.65f, 0f), Vec3(0.42f, 1.5f, 0.06f), Vec3(0.5f, 1.85f, 0.1f)), { u -> 0.075f - 0.025f * u }, 8, Part.PAINTED) }
        b.paint(darkWood) { tube(listOf(Vec3(0.1f, 0.74f, -0.55f), Vec3(0.75f, 0.9f, 0.5f)), { 0.04f }, 6, Part.PAINTED) }
        // A hole in its side, showing the dark inside.
        b.paint(rgba(0xFF120D0A)) { sphere(Vec3(0.35f, 0.42f, 0.56f), 0.2f, Part.PAINTED, 6, 10, scale = Vec3(1.3f, 0.9f, 0.25f)) }
        // The anchor, lying in the sand by the bow on its rope.
        b.paint(iron) {
            tube(listOf(Vec3(1.95f, 0.12f, 0.25f), Vec3(1.95f, 0.75f, 0.25f)), { 0.035f }, 6, Part.PAINTED)
            tube((0..8).map { i -> val a = PI.toFloat() * (0.15f + 0.7f * i / 8f); Vec3(1.95f + 0.28f * cos(a), 0.4f - 0.28f * sin(a), 0.25f) }, { 0.035f }, 6, Part.PAINTED)
            tube((0..10).map { i -> val a = 2f * PI.toFloat() * i / 10f; Vec3(1.95f + 0.08f * cos(a), 0.83f + 0.08f * sin(a), 0.25f) }, { 0.02f }, 5, Part.PAINTED, cap = false)
        }
        b.paint(rgba(0xFF8D7B5A)) { tube(listOf(Vec3(1.55f, 0.85f, 0.05f), Vec3(1.8f, 0.85f, 0.2f), Vec3(1.95f, 0.88f, 0.25f)), { 0.02f }, 5, Part.PAINTED) }
        return b.build()
    }

    /** The hull's cross-section at [x], interpolated between [stations]. */
    private fun hullAt(stations: List<LoftStation>, x: Float): LoftStation {
        val i = stations.indexOfLast { it.x <= x }.coerceIn(0, stations.size - 2)
        val a = stations[i]
        val c = stations[i + 1]
        val u = ((x - a.x) / (c.x - a.x)).coerceIn(0f, 1f)
        fun lerp(p: Float, q: Float) = p + (q - p) * u
        return LoftStation(x, lerp(a.centerY, c.centerY), lerp(a.top, c.top), lerp(a.bottom, c.bottom), lerp(a.halfWidth, c.halfWidth))
    }

    // ---- Decor, built in the same detail as the wreck ----------------------------------------------

    /** A bevelled box: the aquarium is seen up close, so even small bevels show. */
    private fun MeshBuilder.bx(c: Vec3, size: Vec3, color: FloatArray, bevel: Float = 0.04f, part: Float = Part.PAINTED, rotX: Float = 0f, rotY: Float = 0f, rotZ: Float = 0f) =
        bevelBox(Mat4.trs(c, rotX = rotX, rotY = rotY, rotZ = rotZ), size, color, bevel, part, minBevel = 0.005f)

    private fun MeshBuilder.rockAt(at: Vec3, size: Vec3, variant: Int, color: FloatArray, rotY: Float = 0f) =
        paint(color) { append(PropModels.rock(variant), Mat4.trs(at, rotY = rotY, scale = size)) }

    private fun MeshBuilder.blade(foot: Vec3, lean: Vec3, tall: Float, r: Float, color: FloatArray) = paint(color) {
        tube((0..6).map { j -> val u = j / 6f; foot + Vec3(lean.x * u * u, tall * u, lean.z * u * u) }, { u -> r * (1f - 0.85f * u) }, 5, Part.PAINTED)
    }

    /** A CC0 model in a piece of decor (see [ModelAssets]). */
    private class Placed(val model: String, val at: FloatArray)

    /** The CC0 models a piece of decor is made of, around its foot. */
    private fun placed(d: Decor): List<Placed> {
        fun p(model: String, x: Float, z: Float, s: Float, turn: Float, y: Float = 0f) =
            Placed(model, Mat4.trs(Vec3(x, y, z), rotY = turn, scale = Vec3(s, s, s)))
        return when (d) {
            Decor.PLANTS -> listOf(
                p("Plant_1", -0.5f, 0.2f, 1.0f, 0.4f), p("Plant_1", 0.45f, -0.3f, 0.8f, 2.2f),
                p("Plant_7", 0.1f, 0.35f, 1.3f, 1.0f), p("Plant_7", -0.7f, -0.25f, 1.1f, 3.4f),
                p("Grass_Common_Short", -0.2f, -0.35f, 0.75f, 0.2f), p("Grass_Common_Short", 0.7f, 0.25f, 0.6f, 1.9f),
                p("Grass_Common_Short", -0.85f, 0.35f, 0.55f, 4.1f),
            )
            Decor.PEBBLES -> (0 until 16).map { k ->
                val model = listOf("Pebble_Round_1", "Pebble_Round_2", "Pebble_Square_1", "Pebble_Square_2")[k % 4]
                p(model, -0.75f + 1.5f * hash01(k, 2, 31), -0.4f + 0.8f * hash01(k, 3, 31), 0.45f + 0.45f * hash01(k, 1, 31), k * 1.3f)
            }
            Decor.CORAL_GARDEN -> listOf(p("Rock_Medium_2", 0.05f, -0.05f, 0.32f, 0.6f, -0.05f))
            else -> emptyList()
        }
    }

    /** The cut-out textures a piece of decor's leaves use: one `aquarium:leaves:<decor>:<texture>` draw each. */
    fun leafTextures(d: Decor): List<String> = placed(d).flatMap { p -> ModelAssets.model(p.model).leaves.mapNotNull { it.texture } }.distinct()

    private fun leaves(d: Decor, texture: String): MeshData {
        val b = MeshBuilder()
        for (p in placed(d)) for (piece in ModelAssets.model(p.model).leaves) if (piece.texture == texture) b.append(piece.mesh, p.at)
        return b.build()
    }

    private fun decor(d: Decor): MeshData {
        val b = MeshBuilder()
        for (p in placed(d)) for (m in ModelAssets.model(p.model).painted) b.append(m, p.at)
        when (d) {
            Decor.PLANTS -> {
                // A mossy stone and two moss balls; the CC0 plants and grass ([placed]) and the
                // swaying kelp are drawn with it.
                b.rockAt(Vec3(0.05f, 0.08f, -0.05f), Vec3(0.42f, 0.3f, 0.34f), 2, rgba(0xFF7D8A80))
                b.paint(rgba(0xFF2E6B3A)) { sphere(Vec3(0.62f, 0.14f, -0.05f), 0.15f, Part.PAINTED, 6, 8) }
                b.paint(rgba(0xFF356F3F)) { sphere(Vec3(0.8f, 0.11f, 0.15f), 0.11f, Part.PAINTED, 5, 7) }
            }
            Decor.PEBBLES -> {
                // River pebbles (the CC0 ones, [placed]), two scallop shells, a starfish and a conch.
                for ((k, at) in listOf(Vec3(-0.35f, 0.04f, 0.32f), Vec3(0.5f, 0.04f, -0.2f)).withIndex()) {
                    val turn = 0.6f + 1.9f * k
                    b.paint(rgba(0xFFF6D6C2)) { append(MeshBuilder().apply { sphere(Vec3.ZERO, 1f, Part.PAINTED, 5, 10, Vec3(0.2f, 0.06f, 0.18f)) }.build(), Mat4.trs(at, rotY = turn)) }
                    for (j in 0 until 7) {
                        val a = (j / 6f - 0.5f) * 2.2f + turn
                        b.paint(rgba(0xFFE8B4A0)) { tube(listOf(at + Vec3(sin(turn) * -0.12f, 0.05f, cos(turn) * -0.12f), at + Vec3(sin(a) * 0.17f, 0.065f, cos(a) * 0.17f)), { 0.018f }, 4, Part.PAINTED) }
                    }
                }
                val star = Vec3(0.15f, 0.05f, 0.28f)
                for (k in 0 until 5) {
                    val a = k / 5f * 2f * PI.toFloat()
                    b.paint(rgba(0xFFFF8A4C)) { tube(listOf(star, star + Vec3(cos(a) * 0.22f, 0f, sin(a) * 0.22f)), { u -> 0.06f * (1f - 0.75f * u) }, 6, Part.PAINTED) }
                    b.paint(rgba(0xFFFFD08A)) { sphere(star + Vec3(cos(a) * 0.1f, 0.045f, sin(a) * 0.1f), 0.018f, Part.PAINTED, 3, 4) }
                }
                b.paint(rgba(0xFFF3C6B6)) { append(MeshBuilder().apply { lathe(listOf(0.01f to 0.42f, 0.09f to 0.3f, 0.15f to 0.12f, 0.06f to 0f), 10, Part.PAINTED) }.build(), Mat4.trs(Vec3(-0.65f, 0.1f, -0.25f), rotZ = 1.35f)) }
            }
            Decor.SAND_CASTLE -> {
                val sand = rgba(0xFFE0C287)
                val light = rgba(0xFFEED8A8)
                val dark = rgba(0xFF3A2A20)
                b.paint(sand) { lathe(listOf(0.95f to 0.12f, 1.05f to -0.05f), 14, Part.PAINTED) }
                // The keep, with battlements, a gate and arrow slits.
                b.bx(Vec3(0f, 0.55f, 0f), Vec3(1.2f, 0.85f, 0.7f), sand, 0.05f)
                for (k in 0 until 6) b.bx(Vec3(-0.5f + 0.2f * k, 1.05f, 0.31f), Vec3(0.11f, 0.15f, 0.1f), light, 0.02f)
                for (k in 0 until 6) b.bx(Vec3(-0.5f + 0.2f * k, 1.05f, -0.31f), Vec3(0.11f, 0.15f, 0.1f), light, 0.02f)
                b.paint(dark) { append(MeshBuilder().apply { extrude(listOf(-0.16f to 0f, 0.16f to 0f, 0.16f to 0.3f, 0.11f to 0.38f, 0f to 0.42f, -0.11f to 0.38f, -0.16f to 0.3f), 0.05f, Part.PAINTED) }.build(), Mat4.translation(0f, 0.13f, 0.34f)) }
                for (x in listOf(-0.35f, 0.35f)) b.bx(Vec3(x, 0.72f, 0.355f), Vec3(0.06f, 0.18f, 0.02f), dark, 0.01f)
                // Three towers: two in front, a tall one behind, each with battlements, a cone roof and a flag.
                for ((k, t) in listOf(Triple(-0.62f, 0.25f, 1.25f), Triple(0.62f, 0.25f, 1.25f), Triple(0.15f, -0.25f, 1.75f)).withIndex()) {
                    val (x, z, tall) = t
                    val r = if (k == 2) 0.26f else 0.22f
                    b.paint(sand) { lathe(listOf(r to tall, r + 0.03f to 0f), 12, Part.PAINTED, Vec3(x, 0f, z)) }
                    for (j in 0 until 8) {
                        val a = j / 8f * 2f * PI.toFloat()
                        b.bx(Vec3(x + cos(a) * (r + 0.01f), tall + 0.07f, z + sin(a) * (r + 0.01f)), Vec3(0.08f, 0.13f, 0.08f), light, 0.015f, rotY = -a)
                    }
                    b.paint(rgba(0xFFD9A066)) { lathe(listOf(0.01f to tall + 0.55f, r + 0.06f to tall + 0.12f), 12, Part.PAINTED, Vec3(x, 0f, z)) }
                    b.bx(Vec3(x, tall * 0.6f, z + r), Vec3(0.05f, 0.16f, 0.03f), dark, 0.01f)
                    b.paint(rgba(0xFF6D4C41)) { tube(listOf(Vec3(x, tall + 0.5f, z), Vec3(x, tall + 0.85f, z)), { 0.012f }, 4, Part.PAINTED) }
                    val flag = MeshBuilder().apply { extrude(listOf(0f to 0f, 0.22f to -0.06f, 0f to -0.12f), 0.012f, Part.PAINTED) }.build()
                    b.paint(rgba(0xFFE53935)) { append(flag, Mat4.translation(x, tall + 0.84f, z)) }
                }
                // Shells pressed into the walls.
                for ((x, y) in listOf(-0.45f to 0.4f, 0.4f to 0.5f, -0.1f to 0.82f)) b.paint(rgba(0xFFFFF4EC)) { sphere(Vec3(x, y, 0.36f), 0.045f, Part.PAINTED, 3, 6) }
            }
            Decor.BUBBLER -> {
                // A diver's brass helmet on its shoulder plate, on a rock: a round front window with
                // a grille, side ports, bolts, a valve on top and the air hose snaking off.
                val brass = rgba(0xFFC9A227)
                val darkBrass = rgba(0xFF8C6D1A)
                val rubber = rgba(0xFF3B3B3B)
                b.rockAt(Vec3(0f, 0.12f, 0f), Vec3(0.55f, 0.28f, 0.45f), 3, rgba(0xFF8B8F95))
                b.paint(darkBrass) { lathe(listOf(0.3f to 0.75f, 0.52f to 0.52f, 0.56f to 0.4f), 18, Part.PAINTED) }
                b.paint(brass) { sphere(Vec3(0f, 1.0f, 0f), 0.42f, Part.PAINTED, 10, 16) }
                for (k in 0 until 10) {
                    val a = k / 10f * 2f * PI.toFloat()
                    b.paint(darkBrass) { sphere(Vec3(cos(a) * 0.45f, 0.62f, sin(a) * 0.38f), 0.035f, Part.PAINTED, 3, 5) }
                }
                fun port(c: Vec3, n: Vec3, r: Float) {
                    val side = Vec3(n.z, 0f, -n.x).normalized()
                    val ring = (0..16).map { j -> val a = j / 16f * 2f * PI.toFloat(); c + side * (cos(a) * r) + Vec3(0f, sin(a) * r, 0f) }
                    b.paint(darkBrass) { tube(ring, { 0.04f }, 6, Part.PAINTED, cap = false) }
                    b.paint(rgba(0xFF9FD8E8)) { append(MeshBuilder().apply { sphere(Vec3.ZERO, 1f, Part.GLASS, 5, 10, Vec3(r * 0.95f, r * 0.95f, 0.04f)) }.build(), Mat4.trs(c - n * 0.02f, rotY = kotlin.math.atan2(n.x, n.z))) }
                }
                port(Vec3(0f, 1.0f, 0.4f), Vec3(0f, 0f, 1f), 0.2f)
                for (k in 0 until 3) b.paint(darkBrass) { tube(listOf(Vec3(-0.17f, 0.9f + 0.1f * k, 0.45f), Vec3(0.17f, 0.9f + 0.1f * k, 0.45f)), { 0.012f }, 4, Part.PAINTED) }
                for (sx in listOf(-1f, 1f)) port(Vec3(sx * 0.39f, 1.02f, 0.08f), Vec3(sx, 0f, 0.2f).normalized(), 0.11f)
                b.paint(darkBrass) { lathe(listOf(0.02f to 1.58f, 0.09f to 1.5f, 0.09f to 1.38f), 10, Part.PAINTED) }
                b.paint(rubber) {
                    tube(listOf(Vec3(0f, 1.2f, -0.38f), Vec3(-0.2f, 1.25f, -0.6f), Vec3(-0.55f, 0.9f, -0.6f), Vec3(-0.75f, 0.35f, -0.4f), Vec3(-0.95f, 0.08f, -0.1f)), { 0.055f }, 7, Part.PAINTED)
                }
            }
            Decor.TREASURE_CHEST -> {
                val wood = rgba(0xFF8D5A3B)
                val darkWood = rgba(0xFF5E3A24)
                val iron = rgba(0xFF4A4A4F)
                val gold = rgba(0xFFFFC83D)
                b.bx(Vec3(0f, 0.3f, 0f), Vec3(1.0f, 0.56f, 0.62f), wood, 0.04f)
                for (k in 0 until 3) b.bx(Vec3(0f, 0.12f + 0.18f * k, 0.315f), Vec3(0.98f, 0.02f, 0.01f), darkWood, 0.005f)
                for (x in listOf(-0.32f, 0.32f)) {
                    b.bx(Vec3(x, 0.3f, 0.32f), Vec3(0.08f, 0.58f, 0.02f), iron, 0.01f)
                    b.bx(Vec3(x, 0.3f, -0.32f), Vec3(0.08f, 0.58f, 0.02f), iron, 0.01f)
                }
                for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) b.bx(Vec3(sx * 0.47f, 0.06f, sz * 0.28f), Vec3(0.1f, 0.12f, 0.1f), iron, 0.015f)
                b.bx(Vec3(0f, 0.48f, 0.325f), Vec3(0.16f, 0.18f, 0.02f), gold, 0.01f)
                b.bx(Vec3(0f, 0.46f, 0.337f), Vec3(0.03f, 0.07f, 0.01f), rgba(0xFF1A1208), 0.003f)
                // The lid, thrown back on its hinge: a half barrel with its iron bands.
                val lid = MeshBuilder().apply {
                    loft(listOf(LoftStation(-0.5f, 0f, 0.3f, 0.01f, 0.31f), LoftStation(0.5f, 0f, 0.3f, 0.01f, 0.31f)), 14, Part.PAINTED)
                }.build()
                val hinge = Mat4.trs(Vec3(0f, 0.58f, -0.31f), rotX = -1.9f)
                b.paint(wood) { append(lid, Mat4.multiply(hinge, Mat4.translation(0f, 0f, 0.31f))) }
                for (x in listOf(-0.32f, 0.32f)) {
                    val band = MeshBuilder().apply { tube((0..10).map { j -> val a = PI.toFloat() * j / 10f; Vec3(x, sin(a) * 0.315f, cos(a) * 0.315f) }, { 0.025f }, 4, Part.PAINTED, cap = false) }.build()
                    b.paint(iron) { append(band, Mat4.multiply(hinge, Mat4.translation(0f, 0f, 0.31f))) }
                }
                // Gold heaped inside and spilling out, with three glowing gems.
                for (k in 0 until 26) {
                    val x = -0.4f + 0.8f * hash01(k, 1, 57)
                    val z = -0.22f + 0.44f * hash01(k, 2, 57)
                    val y = 0.58f + 0.1f * (1f - kotlin.math.abs(x) / 0.45f) * hash01(k, 3, 57)
                    b.paint(gold) { append(MeshBuilder().apply { tube(listOf(Vec3(0f, -0.012f, 0f), Vec3(0f, 0.012f, 0f)), { 0.07f }, 10, Part.PAINTED) }.build(), Mat4.trs(Vec3(x, y, z), rotX = 0.4f * hash01(k, 4, 57), rotZ = 0.4f * hash01(k, 5, 57))) }
                }
                for (k in 0 until 5) b.paint(gold) { append(MeshBuilder().apply { tube(listOf(Vec3(0f, -0.012f, 0f), Vec3(0f, 0.012f, 0f)), { 0.07f }, 10, Part.PAINTED) }.build(), Mat4.trs(Vec3(-0.3f + 0.2f * k, 0.02f, 0.5f + 0.08f * (k % 2)))) }
                for ((k, c) in listOf(0xFFE53958, 0xFF35D07F, 0xFF4FA3FF).withIndex()) b.paint(rgba(c)) { sphere(Vec3(-0.2f + 0.2f * k, 0.7f, 0.05f * (k - 1)), 0.065f, Part.BULB, 4, 6) }
            }
            Decor.CORAL_GARDEN -> {
                // A reef on a rock: a brain coral, tube anemones, a purple sea fan and an urchin
                // (the branching corals are drawn on top in the reef's colours).
                b.rockAt(Vec3(0f, 0.12f, 0f), Vec3(0.75f, 0.32f, 0.5f), 1, rgba(0xFF8E7F95))
                b.rockAt(Vec3(0.55f, 0.08f, 0.2f), Vec3(0.3f, 0.2f, 0.26f), 6, rgba(0xFF7F7389))
                val brain = Vec3(-0.45f, 0.32f, 0.22f)
                b.paint(rgba(0xFFF2A65A)) { sphere(brain, 0.24f, Part.PAINTED, 7, 12, Vec3(1f, 0.75f, 1f)) }
                for (k in 0 until 5) {
                    val a = k * 0.6f
                    b.paint(rgba(0xFFD9824A)) { tube((0..8).map { j -> val u = j / 8f; brain + Vec3((u - 0.5f) * 0.4f, 0.17f + 0.02f * sin(u * 9f + a), (k - 2) * 0.07f + 0.04f * sin(u * 12f + a)) }, { 0.018f }, 4, Part.PAINTED) }
                }
                for (k in 0 until 7) {
                    val a = k / 7f * 2f * PI.toFloat()
                    val foot = Vec3(0.35f + cos(a) * 0.14f, 0.28f, -0.1f + sin(a) * 0.1f)
                    val tall = 0.25f + 0.2f * hash01(k, 7, 61)
                    b.paint(rgba(0xFFFFB13B)) { lathe(listOf(0.035f to tall, 0.045f to 0f), 6, Part.PAINTED, foot) }
                    b.paint(rgba(0xFFFFE07A)) { sphere(foot + Vec3(0f, tall, 0f), 0.045f, Part.BULB, 3, 6) }
                }
                // The sea fan: branches in one plane.
                val fan = Vec3(0f, 0.35f, -0.3f)
                for (k in 0 until 9) {
                    val a = (k / 8f - 0.5f) * 1.6f
                    val tip = fan + Vec3(sin(a) * 0.55f, cos(a) * 0.7f, 0f)
                    b.paint(rgba(0xFF9C5BD6)) { tube(listOf(fan, (fan + tip) * 0.5f + Vec3(0.05f * sin(k.toFloat()), 0f, 0f), tip), { u -> 0.03f * (1f - 0.7f * u) }, 4, Part.PAINTED) }
                }
                val urchin = Vec3(0.75f, 0.22f, 0.3f)
                b.paint(rgba(0xFF3D2350)) { sphere(urchin, 0.1f, Part.PAINTED, 5, 8) }
                for (k in 0 until 14) {
                    val a = k * 2.4f
                    val e = (k % 5) * 0.35f
                    val dir = Vec3(cos(a) * cos(e), sin(e) + 0.2f, sin(a) * cos(e)).normalized()
                    b.paint(rgba(0xFF2A173A)) { tube(listOf(urchin, urchin + dir * 0.24f), { u -> 0.012f * (1f - u) + 0.002f }, 3, Part.PAINTED) }
                }
            }
            Decor.LANTERN_LIGHTS -> {
                // Paper lanterns on a sagging string along the top of the back glass (built for the
                // small tank; the scene stretches it across a big one).
                val top = AquariumScene.HEIGHT - 0.12f
                fun at(u: Float) = Vec3(-3.7f + 7.4f * u, top - 0.35f * sin(u * PI.toFloat()), 0f)
                b.paint(rgba(0xFF37474F)) { tube((0..20).map { at(it / 20f) }, { 0.02f }, 4, Part.PAINTED, cap = false) }
                for (k in 0 until 9) {
                    val p = at((k + 0.5f) / 9f) - Vec3(0f, 0.3f, 0f)
                    val color = listOf(rgba(0xFFFFD54F), rgba(0xFFFF8A65), rgba(0xFF81D4FA), rgba(0xFFF48FB1))[k % 4]
                    b.paint(color) { lathe(listOf(0.06f to 0.17f, 0.15f to 0.08f, 0.16f to -0.02f, 0.13f to -0.12f, 0.05f to -0.18f), 10, Part.BULB, p) }
                    b.paint(rgba(0xFF3E2723)) {
                        lathe(listOf(0.02f to 0.22f, 0.07f to 0.16f), 8, Part.PAINTED, p)
                        lathe(listOf(0.06f to -0.17f, 0.02f to -0.23f), 8, Part.PAINTED, p)
                        tube(listOf(p + Vec3(0f, 0.22f, 0f), p + Vec3(0f, 0.3f, 0f)), { 0.01f }, 3, Part.PAINTED)
                    }
                }
            }
            Decor.SUNKEN_SHIP -> b.append(wreck(), Mat4.trs(Vec3(-0.1f, -0.08f, 0f), rotZ = 0.1f, rotX = 0.16f, scale = Vec3(0.85f, 0.85f, 0.85f)))
        }
        return b.build()
    }

    /**
     * What the tank stands on, full screen: a wooden cabinet with two panelled doors and brass
     * handles, and a lid over the tank with a lamp strip glowing under its front edge.
     */
    private fun stand(tw: Float): MeshData {
        val b = MeshBuilder()
        val h = AquariumScene.HEIGHT
        val d = AquariumScene.DEPTH
        val wood = rgba(0xFF4A3426)
        val panel = rgba(0xFF5E4331)
        val brass = rgba(0xFFC9A227)
        b.bx(Vec3(0f, -1.25f, 0f), Vec3(tw + 0.7f, 2.0f, d + 0.5f), wood, 0.08f)
        b.bx(Vec3(0f, -0.28f, 0f), Vec3(tw + 0.9f, 0.16f, d + 0.7f), panel, 0.05f)
        b.bx(Vec3(0f, -2.2f, 0f), Vec3(tw + 0.5f, 0.12f, d + 0.3f), rgba(0xFF2E2019), 0.03f)
        val doors = if (tw > 10f) 3 else 2
        val dw = (tw + 0.2f) / doors
        for (k in 0 until doors) {
            val x = -(tw + 0.2f) / 2f + dw * (k + 0.5f)
            b.bx(Vec3(x, -1.25f, d / 2f + 0.26f), Vec3(dw - 0.25f, 1.55f, 0.04f), panel, 0.03f)
            b.bx(Vec3(x, -1.25f, d / 2f + 0.29f), Vec3(dw - 0.65f, 1.1f, 0.03f), wood, 0.02f)
            b.paint(brass) { sphere(Vec3(x + (dw / 2f - 0.35f) * (if (k % 2 == 0) 1f else -1f), -1.2f, d / 2f + 0.34f), 0.06f, Part.PAINTED, 4, 8) }
        }
        // The lid and its lamp.
        b.bx(Vec3(0f, h + 0.42f, 0f), Vec3(tw + 0.5f, 0.32f, d + 0.4f), rgba(0xFF263238), 0.06f)
        b.bx(Vec3(0f, h + 0.24f, d / 2f + 0.1f), Vec3(tw - 0.2f, 0.05f, 0.12f), rgba(0xFFFFF3D6), 0.01f, Part.BULB)
        return b.build()
    }

}
