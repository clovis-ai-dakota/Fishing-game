package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.core.GameConfig
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
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
        val bend = cubic(0f to -0.15f * s, 0f to -0.95f * s, 0.95f * s to -0.95f * s, 0.85f * s to 0.10f * s, 22)
            .drop(1).map { Vec3(it.first, it.second, 0f) }
        val path = shank + bend
        // Forged wire: full thickness along the shank and bend, tapering to a sharp point.
        b.tube(path, { u -> r * (1f - 0.8f * smoothstep(0.82f, 1f, u)) }, 10, Part.METAL)
        // The barb: a flattened spur angled back from just below the point.
        b.tube(listOf(Vec3(0.83f * s, 0.02f * s, 0f), Vec3(0.66f * s, -0.24f * s, 0f)), { u -> r * (0.75f - 0.7f * u) }, 8, Part.METAL, flattenZ = 0.6f)
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
            Cosmetic.CORAL_HOOK -> {
                // A pearl on top of the eye.
                b.sphere(Vec3(0f, 1.4f * s + 0.3f * s, 0f), 0.14f * s, Part.RUNE, 8, 12)
            }
            else -> Unit
        }
        b.build()
    }

    /** The worm threaded on the shank, with a tail the shader wriggles. */
    fun worm(): MeshData = cached("worm") {
        val b = MeshBuilder()
        // A ringed body threaded on the shank (rings bulge slightly), rounded at the top.
        val body = (0..18).map { i -> val u = i / 18f; Vec3(0.02f * s, (0.68f - 1.05f * u) * s, (0.06f + 0.02f * sin(u * PI.toFloat())) * s) }
        b.tube(body, { u -> 0.16f * s * (1f + 0.07f * cos(u * 18f * 2f * PI.toFloat()) - 0.15f * smoothstep(0.85f, 1f, 1f - u)) }, 10, Part.WORM)
        val tail = (0..16).map { i ->
            val u = i / 16f
            Vec3(0.05f * s + 0.25f * s * u, -0.45f * s - 1.0f * s * u, 0.05f * s)
        }
        b.tube(tail, { u -> 0.13f * s * (1f - 0.55f * u) * (1f + 0.06f * cos(u * 14f * 2f * PI.toFloat())) }, 8, Part.WORM_TAIL)
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

    // ---- The boat --------------------------------------------------------------------------------
    //
    // A small wooden fishing boat seen from the side: the bow (raked, higher) to the left (-x),
    // a flat transom to the right, the wheelhouse forward and the working deck aft, where the
    // rod is. The waterline is y = 0, the deck about y = 1, the hull 5 m long and 2.3 m wide, the
    // same envelope as the 2D boat (BoatArt.kt), so skins and hand-made rigs sit where they did.

    /** The top of the hull's side (the sheer), rising towards the bow. */
    fun sheerAt(x: Float): Float = 1.0f + 0.42f * smoothstep(0.2f, 2.6f, -x).let { it * sqrt(it) } + 0.05f * smoothstep(1.6f, 2.45f, x)

    /** The bottom of the hull, rising to the forefoot at the bow and a little at the stern. */
    fun keelAt(x: Float): Float = -0.45f + 0.6f * smoothstep(1.1f, 2.7f, -x) + 0.22f * smoothstep(1.5f, 2.45f, x)

    /** Half the beam: a fine bow, full in the middle, slightly narrower at the transom. */
    fun halfBeamAt(x: Float): Float =
        if (x < 0f) 1.15f * (1f - smoothstep(0.5f, 2.72f, -x).let { it * it }) + 0.02f else 1.15f * (1f - 0.2f * smoothstep(1.0f, 2.45f, x)) + 0.02f

    /** How far the bow's stem leans forward at the top (it is vertical aft). */
    private fun rakeAt(x: Float) = 0.38f * smoothstep(1.4f, 2.7f, -x)

    private const val BOW_X = -2.7f
    private const val STERN_X = 2.45f

    /** The deck's height at [x] (a little below the sheer, inside the rails). */
    fun deckAt(x: Float) = sheerAt(x) - 0.06f

    /** The hull, deck, wheelhouse and fittings shared by every boat; [skin] adds its rigging. */
    fun boat(skin: Cosmetic?): MeshData = cached("boat:${skin?.name}") {
        val b = MeshBuilder()
        b.append(boatBase(antenna = skin == null))
        if (skin != null) {
            // A second stripe near the waterline, for the skins' two-colour hulls.
            val low = (0..24).map { i -> val x = -1.9f + 3.9f * i / 24f; Vec3(x, 0.32f, halfBeamAt(x) + 0.03f) }
            b.tube(low, { 0.045f }, 6, Part.TRIM)
        }
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

    /** Hull, wheelhouse and fittings; the antenna only on boats without a mast of their own. */
    private fun boatBase(antenna: Boolean): MeshData = cached("boatbase:$antenna") {
        val b = MeshBuilder()
        addHull(b)
        addWheelhouse(b)
        addFittings(b, rigged = !antenna)
        b.build()
    }

    /** For hand-made boats with sails: their mast replaces the antenna. */
    private fun boatWithoutAntenna(): MeshData = boatBase(antenna = false)

    private fun addHull(b: MeshBuilder) {
        val stations = 34
        val sides = 16
        val first = b.vertexCount
        val firstTri = b.triangleCount
        val rings = ArrayList<List<Int>>()
        for (i in 0..stations) {
            val x0 = BOW_X + (STERN_X - BOW_X) * i / stations
            val sheer = sheerAt(x0)
            val keel = keelAt(x0)
            val w = halfBeamAt(x0)
            val ring = ArrayList<Int>()
            for (k in 0..sides) {
                val a = k / sides.toFloat() * PI.toFloat() // 0: +z side at the sheer, pi: -z side at the sheer
                // Fuller sections (a firm bilge) with a little flare at the top.
                val down = sin(a).let { kotlin.math.abs(it) }.toDouble().pow(0.7).toFloat()
                val y = sheer - (sheer - keel) * down
                val flare = 1f + 0.06f * (1f - down)
                val z = cos(a) * w * flare
                val x = x0 - rakeAt(x0) * (y - keel) / max(0.05f, sheer - keel)
                ring += b.vertex(Vec3(x, y, z), Vec3(0f, -sin(a), cos(a)), i / stations.toFloat(), k / sides.toFloat(), Part.HULL)
            }
            rings += ring
        }
        for (i in 0 until stations) for (k in 0 until sides) {
            val a = rings[i][k]; val c = rings[i + 1][k]
            b.quad(a, a + 1, c + 1, c)
        }
        b.smoothNormals(first, firstTri)
        // The transom (flat stern) and a cap at the stem, facing out.
        for ((end, sign) in listOf(stations to 1f, 0 to -1f)) {
            val x0 = BOW_X + (STERN_X - BOW_X) * end / stations
            val n = Vec3(sign, 0f, 0f)
            val ring = rings[end].map { idx -> b.position(idx) }
            val mid = Vec3(ring.sumOf { it.x.toDouble() }.toFloat() / ring.size, (sheerAt(x0) + keelAt(x0)) / 2f, 0f)
            val c = b.vertex(mid, n, 0.5f, 0.5f, Part.HULL)
            val ids = ring.map { b.vertex(it, n, 0.5f, 0.5f, Part.HULL) }
            for (k in 0 until ids.size - 1) if (sign > 0) b.triangle(c, ids[k], ids[k + 1]) else b.triangle(c, ids[k + 1], ids[k])
            if (sign > 0) b.triangle(c, ids.last(), ids.first()) else b.triangle(c, ids.first(), ids.last())
        }
        // Deck planks, inside the rails.
        val deckStart = b.vertexCount
        val deckStations = 24
        for (i in 0..deckStations) {
            val x0 = BOW_X + 0.15f + (STERN_X - 0.05f - BOW_X - 0.15f) * i / deckStations
            val x = x0 - rakeAt(x0)
            val w = (halfBeamAt(x0) - 0.05f).coerceAtLeast(0.01f)
            b.vertex(Vec3(x, deckAt(x0), w), Vec3.UP, i / deckStations.toFloat(), 1f, Part.WOOD)
            b.vertex(Vec3(x, deckAt(x0), -w), Vec3.UP, i / deckStations.toFloat(), 0f, Part.WOOD)
        }
        for (i in 0 until deckStations) {
            val a = deckStart + i * 2
            b.quad(a, a + 2, a + 3, a + 1)
        }
        // Rails along the sheer, and a rubbing strake below them.
        for (side in listOf(1f, -1f)) {
            val rail = (0..30).map { i ->
                val x0 = BOW_X + 0.08f + (STERN_X - BOW_X - 0.1f) * i / 30f
                Vec3(x0 - rakeAt(x0), sheerAt(x0) + 0.01f, side * (halfBeamAt(x0) * 1.06f + 0.015f))
            }
            b.tube(rail, { 0.07f }, 6, Part.TRIM)
            val strake = (0..26).map { i ->
                val x0 = BOW_X + 0.45f + (STERN_X - BOW_X - 0.5f) * i / 26f
                val y = sheerAt(x0) - 0.36f
                Vec3(x0 - rakeAt(x0) * 0.75f, y, side * (halfBeamAt(x0) * 1.05f + 0.02f))
            }
            b.tube(strake, { 0.04f }, 5, Part.TRIM)
        }
        // Keel and rudder, below the water.
        b.extrude(listOf(-1.9f to -0.32f, 2.1f to -0.3f, 2.05f to -0.56f, -1.5f to -0.58f), 0.14f, Part.HULL)
        b.extrude(listOf(2.32f to 0.05f, 2.66f to 0.0f, 2.62f to -0.52f, 2.36f to -0.6f), 0.08f, Part.HULL)
        b.tube(listOf(Vec3(2.36f, -0.6f, 0f), Vec3(2.36f, 0.4f, 0f)), { 0.035f }, 6, Part.MAST)
    }

    /** The wheelhouse: a slanted windscreen, framed windows, a door, a life ring and a roof. */
    private fun addWheelhouse(b: MeshBuilder) {
        val back = 0.35f
        val front = -1.35f
        val top = 2.25f
        val half = 0.62f
        // Side profile: a raked windscreen at the front, softly rounded corners.
        b.extrude(
            listOf(front to 0.9f, back to 0.9f, back to top - 0.06f, back - 0.06f to top, front + 0.32f to top, front + 0.27f to top - 0.04f, front to 1.72f),
            2f * half, Part.CABIN,
        )
        val face = half + 0.006f
        fun pane(x0: Float, x1: Float, y0: Float, y1: Float) {
            b.box(Vec3((x0 + x1) / 2f, (y0 + y1) / 2f, face), Vec3(x1 - x0, y1 - y0, 0.02f), Part.WINDOW)
            // White frames.
            b.box(Vec3((x0 + x1) / 2f, y1 + 0.035f, face + 0.012f), Vec3(x1 - x0 + 0.1f, 0.07f, 0.03f), Part.TRIM)
            b.box(Vec3((x0 + x1) / 2f, y0 - 0.035f, face + 0.012f), Vec3(x1 - x0 + 0.1f, 0.07f, 0.03f), Part.TRIM)
            b.box(Vec3(x0 - 0.035f, (y0 + y1) / 2f, face + 0.012f), Vec3(0.07f, y1 - y0, 0.03f), Part.TRIM)
            b.box(Vec3(x1 + 0.035f, (y0 + y1) / 2f, face + 0.012f), Vec3(0.07f, y1 - y0, 0.03f), Part.TRIM)
        }
        pane(-1.02f, -0.62f, 1.62f, 2.05f)
        pane(-0.5f, -0.18f, 1.62f, 2.05f)
        // The windscreen, on the raked front.
        run {
            val p0 = Vec3(front + 0.04f, 1.80f, half - 0.08f); val p1 = Vec3(front + 0.27f, 2.16f, half - 0.08f)
            val n = Vec3(-0.84f, 0.54f, 0f).normalized()
            val off = n * 0.01f
            val quad = listOf(p0 + off, p1 + off, Vec3(p1.x, p1.y, -half + 0.08f) + off, Vec3(p0.x, p0.y, -half + 0.08f) + off)
            val ids = quad.mapIndexed { k, q -> b.vertex(q, n, if (k == 1 || k == 2) 1f else 0f, if (k >= 2) 1f else 0f, Part.WINDOW) }
            b.quad(ids[0], ids[3], ids[2], ids[1])
        }
        // The door, aft, with a porthole and a handle.
        b.box(Vec3(0.08f, 1.43f, face + 0.005f), Vec3(0.4f, 0.98f, 0.02f), Part.ROOF)
        b.box(Vec3(0.08f, 1.43f, face + 0.016f), Vec3(0.46f, 1.04f, 0.01f), Part.TRIM)
        b.box(Vec3(0.08f, 1.43f, face + 0.02f), Vec3(0.36f, 0.94f, 0.012f), Part.ROOF)
        porthole(b, Vec3(0.08f, 1.72f, face + 0.03f), 0.1f)
        b.sphere(Vec3(0.22f, 1.38f, face + 0.05f), 0.035f, Part.METAL, 4, 8)
        // A life ring on the wall, red and white.
        ring(b, Vec3(-0.52f, 1.28f, face + 0.05f), 0.19f, 0.055f, Part.LIFE_RING)
        // The roof: an overhanging slab with a lip, in the hull's colour.
        b.box(Vec3((front + back) / 2f + 0.08f, top + 0.07f, 0f), Vec3(back - front + 0.36f, 0.14f, 2f * half + 0.26f), Part.ROOF)
        b.box(Vec3((front + back) / 2f + 0.08f, top + 0.155f, 0f), Vec3(back - front + 0.2f, 0.04f, 2f * half + 0.1f), Part.ROOF)
    }

    /** Antenna and light, fenders, a rope coil, aft rails and a rod holder. */
    private fun addFittings(b: MeshBuilder, rigged: Boolean) {
        val roof = 2.42f
        // Antenna mast with a cross-tree and a lantern at the top (rigged skins carry their own mast).
        if (!rigged) {
            b.tube(listOf(Vec3(0.18f, roof, 0f), Vec3(0.18f, 3.45f, 0f)), { u -> 0.04f - 0.015f * u }, 6, Part.MAST)
            b.tube(listOf(Vec3(-0.08f, 3.05f, 0f), Vec3(0.44f, 3.05f, 0f)), { 0.022f }, 5, Part.MAST)
            b.sphere(Vec3(0.18f, 3.5f, 0f), 0.075f, Part.LAMP, 6, 10)
        }
        // A searchlight on the roof.
        b.tube(listOf(Vec3(-0.9f, roof + 0.12f, 0.32f), Vec3(-1.08f, roof + 0.12f, 0.32f)), { 0.075f }, 10, Part.REEL)
        b.sphere(Vec3(-1.09f, roof + 0.12f, 0.32f), 0.06f, Part.LAMP, 5, 10, Vec3(0.4f, 1f, 1f))
        b.tube(listOf(Vec3(-0.98f, roof, 0.32f), Vec3(-0.98f, roof + 0.06f, 0.32f)), { 0.03f }, 5, Part.MAST)
        // Fenders hanging over the side on short ropes.
        for (x in listOf(-1.75f, 1.25f)) {
            val z = halfBeamAt(x) * 1.07f + 0.12f
            val yTop = sheerAt(x) - 0.42f
            b.lathe(listOf(0.0f to 0.0f, 0.08f to -0.02f, 0.12f to -0.1f, 0.13f to -0.3f, 0.12f to -0.42f, 0.08f to -0.5f, 0.0f to -0.52f), 10, Part.FENDER, Vec3(x, yTop, z))
            b.tube(listOf(Vec3(x, yTop, z), Vec3(x, sheerAt(x) + 0.02f, z - 0.06f)), { 0.015f }, 4, Part.ROPE)
        }
        // Aft rails: posts and a top rail along the working deck.
        val posts = listOf(0.55f, 1.05f, 1.55f, 2.05f)
        for (side in listOf(1f, -1f)) {
            for (x in posts) {
                val z = side * (halfBeamAt(x) * 1.04f)
                b.tube(listOf(Vec3(x, sheerAt(x), z), Vec3(x, sheerAt(x) + 0.42f, z)), { 0.025f }, 5, Part.TRIM)
            }
            b.tube(posts.map { x -> Vec3(x, sheerAt(x) + 0.42f, side * (halfBeamAt(x) * 1.04f)) }, { 0.028f }, 5, Part.TRIM)
        }
        // A coil of rope on the deck.
        for ((r, h) in listOf(0.24f to 0.04f, 0.17f to 0.09f)) {
            ring(b, Vec3(1.65f, deckAt(1.65f) + h, -0.35f), r, 0.045f, Part.ROPE, flat = true)
        }
        // The rod holder on the rail, where the rod's butt sits.
        b.tube(listOf(Vec3(0.86f, sheerAt(0.86f) - 0.1f, 0.84f), Vec3(0.94f, sheerAt(0.86f) + 0.22f, 0.84f)), { 0.05f }, 8, Part.REEL)
        // A small anchor at the bow.
        val ax = -2.15f
        val az = halfBeamAt(ax) * 1.05f + 0.03f
        val anchor = listOf(-0.04f to 0.2f, 0.04f to 0.2f, 0.04f to -0.08f, 0.14f to 0.0f, 0.16f to -0.05f, 0.03f to -0.2f, -0.03f to -0.2f, -0.16f to -0.05f, -0.14f to 0.0f, -0.04f to -0.08f)
        b.extrude(anchor.map { (x, y) -> ax + x * 1.2f to sheerAt(ax) - 0.42f + y * 1.2f }, 0.03f, Part.MAST, z = az)
    }

    /** A torus: a ring of [r] around [c], facing +z (or flat, facing up), tube radius [tube]. */
    private fun ring(b: MeshBuilder, c: Vec3, r: Float, tube: Float, part: Float, flat: Boolean = false) {
        val path = (0..24).map { k ->
            val a = k / 24f * 2f * PI.toFloat()
            if (flat) Vec3(c.x + cos(a) * r, c.y, c.z + sin(a) * r) else Vec3(c.x + cos(a) * r, c.y + sin(a) * r, c.z)
        }
        b.tube(path, { tube }, 8, part, cap = false)
    }

    /** A round window with a frame, facing +z. */
    private fun porthole(b: MeshBuilder, c: Vec3, r: Float) {
        val glass = (0 until 14).map { k -> val a = k / 14f * 2f * PI.toFloat(); c.x + cos(a) * r to c.y + sin(a) * r }
        b.extrude(glass, 0.01f, Part.WINDOW, z = c.z)
        ring(b, c + Vec3(0f, 0f, 0.008f), r + 0.02f, 0.022f, Part.TRIM)
    }

    /**
     * A hand-made boat (Skins > Customise): the classic hull and cabin, plus the chosen sail,
     * flag and emblem. Colours and the hull pattern are shader uniforms, so only these three
     * choices change the mesh.
     */
    fun customBoat(sail: SailStyle, flag: FlagStyle, emblem: Emblem): MeshData = cached("boatd:${sail.name}:${flag.name}:${emblem.name}") {
        val b = MeshBuilder()
        b.append(if (sail == SailStyle.NONE) boat(null) else boatWithoutAntenna())
        // Where the emblem goes: on the sail, else the flag, else the hull's side.
        var emblemAt: Vec3? = null
        var emblemSize = 0.7f
        when (sail) {
            SailStyle.NONE -> Unit
            SailStyle.TRIANGLE -> {
                b.tube(listOf(Vec3(-0.2f, 1.0f, 0f), Vec3(-0.2f, 5.2f, 0f)), { 0.06f }, 8, Part.MAST)
                b.extrude(quadratic(-0.3f to 5.0f, -1.8f to 3.2f, -2.4f to 1.5f, 10) + listOf(-0.3f to 1.5f), 0.04f, Part.SAIL, z = 0.1f)
                // High on the sail: lower down, the cabin's roof hides it from the raised camera.
                emblemAt = Vec3(-0.95f, 3.3f, 0.16f); emblemSize = 0.6f
            }
            SailStyle.SQUARE -> {
                b.tube(listOf(Vec3(-0.2f, 1.0f, 0f), Vec3(-0.2f, 5.3f, 0f)), { 0.07f }, 8, Part.MAST)
                val top = 4.9f; val bottom = 2.6f
                val sailShape = listOf(-1.7f to top, 1.3f to top) + quadratic(1.3f to top, 1.65f to (top + bottom) / 2f, 1.2f to bottom, 6).drop(1) +
                    listOf(-1.6f to bottom) + quadratic(-1.6f to bottom, -1.4f to (top + bottom) / 2f, -1.7f to top, 6).drop(1).dropLast(1)
                b.extrude(sailShape, 0.05f, Part.SAIL, z = 0.12f)
                b.tube(listOf(Vec3(-1.9f, top + 0.05f, 0.05f), Vec3(1.5f, top + 0.05f, 0.05f)), { 0.05f }, 6, Part.MAST)
                emblemAt = Vec3(-0.2f, 3.75f, 0.18f); emblemSize = 0.85f
            }
            SailStyle.TWIN -> {
                b.tube(listOf(Vec3(-1.2f, 1.0f, 0f), Vec3(-1.2f, 4.6f, 0f)), { 0.055f }, 8, Part.MAST)
                b.tube(listOf(Vec3(0.6f, 1.0f, 0f), Vec3(0.6f, 5.4f, 0f)), { 0.06f }, 8, Part.MAST)
                b.extrude(quadratic(-1.3f to 4.4f, -2.3f to 3.0f, -2.7f to 1.6f, 8) + listOf(-1.3f to 1.6f), 0.04f, Part.SAIL, z = 0.1f)
                b.extrude(quadratic(0.5f to 5.2f, -0.6f to 3.4f, -0.9f to 2.5f, 8) + listOf(0.5f to 2.5f), 0.04f, Part.SAIL, z = 0.1f)
                emblemAt = Vec3(-0.05f, 3.2f, 0.16f); emblemSize = 0.5f
            }
        }
        val mastTop = when (sail) {
            SailStyle.NONE -> null
            SailStyle.TRIANGLE -> Vec3(-0.2f, 5.2f, 0f)
            SailStyle.SQUARE -> Vec3(-0.2f, 5.3f, 0f)
            SailStyle.TWIN -> Vec3(0.6f, 5.4f, 0f)
        }
        if (flag != FlagStyle.NONE) {
            // Without a sail, the flag gets its own little mast on the cabin.
            val top = mastTop ?: Vec3(-0.5f, 4.0f, 0f).also { b.tube(listOf(Vec3(-0.5f, 2.3f, 0f), it), { 0.05f }, 8, Part.MAST) }
            val shape = when (flag) {
                FlagStyle.PENNANT -> listOf(0f to 0f, 1.6f to -0.35f, 0f to -0.7f)
                FlagStyle.SWALLOWTAIL -> listOf(0f to 0f, 1.5f to 0f, 1.0f to -0.4f, 1.5f to -0.8f, 0f to -0.8f)
                FlagStyle.BANNER -> listOf(0f to 0f, 1.3f to 0f, 1.3f to -0.9f, 0.65f to -1.25f, 0f to -0.9f)
                FlagStyle.NONE -> emptyList()
            }
            b.extrude(shape.map { (x, y) -> top.x + x to top.y + y }, 0.03f, Part.FLAG, z = 0.02f)
            if (emblemAt == null) {
                emblemAt = Vec3(top.x + 0.55f, top.y - 0.4f, 0.06f); emblemSize = 0.3f
            }
        }
        if (emblem != Emblem.NONE) {
            val at = emblemAt ?: Vec3(1.0f, 0.45f, 1.22f).also { emblemSize = 0.32f }
            val outline = emblemShape(emblem).map { (x, y) -> at.x + x * emblemSize to at.y + y * emblemSize }
            b.extrude(outline, 0.04f, Part.EMBLEM, z = at.z)
            if (emblem == Emblem.SKULL) {
                // Eyes and nose, in front of the skull.
                for (ex in listOf(-0.17f, 0.17f)) {
                    val eye = (0 until 10).map { k ->
                        val a = k / 10f * 2f * PI.toFloat()
                        at.x + (ex + cos(a) * 0.11f) * emblemSize to at.y + (0.06f + sin(a) * 0.12f) * emblemSize
                    }
                    b.extrude(eye, 0.02f, Part.MAST, z = at.z + 0.03f)
                }
                b.extrude(listOf(-0.05f to -0.16f, 0.05f to -0.16f, 0f to -0.06f).map { (x, y) -> at.x + x * emblemSize to at.y + y * emblemSize }, 0.02f, Part.MAST, z = at.z + 0.03f)
            }
        }
        b.build()
    }

    /** Emblem outlines, about 1 across, centred on the origin. */
    private fun emblemShape(e: Emblem): List<Pair<Float, Float>> = when (e) {
        Emblem.STAR -> (0 until 10).map { k ->
            val a = PI.toFloat() / 2f + k * PI.toFloat() / 5f
            val r = if (k % 2 == 0) 0.5f else 0.21f
            cos(a) * r to sin(a) * r
        }
        Emblem.HEART -> (0..24).map { k ->
            val t = k / 24f * 2f * PI.toFloat()
            val x = 16f * sin(t) * sin(t) * sin(t)
            val y = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
            x / 34f to y / 34f + 0.05f
        }.reversed()
        Emblem.MOON -> (0..16).map { k ->
            val a = -PI.toFloat() * 0.75f + k / 16f * PI.toFloat() * 1.5f
            cos(a) * 0.5f to sin(a) * 0.5f
        } + (16 downTo 0).map { k ->
            val a = -PI.toFloat() * 0.6f + k / 16f * PI.toFloat() * 1.2f
            0.18f + cos(a) * 0.38f to sin(a) * 0.4f
        }
        Emblem.FISH -> listOf(0.5f to 0f, 0.3f to 0.2f, 0f to 0.24f, -0.25f to 0.08f, -0.5f to 0.25f, -0.42f to 0f, -0.5f to -0.25f, -0.25f to -0.08f, 0f to -0.24f, 0.3f to -0.2f)
        Emblem.ANCHOR -> listOf(
            -0.06f to 0.38f, -0.06f to -0.22f, -0.3f to -0.08f, -0.38f to -0.16f, -0.06f to -0.48f, 0.06f to -0.48f,
            0.38f to -0.16f, 0.3f to -0.08f, 0.06f to -0.22f, 0.06f to 0.38f, 0.22f to 0.38f, 0.22f to 0.48f, -0.22f to 0.48f, -0.22f to 0.38f,
        )
        Emblem.WAVE -> (0..12).map { k -> -0.5f + k / 12f to 0.08f + sin(k / 12f * 2f * PI.toFloat() * 1.5f) * 0.14f } +
            (12 downTo 0).map { k -> -0.5f + k / 12f to -0.12f + sin(k / 12f * 2f * PI.toFloat() * 1.5f) * 0.14f }
        Emblem.CROWN -> listOf(-0.5f to -0.32f, 0.5f to -0.32f, 0.5f to 0.22f, 0.25f to -0.02f, 0f to 0.4f, -0.25f to -0.02f, -0.5f to 0.22f)
        // A friendly cartoon skull: round cranium, jaw, and no gore.
        Emblem.SKULL -> (0..18).map { k ->
            val a = -0.15f * PI.toFloat() + k / 18f * 1.3f * PI.toFloat()
            cos(a) * 0.42f to 0.08f + sin(a) * 0.4f
        } + listOf(-0.28f to -0.28f, -0.2f to -0.46f, 0.2f to -0.46f, 0.28f to -0.28f)
        Emblem.LIGHTNING -> listOf(0.12f to 0.5f, -0.28f to -0.04f, -0.02f to -0.04f, -0.14f to -0.5f, 0.3f to 0.08f, 0.04f to 0.08f)
        // A scallop: a fan of rounded lobes over a small hinge.
        // Counter-clockwise, like every outline here: the fan's rim from right to left, then the hinge.
        Emblem.SHELL -> (0..20).map { k ->
            val a = k / 20f * PI.toFloat()
            val r = 0.42f + 0.05f * cos(k * PI.toFloat())
            cos(a) * r to -0.12f + sin(a) * r
        } + listOf(-0.12f to -0.2f, -0.1f to -0.4f, 0.1f to -0.4f, 0.12f to -0.2f)
        Emblem.TRIDENT -> listOf(
            -0.05f to -0.5f, 0.05f to -0.5f, 0.05f to 0.02f, 0.27f to 0.02f, 0.27f to 0.28f, 0.35f to 0.28f, 0.31f to 0.5f,
            0.19f to 0.28f, 0.19f to 0.1f, 0.05f to 0.1f, 0.05f to 0.32f, 0.1f to 0.32f, 0f to 0.52f, -0.1f to 0.32f,
            -0.05f to 0.32f, -0.05f to 0.1f, -0.19f to 0.1f, -0.19f to 0.28f, -0.31f to 0.5f, -0.35f to 0.28f, -0.27f to 0.28f,
            -0.27f to 0.02f, -0.05f to 0.02f,
        )
        Emblem.NONE -> emptyList()
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
