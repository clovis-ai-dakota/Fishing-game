package com.deepline.fishing.ui.village

import com.deepline.fishing.Building
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.MeshBuilder
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Part
import com.deepline.fishing.ui.render3d.PropModels
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.render3d.hash01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The village's meshes, built in code like the rest of the game's models and painted per vertex
 * (the painted material): the ground, the sea, the buildings and everything around them. The
 * still parts are merged into a few big meshes so a frame is a handful of draws. Keys:
 * `village:terrain`, `village:sea`, `village:town`, `village:glass` (see [mesh]).
 */
object VillageModels {
    private val cache = HashMap<String, MeshData>()

    /** Built once (the town takes a moment), then kept: a new GL context only uploads it again. */
    fun mesh(key: String): MeshData = synchronized(cache) {
        cache.getOrPut(key) {
            when (key) {
                "village:terrain" -> terrain()
                "village:sea" -> sea()
                "village:town" -> town()
                "village:glass" -> glass()
                else -> error("Unknown village mesh $key")
            }
        }
    }

    // ---- Ground and sea --------------------------------------------------------------------------

    /** Where the shore is at [x]: the same wavy line as the 2D map. */
    fun shoreZ(x: Float) = VillageLayout.SHORE_Z + 1.6f * sin(x / 9f) + 0.8f * sin(x / 3.7f)

    /** The ground's height (m) at [x], [z]: the beach dipping under the sea, the village's gentle slope, the hill and the headlands. */
    fun height(x: Float, z: Float): Float {
        val d = shoreZ(x) - z // inland distance
        val base = when {
            d < -3.5f -> -0.6f - 0.06f * (-d - 3.5f)
            d < 0f -> 0.5f + (d / 3.5f) * 1.1f
            else -> 0.5f + 0.025f * d
        }
        val hill = 12f * smooth(36f, 70f, d) * (0.8f + 0.2f * sin(x / 13f))
        val headland = 10f * smooth(24f, 40f, abs(x)) * smooth(-14f, 4f, d)
        val bumps = 0.5f * (noise(x * 0.11f, z * 0.11f) - 0.5f) * smooth(2f, 8f, d)
        return max(base + hill + headland + bumps, -6f)
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun noise(x: Float, z: Float): Float {
        val ix = kotlin.math.floor(x).toInt()
        val iz = kotlin.math.floor(z).toInt()
        val fx = x - ix
        val fz = z - iz
        fun h(a: Int, b: Int) = hash01(a, b, 97)
        val ux = fx * fx * (3f - 2f * fx)
        val uz = fz * fz * (3f - 2f * fz)
        return (h(ix, iz) * (1 - ux) + h(ix + 1, iz) * ux) * (1 - uz) + (h(ix, iz + 1) * (1 - ux) + h(ix + 1, iz + 1) * ux) * uz
    }

    /** The streets, as (from, to, width), shared with the ground's colour. */
    private val streets = listOf(
        Triple(Vec3(6f, 0f, 3f), Vec3(0f, 0f, -27f), 4f),
        Triple(Vec3(-12f, 0f, -9.5f), Vec3(12f, 0f, -9.5f), 3.2f),
        Triple(Vec3(-11f, 0f, 4f), Vec3(-1.5f, 0f, -6f), 2.6f),
        Triple(Vec3(-8.5f, 0f, 3f), Vec3(-8.5f, 0f, -2f), 2.2f),
    )

    private fun distanceToSegment(x: Float, z: Float, a: Vec3, b: Vec3): Float {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val t = (((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz)).coerceIn(0f, 1f)
        val px = a.x + t * dx - x
        val pz = a.z + t * dz - z
        return sqrt(px * px + pz * pz)
    }

    /** How shaded the ground is at [x], [z] by the buildings standing next to it (fake contact shadow). */
    private fun groundShade(x: Float, z: Float): Float {
        var s = 0f
        for (spot in VillageLayout.spots.values) {
            val dx = max(abs(x - spot.center.x) - spot.half.x, 0f)
            val dz = max(abs(z - spot.center.z) - spot.half.z, 0f)
            val d = sqrt(dx * dx + dz * dz)
            s = max(s, 1f - smooth(0f, 2.2f, d))
        }
        for (h in houses) {
            val dx = max(abs(x - h.x) - h.w / 2f, 0f)
            val dz = max(abs(z - h.z) - h.d / 2f, 0f)
            s = max(s, 1f - smooth(0f, 1.6f, sqrt(dx * dx + dz * dz)))
        }
        return s
    }

    private fun terrain(): MeshData {
        val b = MeshBuilder()
        val xs = (-60..60).map { it * 2f }
        val zs = (-60..17).map { it * 2f }
        val stride = xs.size
        val sand = rgb(0xFFE8D3A0)
        val wetSand = rgb(0xFFC9B07A)
        val grass = rgb(0xFF86C46C)
        val grassDark = rgb(0xFF5E9E55)
        val rock = rgb(0xFF8E9AA0)
        val path = rgb(0xFFD9C08F)
        for (z in zs) for (x in xs) {
            val y = height(x, z)
            val d = shoreZ(x) - z
            val slope = abs(height(x + 1f, z) - height(x - 1f, z)) + abs(height(x, z + 1f) - height(x, z - 1f))
            var c = when {
                d < -1.4f -> wetSand
                d < 2.5f -> mix(wetSand, sand, smooth(-1.4f, 0.5f, d))
                else -> mix(sand, mix(grass, grassDark, noise(x * 0.07f, z * 0.07f) * 0.8f + smooth(20f, 60f, d) * 0.4f), smooth(2.5f, 5f, d))
            }
            // Rocky cliffs where the ground is steep, and on the headlands.
            c = mix(c, rock, smooth(1.6f, 3.2f, slope) * 0.9f)
            // The streets, worn into the grass.
            val street = streets.minOf { (a, e, w) -> distanceToSegment(x, z, a, e) - w / 2f }
            if (d > 1f) c = mix(c, path, 1f - smooth(-0.3f, 0.7f, street))
            val shade = groundShade(x, z)
            c = floatArrayOf(c[0] * (1f - 0.35f * shade), c[1] * (1f - 0.35f * shade), c[2] * (1f - 0.3f * shade), 1f)
            b.vertex(Vec3(x, y, z), Vec3.UP, x / 40f, z / 40f, Part.PAINTED, c)
        }
        for (j in 0 until zs.size - 1) for (i in 0 until stride - 1) {
            val a = j * stride + i
            b.quad(a, a + stride, a + stride + 1, a + 1)
        }
        b.smoothNormals()
        return b.build()
    }

    private fun sea(): MeshData {
        val b = MeshBuilder()
        val xs = (-40..40).map { it * 3f }
        // Fine near the shore, where the foam is, coarser out at sea.
        val zs = (-5..30).map { it * 1.5f } + (1..14).map { 45f + it * 18f }
        val stride = xs.size
        for (z in zs) for (x in xs) {
            val depth = max(0f, -height(x, z))
            val deep = smooth(0f, 5f, depth)
            val foam = 1f - smooth(0f, 0.7f, depth)
            b.vertex(Vec3(x, 0f, z), Vec3.UP, x / 30f, z / 30f, Part.PAINTED, floatArrayOf(deep, foam, 0f, 1f))
        }
        for (j in 0 until zs.size - 1) for (i in 0 until stride - 1) {
            val a = j * stride + i
            b.quad(a, a + stride, a + stride + 1, a + 1)
        }
        return b.build()
    }

    // ---- Painting helpers ------------------------------------------------------------------------

    private fun rgb(argb: Long) = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(4) { a[it] + (b[it] - a[it]) * t.coerceIn(0f, 1f) }

    private fun shade(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k, c[3])

    /** Builds with [block], then paints what it added in [color]. */
    private inline fun MeshBuilder.paint(color: FloatArray, block: MeshBuilder.() -> Unit) {
        val from = vertexCount
        block()
        recolor(from, color)
    }

    private fun MeshBuilder.cube(center: Vec3, size: Vec3, color: FloatArray, part: Float = Part.PAINTED) = paint(color) { box(center, size, part) }

    /** A post, pole or piling from [a] to [b]. */
    private fun MeshBuilder.pole(a: Vec3, b: Vec3, r: Float, color: FloatArray, sides: Int = 6) = paint(color) { tube(listOf(a, b), { r }, sides, Part.PAINTED, cap = false) }

    /**
     * A pitched roof over a [w] x [d] box whose walls end at [top]: the ridge runs along x at
     * [h] above the eaves, overhanging by [o], with a darker fascia under the edge.
     */
    private fun MeshBuilder.gableRoof(cx: Float, cz: Float, w: Float, d: Float, top: Float, h: Float, o: Float, color: FloatArray, wall: FloatArray) {
        val x0 = cx - w / 2f - o
        val x1 = cx + w / 2f + o
        val zf = cz + d / 2f + o
        val zb = cz - d / 2f - o
        val ridge = top + h
        fun slope(a: Vec3, b: Vec3, c: Vec3, e: Vec3, col: FloatArray) {
            val n = ((b - a) cross (e - a)).normalized()
            val i0 = vertex(a, n, 0f, 0f, Part.PAINTED, col)
            val i1 = vertex(b, n, 1f, 0f, Part.PAINTED, col)
            val i2 = vertex(c, n, 1f, 1f, Part.PAINTED, col)
            val i3 = vertex(e, n, 0f, 1f, Part.PAINTED, col)
            quad(i0, i1, i2, i3)
        }
        // Front and back slopes (counter-clockwise seen from outside).
        slope(Vec3(x0, top, zf), Vec3(x1, top, zf), Vec3(x1, ridge, cz), Vec3(x0, ridge, cz), color)
        slope(Vec3(x1, top, zb), Vec3(x0, top, zb), Vec3(x0, ridge, cz), Vec3(x1, ridge, cz), shade(color, 0.92f))
        // Gable ends: the wall up to the ridge.
        for ((x, sign) in listOf(cx - w / 2f to -1f, cx + w / 2f to 1f)) {
            val n = Vec3(sign, 0f, 0f)
            val a = vertex(Vec3(x, top, cz - d / 2f), n, 0f, 0f, Part.PAINTED, wall)
            val c = vertex(Vec3(x, top, cz + d / 2f), n, 1f, 0f, Part.PAINTED, wall)
            val r = vertex(Vec3(x, ridge - h * (o / (d / 2f + o)), cz), n, 0.5f, 1f, Part.PAINTED, wall)
            if (sign > 0f) triangle(a, r, c) else triangle(a, c, r)
        }
        // The fascia: a thin dark band along both eaves.
        val fascia = shade(color, 0.6f)
        cube(Vec3(cx, top - 0.12f, zf), Vec3(w + 2f * o, 0.24f, 0.12f), fascia)
        cube(Vec3(cx, top - 0.12f, zb), Vec3(w + 2f * o, 0.24f, 0.12f), fascia)
    }

    /** A window on the wall facing +z at ([x], [y], [z]): a white frame and glass (lit at night). */
    private fun MeshBuilder.window(x: Float, y: Float, z: Float, w: Float = 1.1f, h: Float = 1.3f, frame: FloatArray = rgb(0xFFFFFBF2)) {
        cube(Vec3(x, y, z + 0.04f), Vec3(w + 0.24f, h + 0.24f, 0.1f), frame)
        cube(Vec3(x, y, z + 0.1f), Vec3(w, h, 0.06f), rgb(0xFF9FC6D8), Part.GLASS)
        cube(Vec3(x, y, z + 0.14f), Vec3(0.08f, h, 0.04f), frame)
    }

    private fun MeshBuilder.door(x: Float, z: Float, w: Float = 1.3f, h: Float = 2.2f, color: FloatArray = rgb(0xFF6D4C35)) {
        cube(Vec3(x, h / 2f, z + 0.05f), Vec3(w + 0.3f, h + 0.15f, 0.1f), rgb(0xFFF5EBD8))
        cube(Vec3(x, h / 2f, z + 0.1f), Vec3(w, h, 0.08f), color)
    }

    /** A plain house: walls, a pitched roof, windows, a door and a chimney. */
    private fun MeshBuilder.house(h: House) {
        val wallH = h.storeys * 2.8f
        cube(Vec3(h.x, wallH / 2f - 0.5f, h.z), Vec3(h.w, wallH + 1f, h.d), h.wall)
        gableRoof(h.x, h.z, h.w, h.d, wallH, h.w * 0.32f + 0.6f, 0.45f, h.roof, h.wall)
        val front = h.z + h.d / 2f
        door(h.x - h.w * 0.22f, front)
        window(h.x + h.w * 0.22f, 1.5f, front)
        if (h.storeys > 1) {
            window(h.x - h.w * 0.22f, 4.3f, front)
            window(h.x + h.w * 0.22f, 4.3f, front)
        }
        cube(Vec3(h.x + h.w * 0.28f, wallH + h.w * 0.32f, h.z - h.d * 0.15f), Vec3(0.7f, 1.6f, 0.7f), rgb(0xFF9E5B45))
        // A flower box under the ground-floor window.
        cube(Vec3(h.x + h.w * 0.22f, 0.75f, front + 0.25f), Vec3(1.3f, 0.3f, 0.35f), rgb(0xFF8D5A3B))
        cube(Vec3(h.x + h.w * 0.22f, 0.98f, front + 0.25f), Vec3(1.2f, 0.2f, 0.3f), if (hash01(h.x.toInt(), h.z.toInt(), 3) > 0.5f) rgb(0xFFE57373) else rgb(0xFFFFD54F))
    }

    class House(val x: Float, val z: Float, val w: Float, val d: Float, val storeys: Int, val wall: FloatArray, val roof: FloatArray)

    private val walls = listOf(0xFFF7F1E3, 0xFFFFF4D6, 0xFFE9F1F2, 0xFFF6E3D3, 0xFFDDEBE1, 0xFFFBE7E1)
    private val roofs = listOf(0xFFC8553D, 0xFFE07A3F, 0xFF3B7EA1, 0xFF2E8C88, 0xFFB5463A, 0xFF5A6F8C)

    /** The fishermen's houses: along the street, up the hill and by the shore. */
    val houses: List<House> by lazy {
        val spots = listOf(
            Triple(-3.5f, -21f, 1), Triple(4.5f, -22f, 2), Triple(-21f, -27f, 2), Triple(21f, -26f, 1),
            Triple(-24f, -9f, 1), Triple(24f, -7f, 2), Triple(-5f, -44f, 2), Triple(8f, -45f, 1),
            Triple(-15f, -38f, 1), Triple(17f, -39f, 2), Triple(23f, 2f, 1), Triple(-25f, 3f, 1),
            Triple(-30f, -18f, 2), Triple(31f, -17f, 1), Triple(0f, -56f, 1), Triple(-12f, -52f, 1),
        )
        spots.mapIndexed { i, (x, z, storeys) ->
            val w = 4.6f + 1.8f * hash01(i, 1, 5)
            val d = 4.2f + 1.4f * hash01(i, 2, 5)
            House(x, z, w, d, storeys, rgb(walls[i % walls.size]), rgb(roofs[(i * 5 + 2) % roofs.size]))
        }
    }

    /** Trees on the hill and the headlands, never on a building or a street. */
    private val trees: List<Vec3> by lazy {
        val out = ArrayList<Vec3>()
        var k = 0
        while (out.size < 70 && k < 2000) {
            val x = -58f + 116f * hash01(k, 11, 7)
            val z = -110f + 112f * hash01(k, 12, 7)
            k++
            val d = shoreZ(x) - z
            if (d < 6f) continue
            if (VillageLayout.spots.values.any { abs(x - it.center.x) < it.half.x + 3f && abs(z - it.center.z) < it.half.z + 3f }) continue
            if (houses.any { abs(x - it.x) < it.w / 2f + 2.5f && abs(z - it.z) < it.d / 2f + 2.5f }) continue
            if (streets.any { (a, e, w) -> distanceToSegment(x, z, a, e) < w / 2f + 1.5f }) continue
            // Fewer trees down in the village, a wood on the hill behind it.
            if (d < 40f && abs(x) < 26f && hash01(k, 13, 7) > 0.25f) continue
            out += Vec3(x, height(x, z), z)
        }
        out
    }

    private fun MeshBuilder.pine(at: Vec3, scale: Float, variant: Int) {
        pole(at + Vec3(0f, -0.5f, 0f), at + Vec3(0f, 1.4f * scale, 0f), 0.22f * scale, rgb(0xFF7A5536))
        val greens = listOf(rgb(0xFF2F7D4F), rgb(0xFF3C8D57), rgb(0xFF2A6E4A))
        for (tier in 0 until 3) {
            val y0 = at.y + (1.1f + tier * 1.25f) * scale
            val r = (2.1f - tier * 0.55f) * scale
            paint(greens[(tier + variant) % 3]) {
                lathe(listOf(0.02f to 2.2f * scale, r to 0f, r * 0.75f to -0.1f), 7, Part.PAINTED, Vec3(at.x, y0, at.z))
            }
        }
    }

    private fun MeshBuilder.roundTree(at: Vec3, scale: Float, variant: Int) {
        pole(at + Vec3(0f, -0.5f, 0f), at + Vec3(0f, 2f * scale, 0f), 0.25f * scale, rgb(0xFF7A5536))
        val greens = listOf(rgb(0xFF6DB15A), rgb(0xFF5AA052), rgb(0xFF7CBF5E))
        paint(greens[variant % 3]) {
            sphere(at + Vec3(0f, 3.2f * scale, 0f), 1.9f * scale, Part.PAINTED, 5, 8)
            sphere(at + Vec3(1.1f * scale, 2.7f * scale, 0.4f * scale), 1.3f * scale, Part.PAINTED, 4, 7)
            sphere(at + Vec3(-1.0f * scale, 2.8f * scale, -0.3f * scale), 1.2f * scale, Part.PAINTED, 4, 7)
        }
    }

    private fun MeshBuilder.rockAt(at: Vec3, size: Float, variant: Int, color: FloatArray = rgb(0xFF9AA4A8)) {
        paint(color) { append(PropModels.rock(variant), Mat4.trs(at, rotY = variant * 1.3f, scale = Vec3(size, size * 0.7f, size))) }
    }

    private fun MeshBuilder.barrel(at: Vec3, color: FloatArray = rgb(0xFF9C6B43)) {
        paint(color) { lathe(listOf(0.42f to 1.1f, 0.5f to 0.55f, 0.42f to 0f), 9, Part.PAINTED, at) }
        cube(at + Vec3(0f, 1.12f, 0f), Vec3(0.75f, 0.06f, 0.75f), shade(color, 0.75f))
    }

    private fun MeshBuilder.crate(at: Vec3, s: Float = 0.9f, fill: FloatArray? = null) {
        cube(at + Vec3(0f, s / 2f, 0f), Vec3(s, s, s), rgb(0xFFB9874F))
        cube(at + Vec3(0f, s / 2f, s / 2f + 0.02f), Vec3(s * 0.95f, 0.12f, 0.04f), rgb(0xFF8C6239))
        if (fill != null) cube(at + Vec3(0f, s + 0.08f, 0f), Vec3(s * 0.85f, 0.16f, s * 0.85f), fill)
    }

    private fun MeshBuilder.lamp(at: Vec3, height: Float = 3.2f) {
        pole(at, at + Vec3(0f, height, 0f), 0.08f, rgb(0xFF37474F), 5)
        cube(at + Vec3(0f, height + 0.2f, 0f), Vec3(0.45f, 0.5f, 0.45f), rgb(0xFFFFE08A), Part.BULB)
        cube(at + Vec3(0f, height + 0.5f, 0f), Vec3(0.6f, 0.12f, 0.6f), rgb(0xFF37474F))
    }

    /** A pennant on its pole: the cloth flutters (u along it). */
    private fun MeshBuilder.pennant(foot: Vec3, height: Float, length: Float, dir: Float, color: FloatArray) {
        pole(foot, foot + Vec3(0f, height, 0f), 0.09f, rgb(0xFF5D4037), 5)
        val top = foot + Vec3(0f, height - 0.1f, 0f)
        val n = Vec3(0f, 0f, 1f)
        val from = vertexCount
        val a = vertex(top, n, 0f, 0f, Part.CLOTH, color)
        val b = vertex(top + Vec3(0f, -1.4f, 0f), n, 0f, 1f, Part.CLOTH, color)
        val c = vertex(top + Vec3(dir * length, -0.7f, 0f), n, 1f, 0.5f, Part.CLOTH, color)
        if (dir > 0f) triangle(a, b, c) else triangle(a, c, b)
        recolor(from, color)
    }

    // ---- The town: buildings and everything else that stands still ------------------------------

    private fun town(): MeshData {
        val b = MeshBuilder()
        for (h in houses) b.house(h)
        for ((i, t) in trees.withIndex()) {
            val s = 0.8f + 0.5f * hash01(i, 3, 9)
            if (t.y > 6f || hash01(i, 4, 9) > 0.35f) b.pine(t, s, i) else b.roundTree(t, s, i)
        }
        b.dexHall()
        b.market()
        b.ropeWorkshop()
        b.aquariumPavilion()
        b.pier()
        b.arena()
        b.lighthouse()
        b.shoreProps()
        return b.build()
    }

    /** The species hall: stone steps, tall walls, a big pitched roof, a fish weathervane, a bell turret. */
    private fun MeshBuilder.dexHall() {
        val s = VillageLayout.spots.getValue(Building.DEX)
        val c = s.center
        val w = 12f
        val d = 8.5f
        val wallTop = 6.5f
        val stone = rgb(0xFFCFC6B4)
        val wall = rgb(0xFFF4EBD6)
        val roof = rgb(0xFF2F6E8E)
        cube(Vec3(c.x, 0.2f, c.z), Vec3(w + 1.6f, 1.6f, d + 1.6f), stone)
        cube(Vec3(c.x, wallTop / 2f, c.z), Vec3(w, wallTop, d), wall)
        gableRoof(c.x, c.z, w, d, wallTop, 4.6f, 0.6f, roof, wall)
        val front = c.z + d / 2f
        // A columned porch over the big doors, with its own small roof.
        for (x in listOf(-2.6f, 2.6f)) pole(Vec3(c.x + x, 1f, front + 1.6f), Vec3(c.x + x, 4.6f, front + 1.6f), 0.32f, rgb(0xFFF8F3E8), 8)
        cube(Vec3(c.x, 4.8f, front + 1.0f), Vec3(6.8f, 0.4f, 2.6f), rgb(0xFFF8F3E8))
        gableRoof(c.x, front + 1.0f, 6.4f, 2.4f, 5f, 1.4f, 0.25f, roof, rgb(0xFFF8F3E8))
        door(c.x, front, 2.4f, 3.4f, rgb(0xFF2F5E73))
        for (x in listOf(-4.6f, -3.2f, 3.2f, 4.6f)) window(c.x + x, 3.2f, front, 0.9f, 2.2f)
        for (x in listOf(-3.6f, 0f, 3.6f)) window(c.x + x, 8.1f, front - 0.05f + 0f, 0.8f, 0.8f)
        // Steps up to the porch.
        for (k in 0 until 3) cube(Vec3(c.x, 0.15f + k * 0.3f, front + 2.6f - k * 0.45f), Vec3(5.2f, 0.3f, 0.9f), stone)
        // The bell turret on the ridge, and the big golden fish turning over it.
        cube(Vec3(c.x, wallTop + 5.3f, c.z), Vec3(1.8f, 1.6f, 1.8f), wall)
        gableRoof(c.x, c.z, 1.8f, 1.8f, wallTop + 6.1f, 1.2f, 0.25f, roof, wall)
        pole(Vec3(c.x, wallTop + 7.2f, c.z), Vec3(c.x, wallTop + 9.2f, c.z), 0.08f, rgb(0xFF37474F), 5)
        val fish = listOf(
            -1.6f to 0f, -1.1f to 0.55f, 0f to 0.85f, 1.1f to 0.5f, 1.6f to 0.05f, 2.3f to 0.5f, 2.3f to -0.5f, 1.6f to -0.05f,
            1.1f to -0.5f, 0f to -0.8f, -1.1f to -0.5f,
        )
        paint(rgb(0xFFF2B33D)) {
            append(MeshBuilder().apply { extrude(fish, 0.25f, Part.PAINTED) }.build(), Mat4.trs(Vec3(c.x, wallTop + 10.1f, c.z), rotY = 0.35f, scale = Vec3(1.1f, 1.1f, 1.1f)))
        }
        cube(Vec3(c.x - 1.15f, wallTop + 10.25f, c.z + 0.18f), Vec3(0.3f, 0.3f, 0.12f), rgb(0xFF263238))
        // Banners either side of the porch, in the Dex's colours.
        for (x in listOf(-6.3f, 6.3f)) cube(Vec3(c.x + x, 4f, front + 0.12f), Vec3(1.1f, 3f, 0.08f), rgb(0xFF2F6E8E))
    }

    /** The market hall: an open front under a striped awning, stalls of fish and fruit, lanterns. */
    private fun MeshBuilder.market() {
        val s = VillageLayout.spots.getValue(Building.SHOP)
        val c = s.center
        val w = 10f
        val d = 6.5f
        val wall = rgb(0xFFF6E7C8)
        val roof = rgb(0xFFD9573B)
        cube(Vec3(c.x, 2.2f, c.z - d / 2f + 0.3f), Vec3(w, 5.4f, 0.6f), wall)
        for (x in listOf(-w / 2f + 0.3f, w / 2f - 0.3f)) cube(Vec3(c.x + x, 2.2f, c.z), Vec3(0.6f, 5.4f, d), wall)
        for (x in listOf(-3.2f, 0f, 3.2f)) pole(Vec3(c.x + x, 0f, c.z + d / 2f - 0.3f), Vec3(c.x + x, 4.9f, c.z + d / 2f - 0.3f), 0.2f, rgb(0xFF8D6E63), 6)
        gableRoof(c.x, c.z, w, d, 4.9f, 2.6f, 0.5f, roof, wall)
        // The striped awning sloping out over the stalls.
        val stripes = 9
        for (k in 0 until stripes) {
            val x0 = c.x - w / 2f + (k + 0.5f) * (w / stripes)
            paint(if (k % 2 == 0) rgb(0xFFE53935) else rgb(0xFFFFFBF2)) {
                append(MeshBuilder().apply { box(Vec3.ZERO, Vec3(w / stripes, 0.1f, 2.4f), Part.PAINTED) }.build(), Mat4.trs(Vec3(x0, 3.7f, c.z + d / 2f + 1.0f), rotX = 0.38f))
            }
        }
        // Counters with fish on ice, fruit, and crates.
        cube(Vec3(c.x - 2.5f, 0.55f, c.z + d / 2f - 1.0f), Vec3(3.2f, 1.1f, 1.1f), rgb(0xFFB9874F))
        cube(Vec3(c.x - 2.5f, 1.15f, c.z + d / 2f - 1.0f), Vec3(3f, 0.12f, 0.9f), rgb(0xFFE3F2FD))
        for (k in 0 until 4) cube(Vec3(c.x - 3.6f + k * 0.75f, 1.3f, c.z + d / 2f - 1.0f), Vec3(0.55f, 0.16f, 0.22f), rgb(listOf(0xFF90A4AE, 0xFFFFB74D, 0xFF4FC3F7, 0xFFF06292)[k]))
        cube(Vec3(c.x + 2.5f, 0.55f, c.z + d / 2f - 1.0f), Vec3(3.2f, 1.1f, 1.1f), rgb(0xFFB9874F))
        for (k in 0 until 6) paint(rgb(listOf(0xFFFF7043, 0xFFFFCA28, 0xFF9CCC65)[k % 3])) {
            sphere(Vec3(c.x + 1.3f + k * 0.45f, 1.3f, c.z + d / 2f - 1.0f + (k % 2) * 0.25f), 0.22f, Part.PAINTED, 3, 6)
        }
        crate(Vec3(c.x + w / 2f + 0.9f, 0f, c.z + 1.2f), 1f, rgb(0xFF64B5F6))
        crate(Vec3(c.x + w / 2f + 1.0f, 0f, c.z - 0.2f), 0.85f)
        crate(Vec3(c.x + w / 2f + 1.0f, 0.85f, c.z - 0.2f), 0.7f, rgb(0xFFFFB74D))
        barrel(Vec3(c.x - w / 2f - 1f, 0f, c.z + 1.6f))
        // A string of lanterns from the awning's corners.
        for (k in 0..6) {
            val x = c.x - w / 2f + k * (w / 6f)
            val sag = sin(k / 6f * PI.toFloat()) * 0.5f
            cube(Vec3(x, 4.2f - sag, c.z + d / 2f + 2.2f), Vec3(0.3f, 0.36f, 0.3f), rgb(listOf(0xFFFFD54F, 0xFFFF8A65, 0xFF81D4FA)[k % 3]), Part.BULB)
        }
        // The sign: a coin over the entrance.
        paint(rgb(0xFFF2B33D)) { append(MeshBuilder().apply { tube(listOf(Vec3(0f, 0f, -0.1f), Vec3(0f, 0f, 0.1f)), { 0.9f }, 16, Part.PAINTED) }.build(), Mat4.translation(c.x, 6.4f, c.z + d / 2f + 0.3f)) }
    }

    /** The rope workshop: a long timber shed, a giant spool of rope and a winch in front of it. */
    private fun MeshBuilder.ropeWorkshop() {
        val s = VillageLayout.spots.getValue(Building.ROPE)
        val c = s.center
        val w = 12f
        val d = 6f
        val timber = rgb(0xFFB98A5E)
        val roof = rgb(0xFF5D6B7A)
        cube(Vec3(c.x, 1.8f, c.z), Vec3(w, 4.6f, d), timber)
        // Plank lines across the front.
        for (k in 1 until 6) cube(Vec3(c.x, 0.1f + k * 0.75f, c.z + d / 2f + 0.03f), Vec3(w, 0.06f, 0.06f), shade(timber, 0.75f))
        gableRoof(c.x, c.z, w, d, 4.1f, 2.4f, 0.5f, roof, timber)
        // A wide barn door, open, and two windows.
        cube(Vec3(c.x - 2f, 1.6f, c.z + d / 2f + 0.05f), Vec3(3.2f, 3.2f, 0.1f), rgb(0xFF3E2C23))
        cube(Vec3(c.x - 4.1f, 1.6f, c.z + d / 2f + 0.5f), Vec3(1.6f, 3.1f, 0.12f), rgb(0xFF8D6040))
        window(c.x + 2.2f, 2.2f, c.z + d / 2f)
        window(c.x + 4.4f, 2.2f, c.z + d / 2f)
        // The giant spool on its side, wound with rope.
        val spool = Vec3(c.x + 3.4f, 1.9f, c.z + d / 2f + 3.2f)
        for (side in listOf(-0.95f, 0.95f)) {
            paint(rgb(0xFF8D6E63)) { append(MeshBuilder().apply { tube(listOf(Vec3(0f, 0f, -0.15f), Vec3(0f, 0f, 0.15f)), { 1.9f }, 18, Part.PAINTED) }.build(), Mat4.translation(spool.x + side, spool.y, spool.z).let { Mat4.multiply(it, Mat4.rotationY(PI.toFloat() / 2f)) }) }
        }
        paint(rgb(0xFFE3C99A)) {
            append(MeshBuilder().apply { tube(listOf(Vec3(0f, 0f, -0.85f), Vec3(0f, 0f, 0.85f)), { 1.45f }, 18, Part.PAINTED, cap = false) }.build(), Mat4.multiply(Mat4.translation(spool.x, spool.y, spool.z), Mat4.rotationY(PI.toFloat() / 2f)))
        }
        for (k in 0 until 5) {
            val x = spool.x - 0.7f + k * 0.35f
            paint(rgb(0xFFC9AE7E)) {
                append(MeshBuilder().apply { tube((0..16).map { j -> val a = j / 16f * 2f * PI.toFloat(); Vec3(0f, cos(a) * 1.47f, sin(a) * 1.47f) }, { 0.06f }, 4, Part.PAINTED, cap = false) }.build(), Mat4.translation(x, spool.y, spool.z))
            }
        }
        // The winch: an A-frame with a drum, and coils of rope on the ground.
        val wx = c.x - 3.8f
        val wz = c.z + d / 2f + 3f
        for (side in listOf(-0.9f, 0.9f)) {
            pole(Vec3(wx + side, 0f, wz - 0.6f), Vec3(wx + side, 2.6f, wz), 0.12f, rgb(0xFF6D4C35))
            pole(Vec3(wx + side, 0f, wz + 0.6f), Vec3(wx + side, 2.6f, wz), 0.12f, rgb(0xFF6D4C35))
        }
        pole(Vec3(wx - 1.1f, 2.1f, wz), Vec3(wx + 1.1f, 2.1f, wz), 0.3f, rgb(0xFFE3C99A), 10)
        for ((k, at) in listOf(Vec3(c.x - 1f, 0.2f, c.z + d / 2f + 2.2f), Vec3(c.x + 0.4f, 0.2f, c.z + d / 2f + 3.6f)).withIndex()) {
            for (ring in 0 until 3) paint(rgb(0xFFD8BC8C)) {
                tube((0..14).map { j -> val a = j / 14f * 2f * PI.toFloat(); at + Vec3(cos(a) * (0.75f - ring * 0.18f), ring * 0.14f, sin(a) * (0.75f - ring * 0.18f)) }, { 0.09f }, 4, Part.PAINTED, cap = false)
            }
            if (k == 0) barrel(at + Vec3(-1.6f, -0.2f, 0f), rgb(0xFF8D6E63))
        }
    }

    /** The aquarium: a round stone base, a glass dome on iron ribs (the glass is its own mesh), a golden finial. */
    private fun MeshBuilder.aquariumPavilion() {
        val c = VillageLayout.spots.getValue(Building.AQUARIUM).center
        val r = 5f
        val stone = rgb(0xFFF1ECE2)
        paint(stone) { lathe(listOf(r + 0.4f to 1.6f, r + 0.4f to -0.6f), 20, Part.PAINTED, Vec3(c.x, 0f, c.z)) }
        paint(shade(stone, 0.9f)) { lathe(listOf(0.01f to 1.62f, r + 0.4f to 1.6f), 20, Part.PAINTED, Vec3(c.x, 0f, c.z)) }
        // The water inside, seen through the glass.
        paint(rgb(0xFF2BB3C8)) { lathe(listOf(0.01f to 4.4f, r - 0.4f to 4.4f, r - 0.4f to 1.6f), 20, Part.PAINTED, Vec3(c.x, 0f, c.z)) }
        // Iron ribs over the dome, and a ring at its foot.
        val ribs = 8
        for (k in 0 until ribs) {
            val a = k / ribs.toFloat() * 2f * PI.toFloat()
            val path = (0..10).map { j ->
                val t = j / 10f * PI.toFloat() / 2f
                Vec3(c.x + cos(a) * r * cos(t), 1.6f + r * 0.95f * sin(t), c.z + sin(a) * r * cos(t))
            }
            paint(rgb(0xFF2E5E62)) { tube(path, { 0.12f }, 4, Part.PAINTED, cap = false) }
        }
        paint(rgb(0xFF2E5E62)) {
            tube((0..24).map { j -> val a = j / 24f * 2f * PI.toFloat(); Vec3(c.x + cos(a) * r, 1.65f, c.z + sin(a) * r) }, { 0.16f }, 4, Part.PAINTED, cap = false)
        }
        paint(rgb(0xFFF2B33D)) { sphere(Vec3(c.x, 1.6f + r * 0.95f + 0.35f, c.z), 0.45f, Part.PAINTED, 5, 8) }
        // An arched entrance on the street side.
        cube(Vec3(c.x + 1.8f, 1.2f, c.z + r - 0.2f), Vec3(1.8f, 2.4f, 0.8f), rgb(0xFF2E5E62))
        lamp(Vec3(c.x + 3.4f, 0f, c.z + r + 1.2f), 2.8f)
    }

    /** The aquarium's glass dome, drawn see-through after the fish inside it. */
    private fun glass(): MeshData {
        val b = MeshBuilder()
        val c = VillageLayout.spots.getValue(Building.AQUARIUM).center
        val r = 5f
        b.paint(rgb(0xFFBFE9F2)) {
            // A half sphere (the top half of a squashed sphere), glass all round.
            val lat = 7
            val lon = 20
            val first = vertexCount
            for (i in 0..lat) {
                val th = i / lat.toFloat() * PI.toFloat() / 2f
                for (j in 0..lon) {
                    val ph = j / lon.toFloat() * 2f * PI.toFloat()
                    val n = Vec3(cos(th) * cos(ph), sin(th), cos(th) * sin(ph))
                    vertex(Vec3(c.x + n.x * r, 1.6f + n.y * r * 0.95f, c.z + n.z * r), n, j / lon.toFloat(), 1f - i / lat.toFloat(), Part.GLASS)
                }
            }
            val stride = lon + 1
            for (i in 0 until lat) for (j in 0 until lon) {
                val a = first + i * stride + j
                quad(a, a + stride, a + stride + 1, a + 1)
            }
        }
        return b.build()
    }

    /** The pier: a long deck on pilings, a harbour master's hut at its foot, lamps, bollards and rope. */
    private fun MeshBuilder.pier() {
        val a = VillageLayout.pierFrom
        val e = VillageLayout.pierTo
        val wood = rgb(0xFFB98A57)
        val len = e.z - a.z
        cube(Vec3(a.x, a.y - 0.15f, (a.z + e.z) / 2f), Vec3(3.2f, 0.3f, len), wood)
        var z = a.z + 0.6f
        var k = 0
        while (z < e.z) {
            cube(Vec3(a.x, a.y + 0.02f, z), Vec3(3.3f, 0.05f, 0.1f), shade(wood, 0.72f))
            if (k % 3 == 0) for (side in listOf(-1.5f, 1.5f)) pole(Vec3(a.x + side, -2.5f, z), Vec3(a.x + side, a.y + 0.6f, z), 0.2f, rgb(0xFF6D4C35), 6)
            z += 1.1f
            k++
        }
        // Bollards with the boat's mooring lines, and lamps along the edge.
        for (zz in listOf(e.z - 6f, e.z - 1.2f)) paint(rgb(0xFF455A64)) { lathe(listOf(0.22f to 0.7f, 0.3f to 0.55f, 0.2f to 0f), 8, Part.PAINTED, Vec3(a.x + 1.3f, a.y, zz)) }
        for (zz in listOf(a.z + 5f, a.z + 13f, e.z - 0.5f)) lamp(Vec3(a.x - 1.4f, a.y, zz), 2.6f)
        // The harbour master's hut at the foot of the pier.
        val hut = House(a.x + 4.2f, a.z - 3.2f, 3.6f, 3.2f, 1, rgb(0xFFE9F1F2), rgb(0xFF3B7EA1))
        house(hut)
        // A life ring on the hut's wall, and crates of the day's catch.
        paint(rgb(0xFFE53935)) {
            tube((0..14).map { j -> val t = j / 14f * 2f * PI.toFloat(); Vec3(hut.x - 0.6f + cos(t) * 0.45f, 1.9f + sin(t) * 0.45f, hut.z + hut.d / 2f + 0.15f) }, { 0.12f }, 5, Part.PAINTED, cap = false)
        }
        crate(Vec3(a.x - 0.8f, a.y, e.z - 3f), 0.8f, rgb(0xFF90A4AE))
        barrel(Vec3(a.x + 0.9f, a.y, a.z + 3f))
    }

    /** The duelling platform: a round deck on the water, a rope rail, the rivals' pennants and buoys. */
    private fun MeshBuilder.arena() {
        val s = VillageLayout.spots.getValue(Building.DUEL)
        val c = s.center
        val r = 6.6f
        val deckY = 0.9f
        paint(rgb(0xFFB98A57)) { lathe(listOf(0.01f to deckY, r to deckY, r to -0.4f), 28, Part.PAINTED, Vec3(c.x, 0f, c.z)) }
        // Planks across, and the white starting circle in the middle.
        for (k in -5..5) cube(Vec3(c.x, deckY + 0.01f, c.z + k * 1.2f), Vec3(2f * sqrt(max(0f, r * r - (k * 1.2f) * (k * 1.2f))), 0.03f, 0.08f), rgb(0xFF8C6239))
        paint(rgb(0xFFFFFBF2)) {
            tube((0..28).map { j -> val a = j / 28f * 2f * PI.toFloat(); Vec3(c.x + cos(a) * 2.2f, deckY + 0.05f, c.z + sin(a) * 2.2f) }, { 0.12f }, 4, Part.PAINTED, cap = false)
        }
        // Posts and a rope rail around the edge (open towards the jetty).
        val posts = 14
        for (k in 0 until posts) {
            val a = k / posts.toFloat() * 2f * PI.toFloat()
            if (abs(a - 3f * PI.toFloat() / 2f) < 0.4f) continue
            pole(Vec3(c.x + cos(a) * (r - 0.3f), -1.5f, c.z + sin(a) * (r - 0.3f)), Vec3(c.x + cos(a) * (r - 0.3f), deckY + 1.3f, c.z + sin(a) * (r - 0.3f)), 0.13f, rgb(0xFF6D4C35), 5)
        }
        paint(rgb(0xFFE3C99A)) {
            tube((0..40).map { j -> val a = -PI.toFloat() / 2f + 0.4f + j / 40f * (2f * PI.toFloat() - 0.8f); Vec3(c.x + cos(a) * (r - 0.3f), deckY + 1.15f - 0.12f * sin(j * 0.8f), c.z + sin(a) * (r - 0.3f)) }, { 0.07f }, 4, Part.PAINTED, cap = false)
        }
        // The rivals' pennants, blue and red, facing each other.
        pennant(Vec3(c.x - r * 0.6f, deckY, c.z), 6.2f, 2.8f, 1f, rgb(0xFF4FC3F7))
        pennant(Vec3(c.x + r * 0.6f, deckY, c.z), 6.2f, 2.8f, -1f, rgb(0xFFFF5A4A))
        // Buoys around it.
        for (k in 0 until 6) {
            val a = k / 6f * 2f * PI.toFloat() + 0.3f
            val at = Vec3(c.x + cos(a) * (r + 2.5f), 0.1f, c.z + sin(a) * (r + 2.5f))
            paint(if (k % 2 == 0) rgb(0xFFE53935) else rgb(0xFFFFFBF2)) { sphere(at, 0.45f, Part.PAINTED, 4, 8) }
        }
        // The jetty from the beach.
        val ja = VillageLayout.jettyFrom
        val je = VillageLayout.jettyTo
        cube(Vec3(ja.x, ja.y - 0.15f, (ja.z + je.z) / 2f), Vec3(2.4f, 0.3f, je.z - ja.z), rgb(0xFFB98A57))
        var z = ja.z + 0.5f
        while (z < je.z) {
            cube(Vec3(ja.x, ja.y + 0.02f, z), Vec3(2.5f, 0.05f, 0.1f), rgb(0xFF8C6239))
            z += 1.1f
        }
        for (zz in listOf(ja.z + 3f, ja.z + 6.5f)) for (side in listOf(-1.1f, 1.1f)) pole(Vec3(ja.x + side, -2f, zz), Vec3(ja.x + side, ja.y, zz), 0.16f, rgb(0xFF6D4C35), 5)
    }

    /** The lighthouse on its rock, out beyond the arena: decor (its light turns at night). */
    private fun MeshBuilder.lighthouse() {
        val at = Vec3(18f, 0f, 36f)
        rockAt(at + Vec3(0f, -0.5f, 0f), 6f, 3)
        rockAt(at + Vec3(4f, -0.8f, 2f), 3f, 5)
        val base = 3.2f
        val bands = listOf(rgb(0xFFFFFBF2), rgb(0xFFD84B3B), rgb(0xFFFFFBF2), rgb(0xFFD84B3B), rgb(0xFFFFFBF2))
        for ((k, col) in bands.withIndex()) {
            val y0 = base + k * 2.2f
            val r0 = 1.6f - k * 0.16f
            paint(col) { lathe(listOf(r0 - 0.16f to y0 + 2.2f, r0 to y0), 12, Part.PAINTED, Vec3(at.x, 0f, at.z)) }
        }
        val top = base + bands.size * 2.2f
        cube(Vec3(at.x, top + 0.15f, at.z), Vec3(2.4f, 0.3f, 2.4f), rgb(0xFF37474F))
        paint(rgb(0xFFFFF1B0)) { lathe(listOf(0.7f to top + 1.5f, 0.7f to top + 0.3f), 10, Part.BULB, Vec3(at.x, 0f, at.z)) }
        paint(rgb(0xFFD84B3B)) { lathe(listOf(0.02f to top + 2.6f, 1.1f to top + 1.5f), 10, Part.PAINTED, Vec3(at.x, 0f, at.z)) }
    }

    /** By the shore: rowboats pulled up on the sand, nets drying on poles, rocks, a bench. */
    private fun MeshBuilder.shoreProps() {
        for ((k, at) in listOf(Vec3(-17f, 0f, 5.5f), Vec3(-2.5f, 0f, 6.5f), Vec3(14f, 0f, 6f)).withIndex()) {
            val y = height(at.x, at.z) + 0.25f
            val hull = listOf(-1.6f to 0.5f, -1.3f to -0.15f, 1.3f to -0.15f, 1.8f to 0.5f)
            val col = rgb(listOf(0xFF3B7EA1, 0xFFE07A3F, 0xFF2E8C88)[k])
            paint(col) { append(MeshBuilder().apply { extrude(hull, 1.2f, Part.PAINTED) }.build(), Mat4.trs(Vec3(at.x, y, at.z), rotY = 0.6f + k * 0.9f)) }
            paint(rgb(0xFFF5EBD8)) { append(MeshBuilder().apply { box(Vec3(0f, 0.42f, 0f), Vec3(2.9f, 0.08f, 1.0f), Part.PAINTED) }.build(), Mat4.trs(Vec3(at.x, y, at.z), rotY = 0.6f + k * 0.9f)) }
        }
        // Nets drying between poles.
        for (x in listOf(-22f, 18f)) {
            val z = shoreZ(x) - 3f
            val y = height(x, z)
            for (dx in listOf(-2f, 2f)) pole(Vec3(x + dx, y - 0.3f, z), Vec3(x + dx, y + 2.2f, z), 0.1f, rgb(0xFF6D4C35), 5)
            cube(Vec3(x, y + 1.4f, z), Vec3(4f, 1.4f, 0.05f), rgb(0xFF7D8F86))
        }
        // Rocks along the shore and on the headlands.
        for (k in 0 until 14) {
            val x = if (k % 2 == 0) -26f - 18f * hash01(k, 1, 13) else 26f + 18f * hash01(k, 1, 13)
            val z = shoreZ(x) + 2f + 8f * hash01(k, 2, 13)
            rockAt(Vec3(x, height(x, z) - 0.3f, z), 1.4f + 2f * hash01(k, 3, 13), k % 8)
        }
        // Benches and lamps along the street.
        for (z in listOf(-4f, -12f, -20f)) lamp(Vec3(3.6f + (z + 4f) * -0.2f, height(3.6f, z), z), 3f)
    }

    /** The lights that glow at night (for gulls and sparkles, see VillageScene): bulb positions. */
    val lighthouseLamp = Vec3(18f, 15.4f, 36f)
}
