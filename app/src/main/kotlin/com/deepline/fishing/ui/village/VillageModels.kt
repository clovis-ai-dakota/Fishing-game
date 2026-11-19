package com.deepline.fishing.ui.village

import com.deepline.fishing.Building
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.MeshBuilder
import com.deepline.fishing.ui.render3d.ModelAssets
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Part
import com.deepline.fishing.ui.render3d.PropModels
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.render3d.bevelBox
import com.deepline.fishing.ui.render3d.hash01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The village's meshes, built in code like the rest of the game's models and painted per vertex
 * (the painted material): the island, the sea around it and the six places that open a menu.
 *
 * The look follows what stylised mobile games do on a phone's budget: the light is baked into the
 * vertex colours once (cast shadows from the sun in the alpha channel, ambient occlusion and a
 * gentle darkening towards the ground in the colour), edges are bevelled so they catch the light,
 * shapes are few, chunky and readable, and the still parts are merged into a few big meshes so a
 * frame is a handful of draws. Keys: `village:terrain`, `village:sea`, `village:town`,
 * `village:glass` and `village:beam`, and the decorations of the skin the player wears
 * (`village:dress:bunting` and one monument per legendary set, see [IslandDress]).
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
                "village:beam" -> beam()
                "village:dress:bunting" -> bunting()
                else -> if (key.startsWith("village:leaves:")) leaves(key.removePrefix("village:leaves:"))
                else if (key.startsWith("village:dress:")) monument(key.removePrefix("village:dress:"))
                else if (key.startsWith("village:theme:")) VillageThemes.build(com.deepline.fishing.core.MenuTheme.valueOf(key.removePrefix("village:theme:"))) else error("Unknown village mesh $key")
            }
        }
    }

    /**
     * Towards the sun: high, from the west and a little from the front, so the fronts facing the
     * player are lit and the shadows fall to the east, where they show. The moon shines from the
     * same side, so the baked shadows hold at night.
     */
    val SUN: Vec3 = Vec3(-0.65f, 0.9f, 0.22f).normalized()

    // ---- The island -------------------------------------------------------------------------------

    private val lighthouseAt get() = VillageLayout.spots.getValue(Building.DEX).center

    /**
     * The ground's height (m) at [x], [z]: a gentle green island with a sandy beach all round,
     * shelving into the sea, and a rocky knoll under the lighthouse.
     */
    fun height(x: Float, z: Float): Float {
        val s = VillageLayout.shoreDistance(x, z)
        val ground = when {
            s < 0f -> max(0.2f + s * 0.55f, -7f)
            s < 4f -> 0.2f + s * 0.18f
            else -> 0.92f + 1.5f * smooth(4f, 20f, s) + 0.7f * (noise(x * 0.09f, z * 0.09f) - 0.5f) * smooth(6f, 12f, s) * (1f - plotMask(x, z))
        }
        val l = lighthouseAt
        val dl = sqrt((x - l.x) * (x - l.x) + (z - l.z) * (z - l.z))
        return ground + 2.6f * (1f - smooth(3.5f, 10f, dl))
    }

    /** 1 on and right around a building's plot on land, 0 elsewhere (the ground is levelled there). */
    private fun plotMask(x: Float, z: Float): Float {
        var m = 0f
        for (b in listOf(Building.SHOP, Building.ROPE, Building.AQUARIUM)) {
            val s = VillageLayout.spots.getValue(b)
            m = max(m, 1f - smooth(0f, 3f, outside(x, z, s)))
        }
        return m
    }

    /** How far ([x], [z]) is outside [s]'s footprint (0 inside). */
    internal fun outside(x: Float, z: Float, s: VillageLayout.Spot): Float {
        val dx = max(abs(x - s.center.x) - s.half.x, 0f)
        val dz = max(abs(z - s.center.z) - s.half.z, 0f)
        return sqrt(dx * dx + dz * dz)
    }

    /** The floor of a building on land: its stone plinth's top, just above the highest ground under it. */
    fun floorOf(b: Building): Float {
        val s = VillageLayout.spots.getValue(b)
        var top = -10f
        for (sx in listOf(-1f, 0f, 1f)) for (sz in listOf(-1f, 0f, 1f)) {
            top = max(top, height(s.center.x + sx * s.half.x * 0.8f, s.center.z + sz * s.half.z * 0.8f))
        }
        return top + 0.3f
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun noise(x: Float, z: Float): Float {
        val ix = floor(x).toInt()
        val iz = floor(z).toInt()
        val fx = x - ix
        val fz = z - iz
        fun h(a: Int, b: Int) = hash01(a, b, 97)
        val ux = fx * fx * (3f - 2f * fx)
        val uz = fz * fz * (3f - 2f * fz)
        return (h(ix, iz) * (1 - ux) + h(ix + 1, iz) * ux) * (1 - uz) + (h(ix, iz + 1) * (1 - ux) + h(ix + 1, iz + 1) * ux) * uz
    }

    private fun distanceToSegment(x: Float, z: Float, a: Vec3, b: Vec3): Float {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val t = (((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz)).coerceIn(0f, 1f)
        val px = a.x + t * dx - x
        val pz = a.z + t * dz - z
        return sqrt(px * px + pz * pz)
    }

    /** The paths, from the square to each place's door (from, to, width). */
    internal val paths: List<Triple<Vec3, Vec3, Float>> by lazy {
        val sq = VillageLayout.square
        listOf(
            Triple(sq, Vec3(lighthouseAt.x, 0f, lighthouseAt.z + 3.2f), 2.2f),
            Triple(sq, Vec3(-11f, 0f, -13.6f), 2.4f),
            Triple(sq, Vec3(11f, 0f, -12.6f), 2.4f),
            Triple(sq, Vec3(-6.2f, 0f, -1.4f), 2.2f),
            Triple(sq, Vec3(VillageLayout.pierFrom.x, 0f, VillageLayout.pierFrom.z + 0.5f), 2.6f),
            Triple(Vec3(-4f, 0f, -6.5f), Vec3(VillageLayout.jettyFrom.x, 0f, VillageLayout.jettyFrom.z + 0.5f), 2f),
        )
    }

    internal fun pathDistance(x: Float, z: Float): Float {
        val sq = VillageLayout.square
        val plaza = sqrt((x - sq.x) * (x - sq.x) + (z - sq.z) * (z - sq.z)) - VillageLayout.SQUARE_R
        return min(plaza, paths.minOf { (a, e, w) -> distanceToSegment(x, z, a, e) - w / 2f })
    }

    // ---- Baked light -----------------------------------------------------------------------------

    /** Simple shapes standing in for what casts a shadow (tested against a ray towards the sun). */
    private sealed interface Occluder {
        fun hit(o: Vec3, d: Vec3): Boolean
        /** How far ([x], [z]) is from its foot on the ground (for contact shadows), or a large number. */
        fun footDistance(x: Float, z: Float): Float = 99f
    }

    private class BoxOcc(val min: Vec3, val max: Vec3) : Occluder {
        override fun hit(o: Vec3, d: Vec3): Boolean {
            var t0 = 0.02f
            var t1 = 1e6f
            for (k in 0..2) {
                val oo = when (k) { 0 -> o.x; 1 -> o.y; else -> o.z }
                val dd = when (k) { 0 -> d.x; 1 -> d.y; else -> d.z }
                val lo = when (k) { 0 -> min.x; 1 -> min.y; else -> min.z }
                val hi = when (k) { 0 -> max.x; 1 -> max.y; else -> max.z }
                if (abs(dd) < 1e-6f) {
                    if (oo < lo || oo > hi) return false
                } else {
                    var a = (lo - oo) / dd
                    var b = (hi - oo) / dd
                    if (a > b) { val s = a; a = b; b = s }
                    t0 = max(t0, a)
                    t1 = min(t1, b)
                    if (t0 > t1) return false
                }
            }
            return true
        }

        override fun footDistance(x: Float, z: Float): Float {
            if (min.y > 1.5f) return 99f
            val dx = max(max(min.x - x, x - max.x), 0f)
            val dz = max(max(min.z - z, z - max.z), 0f)
            return sqrt(dx * dx + dz * dz)
        }
    }

    private class CylOcc(val x: Float, val z: Float, val r: Float, val y0: Float, val y1: Float) : Occluder {
        override fun hit(o: Vec3, d: Vec3): Boolean {
            val ox = o.x - x
            val oz = o.z - z
            val a = d.x * d.x + d.z * d.z
            val b = 2f * (ox * d.x + oz * d.z)
            val c = ox * ox + oz * oz - r * r
            if (c <= 0f) return o.y <= y1 && o.y >= y0 - 0.01f
            val disc = b * b - 4f * a * c
            if (disc < 0f || a < 1e-8f) return false
            val t = (-b - sqrt(disc)) / (2f * a)
            if (t < 0.02f) return false
            val y = o.y + d.y * t
            return y in y0..y1
        }

        override fun footDistance(x: Float, z: Float): Float {
            if (y0 > 1.5f) return 99f
            return max(sqrt((x - this.x) * (x - this.x) + (z - this.z) * (z - this.z)) - r, 0f)
        }
    }

    private class SphereOcc(val c: Vec3, val r: Float) : Occluder {
        override fun hit(o: Vec3, d: Vec3): Boolean {
            val oc = o - c
            val b = oc dot d
            val cc = (oc dot oc) - r * r
            if (cc <= 0f) return true
            val disc = b * b - cc
            return disc >= 0f && -b - sqrt(disc) > 0.02f
        }
    }

    /** What casts shadows on the ground and the water: the buildings, the decks, the trees. */
    private val occluders: List<Occluder> by lazy {
        val out = ArrayList<Occluder>()
        val l = lighthouseAt
        val lg = height(l.x, l.z)
        out += CylOcc(l.x, l.z, 3f, lg - 1f, lg + 1f)
        out += CylOcc(l.x, l.z, 2.1f, lg, lg + 12f)
        out += CylOcc(l.x, l.z, 2.7f, lg + 12f, lg + 12.4f)
        out += CylOcc(l.x, l.z, 1.4f, lg + 12.4f, lg + 15.8f)
        // The halls: their walls, then their pitched roofs as two narrowing steps.
        for ((b, walls) in listOf(Building.SHOP to 4.6f, Building.ROPE to 4f)) {
            val s = VillageLayout.spots.getValue(b)
            val f = floorOf(b)
            val x0 = s.center.x - s.half.x
            val x1 = s.center.x + s.half.x
            val z0 = s.center.z - s.half.z
            val z1 = s.center.z + s.half.z - 0.3f
            val ridge = if (b == Building.SHOP) 2.7f else 2.4f
            out += BoxOcc(Vec3(x0, f - 1.5f, z0), Vec3(x1, f + walls + ridge * 0.35f, z1))
            val inset = (z1 - z0) * 0.3f
            out += BoxOcc(Vec3(x0, f + walls, z0 + inset), Vec3(x1, f + walls + ridge * 0.75f, z1 - inset))
            out += BoxOcc(Vec3(x0, f + walls, s.center.z - 0.5f), Vec3(x1, f + walls + ridge, s.center.z + 0.5f))
        }
        val aq = VillageLayout.spots.getValue(Building.AQUARIUM).center
        val af = floorOf(Building.AQUARIUM)
        out += CylOcc(aq.x, aq.z, AQ_R + 0.4f, af - 1.5f, af + 1.3f)
        out += SphereOcc(Vec3(aq.x, af + 1.3f, aq.z), AQ_R)
        val p0 = VillageLayout.pierFrom
        val p1 = VillageLayout.pierTo
        out += BoxOcc(Vec3(p0.x - 1.75f, 0.55f, p0.z), Vec3(p0.x + 1.75f, 0.95f, p1.z))
        val j0 = VillageLayout.jettyFrom
        val j1 = VillageLayout.jettyTo
        out += BoxOcc(Vec3(j0.x - 1.25f, 0.55f, j0.z), Vec3(j0.x + 1.25f, 0.95f, j1.z))
        val duel = VillageLayout.spots.getValue(Building.DUEL).center
        out += CylOcc(duel.x, duel.z, ARENA_R, -0.3f, 0.95f)
        // The boat at the pier (its usual size: the skins keep the same hull).
        val boat = VillageLayout.boatAt
        out += BoxOcc(Vec3(boat.x - 1.1f, 0f, boat.z - 2.6f), Vec3(boat.x + 1.1f, 2.2f, boat.z + 2.6f))
        // The theme's centrepiece in the square, as big as a building: a soft shadow its rough size
        // (the ground is shared by every theme).
        val sq = VillageLayout.square
        val sg = height(sq.x, sq.z)
        out += CylOcc(sq.x, sq.z, 2.8f, sg, sg + 3.2f)
        for (t in trees) {
            out += SphereOcc(Vec3(t.at.x, t.at.y + t.crownY, t.at.z), t.crownR)
            out += CylOcc(t.at.x, t.at.z, 0.25f * t.scale, t.at.y - 0.5f, t.at.y + t.crownY)
        }
        out
    }

    /**
     * Rays towards the sun's disc: the middle and four around it, so a shadow's edge goes soft
     * the further it falls from what casts it (a penumbra), as in the stylised games' baked light.
     */
    private val sunRays: List<Vec3> by lazy {
        val side = (SUN cross Vec3.UP).normalized()
        val up = (side cross SUN).normalized()
        val spread = 0.06f
        listOf(SUN) + listOf(side, -side, up, -up).map { (SUN + it * spread).normalized() }
    }

    /** How much of the sun ([p]) sees: 1 in the light, 0 deep in a cast shadow. */
    private fun sunlight(p: Vec3): Float {
        val o = p + Vec3(0f, 0.05f, 0f)
        var lit = 0
        for (d in sunRays) if (occluders.none { it.hit(o, d) }) lit++
        return lit / sunRays.size.toFloat()
    }

    /** Softens a grid of values (row-major, [w] wide) with a 3x3 average: no stair steps between vertices. */
    private fun blur(v: FloatArray, w: Int): FloatArray {
        val h = v.size / w
        val out = FloatArray(v.size)
        for (j in 0 until h) for (i in 0 until w) {
            var sum = 0f
            var n = 0
            for (dj in -1..1) for (di in -1..1) {
                val ii = i + di
                val jj = j + dj
                if (ii !in 0 until w || jj !in 0 until h) continue
                sum += v[jj * w + ii]
                n++
            }
            out[j * w + i] = sum / n
        }
        return out
    }

    /** Darkening of the ground at the foot of what stands on it (0 none, 1 right against it). */
    private fun contactShade(x: Float, z: Float): Float {
        var d = 99f
        for (o in occluders) d = min(d, o.footDistance(x, z))
        return 1f - smooth(0f, 1.8f, d)
    }

    // ---- Ground and sea --------------------------------------------------------------------------

    private fun terrain(): MeshData {
        val b = MeshBuilder()
        val step = 0.5f
        val xs = (0..132).map { -33f + it * step }
        val zs = (0..120).map { -50f + it * step }
        val stride = xs.size
        val sand = rgb(0xFFF3E1AF)
        val wetSand = rgb(0xFFD8BD85)
        val grass = rgb(0xFF8ACB62)
        val grassDeep = rgb(0xFF62AE55)
        val grassSun = rgb(0xFFB0D86A)
        val rock = rgb(0xFFA9A196)
        val rockDark = rgb(0xFF8A847C)
        val path = rgb(0xFFE6D3A8)
        val cobble = rgb(0xFFD2BE94)
        val flowers = listOf(rgb(0xFFFF8FA3), rgb(0xFFFFE066), rgb(0xFFFFFFFF), rgb(0xFFB39DDB))
        val l = lighthouseAt
        val land = BooleanArray(xs.size * zs.size)
        val light = FloatArray(xs.size * zs.size)
        for ((j, z) in zs.withIndex()) for ((i, x) in xs.withIndex()) {
            light[j * stride + i] = if (VillageLayout.shoreDistance(x, z) > -2f) sunlight(Vec3(x, height(x, z), z)) else 1f
        }
        val sun = blur(light, stride)
        for ((j, z) in zs.withIndex()) for ((i, x) in xs.withIndex()) {
            val s = VillageLayout.shoreDistance(x, z)
            land[j * stride + i] = s > -1.5f
            val y = height(x, z)
            var c = when {
                s < 0.9f -> wetSand
                s < 3.2f -> mix(wetSand, sand, smooth(0.9f, 1.8f, s))
                else -> {
                    val g = mix(grass, grassDeep, noise(x * 0.12f, z * 0.12f) * 0.9f)
                    mix(sand, mix(g, grassSun, smooth(1.4f, 2.6f, y) * 0.45f), smooth(3.2f, 4.6f, s))
                }
            }
            // The lighthouse's knoll: bare rock.
            val dl = sqrt((x - l.x) * (x - l.x) + (z - l.z) * (z - l.z))
            val rocky = 1f - smooth(5f, 8.5f, dl)
            c = mix(c, mix(rock, rockDark, noise(x * 0.6f, z * 0.6f)), rocky * 0.9f)
            // Paths and the paved square, with a cobble pattern.
            val pd = pathDistance(x, z)
            if (s > 1.2f && pd < 0.6f) {
                // Lighter in the middle where it's worn, a darker edge of stones.
                val paved = mix(path, cobble, smooth(-0.9f, 0.1f, pd))
                c = mix(c, paved, 1f - smooth(-0.1f, 0.5f, pd))
            }
            // A few wildflowers in the grass.
            if (s > 6f && pd > 1.2f && rocky < 0.1f) {
                val f = hash01(i, j, 59)
                if (f > 0.985f) c = flowers[(f * 1000f).toInt() % flowers.size]
            }
            // Contact shadow at the foot of the buildings, decks and trees.
            val ao = 1f - 0.32f * contactShade(x, z)
            c = floatArrayOf(c[0] * ao, c[1] * ao, c[2] * ao, sun[j * stride + i])
            b.vertex(Vec3(x, y, z), Vec3.UP, x / 40f, z / 40f, Part.PAINTED, c)
        }
        for (j in 0 until zs.size - 1) for (i in 0 until stride - 1) {
            val a = j * stride + i
            // Only the island and its edge: the sea bed is hidden under the water.
            if (!land[a] && !land[a + 1] && !land[a + stride] && !land[a + stride + 1]) continue
            b.quad(a, a + stride, a + stride + 1, a + 1)
        }
        b.smoothNormals()
        return b.build()
    }

    /** How far ([x], [z]) is from something standing in the water (pilings, the arena, the boat, rocks). */
    private fun waterObjectDistance(x: Float, z: Float): Float {
        var d = 99f
        val p0 = VillageLayout.pierFrom
        val p1 = VillageLayout.pierTo
        for (side in listOf(-1.55f, 1.55f)) d = min(d, distanceToSegment(x, z, Vec3(p0.x + side, 0f, p0.z), Vec3(p1.x + side, 0f, p1.z)) - 0.2f)
        d = min(d, sqrt((x - p1.x) * (x - p1.x) + (z - p1.z) * (z - p1.z)) - 1.6f)
        val j0 = VillageLayout.jettyFrom
        val j1 = VillageLayout.jettyTo
        for (side in listOf(-1.1f, 1.1f)) d = min(d, distanceToSegment(x, z, Vec3(j0.x + side, 0f, j0.z), Vec3(j1.x + side, 0f, j1.z)) - 0.18f)
        val duel = VillageLayout.spots.getValue(Building.DUEL).center
        d = min(d, abs(sqrt((x - duel.x) * (x - duel.x) + (z - duel.z) * (z - duel.z)) - ARENA_R))
        for (bu in buoys) d = min(d, sqrt((x - bu.x) * (x - bu.x) + (z - bu.z) * (z - bu.z)) - 0.45f)
        val boat = VillageLayout.boatAt
        d = min(d, distanceToSegment(x, z, Vec3(boat.x, 0f, boat.z - 2.2f), Vec3(boat.x, 0f, boat.z + 2.4f)) - 1.0f)
        for (r in seaRocks) d = min(d, sqrt((x - r.x) * (x - r.x) + (z - r.z) * (z - r.z)) - r.y)
        val row = rowboatAt
        d = min(d, sqrt((x - row.x) * (x - row.x) + (z - row.z) * (z - row.z)) - 1.4f)
        for (bu in pierBuoys) d = min(d, sqrt((x - bu.x) * (x - bu.x) + (z - bu.z) * (z - bu.z)) - 0.4f)
        return max(d, 0f)
    }

    /**
     * The sea, one big plane: fine around the island, coarse out to the horizon. Its colours are
     * data for the sea material: r the depth (0 shallow to 1 deep), g the closeness to the shore
     * (1 at the beach, 0 seven metres out: the foam lines), b the closeness to things standing in
     * the water (foam collars), a the sunlight (the decks' shadows).
     */
    private fun sea(): MeshData {
        val b = MeshBuilder()
        val coarse = listOf(-240f, -170f, -120f, -85f, -60f, -46f)
        val xs = coarse + (-38..38).map { it.toFloat() } + coarse.reversed().map { -it }
        val zs = coarse + (-54..36).map { it.toFloat() } + coarse.reversed().map { -it }
        val stride = xs.size
        val light = FloatArray(xs.size * zs.size)
        for ((j, z) in zs.withIndex()) for ((i, x) in xs.withIndex()) {
            val fine = x in -38f..38f && z in -54f..36f
            light[j * stride + i] = if (fine) sunlight(Vec3(x, 0f, z)) else 1f
        }
        val sun = blur(light, stride)
        for ((j, z) in zs.withIndex()) for ((i, x) in xs.withIndex()) {
            val fine = x in -38f..38f && z in -54f..36f
            if (!fine) {
                b.vertex(Vec3(x, 0f, z), Vec3.UP, x / 30f, z / 30f, Part.PAINTED, floatArrayOf(1f, 0f, 0f, 1f))
                continue
            }
            val s = VillageLayout.shoreDistance(x, z)
            val depth = max(0f, -height(x, z))
            val deep = smooth(0.2f, 6.5f, depth)
            val shore = if (s >= 0f) 1f else 1f - smooth(0f, 7f, -s)
            val collar = 1f - smooth(0f, 1.1f, waterObjectDistance(x, z))
            b.vertex(Vec3(x, 0f, z), Vec3.UP, x / 30f, z / 30f, Part.PAINTED, floatArrayOf(deep, shore, collar, sun[j * stride + i]))
        }
        for (j in 0 until zs.size - 1) for (i in 0 until stride - 1) {
            val a = j * stride + i
            b.quad(a, a + stride, a + stride + 1, a + 1)
        }
        return b.build()
    }

    // ---- Painting helpers ------------------------------------------------------------------------

    internal fun rgb(argb: Long) = floatArrayOf(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(4) { a[it] + (b[it] - a[it]) * t.coerceIn(0f, 1f) }

    internal fun shade(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k, c[3])

    /** [c] a little lighter or darker by [k] (-1..1) from a hash: planks and stones that aren't all alike. */
    private fun vary(c: FloatArray, i: Int, salt: Int, k: Float = 0.08f) = shade(c, 1f + k * (hash01(i, salt, 23) * 2f - 1f))

    /** Builds with [block], then paints what it added in [color]. */
    private inline fun MeshBuilder.paint(color: FloatArray, block: MeshBuilder.() -> Unit) {
        val from = vertexCount
        block()
        recolor(from, color)
    }

    private fun MeshBuilder.box(c: Vec3, size: Vec3, color: FloatArray, bevel: Float = 0.08f, part: Float = Part.PAINTED) =
        bevelBox(Mat4.translation(c.x, c.y, c.z), size, color, bevel, part)

    private fun MeshBuilder.boxR(c: Vec3, size: Vec3, color: FloatArray, rotX: Float = 0f, rotY: Float = 0f, rotZ: Float = 0f, bevel: Float = 0.08f, part: Float = Part.PAINTED) =
        bevelBox(Mat4.trs(c, rotX = rotX, rotY = rotY, rotZ = rotZ), size, color, bevel, part)

    /** A post, pole or piling from [a] to [b]. */
    private fun MeshBuilder.pole(a: Vec3, b: Vec3, r: Float, color: FloatArray, sides: Int = 8, cap: Boolean = false) =
        paint(color) { tube(listOf(a, b), { r }, sides, Part.PAINTED, cap = cap) }

    /** A flat disc of radius [r] at [y], facing up. */
    private fun MeshBuilder.disc(at: Vec3, r: Float, color: FloatArray, sides: Int, part: Float = Part.PAINTED) =
        paint(color) { lathe(listOf(0.01f to at.y, r to at.y), sides, part, Vec3(at.x, 0f, at.z)) }

    /** A ring of rope or iron through [points] (closed). */
    private fun MeshBuilder.loop(points: List<Vec3>, r: Float, color: FloatArray, sides: Int = 5) =
        paint(color) { tube(points + points.first(), { r }, sides, Part.PAINTED, cap = false) }

    private fun circle(c: Vec3, r: Float, n: Int, y: Float = c.y) = (0 until n).map { k ->
        val a = k / n.toFloat() * 2f * PI.toFloat()
        Vec3(c.x + cos(a) * r, y, c.z + sin(a) * r)
    }

    /**
     * A pitched roof over a [w] x [d] building whose walls end at [top]: the ridge runs along x at
     * [h] above the eaves, overhanging by [o]. Thick slabs with rows of tiles, a ridge cap and the
     * gable ends filled with the [wall] colour.
     */
    private fun MeshBuilder.gableRoof(cx: Float, cz: Float, w: Float, d: Float, top: Float, h: Float, o: Float, color: FloatArray, wall: FloatArray) {
        val run = d / 2f + o
        val slope = atan2(h, run)
        val len = sqrt(run * run + h * h)
        val thick = 0.32f
        val width = w + 2f * o
        for (side in listOf(1f, -1f)) {
            val rot = side * slope
            // The slab, centred halfway down the slope and sunk by half its thickness.
            val mid = Vec3(cx, top + h / 2f + thick * 0.5f * cos(slope), cz + side * run / 2f + side * thick * 0.5f * sin(slope))
            val m = Mat4.trs(mid, rotX = rot)
            bevelBox(m, Vec3(width, thick, len), shade(color, if (side > 0f) 1f else 0.9f), 0.1f, Part.DRESS_ROOF)
            // Rows of tiles: thin ribs across the slope, each a touch darker at its lower edge.
            val rows = (len / 0.62f).toInt()
            for (k in 1 until rows) {
                val along = -len / 2f + k * (len / rows)
                val local = Mat4.multiply(m, Mat4.translation(0f, thick / 2f + 0.03f, along * side))
                bevelBox(local, Vec3(width - 0.1f, 0.08f, 0.16f), shade(color, 0.86f), 0.03f, Part.DRESS_ROOF)
            }
        }
        // The ridge cap.
        box(Vec3(cx, top + h + thick * 0.6f, cz), Vec3(width + 0.1f, 0.34f, 0.5f), shade(color, 0.78f), 0.1f, Part.DRESS_ROOF)
        // Gable ends: the wall up under the roof, to the ridge.
        val atWall = top + h * o / run
        for ((x, sign) in listOf(cx - w / 2f to -1f, cx + w / 2f to 1f)) {
            facet(
                listOf(Vec3(x, top - 0.05f, cz - d / 2f), Vec3(x, top - 0.05f, cz + d / 2f), Vec3(x, atWall, cz + d / 2f), Vec3(x, top + h, cz), Vec3(x, atWall, cz - d / 2f)),
                Vec3(sign, 0f, 0f), wall,
            )
        }
    }

    /** A flat convex polygon facing [n] (wound to match it). */
    private fun MeshBuilder.facet(points: List<Vec3>, n: Vec3, color: FloatArray) {
        val ids = points.map { vertex(it, n, 0f, 0f, Part.PAINTED, color) }
        val flip = (((points[1] - points[0]) cross (points[2] - points[0])) dot n) < 0f
        for (k in 1 until ids.size - 1) if (flip) triangle(ids[0], ids[k + 1], ids[k]) else triangle(ids[0], ids[k], ids[k + 1])
    }

    /** A window on a wall facing +z at ([x], [y], [z]): a frame, a sill, a cross of glazing bars, glass that's lit at night. */
    private fun MeshBuilder.window(x: Float, y: Float, z: Float, w: Float = 1.1f, h: Float = 1.3f, frame: FloatArray = rgb(0xFFFFFBF2), shutters: FloatArray? = null) {
        box(Vec3(x, y, z + 0.06f), Vec3(w + 0.3f, h + 0.3f, 0.14f), frame, 0.05f)
        box(Vec3(x, y, z + 0.1f), Vec3(w, h, 0.08f), rgb(0xFF8DBBD2), 0.02f, Part.GLASS)
        box(Vec3(x, y, z + 0.16f), Vec3(0.09f, h, 0.05f), frame, 0.02f)
        box(Vec3(x, y + h * 0.12f, z + 0.16f), Vec3(w, 0.09f, 0.05f), frame, 0.02f)
        box(Vec3(x, y - h / 2f - 0.2f, z + 0.18f), Vec3(w + 0.5f, 0.14f, 0.32f), shade(frame, 0.92f), 0.04f)
        if (shutters != null) for (s in listOf(-1f, 1f)) box(Vec3(x + s * (w / 2f + 0.42f), y, z + 0.08f), Vec3(0.5f, h + 0.1f, 0.1f), shutters, 0.04f)
    }

    /** A door on a wall facing +z: frame, planks and a brass knob. */
    private fun MeshBuilder.door(x: Float, floor: Float, z: Float, w: Float = 1.3f, h: Float = 2.2f, color: FloatArray = rgb(0xFF7A4E33), frame: FloatArray = rgb(0xFFF5EBD8)) {
        box(Vec3(x, floor + h / 2f + 0.05f, z + 0.05f), Vec3(w + 0.36f, h + 0.2f, 0.12f), frame, 0.05f)
        for (k in 0 until 3) box(Vec3(x - w / 3f + k * w / 3f, floor + h / 2f, z + 0.1f), Vec3(w / 3f - 0.03f, h, 0.08f), vary(color, k, 7), 0.02f)
        box(Vec3(x + w * 0.32f, floor + h * 0.48f, z + 0.17f), Vec3(0.1f, 0.1f, 0.08f), rgb(0xFFF2B33D), 0.03f)
    }

    /** The ground under ([x], [z]). */
    internal fun ground(x: Float, z: Float) = Vec3(x, height(x, z), z)

    private fun MeshBuilder.rockAt(at: Vec3, size: Float, variant: Int, color: FloatArray = rgb(0xFFA9A196)) {
        paint(vary(color, variant, 5, 0.1f)) { append(PropModels.rock(variant), Mat4.trs(at, rotY = variant * 1.3f, scale = Vec3(size, size * 0.72f, size))) }
    }

    private fun MeshBuilder.barrel(at: Vec3, color: FloatArray = rgb(0xFFA0703F)) {
        paint(color) { lathe(listOf(0.4f to 1.1f, 0.48f to 0.82f, 0.5f to 0.55f, 0.48f to 0.28f, 0.4f to 0f), 12, Part.PAINTED, at) }
        disc(at + Vec3(0f, 1.1f, 0f), 0.4f, shade(color, 0.8f), 12)
        for (y in listOf(0.22f, 0.88f)) loop(circle(at + Vec3(0f, y, 0f), 0.47f, 14), 0.035f, rgb(0xFF5B5F63), 4)
    }

    private fun MeshBuilder.crate(at: Vec3, s: Float = 0.9f, fill: FloatArray? = null, turn: Float = 0f) {
        val wood = rgb(0xFFC8955A)
        boxR(at + Vec3(0f, s / 2f, 0f), Vec3(s, s, s), wood, rotY = turn, bevel = 0.06f)
        // Slats on the sides facing the player.
        for (k in 0 until 2) {
            val m = Mat4.multiply(Mat4.trs(at + Vec3(0f, s / 2f, 0f), rotY = turn), Mat4.translation(0f, -s / 4f + k * s / 2f, s / 2f + 0.02f))
            bevelBox(m, Vec3(s * 0.96f, s * 0.16f, 0.05f), shade(wood, 0.8f), 0.02f)
        }
        if (fill != null) boxR(at + Vec3(0f, s + 0.06f, 0f), Vec3(s * 0.86f, 0.14f, s * 0.86f), fill, rotY = turn, bevel = 0.05f)
    }

    internal fun MeshBuilder.lamp(at: Vec3, height: Float = 3.2f) {
        val iron = rgb(0xFF37474F)
        box(at + Vec3(0f, 0.12f, 0f), Vec3(0.5f, 0.24f, 0.5f), iron, 0.06f)
        pole(at, at + Vec3(0f, height, 0f), 0.08f, iron, 6)
        box(at + Vec3(0f, height + 0.25f, 0f), Vec3(0.44f, 0.5f, 0.44f), rgb(0xFFFFE08A), 0.04f, Part.BULB)
        paint(iron) { lathe(listOf(0.02f to height + 0.85f, 0.38f to height + 0.5f), 4, Part.PAINTED, at) }
    }

    /** A pennant on its pole: the cloth flutters (u along it). */
    private fun MeshBuilder.pennant(foot: Vec3, height: Float, length: Float, dir: Float, color: FloatArray, part: Float = Part.CLOTH) {
        pole(foot, foot + Vec3(0f, height, 0f), 0.09f, rgb(0xFF5D4037), 6)
        paint(rgb(0xFFF2B33D)) { sphere(foot + Vec3(0f, height + 0.1f, 0f), 0.16f, Part.PAINTED, 4, 6) }
        val top = foot + Vec3(0f, height - 0.15f, 0f)
        val n = Vec3(0f, 0f, 1f)
        val a = vertex(top, n, 0f, 0f, part, color)
        val b = vertex(top + Vec3(0f, -1.5f, 0f), n, 0f, 1f, part, color)
        val c = vertex(top + Vec3(dir * length, -0.75f, 0f), n, 1f, 0.5f, part, color)
        if (dir > 0f) triangle(a, b, c) else triangle(a, c, b)
    }

    // ---- Trees -----------------------------------------------------------------------------------

    private class Tree(val at: Vec3, val scale: Float, val palm: Boolean, val variant: Int) {
        // The CC0 models' crowns (see [nature]): a palm's fronds, a broad-leaved tree's canopy.
        val crownY get() = if (palm) 5.0f * scale else 5.4f * scale
        val crownR get() = if (palm) 1.9f * scale else 1.45f * scale
    }

    /** A few palms along the beach and round trees inland, never on a path or a building. */
    private val trees: List<Tree> by lazy {
        fun free(x: Float, z: Float): Boolean =
            pathDistance(x, z) > 1.6f &&
                VillageLayout.spots.values.none { outside(x, z, it) < 2.4f } &&
                sqrt((x - legendAt.x) * (x - legendAt.x) + (z - legendAt.z) * (z - legendAt.z)) > 4.2f &&
                sqrt((x - lighthouseAt.x).let { it * it } + (z - lighthouseAt.z).let { it * it }) > 7f
        val out = ArrayList<Tree>()
        // Palms on the beach (angle around the island, inland distance).
        for ((k, a) in listOf(0.05f, 0.5f, 0.95f, 2.75f, 3.35f, -0.6f, -2.45f, 1.6f).withIndex()) {
            val r = VillageLayout.islandRadius(a) - 2.4f - 0.8f * hash01(k, 1, 31)
            val x = VillageLayout.ISLAND_X + cos(a) * r
            val z = VillageLayout.ISLAND_Z + sin(a) * r
            if (free(x, z)) out += Tree(Vec3(x, height(x, z), z), 0.9f + 0.25f * hash01(k, 2, 31), true, k)
        }
        // Round trees in small groups between the places.
        val inland = listOf(
            -1.5f to -26f, 3f to -27.5f, 17f to -26f, 19.5f to -23f, -18f to -26f, -19.5f to -12f,
            17.5f to -7f, 19f to -10.5f, 1.5f to -3.5f, -2f to -5f, -15f to -30f, 9f to -31f,
        )
        for ((k, p) in inland.withIndex()) {
            val (x, z) = p
            if (free(x, z) && VillageLayout.shoreDistance(x, z) > 4.5f) out += Tree(Vec3(x, height(x, z), z), 0.8f + 0.35f * hash01(k, 3, 31), false, k)
        }
        out
    }

    /** Rocks along the coast, half in the water (each with its foam collar): (x, z) and size in y. */
    private val seaRocks: List<Vec3> by lazy {
        listOf(-2.15f, -1.75f, -1.25f, -0.35f, 0.2f, 2.45f, 3.0f).mapIndexed { k, a ->
            val r = VillageLayout.islandRadius(a) + 0.6f + 1.4f * hash01(k, 1, 17)
            Vec3(VillageLayout.ISLAND_X + cos(a) * r, 0.9f + 0.9f * hash01(k, 2, 17), VillageLayout.ISLAND_Z + sin(a) * r)
        }
    }

    /** The buoys around the duelling platform. */
    private val buoys: List<Vec3> by lazy {
        val c = VillageLayout.spots.getValue(Building.DUEL).center
        (0 until 6).map { k ->
            val a = k / 6f * 2f * PI.toFloat() + 0.3f
            Vec3(c.x + cos(a) * (ARENA_R + 2.4f), 0.1f, c.z + sin(a) * (ARENA_R + 2.4f))
        }
    }

    /** A CC0 model placed on the island (see [ModelAssets], `assets/models/CREDITS.md`). */
    private class Placed(val model: String, val at: FloatArray)

    /** The leaf textures the island's models use: one `village:leaves:<texture>` draw each. */
    val leafTextures: List<String> by lazy { nature.flatMap { p -> ModelAssets.model(p.model).leaves.mapNotNull { it.texture } }.distinct().sorted() }

    /**
     * What grows and lies around the places, from the CC0 packs: palms on the beach (Kenney),
     * broad-leaved trees, bushes and flowers inland (Quaternius), grass tufts, rocks in the surf
     * and on the sand, a rowboat by the jetty, flagged buoys off the pier, a barrel and a crate
     * on it. Never on a path or a building (the trees keep the spots of [trees]).
     */
    private val nature: List<Placed> by lazy {
        val out = ArrayList<Placed>()
        fun place(model: String, at: Vec3, scale: Float, turn: Float) { out += Placed(model, Mat4.trs(at, rotY = turn, scale = Vec3(scale, scale, scale))) }
        for (t in trees) {
            if (t.palm) {
                // The bent palm leans out to sea.
                val ox = t.at.x - VillageLayout.ISLAND_X
                val oz = t.at.z - VillageLayout.ISLAND_Z
                val bent = t.variant % 2 == 0
                place(if (bent) "palm-detailed-bend" else "palm-detailed-straight", t.at + Vec3(0f, -0.15f, 0f), 1.2f * t.scale, if (bent) atan2(oz, -ox) else t.variant * 1.3f)
            } else {
                place("CommonTree_3", t.at + Vec3(0f, -0.1f, 0f), 0.8f * t.scale, t.variant * 2.1f)
            }
        }
        // Bushes by the plinths, a few in flower.
        for ((k, p) in listOf(-17.2f to -14.6f, -4.4f to -16.8f, 3.4f to -16.6f, 17.6f to -13.4f, -15.6f to -1.2f, -3.2f to -31.5f, 0.6f to -33.5f).withIndex()) {
            val (x, z) = p
            place(if (k % 3 == 1) "Flower_3_Group" else "Bush_Common", ground(x, z) + Vec3(0f, -0.1f, 0f), if (k % 3 == 1) 0.8f else 1.05f + 0.2f * hash01(k, 1, 71), k * 1.7f)
        }
        // Tufts of grass on the lawns.
        for (k in 0 until 40) {
            val x = VillageLayout.ISLAND_X - 20f + 40f * hash01(k, 1, 73)
            val z = VillageLayout.ISLAND_Z - 18f + 34f * hash01(k, 2, 73)
            if (VillageLayout.shoreDistance(x, z) < 4f || pathDistance(x, z) < 1.4f || VillageLayout.spots.values.any { outside(x, z, it) < 1.5f }) continue
            if (trees.any { sqrt((it.at.x - x) * (it.at.x - x) + (it.at.z - z) * (it.at.z - z)) < 1.5f }) continue
            place("grass-plant", ground(x, z), 1.1f + 0.5f * hash01(k, 3, 73), k * 0.9f)
        }
        // Rocks in the surf, and two on the sand.
        for ((k, r) in seaRocks.withIndex()) place(if (k % 2 == 0) "Rock_Medium_1" else "Rock_Medium_2", Vec3(r.x, -0.6f, r.z), (0.55f + 0.35f * r.y) * 0.9f, k * 1.9f)
        for ((k, a) in listOf(1.2f, -1.0f).withIndex()) {
            val rr = VillageLayout.islandRadius(a) - 1.3f
            place("rocks-sand-a", ground(VillageLayout.ISLAND_X + cos(a) * rr, VillageLayout.ISLAND_Z + sin(a) * rr), 0.45f, k * 2.4f)
        }
        // By the jetty, a rowboat; off the pier's end, two flagged buoys; on the pier, a barrel and a crate.
        place("boat-row-small", rowboatAt, 1.2f, 0.35f)
        for (bu in pierBuoys) place("buoy-flag", bu, 0.8f, 0.6f)
        val p0 = VillageLayout.pierFrom
        place("barrel", Vec3(p0.x + 0.95f, p0.y, p0.z + 4.5f), 0.6f, 0.4f)
        place("crate", Vec3(p0.x - 0.95f, p0.y, p0.z + 7.5f), 0.75f, 0.2f)
        out
    }

    private val rowboatAt: Vec3 get() = Vec3(VillageLayout.jettyFrom.x + 2.4f, 0.02f, VillageLayout.jettyFrom.z + 6.5f)
    private val pierBuoys: List<Vec3> get() = listOf(-4.2f, 4.4f).map { dx -> Vec3(VillageLayout.pierTo.x + dx, -0.05f, VillageLayout.pierTo.z + 2.5f) }

    /** The leaves of the island's models in one texture, lit like the town (ambient occlusion baked in). */
    private fun leaves(texture: String): MeshData {
        val b = MeshBuilder()
        for ((i, p) in nature.withIndex()) for (piece in ModelAssets.model(p.model).leaves) if (piece.texture == texture) {
            val from = b.vertexCount
            b.append(piece.mesh, p.at)
            // Each tree its own shade of the island's green (a little lighter or deeper, warmer or cooler).
            val k = 0.9f + 0.2f * hash01(i, 1, 79)
            val warm = 0.06f * (hash01(i, 2, 79) - 0.5f)
            b.bakeColors(from) { _, _, _, c -> c[0] *= k * (1f + warm); c[1] *= k; c[2] *= k * (1f - warm) }
        }
        b.bakeColors { q, n, _, c ->
            val ground = max(height(q.x, q.z), 0f)
            val ao = 0.72f + 0.28f * smooth(0f, 3f, q.y - ground)
            val under = if (n.y < -0.3f) 0.85f else 1f
            for (k in 0..2) c[k] *= ao * under
        }
        return b.build()
    }

    // ---- The town: the six places and what grows around them --------------------------------------

    private const val AQ_R = 5f
    private const val ARENA_R = 6.6f

    private fun town(): MeshData {
        val b = MeshBuilder()
        b.lighthouse()
        b.market()
        b.ropeWorkshop()
        b.aquariumPavilion()
        b.pier()
        b.arena()
        // Trees, bushes, flowers, rocks and props: the CC0 models' painted parts (their leaves are
        // `village:leaves:*`, drawn with the leaf shader).
        for (p in nature) for (m in ModelAssets.model(p.model).painted) {
            val from = b.vertexCount
            b.append(m, p.at)
            // The rowboat by the jetty is painted like the player's boat (see IslandDress).
            if (p.model == "boat-row-small") b.repart(from, Part.DRESS_HULL)
        }
        // The light baked into the colours: ambient occlusion towards the ground (and the water),
        // so everything sits on the island instead of floating over it.
        b.bakeColors { p, n, part, c ->
            if (part == Part.GLASS || part == Part.BULB || part == Part.CLOTH || part == Part.DRESS_FLAG) return@bakeColors
            val ground = max(height(p.x, p.z), 0f)
            val ao = 0.7f + 0.3f * smooth(0f, 2.4f, p.y - ground)
            // Faces looking down (under eaves and decks) get a little less sky.
            val under = if (n.y < -0.3f) 0.85f else 1f
            for (k in 0..2) c[k] *= ao * under
        }
        return b.build()
    }

    /**
     * The lighthouse (the Dex): on its rocky knoll, an octagonal stone plinth, a tapering tower in
     * red and white bands with windows up the front, the gallery and its railing, the glass lantern
     * room (lit at night, with a turning beam drawn by the scene), a red dome and a golden fish
     * weathervane on top.
     */
    private fun MeshBuilder.lighthouse() {
        val c = lighthouseAt
        val g = height(c.x, c.z)
        val at = Vec3(c.x, 0f, c.z)
        val stone = rgb(0xFFD9D0C1)
        val red = rgb(0xFFD8473A)
        val white = rgb(0xFFFFF9EE)
        val iron = rgb(0xFF2F3E46)
        // Rocks around the knoll.
        for (k in 0 until 10) {
            val a = k / 10f * 2f * PI.toFloat() + 0.4f * hash01(k, 1, 13)
            val r = 3.6f + 2.2f * hash01(k, 2, 13)
            val x = c.x + cos(a) * r
            val z = c.z + sin(a) * r
            rockAt(Vec3(x, height(x, z) - 0.25f, z), 0.8f + 0.9f * hash01(k, 3, 13), k)
        }
        // The plinth, with steps up to the door.
        paint(stone) { lathe(listOf(3.05f to g + 1.0f, 3.05f to g - 1.6f), 8, Part.PAINTED, at) }
        disc(Vec3(c.x, g + 1.0f, c.z), 3.05f, shade(stone, 1.05f), 8)
        paint(shade(stone, 0.85f)) { lathe(listOf(3.25f to g + 0.15f, 3.25f to g - 1.6f), 8, Part.PAINTED, at) }
        for (k in 0 until 3) box(Vec3(c.x, g + 0.17f + k * 0.28f, c.z + 3.75f - k * 0.38f), Vec3(2.2f, 0.28f, 0.6f), vary(stone, k, 3), 0.05f)
        // The tower in bands.
        val y0 = g + 1f
        val tall = 11f
        fun r(y: Float) = 2.15f - 0.6f * (y - y0) / tall
        val bands = 5
        for (k in 0 until bands) {
            val lo = y0 + k * tall / bands
            val hi = lo + tall / bands
            paint(if (k % 2 == 0) white else red) { lathe(listOf(r(hi) to hi, r(lo) to lo), 28, if (k % 2 == 0) Part.PAINTED else Part.DRESS_ROOF, at) }
        }
        // A trim ring between each band.
        for (k in 1 until bands) {
            val y = y0 + k * tall / bands
            loop(circle(Vec3(c.x, y, c.z), r(y) + 0.02f, 28), 0.07f, shade(white, 0.88f), 4)
        }
        // Windows up the front, facing out.
        fun onTower(y: Float, angle: Float, size: Vec3, color: FloatArray, part: Float = Part.PAINTED, out: Float = 0f) {
            val rr = r(y) + out
            val p = Vec3(c.x + cos(angle) * rr, y, c.z + sin(angle) * rr)
            bevelBox(Mat4.trs(p, rotY = atan2(cos(angle), sin(angle))), size, color, 0.04f, part)
        }
        for ((k, a) in listOf(1.75f, 1.35f, 1.65f).withIndex()) {
            val y = y0 + 3.2f + k * 2.6f
            onTower(y, a, Vec3(0.8f, 1.05f, 0.16f), white, out = 0.02f)
            onTower(y, a, Vec3(0.56f, 0.8f, 0.12f), rgb(0xFF8DBBD2), Part.GLASS, out = 0.08f)
            onTower(y - 0.6f, a, Vec3(0.95f, 0.12f, 0.3f), white, out = 0.08f)
        }
        // The door, under a little roof.
        onTower(y0 + 1.15f, PI.toFloat() / 2f, Vec3(1.5f, 2.3f, 0.2f), white, out = 0.02f)
        onTower(y0 + 1.1f, PI.toFloat() / 2f, Vec3(1.1f, 2.1f, 0.14f), rgb(0xFF2F6E8E), out = 0.1f)
        onTower(y0 + 2.45f, PI.toFloat() / 2f, Vec3(1.8f, 0.16f, 0.8f), red, Part.DRESS_ROOF, out = 0.4f)
        // The gallery: a deck on brackets, posts and a rail.
        val gy = y0 + tall
        paint(iron) { lathe(listOf(2.75f to gy + 0.32f, 2.75f to gy), 28, Part.PAINTED, at) }
        disc(Vec3(c.x, gy + 0.32f, c.z), 2.75f, shade(iron, 1.25f), 28)
        paint(iron) { lathe(listOf(2.75f to gy + 0.01f, r(gy) to gy - 0.7f), 28, Part.PAINTED, at) }
        for (p in circle(Vec3(c.x, gy, c.z), 2.6f, 18)) pole(Vec3(p.x, gy + 0.3f, p.z), Vec3(p.x, gy + 1.25f, p.z), 0.045f, iron, 4)
        loop(circle(Vec3(c.x, gy + 1.25f, c.z), 2.6f, 36), 0.07f, iron, 5)
        loop(circle(Vec3(c.x, gy + 0.8f, c.z), 2.6f, 36), 0.04f, iron, 4)
        // The lantern room: a stone collar, glass, mullions and the lamp.
        paint(white) { lathe(listOf(1.45f to gy + 0.85f, 1.45f to gy + 0.3f), 20, Part.PAINTED, at) }
        paint(rgb(0xFFFFF1B0)) { lathe(listOf(1.2f to gy + 2.55f, 1.2f to gy + 0.85f), 16, Part.GLASS, at) }
        paint(rgb(0xFFFFE9A0)) { sphere(Vec3(c.x, gy + 1.7f, c.z), 0.62f, Part.BULB, 6, 10) }
        for (p in circle(Vec3(c.x, 0f, c.z), 1.24f, 8)) pole(Vec3(p.x, gy + 0.85f, p.z), Vec3(p.x, gy + 2.6f, p.z), 0.06f, iron, 4)
        // The dome, the finial and the fish weathervane.
        paint(red) {
            lathe(listOf(0.05f to gy + 3.75f, 0.6f to gy + 3.55f, 1.05f to gy + 3.15f, 1.38f to gy + 2.7f, 1.5f to gy + 2.55f), 20, Part.DRESS_ROOF, at)
        }
        loop(circle(Vec3(c.x, gy + 2.56f, c.z), 1.5f, 24), 0.08f, iron, 4)
        pole(Vec3(c.x, gy + 3.7f, c.z), Vec3(c.x, gy + 5.2f, c.z), 0.06f, iron, 5)
        paint(rgb(0xFFF2B33D)) { sphere(Vec3(c.x, gy + 3.95f, c.z), 0.22f, Part.PAINTED, 5, 8) }
        val fish = listOf(
            -1.0f to 0f, -0.7f to 0.34f, 0f to 0.5f, 0.7f to 0.3f, 1.0f to 0.04f, 1.45f to 0.32f, 1.45f to -0.32f, 1.0f to -0.04f,
            0.7f to -0.3f, 0f to -0.48f, -0.7f to -0.32f,
        )
        paint(rgb(0xFFF2B33D)) {
            append(MeshBuilder().apply { extrude(fish, 0.16f, Part.PAINTED) }.build(), Mat4.trs(Vec3(c.x, gy + 5.0f, c.z), rotY = 0.5f))
        }
    }

    /** The lamp at the top of the lighthouse (the beam turns around it at night). */
    val lighthouseLamp: Vec3 get() = Vec3(lighthouseAt.x, height(lighthouseAt.x, lighthouseAt.z) + 1f + 11f + 1.7f, lighthouseAt.z)

    /**
     * The market hall (the Shop): a half-timbered hall open at the front, under a terracotta roof,
     * a red and white striped awning over two stalls (fish on ice, fruit), crates and barrels, a
     * string of lanterns, and a big gold coin over the entrance.
     */
    private fun MeshBuilder.market() {
        val s = VillageLayout.spots.getValue(Building.SHOP)
        val c = s.center
        val f = floorOf(Building.SHOP)
        val w = 10f
        val d = 6.6f
        val plaster = rgb(0xFFF7EAD0)
        val timber = rgb(0xFF6E4A33)
        val roof = rgb(0xFFD2553A)
        val stone = rgb(0xFFD3C9B8)
        box(Vec3(c.x, f - 0.9f, c.z), Vec3(w + 1.2f, 1.8f, d + 1.4f), stone, 0.14f)
        val wallH = 4.6f
        val front = c.z + d / 2f
        val back = c.z - d / 2f
        // Back and side walls, with their timber frames.
        box(Vec3(c.x, f + wallH / 2f, back + 0.3f), Vec3(w, wallH, 0.6f), plaster, 0.06f)
        for (sx in listOf(-1f, 1f)) {
            val x = c.x + sx * (w / 2f - 0.3f)
            box(Vec3(x, f + wallH / 2f, c.z - 0.4f), Vec3(0.6f, wallH, d - 0.8f), plaster, 0.06f)
            for (zz in listOf(back + 0.2f, c.z - 0.4f, front - 0.9f)) box(Vec3(x + sx * 0.32f, f + wallH / 2f, zz), Vec3(0.12f, wallH, 0.32f), timber, 0.04f)
            for (yy in listOf(f + 0.2f, f + wallH * 0.55f, f + wallH - 0.1f)) box(Vec3(x + sx * 0.32f, yy, c.z - 0.4f), Vec3(0.12f, 0.3f, d - 0.6f), timber, 0.04f)
            // A brace in each panel.
            boxR(Vec3(x + sx * 0.32f, f + wallH * 0.3f, c.z - 1.6f), Vec3(0.1f, 0.24f, 2.6f), timber, rotX = 0.55f, bevel = 0.03f)
        }
        // The open front: posts and a beam.
        for (x in listOf(-4.7f, -1.6f, 1.6f, 4.7f)) box(Vec3(c.x + x, f + wallH / 2f, front - 0.2f), Vec3(0.38f, wallH, 0.38f), timber, 0.06f)
        box(Vec3(c.x, f + wallH - 0.2f, front - 0.2f), Vec3(w + 0.3f, 0.45f, 0.45f), timber, 0.08f)
        gableRoof(c.x, c.z, w, d, f + wallH, 2.7f, 0.55f, roof, plaster)
        // The striped awning sloping out over the stalls, with a scalloped edge.
        val stripes = 10
        val sw = (w + 0.4f) / stripes
        for (k in 0 until stripes) {
            val x = c.x - (w + 0.4f) / 2f + (k + 0.5f) * sw
            val col = if (k % 2 == 0) rgb(0xFFE5483C) else rgb(0xFFFFFBF2)
            val part = if (k % 2 == 0) Part.DRESS_TRIM else Part.PAINTED
            boxR(Vec3(x, f + wallH - 0.95f, front + 1.0f), Vec3(sw, 0.1f, 2.5f), col, rotX = 0.42f, bevel = 0.03f, part = part)
            boxR(Vec3(x, f + wallH - 1.62f, front + 2.12f), Vec3(sw * 0.9f, 0.42f, 0.06f), col, bevel = 0.03f, part = part)
        }
        // Stalls: fish on ice, and fruit.
        val stall = rgb(0xFFB98652)
        for ((k, x) in listOf(-2.6f, 2.6f).withIndex()) {
            box(Vec3(c.x + x, f + 0.5f, front - 0.9f), Vec3(3.4f, 1.0f, 1.1f), stall, 0.06f)
            for (p in 0 until 4) box(Vec3(c.x + x - 1.25f + p * 0.83f, f + 0.5f, front - 0.33f), Vec3(0.76f, 0.9f, 0.06f), vary(stall, p + k * 4, 9), 0.02f)
            box(Vec3(c.x + x, f + 1.06f, front - 0.9f), Vec3(3.5f, 0.12f, 1.2f), shade(stall, 0.8f), 0.04f)
        }
        box(Vec3(c.x - 2.6f, f + 1.18f, front - 0.9f), Vec3(3.1f, 0.14f, 0.95f), rgb(0xFFE8F4FA), 0.04f)
        for (k in 0 until 5) {
            val col = rgb(listOf(0xFF9FB4C0, 0xFFFFA45C, 0xFF5CC1E8, 0xFFF07FA0, 0xFFB7C7A0)[k])
            paint(col) { sphere(Vec3(c.x - 3.8f + k * 0.6f, f + 1.32f, front - 0.9f + (k % 2) * 0.12f), 0.22f, Part.PAINTED, 4, 8, Vec3(1.4f, 0.5f, 0.7f)) }
        }
        for (k in 0 until 9) {
            val row = if (k < 5) 0 else 1
            val i = if (k < 5) k else k - 5
            paint(rgb(listOf(0xFFFF7043, 0xFFFFCA28, 0xFF9CCC65)[k % 3])) {
                sphere(Vec3(c.x + 1.55f + i * 0.5f + row * 0.25f, f + 1.32f + row * 0.3f, front - 0.9f - row * 0.1f), 0.22f, Part.PAINTED, 4, 7)
            }
        }
        crate(ground(c.x + w / 2f + 1.3f, c.z + 1.6f), 0.95f, rgb(0xFF64B5F6), 0.2f)
        val stack = ground(c.x + w / 2f + 1.4f, c.z + 0.3f)
        crate(stack, 0.85f, null, -0.1f)
        crate(stack + Vec3(0f, 0.85f, 0f), 0.7f, rgb(0xFFFFB74D), 0.3f)
        barrel(ground(c.x - w / 2f - 1.3f, c.z + 1.8f))
        barrel(ground(c.x - w / 2f - 1.5f, c.z + 0.6f), rgb(0xFF8E6440))
        // A string of lanterns along the awning's edge.
        val string = (0..12).map { k ->
            val x = c.x - w / 2f - 0.2f + k * ((w + 0.4f) / 12f)
            Vec3(x, f + wallH - 1.75f - sin(k / 12f * PI.toFloat()) * 0.35f, front + 2.25f)
        }
        paint(rgb(0xFF4E3B30)) { tube(string, { 0.025f }, 3, Part.PAINTED, cap = false) }
        for ((k, p) in string.withIndex()) if (k % 2 == 1) {
            box(p + Vec3(0f, -0.25f, 0f), Vec3(0.28f, 0.34f, 0.28f), rgb(listOf(0xFFFFD54F, 0xFFFF8A65, 0xFF81D4FA)[k % 3]), 0.06f, Part.BULB)
        }
        // The sign: a gold coin over the entrance, on a bracket under the ridge.
        val coinAt = Vec3(c.x, f + wallH + 1.25f, front + 0.75f)
        paint(rgb(0xFFF2B33D)) { append(MeshBuilder().apply { tube(listOf(Vec3(0f, 0f, -0.12f), Vec3(0f, 0f, 0.12f)), { 0.85f }, 24, Part.PAINTED, cap = false) }.build(), Mat4.translation(coinAt.x, coinAt.y, coinAt.z)) }
        paint(rgb(0xFFF7C95A)) { append(MeshBuilder().apply { lathe(listOf(0.01f to 0.13f, 0.85f to 0.12f), 24, Part.PAINTED) }.build(), Mat4.trs(coinAt, rotX = PI.toFloat() / 2f)) }
        loop((0 until 24).map { k -> val a = k / 24f * 2f * PI.toFloat(); coinAt + Vec3(cos(a) * 0.6f, sin(a) * 0.6f, 0.15f) }, 0.05f, rgb(0xFFC98A1E), 4)
        box(coinAt + Vec3(0f, 0f, 0.16f), Vec3(0.18f, 0.72f, 0.06f), rgb(0xFFC98A1E), 0.02f)
    }

    /**
     * The rope workshop (Rope): a long shed of lapped timber boards under a slate roof, its big
     * doors open on a dark workshop, a giant spool wound with rope in front, a winch, coils on the
     * ground and a rope hung out to dry between two posts.
     */
    private fun MeshBuilder.ropeWorkshop() {
        val s = VillageLayout.spots.getValue(Building.ROPE)
        val c = s.center
        val f = floorOf(Building.ROPE)
        val w = 12f
        val d = 6.2f
        val board = rgb(0xFFBF8B5A)
        val dark = rgb(0xFF4A3426)
        val roof = rgb(0xFF51708C)
        val stone = rgb(0xFFCFC6B6)
        box(Vec3(c.x, f - 0.9f, c.z), Vec3(w + 1.0f, 1.8f, d + 1.0f), stone, 0.14f)
        val wallH = 4f
        val front = c.z + d / 2f
        box(Vec3(c.x, f + wallH / 2f, c.z), Vec3(w - 0.2f, wallH, d - 0.2f), dark, 0.04f)
        // Lapped boards on the front (around the door) and the sides.
        val rows = 7
        val doorX = c.x - 2f
        val doorW = 3.4f
        for (k in 0 until rows) {
            val y = f + 0.3f + k * (wallH - 0.3f) / rows
            val col = vary(board, k, 11, 0.06f)
            val left = c.x - w / 2f
            val right = c.x + w / 2f
            val y1 = y + 0.28f
            if (y1 < f + 3.4f) {
                box(Vec3((left + doorX - doorW / 2f) / 2f, y1, front + 0.04f), Vec3(doorX - doorW / 2f - left, 0.56f, 0.12f), col, 0.05f)
                box(Vec3((doorX + doorW / 2f + right) / 2f, y1, front + 0.04f), Vec3(right - doorX - doorW / 2f, 0.56f, 0.12f), col, 0.05f)
            } else {
                box(Vec3(c.x, y1, front + 0.04f), Vec3(w, 0.56f, 0.12f), col, 0.05f)
            }
            for (sx in listOf(-1f, 1f)) box(Vec3(c.x + sx * (w / 2f + 0.04f), y1, c.z), Vec3(0.12f, 0.56f, d), col, 0.05f)
        }
        // Corner posts.
        for (sx in listOf(-1f, 1f)) box(Vec3(c.x + sx * w / 2f, f + wallH / 2f, front), Vec3(0.34f, wallH, 0.34f), dark, 0.06f)
        gableRoof(c.x, c.z, w, d, f + wallH, 2.4f, 0.55f, roof, board)
        // The big doors, open, with X braces.
        box(Vec3(doorX, f + 1.7f, front + 0.1f), Vec3(doorW + 0.4f, 0.3f, 0.2f), dark, 0.05f)
        for (side in listOf(-1f, 1f)) {
            val hinge = Vec3(doorX + side * doorW / 2f, f + 1.6f, front + 0.12f)
            val turn = side * 1.15f
            val m = Mat4.multiply(Mat4.trs(hinge, rotY = turn), Mat4.translation(-side * doorW / 4f, 0f, 0f))
            bevelBox(m, Vec3(doorW / 2f, 3.1f, 0.12f), shade(board, 0.9f), 0.04f)
            for (diag in listOf(0.82f, -0.82f)) bevelBox(Mat4.multiply(m, Mat4.multiply(Mat4.translation(0f, 0f, 0.08f), Mat4.rotationZ(diag))), Vec3(0.16f, 3.3f, 0.06f), dark, 0.02f)
        }
        // A sign board with a coil of rope over the door.
        box(Vec3(doorX, f + 3.75f, front + 0.2f), Vec3(2.4f, 0.7f, 0.14f), rgb(0xFFF3E3C2), 0.06f)
        loop(circle(Vec3(doorX, 0f, front + 0.32f), 0.25f, 12).map { Vec3(it.x, f + 3.75f + (it.z - front - 0.32f), front + 0.32f) }, 0.07f, rgb(0xFFC9A26B), 4)
        // The giant spool on its side, in a wooden cradle.
        val spool = ground(c.x + 3.3f, front + 3.2f) + Vec3(0f, 1.7f, 0f)
        val spoolM = Mat4.multiply(Mat4.translation(spool.x, spool.y, spool.z), Mat4.rotationZ(PI.toFloat() / 2f))
        val wound = MeshBuilder().apply {
            paint(rgb(0xFF9C6E47)) {
                for (y in listOf(-1.05f, 0.85f)) {
                    lathe(listOf(1.75f to y + 0.2f, 1.75f to y), 28, Part.PAINTED)
                    lathe(listOf(0.01f to y + 0.2f, 1.75f to y + 0.2f), 28, Part.PAINTED)
                    lathe(listOf(1.75f to y, 0.01f to y), 28, Part.PAINTED)
                }
            }
            paint(rgb(0xFFE0C08C)) { lathe((0..24).map { k -> val y = 0.85f - k * 1.7f / 24f; (1.32f + 0.07f * abs(sin(k * PI.toFloat() / 3f))) to y }, 28, Part.PAINTED) }
        }.build()
        append(wound, spoolM)
        for (sx in listOf(-1.15f, 1.15f)) box(Vec3(spool.x + sx, spool.y - 1.62f, spool.z), Vec3(0.3f, 0.3f, 2.4f), dark, 0.06f)
        // The winch: an A-frame and a drum with rope.
        val wx = c.x - 4.2f
        val wz = front + 2.6f
        val wy = height(wx, wz)
        for (side in listOf(-0.9f, 0.9f)) {
            pole(Vec3(wx + side, wy - 0.2f, wz - 0.7f), Vec3(wx + side, wy + 2.4f, wz), 0.12f, dark, 6)
            pole(Vec3(wx + side, wy - 0.2f, wz + 0.7f), Vec3(wx + side, wy + 2.4f, wz), 0.12f, dark, 6)
        }
        pole(Vec3(wx - 1.1f, wy + 2.0f, wz), Vec3(wx + 1.1f, wy + 2.0f, wz), 0.32f, rgb(0xFFE0C08C), 12)
        // Coils of rope on the ground.
        for (at in listOf(ground(c.x - 0.6f, front + 1.8f), ground(c.x + 0.6f, front + 3.4f))) {
            for (ring in 0 until 4) loop(circle(at + Vec3(0f, 0.08f + ring * 0.13f, 0f), 0.72f - ring * 0.13f, 16), 0.09f, rgb(0xFFD8B985), 5)
        }
        barrel(ground(c.x - 7.0f, front - 0.6f), rgb(0xFF8E6440))
        // A rope hung between two posts.
        val p0 = ground(c.x + 7.2f, c.z + 2.4f) + Vec3(0f, -0.3f, 0f)
        val p1 = ground(c.x + 7.2f, c.z - 2.4f) + Vec3(0f, -0.3f, 0f)
        pole(p0, p0 + Vec3(0f, 2.8f, 0f), 0.1f, dark, 6)
        pole(p1, p1 + Vec3(0f, 2.8f, 0f), 0.1f, dark, 6)
        paint(rgb(0xFFD8B985)) { tube((0..10).map { k -> val u = k / 10f; Vec3(p0.x + 0.05f, p0.y + (p1.y - p0.y) * u + 2.4f - sin(u * PI.toFloat()) * 0.7f, p0.z + (p1.z - p0.z) * u) }, { 0.07f }, 4, Part.PAINTED, cap = false) }
    }

    /**
     * The aquarium (the glass pavilion): a round stone base with a band of blue tiles, the water
     * inside, a glass dome on teal iron ribs (the glass is its own see-through mesh), a gold
     * finial, an arched entrance between lamps and planters of flowers.
     */
    private fun MeshBuilder.aquariumPavilion() {
        val c = VillageLayout.spots.getValue(Building.AQUARIUM).center
        val f = floorOf(Building.AQUARIUM)
        val at = Vec3(c.x, 0f, c.z)
        val r = AQ_R
        val stone = rgb(0xFFF1ECE2)
        val iron = rgb(0xFF2E6B70)
        val top = f + 1.3f
        paint(stone) { lathe(listOf(r + 0.4f to top, r + 0.4f to f - 1.6f), 36, Part.PAINTED, at) }
        paint(rgb(0xFF4A9BC4)) { lathe(listOf(r + 0.43f to top - 0.45f, r + 0.43f to top - 0.85f), 36, Part.DRESS_TRIM, at) }
        paint(shade(stone, 0.95f)) { lathe(listOf(r - 0.1f to top + 0.12f, r + 0.6f to top + 0.12f, r + 0.6f to top - 0.1f), 36, Part.PAINTED, at) }
        // The water inside, seen through the glass.
        disc(Vec3(c.x, top + 2.6f, c.z), r - 0.3f, rgb(0xFF2BB3C8), 32)
        paint(rgb(0xFF1E9BB5)) { lathe(listOf(r - 0.3f to top + 2.6f, r - 0.3f to top), 32, Part.PAINTED, at) }
        // Iron ribs over the dome, a ring at its foot and at its crown.
        val ribs = 12
        for (k in 0 until ribs) {
            val a = k / ribs.toFloat() * 2f * PI.toFloat()
            val path = (0..12).map { j ->
                val t = j / 12f * PI.toFloat() / 2f * 0.94f
                Vec3(c.x + cos(a) * (r + 0.05f) * cos(t), top + (r + 0.05f) * 0.95f * sin(t), c.z + sin(a) * (r + 0.05f) * cos(t))
            }
            paint(iron) { tube(path, { 0.11f }, 5, Part.PAINTED, cap = false) }
        }
        loop(circle(Vec3(c.x, top + 0.08f, c.z), r + 0.05f, 40), 0.16f, iron, 6)
        loop(circle(Vec3(c.x, top + r * 0.95f * sin(PI.toFloat() / 2f * 0.94f), c.z), (r + 0.05f) * cos(PI.toFloat() / 2f * 0.94f), 12), 0.12f, iron, 5)
        paint(rgb(0xFFF2B33D)) {
            sphere(Vec3(c.x, top + r * 0.95f + 0.35f, c.z), 0.42f, Part.PAINTED, 6, 10)
            lathe(listOf(0.02f to top + r * 0.95f + 1.4f, 0.14f to top + r * 0.95f + 0.7f), 6, Part.PAINTED, at)
        }
        // The arched entrance, facing the square (south-east).
        val a = 0.75f
        val ex = c.x + cos(a) * (r + 0.4f)
        val ez = c.z + sin(a) * (r + 0.4f)
        val turn = atan2(cos(a), sin(a))
        val m = Mat4.trs(Vec3(ex, f, ez), rotY = turn)
        for (side in listOf(-1f, 1f)) bevelBox(Mat4.multiply(m, Mat4.translation(side * 1.05f, 1.5f, 0.2f)), Vec3(0.45f, 3.0f, 0.7f), stone, 0.06f)
        bevelBox(Mat4.multiply(m, Mat4.translation(0f, 3.2f, 0.2f)), Vec3(2.8f, 0.5f, 0.75f), stone, 0.08f)
        bevelBox(Mat4.multiply(m, Mat4.translation(0f, 1.25f, 0.05f)), Vec3(1.65f, 2.5f, 0.4f), rgb(0xFF1F4E5A), 0.04f)
        bevelBox(Mat4.multiply(m, Mat4.translation(0f, 3.55f, 0.25f)), Vec3(1.1f, 0.3f, 0.6f), rgb(0xFF4A9BC4), 0.05f, Part.DRESS_TRIM)
        for (side in listOf(-1f, 1f)) {
            val p = Mat4.transformPoint(m, Vec3(side * 2.2f, 0f, 1.0f))
            lamp(ground(p.x, p.z), 2.6f)
            val q = ground(Mat4.transformPoint(m, Vec3(side * 1.6f, 0f, 1.7f)).x, Mat4.transformPoint(m, Vec3(side * 1.6f, 0f, 1.7f)).z)
            box(q + Vec3(0f, 0.3f, 0f), Vec3(0.8f, 0.7f, 0.8f), rgb(0xFFC77D55), 0.06f)
            paint(rgb(0xFF5FAE52)) { sphere(q + Vec3(0f, 0.85f, 0f), 0.45f, Part.PAINTED, 4, 7) }
            paint(rgb(if (side > 0f) 0xFFFF8FA3 else 0xFFFFE066)) { sphere(q + Vec3(0.1f, 1.2f, 0.15f), 0.13f, Part.PAINTED, 3, 5) }
        }
    }

    /** The aquarium's glass dome, drawn see-through after the fish inside it. */
    private fun glass(): MeshData {
        val b = MeshBuilder()
        val c = VillageLayout.spots.getValue(Building.AQUARIUM).center
        val top = floorOf(Building.AQUARIUM) + 1.3f
        val r = AQ_R
        b.paint(rgb(0xFFBFE9F2)) {
            val lat = 10
            val lon = 32
            val first = vertexCount
            for (i in 0..lat) {
                val th = i / lat.toFloat() * PI.toFloat() / 2f
                for (j in 0..lon) {
                    val ph = j / lon.toFloat() * 2f * PI.toFloat()
                    val n = Vec3(cos(th) * cos(ph), sin(th), cos(th) * sin(ph))
                    vertex(Vec3(c.x + n.x * r, top + n.y * r * 0.95f, c.z + n.z * r), n, j / lon.toFloat(), 1f - i / lat.toFloat(), Part.GLASS)
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

    /** The lighthouse's beam: two long, soft wedges of light around the lamp (turned by the scene). */
    private fun beam(): MeshData {
        val b = MeshBuilder()
        val col = rgb(0xFFFFF4C8)
        for (dir in listOf(1f, -1f)) {
            val n = Vec3.UP
            val o = b.vertex(Vec3(0f, 0f, 0f), n, 0f, 0f, Part.BULB, col)
            val l = b.vertex(Vec3(dir * 38f, -6f, -4f), n, 1f, 0f, Part.BULB, col)
            val r = b.vertex(Vec3(dir * 38f, -6f, 4f), n, 1f, 1f, Part.BULB, col)
            b.triangle(o, l, r)
        }
        return b.build()
    }

    /**
     * The pier (Fish): a deck of planks on stringers and pilings from the beach out to your boat,
     * bollards with the mooring lines, lamps, a life ring, crates of the day's catch and a coil
     * of rope.
     */
    private fun MeshBuilder.pier() {
        val a = VillageLayout.pierFrom
        val e = VillageLayout.pierTo
        val wood = rgb(0xFFC28E58)
        val dark = rgb(0xFF5E4330)
        val len = e.z - a.z
        for (side in listOf(-1.25f, 1.25f)) box(Vec3(a.x + side, a.y - 0.3f, (a.z + e.z) / 2f), Vec3(0.26f, 0.3f, len), dark, 0.05f)
        var z = a.z + 0.25f
        var k = 0
        while (z < e.z) {
            box(Vec3(a.x + (hash01(k, 1, 19) - 0.5f) * 0.08f, a.y - 0.08f, z), Vec3(3.4f, 0.16f, 0.46f), vary(wood, k, 21), 0.04f)
            z += 0.53f
            k++
        }
        z = a.z + 2f
        while (z < e.z + 0.1f) {
            for (side in listOf(-1.55f, 1.55f)) {
                paint(dark) { tube(listOf(Vec3(a.x + side, -2.6f, z), Vec3(a.x + side, a.y + 0.45f, z)), { 0.2f }, 8, Part.PAINTED, cap = true) }
            }
            z += 2.6f
        }
        // Bollards with the boat's mooring lines, lamps on the left edge.
        val boat = VillageLayout.boatAt
        for (zz in listOf(boat.z - 3.6f, boat.z + 2.8f)) {
            val bp = Vec3(a.x + 1.3f, a.y, zz)
            paint(rgb(0xFF3E4A52)) { lathe(listOf(0.24f to 0.72f, 0.32f to 0.6f, 0.2f to 0.45f, 0.24f to 0f), 10, Part.PAINTED, bp) }
            val tie = Vec3(boat.x - 0.8f, 0.9f, zz + if (zz < boat.z) 1.4f else -1.4f)
            paint(rgb(0xFFD8B985)) { tube((0..6).map { j -> val u = j / 6f; Vec3.lerp(bp + Vec3(0f, 0.55f, 0f), tie, u) + Vec3(0f, -sin(u * PI.toFloat()) * 0.3f, 0f) }, { 0.045f }, 4, Part.PAINTED, cap = false) }
        }
        for (zz in listOf(a.z + 5f, a.z + 12f, e.z - 0.8f)) lamp(Vec3(a.x - 1.45f, a.y, zz), 2.6f)
        // A life ring on a post at the end.
        val ringAt = Vec3(a.x - 1.1f, a.y + 1.1f, e.z + 0.05f)
        pole(Vec3(ringAt.x, a.y, e.z - 0.1f), Vec3(ringAt.x, a.y + 1.8f, e.z - 0.1f), 0.09f, dark, 6)
        loop((0 until 18).map { j -> val t = j / 18f * 2f * PI.toFloat(); ringAt + Vec3(cos(t) * 0.48f, sin(t) * 0.48f, 0.1f) }, 0.14f, rgb(0xFFE53935), 6)
        for (q in 0 until 4) {
            val t = q / 4f * 2f * PI.toFloat() + 0.4f
            box(ringAt + Vec3(cos(t) * 0.48f, sin(t) * 0.48f, 0.12f), Vec3(0.2f, 0.2f, 0.3f), rgb(0xFFFFFBF2), 0.05f)
        }
        // Crates of the catch and a coil of rope.
        crate(Vec3(a.x - 0.7f, a.y, e.z - 3.6f), 0.8f, rgb(0xFF90A4AE), 0.15f)
        crate(Vec3(a.x - 0.75f, a.y, e.z - 4.5f), 0.7f, rgb(0xFF5CC1E8), -0.2f)
        for (ring in 0 until 3) loop(circle(Vec3(a.x + 0.5f, a.y + 0.08f + ring * 0.11f, a.z + 8f), 0.5f - ring * 0.1f, 14), 0.07f, rgb(0xFFD8B985), 4)
        barrel(Vec3(a.x + 0.9f, a.y, a.z + 3f))
    }

    /**
     * The duelling platform (Battle): a round deck of planks on the water inside a dark rim, the
     * white starting circle, posts and a rope rail, the rivals' pennants (blue and red) facing each
     * other, red and white buoys around it and a jetty from the beach.
     */
    private fun MeshBuilder.arena() {
        val c = VillageLayout.spots.getValue(Building.DUEL).center
        val r = ARENA_R
        val deckY = 0.9f
        val wood = rgb(0xFFC28E58)
        val dark = rgb(0xFF5E4330)
        val at = Vec3(c.x, 0f, c.z)
        paint(dark) { lathe(listOf(r + 0.25f to deckY + 0.1f, r + 0.25f to -0.5f), 40, Part.PAINTED, at) }
        loop(circle(Vec3(c.x, deckY + 0.1f, c.z), r + 0.12f, 48), 0.18f, shade(dark, 1.2f), 5)
        var k = 0
        var z = -r + 0.3f
        while (z < r - 0.2f) {
            val half = sqrt(max(0f, r * r - z * z)) - 0.05f
            box(Vec3(c.x, deckY - 0.02f, c.z + z), Vec3(2f * half, 0.16f, 0.48f), vary(wood, k, 27), 0.04f)
            z += 0.53f
            k++
        }
        loop(circle(Vec3(c.x, deckY + 0.07f, c.z), 2.2f, 40), 0.1f, rgb(0xFFFFFBF2), 4)
        // Posts and a rope rail around the edge (open towards the jetty).
        val posts = 16
        val gap = 3f * PI.toFloat() / 2f
        for (i in 0 until posts) {
            val ang = i / posts.toFloat() * 2f * PI.toFloat()
            if (abs(ang - gap) < 0.35f) continue
            val p = Vec3(c.x + cos(ang) * (r - 0.25f), 0f, c.z + sin(ang) * (r - 0.25f))
            paint(dark) { tube(listOf(Vec3(p.x, -1.6f, p.z), Vec3(p.x, deckY + 1.3f, p.z)), { 0.14f }, 6, Part.PAINTED, cap = true) }
        }
        paint(rgb(0xFFE3C99A)) {
            tube((0..44).map { j -> val ang = gap + 0.35f + j / 44f * (2f * PI.toFloat() - 0.7f); Vec3(c.x + cos(ang) * (r - 0.25f), deckY + 1.12f - 0.14f * abs(sin(j * PI.toFloat() / 2.75f)), c.z + sin(ang) * (r - 0.25f)) }, { 0.07f }, 4, Part.PAINTED, cap = false)
        }
        // The blue pennant is the player's: it takes the colours of their skin (see IslandDress).
        pennant(Vec3(c.x - r * 0.62f, deckY, c.z), 6.2f, 2.8f, 1f, rgb(0xFF3FA9F5), Part.DRESS_FLAG)
        pennant(Vec3(c.x + r * 0.62f, deckY, c.z), 6.2f, 2.8f, -1f, rgb(0xFFFF5A4A))
        for ((i, at2) in buoys.withIndex()) {
            paint(if (i % 2 == 0) rgb(0xFFE53935) else rgb(0xFFFFFBF2)) { sphere(at2, 0.45f, Part.PAINTED, 6, 10) }
            pole(at2 + Vec3(0f, 0.3f, 0f), at2 + Vec3(0f, 1.0f, 0f), 0.05f, rgb(0xFF37474F), 4)
        }
        // The jetty from the beach.
        val ja = VillageLayout.jettyFrom
        val je = VillageLayout.jettyTo
        for (side in listOf(-0.9f, 0.9f)) box(Vec3(ja.x + side, ja.y - 0.3f, (ja.z + je.z) / 2f), Vec3(0.22f, 0.26f, je.z - ja.z), dark, 0.05f)
        z = ja.z + 0.25f
        k = 0
        while (z < je.z) {
            box(Vec3(ja.x, ja.y - 0.08f, z), Vec3(2.5f, 0.14f, 0.44f), vary(wood, k, 29), 0.04f)
            z += 0.5f
            k++
        }
        for (zz in listOf(ja.z + 3f, ja.z + 6.5f, je.z - 0.3f)) for (side in listOf(-1.1f, 1.1f)) {
            paint(dark) { tube(listOf(Vec3(ja.x + side, -2f, zz), Vec3(ja.x + side, ja.y + 0.3f, zz)), { 0.17f }, 7, Part.PAINTED, cap = true) }
        }
    }

    // ---- The skin the player wears, on the island (see IslandDress) -------------------------------

    /** Ambient occlusion towards the ground, as baked into the town. */
    internal fun MeshBuilder.bakeGround(from: Int) = bakeColors(from) { p, n, part, c ->
        if (part == Part.GLASS || part == Part.BULB || part == Part.CLOTH || part == Part.DRESS_FLAG) return@bakeColors
        val ao = 0.7f + 0.3f * smooth(0f, 2.4f, p.y - max(height(p.x, p.z), 0f))
        val under = if (n.y < -0.3f) 0.85f else 1f
        for (k in 0..2) c[k] *= ao * under
    }

    /**
     * A string of little flags from [a] to [b], sagging by [sag]: the flags alternate between the
     * skin's flag and trim colours (tinted in the shader) and flutter.
     */
    private fun MeshBuilder.flagString(a: Vec3, b: Vec3, sag: Float) {
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z))
        val n = max(4, (len / 0.75f).toInt())
        fun at(t: Float) = Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - sag * sin(t * PI.toFloat()), a.z + (b.z - a.z) * t)
        paint(rgb(0xFF4E3B30)) { tube((0..n).map { at(it / n.toFloat()) }, { 0.03f }, 3, Part.PAINTED, cap = false) }
        val side = Vec3(-(b.z - a.z), 0f, b.x - a.x).normalized()
        for (i in 0 until n) {
            val p = at((i + 0.15f) / n)
            val q = at((i + 0.85f) / n)
            val tip = at((i + 0.5f) / n) + Vec3(0f, -0.6f, 0f)
            val part = if (i % 2 == 0) Part.DRESS_FLAG else Part.DRESS_TRIM
            val col = if (i % 2 == 0) rgb(0xFF3FA9F5) else rgb(0xFFFFFBF2)
            val i0 = vertex(p, side, 0f, 0f, part, col)
            val i1 = vertex(q, side, 0f, 1f, part, col)
            val i2 = vertex(tip, side, 1f, 0.5f, part, col)
            triangle(i0, i1, i2)
        }
    }

    /**
     * Bunting for the island's party when the player wears a boat of their own: along the pier,
     * between the arena's pennants and down from the lighthouse's gallery (the square is the
     * theme's).
     */
    private fun bunting(): MeshData {
        val b = MeshBuilder()
        // Along the pier, on three tall poles.
        val a = VillageLayout.pierFrom
        val e = VillageLayout.pierTo
        val poles = listOf(a.z + 1.2f, (a.z + e.z) / 2f, e.z - 0.6f).map { z -> Vec3(a.x - 1.55f, a.y + 3.4f, z) }
        for (p in poles) b.pole(Vec3(p.x, a.y + 0.3f, p.z), p + Vec3(0f, 0.2f, 0f), 0.07f, rgb(0xFF5E4330), 5)
        for (i in 0 until poles.size - 1) b.flagString(poles[i], poles[i + 1], 0.7f)
        // Between the arena's pennants.
        val arena = VillageLayout.spots.getValue(Building.DUEL).center
        b.flagString(Vec3(arena.x - ARENA_R * 0.62f, 0.9f + 5.8f, arena.z), Vec3(arena.x + ARENA_R * 0.62f, 0.9f + 5.8f, arena.z), 1.2f)
        // Down from the lighthouse's gallery to either side of its steps.
        val c = lighthouseAt
        val gy = lighthouseLamp.y - 1.7f
        for (side in listOf(-1f, 1f)) {
            val foot = ground(c.x + side * 4.2f, c.z + 4.6f)
            b.pole(foot, foot + Vec3(0f, 2.6f, 0f), 0.07f, rgb(0xFF5E4330), 5)
            b.flagString(Vec3(c.x + side * 0.9f, gy + 1.2f, c.z + 2.45f), foot + Vec3(0f, 2.6f, 0f), 1.4f)
        }
        b.bakeGround(0)
        return b.build()
    }

    /**
     * What a legendary set raises on the island, its hardest look: a monument in the square and a
     * crown for the lighthouse. "leviathan": a leviathan's skeleton with cyan runes; "prism": a
     * cluster of rainbow crystals; "crown": a golden fish on a tall plinth with royal banners.
     * They glow a little at night. Small enough to leave the paths and the places clear.
     */
    /**
     * Where a legendary boat's monument stands: in the lighthouse's grounds, to the east of its
     * knoll (the square's centre is the menu theme's, see [VillageThemes]).
     */
    val legendAt: Vec3 get() = Vec3(5.2f, 0f, -30.5f)

    private fun monument(set: String): MeshData {
        val b = MeshBuilder()
        val sq = legendAt
        val g = height(sq.x, sq.z)
        val at = Vec3(sq.x, g, sq.z)
        // Lathe profiles give absolute heights: turned round this.
        val axis = Vec3(sq.x, 0f, sq.z)
        val stone = rgb(0xFFD8CFBF)
        val c = lighthouseAt
        val gy = lighthouseLamp.y - 1.7f
        when (set) {
            "leviathan" -> {
                val bone = rgb(0xFFEDE6D2)
                val cyan = rgb(0xFF59E3F2)
                b.paint(stone) { lathe(listOf(2.6f to g + 0.35f, 2.75f to g - 1.2f), 16, Part.PAINTED, axis) }
                b.disc(Vec3(at.x, g + 0.35f, at.z), 2.6f, shade(stone, 1.05f), 16)
                val spine = (0..10).map { i -> val x = -2.3f + 4.4f * i / 10f; Vec3(at.x + x, g + 1.25f + 0.25f * sin(i / 10f * PI.toFloat()), at.z) }
                b.paint(bone) { tube(spine, { u -> 0.16f - 0.06f * u }, 6, Part.PAINTED) }
                // The skull and its jaw.
                b.paint(bone) {
                    sphere(Vec3(at.x + 2.45f, g + 1.15f, at.z), 0.55f, Part.PAINTED, 6, 10, Vec3(1.3f, 0.75f, 0.9f))
                    tube(listOf(Vec3(at.x + 1.9f, g + 0.75f, at.z), Vec3(at.x + 2.9f, g + 0.62f, at.z)), { 0.12f }, 5, Part.PAINTED)
                }
                b.paint(rgb(0xFF1B2A30)) { sphere(Vec3(at.x + 2.7f, g + 1.3f, at.z + 0.5f), 0.12f, Part.PAINTED, 3, 6) }
                // Ribs arching over the spine.
                for (i in 0 until 6) {
                    val x = at.x - 1.7f + 0.65f * i
                    val k = 1f - 0.12f * abs(i - 2.5f)
                    val rib = (0..8).map { j ->
                        val a = PI.toFloat() * j / 8f
                        Vec3(x, g + 0.4f + (1.1f + 0.9f * k) * sin(a), at.z + (1.3f * k) * cos(a))
                    }
                    b.paint(bone) { tube(rib, { 0.08f }, 5, Part.PAINTED, cap = true) }
                }
                // Runes round the plinth, and a cyan ring on the lighthouse's gallery.
                for (k in 0 until 10) {
                    val a = k / 10f * 2f * PI.toFloat()
                    b.paint(cyan) { box(Vec3(at.x + cos(a) * 2.72f, g + 0.05f, at.z + sin(a) * 2.72f), Vec3(0.22f, 0.28f, 0.22f), Part.BULB) }
                }
                b.paint(cyan) { tube(circle(Vec3(c.x, gy + 1.3f, c.z), 2.62f, 36).let { it + it.first() }, { 0.06f }, 4, Part.BULB, cap = false) }
            }
            "prism" -> {
                b.paint(stone) { lathe(listOf(1.9f to g + 0.45f, 2.05f to g - 1.2f), 16, Part.PAINTED, axis) }
                b.disc(Vec3(at.x, g + 0.45f, at.z), 1.9f, shade(stone, 1.05f), 16)
                val colors = listOf(0xFFFF6B6B, 0xFFFFB347, 0xFFFFE66D, 0xFF7BE495, 0xFF5BC0EB, 0xFF9B7BFF, 0xFFFF8AD8)
                for ((k, col) in colors.withIndex()) {
                    val tall = if (k == 0) 3.6f else 1.4f + 0.35f * (k % 3)
                    val r = if (k == 0) 0.45f else 0.26f
                    val a = k / 6f * 2f * PI.toFloat()
                    val off = if (k == 0) Vec3.ZERO else Vec3(cos(a) * 0.75f, 0f, sin(a) * 0.75f)
                    val crystal = MeshBuilder().apply { lathe(listOf(0.01f to tall, r to tall * 0.72f, r to 0.15f, 0.01f to 0f), 6, Part.BULB) }.build()
                    b.paint(rgb(col)) { append(crystal, Mat4.trs(Vec3(at.x + off.x, g + 0.4f, at.z + off.z), rotX = off.z * 0.35f, rotZ = -off.x * 0.35f)) }
                }
                // A prism over the lighthouse's weathervane.
                val prism = MeshBuilder().apply { lathe(listOf(0.01f to 1.2f, 0.32f to 0.6f, 0.01f to 0f), 6, Part.BULB) }.build()
                b.paint(rgb(0xFFBFF3FF)) { append(prism, Mat4.translation(c.x, gy + 5.6f, c.z)) }
            }
            else -> {
                // "crown".
                val gold = rgb(0xFFF2C14E)
                val purple = rgb(0xFF6A3FA0)
                b.box(Vec3(at.x, g + 0.3f, at.z), Vec3(2.2f, 2.4f, 2.2f), stone, 0.12f)
                b.box(Vec3(at.x, g + 1.6f, at.z), Vec3(2.5f, 0.25f, 2.5f), shade(stone, 1.05f), 0.08f)
                for ((dx, dz) in listOf(0f to 1.12f, 0f to -1.12f, 1.12f to 0f, -1.12f to 0f)) {
                    b.box(Vec3(at.x + dx, g + 1.0f, at.z + dz), Vec3(if (dx == 0f) 1.0f else 0.06f, 1.0f, if (dz == 0f) 1.0f else 0.06f), purple, 0.02f)
                    b.box(Vec3(at.x + dx * 1.02f, g + 0.52f, at.z + dz * 1.02f), Vec3(if (dx == 0f) 0.3f else 0.07f, 0.12f, if (dz == 0f) 0.3f else 0.07f), gold, 0.02f)
                }
                val fish = listOf(
                    -1.0f to 0f, -0.7f to 0.34f, 0f to 0.5f, 0.7f to 0.3f, 1.0f to 0.04f, 1.45f to 0.32f, 1.45f to -0.32f, 1.0f to -0.04f,
                    0.7f to -0.3f, 0f to -0.48f, -0.7f to -0.32f,
                )
                b.paint(gold) { append(MeshBuilder().apply { extrude(fish, 0.3f, Part.PAINTED) }.build(), Mat4.trs(Vec3(at.x, g + 2.75f, at.z), rotZ = 0.35f, scale = Vec3(1.3f, 1.3f, 1.3f))) }
                b.pole(Vec3(at.x, g + 1.7f, at.z), Vec3(at.x, g + 2.3f, at.z), 0.1f, gold, 6)
                // A crown round the top of the lighthouse's dome.
                val ring = circle(Vec3(c.x, gy + 3.45f, c.z), 0.72f, 16)
                b.paint(gold) { tube(ring + ring.first(), { 0.09f }, 5, Part.PAINTED, cap = false) }
                for (k in 0 until 8) {
                    val a = k / 8f * 2f * PI.toFloat()
                    val p = Vec3(c.x + cos(a) * 0.72f, gy + 3.45f, c.z + sin(a) * 0.72f)
                    b.paint(gold) { lathe(listOf(0.01f to 0.55f, 0.13f to 0f), 5, Part.PAINTED, p) }
                    b.paint(if (k % 2 == 0) rgb(0xFFE53958) else rgb(0xFF4FC3F7)) { sphere(p + Vec3(0f, 0.62f, 0f), 0.09f, Part.BULB, 3, 5) }
                }
            }
        }
        b.bakeGround(0)
        return b.build()
    }

}
