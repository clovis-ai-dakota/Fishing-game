package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.GameConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Procedural meshes for everything that isn't a fish: the hook and bait, obstacles, the boat and
 * its skins, and background decor. Sizes are in meters unless noted.
 */
object PropModels {
    private val cache = HashMap<String, MeshData>()

    private fun cached(key: String, build: () -> MeshData) = synchronized(cache) { cache.getOrPut(key, build) }

    /** The hook's size: the same as the classic 2D hook and the collision radius, whatever the skin. */
    private val s = GameConfig.HOOK_RADIUS

    /**
     * The J-hook with its eye, the bend at the origin (where it catches fish), tied on at the eye
     * 1.4 s above. Skins change materials and add ornaments, never the hook's shape or size.
     */
    fun hook(skin: Cosmetic?): MeshData = cached("hook:${skin?.name}") {
        val b = MeshBuilder()
        val r = s * 0.08f
        val shank = listOf(Vec3(0f, 1.23f * s, 0f), Vec3(0f, 0.5f * s, 0f), Vec3(0f, -0.15f * s, 0f))
        val bend = cubic(0f to -0.15f * s, 0f to -0.95f * s, 0.95f * s to -0.95f * s, 0.85f * s to 0.10f * s, 14)
            .drop(1).map { Vec3(it.first, it.second, 0f) }
        val path = shank + bend
        b.tube(path, { u -> r * (1f - 0.35f * smoothstep(0.75f, 1f, u)) }, 10, Part.METAL)
        // Barb.
        b.tube(listOf(Vec3(0.85f * s, 0.10f * s, 0f), Vec3(0.62f * s, -0.20f * s, 0f)), { u -> r * (0.8f - 0.5f * u) }, 8, Part.METAL)
        // Eye: a ring the line ties to.
        val eye = (0..20).map { k ->
            val a = k / 20f * 2f * PI.toFloat()
            Vec3(sin(a) * 0.17f * s, 1.4f * s + cos(a) * 0.17f * s, 0f)
        }
        b.tube(eye, { r * 0.8f }, 8, Part.METAL, cap = false)
        when (skin) {
            Cosmetic.LEVIATHAN_BONE_HOOK -> for (k in 0 until 3) {
                // Carved rune rings that pulse cyan.
                val y = (1.05f - 0.32f * k) * s
                val ring = (0..16).map { j ->
                    val a = j / 16f * 2f * PI.toFloat()
                    Vec3(cos(a) * r * 1.25f, y, sin(a) * r * 1.25f)
                }
                b.tube(ring, { r * 0.35f }, 6, Part.RUNE, cap = false)
            }
            Cosmetic.CROWN_HOOK -> {
                // A little crown sitting on the eye.
                val base = 1.4f * s + 0.2f * s
                b.lathe(listOf(0.30f * s to base + 0.12f * s, 0.32f * s to base, 0.26f * s to base - 0.02f * s), 14, Part.RUNE)
                for (k in 0 until 5) {
                    val a = k / 5f * 2f * PI.toFloat()
                    b.tube(
                        listOf(Vec3(cos(a) * 0.30f * s, base + 0.10f * s, sin(a) * 0.30f * s), Vec3(cos(a) * 0.33f * s, base + 0.42f * s, sin(a) * 0.33f * s)),
                        { u -> 0.06f * s * (1f - 0.8f * u) }, 6, Part.RUNE,
                    )
                }
            }
            else -> Unit
        }
        b.build()
    }

    /** The worm threaded on the shank, with a tail the shader wriggles. */
    fun worm(): MeshData = cached("worm") {
        val b = MeshBuilder()
        b.tube(listOf(Vec3(0.02f * s, 0.65f * s, 0.06f * s), Vec3(0.02f * s, 0.15f * s, 0.08f * s), Vec3(0.02f * s, -0.35f * s, 0.06f * s)), { 0.16f * s }, 10, Part.WORM)
        val tail = (0..10).map { i ->
            val u = i / 10f
            Vec3(0.05f * s + 0.25f * s * u, -0.45f * s - 1.0f * s * u, 0.05f * s)
        }
        b.tube(tail, { u -> 0.13f * s * (1f - 0.5f * u) }, 8, Part.WORM_TAIL)
        b.build()
    }

    /** A small lead sinker, radius 1 (scaled when drawn). */
    fun sinker(): MeshData = cached("sinker") { MeshBuilder().apply { sphere(Vec3.ZERO, 1f, Part.METAL, 8, 12, Vec3(0.8f, 1.1f, 0.8f)) }.build() }

    /** A lumpy rock of radius about 1, different for every [variant]. */
    fun rock(variant: Int): MeshData = cached("rock:${variant % 8}") {
        val v = variant % 8
        val b = MeshBuilder()
        b.sphere(Vec3.ZERO, 1f, Part.ROCK, 9, 14)
        val m = b.build()
        // Push the vertices in and out (seams share positions, so the surface stays closed).
        val pos = m.positions.copyOf()
        for (i in 0 until m.vertexCount) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
            val n = 0.78f + 0.16f * sin(x * 3.1f + v) * cos(y * 2.7f - v * 0.7f) + 0.12f * sin(z * 4.3f + y * 1.7f + v * 1.3f)
            pos[i * 3] = x * n * 1.08f; pos[i * 3 + 1] = y * n * 0.86f; pos[i * 3 + 2] = z * n
        }
        recomputeNormals(MeshData(pos, m.normals, m.uvs, m.parts, null, m.indices))
    }

    /** A bent log about 2 long (x from -1 to 1), radius about 0.3. */
    fun driftwood(variant: Int): MeshData = cached("wood:${variant % 4}") {
        val v = variant % 4
        val b = MeshBuilder()
        val path = (0..12).map { i ->
            val t = i / 12f
            Vec3(-1f + 2f * t, sin(t * PI.toFloat()) * (0.12f + 0.05f * v), sin(t * 5f + v) * 0.05f)
        }
        b.tube(path, { u -> 0.27f + 0.05f * sin(u * 9f + v) - 0.06f * u }, 10, Part.WOOD)
        // A broken-off branch.
        b.tube(listOf(Vec3(0.2f, 0.25f, 0f), Vec3(0.45f, 0.62f, 0.05f)), { u -> 0.11f * (1f - 0.5f * u) }, 8, Part.WOOD)
        b.build()
    }

    /** A jelly cluster: a translucent bell about 1 wide with tentacles the shader sways. */
    fun jelly(): MeshData = cached("jelly") {
        val b = MeshBuilder()
        b.lathe(
            listOf(0f to 0.62f, 0.35f to 0.58f, 0.62f to 0.45f, 0.82f to 0.22f, 0.95f to -0.05f, 0.98f to -0.16f, 0.85f to -0.2f, 0.55f to -0.08f, 0f to -0.02f),
            20,
            Part.JELLY,
        )
        for (k in 0 until 8) {
            val a = k / 8f * 2f * PI.toFloat()
            val r = 0.85f
            val path = (0..10).map { i ->
                val u = i / 10f
                Vec3(cos(a) * r * (1f - 0.2f * u), -0.18f - 1.3f * u, sin(a) * r * (1f - 0.2f * u))
            }
            b.tube(path, { u -> 0.035f * (1f - 0.6f * u) }, 5, Part.JELLY_TENTACLE, cap = false)
        }
        // Frilly oral arms.
        for (k in 0 until 3) {
            val a = k / 3f * 2f * PI.toFloat() + 0.4f
            val path = (0..8).map { i ->
                val u = i / 8f
                Vec3(cos(a) * 0.2f + sin(u * 7f) * 0.06f, -0.1f - 1.0f * u, sin(a) * 0.2f)
            }
            b.tube(path, { u -> 0.09f * (1f - 0.5f * u) }, 7, Part.JELLY_TENTACLE)
        }
        b.build()
    }

    /** The boat's hull, deck, cabin and trim, centred on x = 0 with the waterline at y = 0. */
    fun boat(skin: Cosmetic?): MeshData = cached("boat:${skin?.name}") {
        val b = MeshBuilder()
        // Hull: U-shaped sections from bow to stern, deck at y = 1.
        val stations = 24
        val sides = 14
        val first = b.vertexCount
        val firstTri = b.triangleCount
        fun bottomAt(x: Float) = if (kotlin.math.abs(x) < 1.5f) -0.4f else -0.4f + 1.4f * smoothstep(1.5f, 2.5f, kotlin.math.abs(x))
        fun halfWidthAt(x: Float) = 1.15f * (1f - 0.85f * smoothstep(1.2f, 2.5f, kotlin.math.abs(x))) + 0.04f
        for (i in 0..stations) {
            val x = -2.5f + 5f * i / stations
            val bottom = bottomAt(x)
            val w = halfWidthAt(x)
            for (k in 0..sides) {
                val a = k / sides.toFloat() * PI.toFloat() // 0: +z side at deck, pi: -z side at deck
                val z = cos(a) * w
                val y = 1f - (1f - bottom) * sin(a)
                b.vertex(Vec3(x, y, z), Vec3(0f, -sin(a), cos(a)), i / stations.toFloat(), k / sides.toFloat(), Part.HULL)
            }
        }
        val stride = sides + 1
        for (i in 0 until stations) for (k in 0 until sides) {
            val a = first + i * stride + k
            val c = a + stride
            b.quad(a, a + 1, c + 1, c)
        }
        b.smoothNormals(first, firstTri)
        // Deck.
        val deck = (0..stations).flatMap { i ->
            val x = -2.5f + 5f * i / stations
            listOf(x to halfWidthAt(x), x to -halfWidthAt(x))
        }
        val deckStart = b.vertexCount
        for ((x, z) in deck) b.vertex(Vec3(x, 0.99f, z), Vec3.UP, (x + 2.5f) / 5f, if (z > 0) 1f else 0f, Part.WOOD)
        for (i in 0 until stations) {
            val a = deckStart + i * 2
            b.quad(a, a + 2, a + 3, a + 1)
        }
        // Trim along the gunwale, and a second stripe near the waterline for skins.
        val trimPath = (0..stations).map { i -> val x = -2.45f + 4.9f * i / stations; Vec3(x, 0.92f, halfWidthAt(x) + 0.02f) }
        b.tube(trimPath, { 0.07f }, 6, Part.TRIM)
        b.tube(trimPath.map { Vec3(it.x, it.y, -it.z) }, { 0.07f }, 6, Part.TRIM)
        if (skin != null) {
            val low = (0..stations).map { i -> val x = -1.9f + 3.8f * i / stations; Vec3(x, 0.15f, halfWidthAt(x) + 0.03f) }
            b.tube(low, { 0.05f }, 6, Part.TRIM)
        }
        // Cabin with windows and a roof.
        b.box(Vec3(-0.45f, 1.65f, 0f), Vec3(1.7f, 1.3f, 1.3f), Part.CABIN)
        b.box(Vec3(-0.45f, 2.36f, 0f), Vec3(1.9f, 0.12f, 1.5f), Part.MAST)
        b.box(Vec3(-0.7f, 1.78f, 0.66f), Vec3(0.6f, 0.5f, 0.03f), Part.WINDOW)
        b.box(Vec3(-0.7f, 1.78f, -0.66f), Vec3(0.6f, 0.5f, 0.03f), Part.WINDOW)
        b.box(Vec3(0.41f, 1.78f, 0f), Vec3(0.03f, 0.45f, 0.8f), Part.WINDOW)
        // Rigging, by skin.
        when (skin) {
            Cosmetic.GIANT_SLAYER_PENNANT -> {
                b.tube(listOf(Vec3(-0.4f, 2.3f, 0f), Vec3(-0.4f, 5.0f, 0f)), { 0.06f }, 8, Part.MAST)
            }
            Cosmetic.PRISM_SAIL -> {
                b.tube(listOf(Vec3(-0.2f, 1.0f, 0f), Vec3(-0.2f, 5.2f, 0f)), { 0.06f }, 8, Part.MAST)
                val sail = quadratic(-0.3f to 5.0f, -1.8f to 3.2f, -2.4f to 1.5f, 10) + listOf(-0.3f to 1.5f)
                b.extrude(sail, 0.04f, Part.SAIL, z = 0.1f)
            }
            Cosmetic.ROYAL_BOAT -> {
                b.tube(listOf(Vec3(-0.2f, 1.0f, 0f), Vec3(-0.2f, 5.4f, 0f)), { 0.08f }, 8, Part.TRIM)
                val top = 4.9f; val bottom = 2.6f
                val sail = listOf(-1.8f to top, 1.4f to top) + quadratic(1.4f to top, 1.75f to (top + bottom) / 2f, 1.3f to bottom, 6).drop(1) +
                    listOf(-1.7f to bottom) + quadratic(-1.7f to bottom, -1.5f to (top + bottom) / 2f, -1.8f to top, 6).drop(1).dropLast(1)
                b.extrude(sail, 0.05f, Part.SAIL, z = 0.12f)
                // Gold border behind the sail, and a crown emblem on it.
                b.extrude(sail.map { (x, y) -> (x - 0.2f) * 1.08f + 0.2f to (y - 3.75f) * 1.08f + 3.75f }, 0.04f, Part.TRIM, z = 0.07f)
                val cx = -0.2f; val cy = 3.75f; val w = 0.7f
                val crown = listOf(cx - w to cy - 0.35f, cx + w to cy - 0.35f, cx + w to cy + 0.25f, cx + w * 0.5f to cy - 0.05f, cx to cy + 0.45f, cx - w * 0.5f to cy - 0.05f, cx - w to cy + 0.25f)
                b.extrude(crown, 0.05f, Part.TRIM, z = 0.18f)
            }
            else -> Unit
        }
        b.build()
    }

    /** The Giant-slayer pennant's flag (the shader waves it), hung from the mast top at the origin. */
    fun pennantFlag(): MeshData = cached("flag:pennant") {
        MeshBuilder().apply {
            extrude(quadratic(0f to 0f, 1.2f to -0.25f, 2.4f to -0.6f, 8) + quadratic(2.4f to -0.6f, 1.2f to -1.0f, 0f to -1.3f, 8).drop(1), 0.03f, Part.FLAG)
        }.build()
    }

    /** The royal streamer, hung from the mast top at the origin. */
    fun royalStreamer(): MeshData = cached("flag:royal") {
        MeshBuilder().apply { extrude(listOf(0f to 0f, 1.3f to -0.2f, 0f to -0.42f), 0.03f, Part.TRIM) }.build()
    }

    /** A tall swaying kelp strand, [height] meters from its holdfast at the origin. */
    fun kelp(variant: Int): MeshData = cached("kelp:${variant % 4}") {
        val v = variant % 4
        val b = MeshBuilder()
        val height = 10f + v * 3f
        val path = (0..16).map { i ->
            val u = i / 16f
            Vec3(sin(u * 4f + v) * 0.4f, u * height, cos(u * 3f + v) * 0.3f)
        }
        b.tube(path, { u -> 0.12f * (1f - 0.5f * u) }, 6, Part.KELP, flattenZ = 0.5f)
        // Leaf blades along the stipe.
        for (k in 1 until 8) {
            val u = k / 8f
            val p = path[(u * 16).toInt()]
            val side = if (k % 2 == 0) 1f else -1f
            val blade = listOf(0f to 0f, side * 0.6f to 0.35f, side * 1.4f to 0.2f, side * 0.7f to -0.12f)
            b.extrude(blade.map { (x, y) -> p.x + x to p.y + y }, 0.03f, Part.KELP, z = p.z)
        }
        b.build()
    }

    /** A big background rock formation (decor only). */
    fun decorRock(variant: Int): MeshData = cached("decor:${variant % 6}") {
        val m = rock(variant + 3)
        val b = MeshBuilder()
        b.append(m, Mat4.scale(1f, 1.4f, 1f))
        b.append(rock(variant + 5), Mat4.trs(Vec3(0.9f, -0.4f, 0.3f), scale = Vec3(0.7f, 0.6f, 0.7f)))
        val built = b.build()
        MeshData(built.positions, built.normals, built.uvs, FloatArray(built.vertexCount) { Part.DECOR_ROCK }, null, built.indices)
    }

    /** A branching coral (decor in the shallows). */
    fun coral(variant: Int): MeshData = cached("coral:${variant % 4}") {
        val v = variant % 4
        val b = MeshBuilder()
        fun branch(from: Vec3, dir: Vec3, len: Float, r: Float, depth: Int) {
            val to = from + dir * len
            b.tube(listOf(from, Vec3.lerp(from, to, 0.5f) + Vec3(0.05f * sin(len * 7f), 0f, 0f), to), { u -> r * (1f - 0.3f * u) }, 6, Part.CORAL)
            if (depth > 0) {
                for (k in listOf(-1f, 1f)) {
                    val nd = Vec3(dir.x + k * 0.55f, dir.y, dir.z + 0.2f * k * (v - 1.5f) / 2f).normalized()
                    branch(to, nd, len * 0.72f, r * 0.7f, depth - 1)
                }
            }
        }
        branch(Vec3.ZERO, Vec3.UP, 1.2f, 0.18f, 3)
        b.build()
    }

    /** A camera-facing unit quad (particles are built on the CPU, see SceneBuilder). */
    fun fullscreenTriangle(): MeshData = cached("fullscreen") {
        MeshData(
            floatArrayOf(-1f, -1f, 0f, 3f, -1f, 0f, -1f, 3f, 0f),
            FloatArray(9) { if (it % 3 == 2) 1f else 0f },
            floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            FloatArray(3),
            null,
            intArrayOf(0, 1, 2),
        )
    }

    private fun recomputeNormals(m: MeshData): MeshData {
        // Smooth normals, merging the vertices that share a position (sphere seams and poles).
        val key = HashMap<Long, Int>()
        val group = IntArray(m.vertexCount)
        for (i in 0 until m.vertexCount) {
            val k = (Math.round(m.positions[i * 3] * 1000).toLong() * 73856093L) xor
                (Math.round(m.positions[i * 3 + 1] * 1000).toLong() * 19349663L) xor
                (Math.round(m.positions[i * 3 + 2] * 1000).toLong() * 83492791L)
            group[i] = key.getOrPut(k) { i }
        }
        val acc = FloatArray(m.vertexCount * 3)
        for (t in 0 until m.triangleCount) {
            val a = m.indices[t * 3]; val b = m.indices[t * 3 + 1]; val c = m.indices[t * 3 + 2]
            fun p(i: Int) = Vec3(m.positions[i * 3], m.positions[i * 3 + 1], m.positions[i * 3 + 2])
            val fn = (p(b) - p(a)) cross (p(c) - p(a))
            for (v in intArrayOf(a, b, c)) {
                val g = group[v]
                acc[g * 3] += fn.x; acc[g * 3 + 1] += fn.y; acc[g * 3 + 2] += fn.z
            }
        }
        val normals = FloatArray(m.vertexCount * 3)
        for (i in 0 until m.vertexCount) {
            val g = group[i]
            val n = Vec3(acc[g * 3], acc[g * 3 + 1], acc[g * 3 + 2]).normalized()
            normals[i * 3] = n.x; normals[i * 3 + 1] = n.y; normals[i * 3 + 2] = n.z
        }
        return MeshData(m.positions, normals, m.uvs, m.parts, m.colors, m.indices)
    }

    @Suppress("unused")
    private fun clamp01(x: Float) = max(0f, kotlin.math.min(1f, x))
}
