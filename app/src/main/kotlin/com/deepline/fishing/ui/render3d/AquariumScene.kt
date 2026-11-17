package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.FishSpecies
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * The aquarium in 3D, seen from the front: a tank of water over sand, the decor the player owns,
 * the fish they picked swimming lengths of it and turning at the glass, bubbles rising. Fills
 * its view whatever its shape (a card in the aquarium screen, the whole phone held sideways).
 * Looks only, like the 2D tank (`AquariumTank`), which is the fallback.
 */
class AquariumScene {
    /** The tank: [width] by [HEIGHT] by [DEPTH] metres, the sand at y = 0. */
    private fun width(big: Boolean) = if (big) 12f else 8f

    fun build(widthPx: Int, heightPx: Int, t: Float, fish: List<FishSpecies>, decor: List<Decor>, big: Boolean): Frame {
        val tw = width(big)
        val aspect = widthPx / heightPx.toFloat()
        val fov = 32f * PI.toFloat() / 180f
        val halfTan = tan(fov / 2f)
        // Fit the tank's front: its width and its height, with a little room around.
        val distance = max(tw * 0.5f * 1.04f / (halfTan * aspect), HEIGHT * 0.5f * 1.06f / halfTan) + DEPTH / 2f
        val center = Vec3(0f, HEIGHT / 2f, 0f)
        val eye = Vec3(0f, HEIGHT * 0.55f, distance)
        val view = Mat4.lookAt(eye, center, Vec3.UP)
        val proj = Mat4.perspective(fov, aspect, max(0.5f, distance - DEPTH * 2f), distance + DEPTH * 3f)
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
        // The decor, spread over the sand in the order it was bought.
        val slots = decor.size.coerceAtLeast(1)
        for ((i, d) in decor.withIndex()) {
            val x = -tw / 2f + tw * (i + 0.5f) / slots
            items += decorItems(d, Vec3(x, 0f, -0.35f + 0.5f * ((i % 2) - 0.5f)), t, i, tw)
        }
        // The fish: each swims lengths at its own depth and pace, turning at the ends.
        for ((i, s) in fish.withIndex()) {
            val lane = 0.9f + (HEIGHT - 1.6f) * ((i * 0.37f + 0.15f) % 1f)
            val speed = 0.22f + 0.05f * (i % 4)
            val phase = t * speed + i * 1.7f
            val reach = tw / 2f - 0.9f
            val x = reach * sin(phase)
            val goingRight = cos(phase)
            // Turn smoothly through facing the viewer as it slows at the glass.
            val yaw = SceneBuilder.FACING_LEFT + (SceneBuilder.FACING_RIGHT - SceneBuilder.FACING_LEFT) * (0.5f + 0.5f * goingRight.coerceIn(-1f, 1f))
            val z = -0.7f + 1.4f * ((i * 0.53f) % 1f)
            val len = (0.55f + 0.18f * ln(1f + s.bodyLengthM)).coerceIn(0.5f, 1.1f)
            val bob = 0.12f * sin(t * 0.9f + i)
            items += SceneBuilder.fishItem(s, Vec3(x, lane + bob, z), yaw, 0f, t * 6f + i, 1f, false, i.toFloat(), len = len)
        }
        // Bubbles rising from the bubbler (or from the sand), drawn last, see-through.
        val sprites = SpriteBatch()
        val from = decor.indexOf(Decor.BUBBLER).takeIf { it >= 0 }?.let { -tw / 2f + tw * (it + 0.5f) / slots } ?: (tw * 0.3f)
        for (k in 0 until 14) {
            val p = (t * (0.18f + 0.02f * (k % 3)) + k / 14f) % 1f
            val x = from + 0.25f * sin(p * 9f + k) + if (k % 2 == 0) 0f else 0.15f
            sprites.quad(Vec3(x, 0.6f + p * (HEIGHT - 0.8f), 0.3f), 0.08f + 0.06f * (k % 3), floatArrayOf(0.9f, 0.97f, 1f, 0.75f * (1f - p * 0.6f)), SceneBuilder.SHAPE_RING)
        }
        // Light falling through the water.
        for (k in 0 until 4) {
            val x = -tw / 2f + tw * (k + 0.5f) / 4f + 0.4f * sin(t * 0.2f + k)
            sprites.quad(Vec3(x, HEIGHT * 0.55f, -DEPTH / 2f + 0.05f), 0.9f, HEIGHT * 1.1f, floatArrayOf(1f, 1f, 0.9f, 0.06f), 7f)
        }
        items += sprites.toItem(Blend.ALPHA)
        return Frame(widthPx, heightPx, globals, items)
    }

    private fun decorItems(d: Decor, at: Vec3, t: Float, i: Int, tw: Float): List<DrawItem> = when (d) {
        Decor.PLANTS -> (0 until 3).map { k ->
            val m = Mat4.trs(at + Vec3(-0.5f + 0.5f * k, 0f, -0.2f * k), scale = Vec3(0.2f, 0.17f + 0.03f * k, 0.2f))
            DrawItem(Program.TOON, "kelp:${k + i}", uniforms = model(m) + mapOf("uMaterial" to f(4f), "uSwim" to f(0f, 0f, k + i * 1.3f, 0f)), cullBack = false)
        }
        Decor.CORAL_GARDEN -> (0 until 3).map { k ->
            val colors = listOf(0xFFF78FD0, 0xFFFF9E5E, 0xFFFF6B8A)
            val m = Mat4.trs(at + Vec3(-0.45f + 0.45f * k, 0f, 0.1f * k), rotY = k * 1.1f, scale = Vec3(0.32f, 0.32f, 0.32f))
            DrawItem(Program.TOON, "coral:${k + i}", uniforms = model(m) + mapOf("uMaterial" to f(4f), "uBase" to rgb(colors[k])))
        }
        else -> listOf(DrawItem(Program.TOON, "aquarium:${d.name}", uniforms = model(Mat4.trs(at)) + mapOf("uMaterial" to f(5f), "uGlow" to f(0.7f + 0.3f * sin(t * 2f + i)))))
    }

    private fun model(m: FloatArray) = mapOf("uModel" to m, "uNormalMatrix" to Mat4.normalMatrix(m))

    companion object {
        const val HEIGHT = 4.5f
        const val DEPTH = 3f

        /** The tank's own meshes: `aquarium:tank`, `aquarium:tank:big` and one per piece of decor. */
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
                else -> decor(Decor.valueOf(key.substringAfter("aquarium:")))
            }
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
            val y = 0.12f + 0.08f * sin(x * 1.3f) * cos(z * 1.7f) + 0.1f * (1f - j / rows.toFloat())
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

    private fun decor(d: Decor): MeshData {
        val b = MeshBuilder()
        when (d) {
            Decor.PEBBLES -> for (k in 0 until 12) {
                val color = listOf(rgba(0xFF9E9E9E), rgba(0xFFBCAAA4), rgba(0xFF8D99A6), rgba(0xFFD7CCC8))[k % 4]
                val s = 0.1f + 0.08f * hash01(k, 1, 31)
                b.paint(color) { append(PropModels.rock(k % 8), Mat4.trs(Vec3(-0.7f + 1.4f * hash01(k, 2, 31), 0.15f, -0.4f + 0.8f * hash01(k, 3, 31)), scale = Vec3(s, s * 0.6f, s))) }
            }
            Decor.SAND_CASTLE -> {
                val sand = rgba(0xFFE0C287)
                b.cube(Vec3(0f, 0.35f, 0f), Vec3(1.4f, 0.5f, 0.8f), sand)
                for (x in listOf(-0.6f, 0.6f)) {
                    b.paint(sand) { lathe(listOf(0.22f to 1.2f, 0.25f to 0.1f), 10, Part.PAINTED, Vec3(x, 0f, 0f)) }
                    b.paint(rgba(0xFFC9A66B)) { lathe(listOf(0.01f to 1.6f, 0.3f to 1.2f), 10, Part.PAINTED, Vec3(x, 0f, 0f)) }
                }
                b.paint(sand) { lathe(listOf(0.3f to 1.5f, 0.32f to 0.5f), 10, Part.PAINTED, Vec3(0f, 0f, -0.1f)) }
                b.paint(rgba(0xFFC9A66B)) { lathe(listOf(0.01f to 2.0f, 0.38f to 1.5f), 10, Part.PAINTED, Vec3(0f, 0f, -0.1f)) }
                b.cube(Vec3(0f, 0.35f, 0.41f), Vec3(0.3f, 0.4f, 0.04f), rgba(0xFF5D4037))
            }
            Decor.BUBBLER -> {
                // A diver's helmet on the sand, its window glowing a little.
                b.paint(rgba(0xFFC9A227)) { sphere(Vec3(0f, 0.65f, 0f), 0.5f, Part.PAINTED, 8, 12) }
                b.cube(Vec3(0f, 0.2f, 0f), Vec3(0.9f, 0.3f, 0.7f), rgba(0xFF8D6E63))
                b.paint(rgba(0xFF80DEEA)) { tube(listOf(Vec3(0f, 0.7f, 0.42f), Vec3(0f, 0.7f, 0.55f)), { 0.26f }, 14, Part.GLASS, cap = false) }
                b.paint(rgba(0xFF6D5A1F)) { tube((0..14).map { j -> val a = j / 14f * 2f * PI.toFloat(); Vec3(cos(a) * 0.28f, 0.7f + sin(a) * 0.28f, 0.5f) }, { 0.04f }, 5, Part.PAINTED, cap = false) }
            }
            Decor.TREASURE_CHEST -> {
                val wood = rgba(0xFF8D5A3B)
                val gold = rgba(0xFFFFC83D)
                b.cube(Vec3(0f, 0.35f, 0f), Vec3(1.1f, 0.6f, 0.7f), wood)
                b.cube(Vec3(0f, 0.68f, 0.05f), Vec3(1.0f, 0.12f, 0.55f), gold)
                for (k in 0 until 6) b.paint(gold) { sphere(Vec3(-0.35f + 0.14f * k, 0.78f, 0.05f + 0.08f * (k % 2)), 0.1f, Part.BULB, 4, 8) }
                // The lid, thrown open on its hinge at the back.
                b.paint(wood) {
                    append(MeshBuilder().apply { tube(listOf(Vec3(-0.55f, 0f, 0f), Vec3(0.55f, 0f, 0f)), { 0.35f }, 12, Part.PAINTED) }.build(), Mat4.trs(Vec3(0f, 1.0f, -0.4f), rotX = -0.9f, scale = Vec3(1f, 1f, 0.5f)))
                }
                for (x in listOf(-0.45f, 0.45f)) b.cube(Vec3(x, 0.35f, 0.36f), Vec3(0.1f, 0.62f, 0.04f), gold)
            }
            Decor.LANTERN_LIGHTS -> {
                // A string of glowing lanterns along the top of the back glass.
                for (k in 0 until 9) {
                    val x = -3.6f + 0.9f * k
                    val y = AquariumScene.HEIGHT - 0.3f - 0.18f * sin(k / 8f * PI.toFloat())
                    val color = listOf(rgba(0xFFFFD54F), rgba(0xFFFF8A65), rgba(0xFF81D4FA))[k % 3]
                    b.cube(Vec3(x, y, -1.2f), Vec3(0.2f, 0.26f, 0.2f), color, Part.BULB)
                }
                b.paint(rgba(0xFF37474F)) { tube((0..16).map { j -> val x = -3.8f + 7.6f * j / 16f; Vec3(x, AquariumScene.HEIGHT - 0.15f - 0.18f * sin(j / 16f * PI.toFloat()), -1.2f) }, { 0.02f }, 4, Part.PAINTED, cap = false) }
            }
            Decor.SUNKEN_SHIP -> {
                val wood = rgba(0xFF5D4632)
                val hull = listOf(-1.4f to 0.6f, -1.0f to 0f, 1.0f to 0f, 1.5f to 0.7f)
                b.paint(wood) { append(MeshBuilder().apply { extrude(hull, 0.8f, Part.PAINTED) }.build(), Mat4.trs(Vec3(0f, 0.1f, 0f), rotZ = 0.18f)) }
                b.cube(Vec3(0.1f, 0.62f, 0f), Vec3(2.2f, 0.06f, 0.7f), rgba(0xFF7B5E45))
                // The broken mast, leaning.
                b.paint(rgba(0xFF4E3B2A)) { tube(listOf(Vec3(0.2f, 0.6f, 0f), Vec3(0.6f, 1.9f, 0.1f)), { 0.06f }, 6, Part.PAINTED) }
                b.cube(Vec3(-0.6f, 0.85f, 0.41f), Vec3(0.18f, 0.18f, 0.04f), rgba(0xFF2F3E46))
            }
            Decor.PLANTS, Decor.CORAL_GARDEN -> b.cube(Vec3.ZERO, Vec3(0.01f, 0.01f, 0.01f), rgba(0xFF000000))
        }
        return b.build()
    }
}
