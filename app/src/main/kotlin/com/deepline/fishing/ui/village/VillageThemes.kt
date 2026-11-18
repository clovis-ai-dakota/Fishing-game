package com.deepline.fishing.ui.village

import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.MeshBuilder
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.ModelAssets
import com.deepline.fishing.ui.render3d.Part
import com.deepline.fishing.ui.render3d.PropModels
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.render3d.bevelBox
import com.deepline.fishing.ui.render3d.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The menu theme on the island: the square's centrepiece is the theme's, and the dearer or harder
 * to get the theme, the more it changes (looks only, nothing opens anything):
 * - Lagoon (free): a stone fountain.
 * - Tier 1 (Kelp Forest, Sandbar): their own centrepiece.
 * - Tier 2 (Coral Reef, Polar Waters): and the theme around the square.
 * - Tier 3 (Sunset Harbor, Koi Pond, Treasure Cove, the Club's Aurora): and details all over the
 *   island (along the paths, on the beach, in the shallows).
 * - Tier 4 (Glow Tide): all of that, and living lights by night.
 * The boat the player wears gives the island its colours ([IslandDress]); the theme its objects.
 * One mesh per theme, `village:theme:<THEME>`.
 */
internal object VillageThemes {
    fun key(theme: MenuTheme) = "village:theme:${theme.name}"

    private val c: Vec3 get() = VillageLayout.square
    private val axis: Vec3 get() = Vec3(c.x, 0f, c.z)
    private val g: Float get() = VillageModels.height(c.x, c.z)
    private fun rgb(argb: Long) = VillageModels.rgb(argb)

    fun build(theme: MenuTheme): MeshData {
        val b = MeshBuilder()
        when (theme) {
            MenuTheme.LAGOON -> b.fountain()
            MenuTheme.KELP_FOREST -> b.kelpForest()
            MenuTheme.SANDBAR -> b.sandbar()
            MenuTheme.CORAL_REEF -> b.coralReef()
            MenuTheme.POLAR -> b.polar()
            MenuTheme.SUNSET_HARBOR -> b.sunsetHarbor()
            MenuTheme.KOI_POND -> b.koiPond()
            MenuTheme.TREASURE_COVE -> b.treasureCove()
            MenuTheme.AURORA -> b.aurora()
            MenuTheme.GLOW_TIDE -> b.glowTide()
        }
        with(VillageModels) { b.bakeGround(0) }
        return b.build()
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private inline fun MeshBuilder.paint(color: FloatArray, block: MeshBuilder.() -> Unit) {
        val from = vertexCount
        block()
        recolor(from, color)
    }

    private fun MeshBuilder.bx(at: Vec3, size: Vec3, color: FloatArray, bevel: Float = 0.06f, part: Float = Part.PAINTED, rotY: Float = 0f, rotZ: Float = 0f, rotX: Float = 0f) =
        bevelBox(Mat4.trs(at, rotX = rotX, rotY = rotY, rotZ = rotZ), size, color, bevel, part)

    private fun MeshBuilder.ball(at: Vec3, r: Float, color: FloatArray, part: Float = Part.PAINTED, scale: Vec3 = Vec3(1f, 1f, 1f), lat: Int = 5, lon: Int = 8) =
        paint(color) { sphere(at, r, part, lat, lon, scale) }

    private fun MeshBuilder.stick(path: List<Vec3>, r: (Float) -> Float, color: FloatArray, part: Float = Part.PAINTED, sides: Int = 5) =
        paint(color) { tube(path, r, sides, part) }

    /** A lathed shape (profile: radius to absolute height, top first) around [at]'s vertical axis. */
    private fun MeshBuilder.turned(at: Vec3, profile: List<Pair<Float, Float>>, color: FloatArray, sides: Int = 14, part: Float = Part.PAINTED) =
        paint(color) { lathe(profile, sides, part, Vec3(at.x, 0f, at.z)) }

    private fun MeshBuilder.model(name: String, at: Vec3, scale: Float, turn: Float) {
        for (m in ModelAssets.model(name).painted) append(m, Mat4.trs(at, rotY = turn, scale = Vec3(scale, scale, scale)))
    }

    /** A basin of stone round the square's middle, water inside. */
    private fun MeshBuilder.basin(r: Float, stone: FloatArray, water: FloatArray) {
        turned(c, listOf(r to g + 0.55f, r + 0.15f to g - 0.6f), stone, 18)
        turned(c, listOf(r - 0.35f to g + 0.6f, r + 0.05f to g + 0.62f), VillageModels.shade(stone, 1.08f), 18)
        paint(water) { lathe(listOf(0.01f to g + 0.45f, r - 0.3f to g + 0.45f), 18, Part.PAINTED, axis) }
    }

    /** [count] free spots on a ring round the square, off the paths, spread out round it. */
    private fun aroundSquare(count: Int, r: Float, salt: Int): List<Vec3> {
        val out = ArrayList<Vec3>()
        val gap = 2f * PI.toFloat() * r / count * 0.55f
        for (k in 0 until count * 8) {
            if (out.size == count) break
            val a = k / (count * 8f) * 2f * PI.toFloat() + salt * 0.37f
            val x = c.x + cos(a) * r
            val z = c.z + sin(a) * r
            if (free(x, z, 0.7f) && out.none { dist(x, z, it) < gap }) out += VillageModels.ground(x, z)
        }
        return out
    }

    /** Off the paths, the buildings and the shore. */
    private fun free(x: Float, z: Float, margin: Float) =
        VillageModels.pathDistance(x, z) > margin && VillageLayout.shoreDistance(x, z) > 1.2f &&
            VillageLayout.spots.values.none { VillageModels.outside(x, z, it) < 1.6f } &&
            dist(x, z, VillageModels.legendAt) > 3.5f

    private fun dist(x: Float, z: Float, p: Vec3) = sqrt((x - p.x) * (x - p.x) + (z - p.z) * (z - p.z))

    /** Spots along both sides of the paths, every [step] m. */
    private fun alongPaths(step: Float, side: Float): List<Vec3> {
        val out = ArrayList<Vec3>()
        for ((k, path) in VillageModels.paths.withIndex()) {
            val (a, e, w) = path
            val len = sqrt((e.x - a.x) * (e.x - a.x) + (e.z - a.z) * (e.z - a.z))
            val nx = -(e.z - a.z) / len
            val nz = (e.x - a.x) / len
            var t = 5.5f
            var i = 0
            while (t < len - 1.5f) {
                val sgn = if ((i + k) % 2 == 0) 1f else -1f
                val x = a.x + (e.x - a.x) * t / len + nx * sgn * (w / 2f + side)
                val z = a.z + (e.z - a.z) * t / len + nz * sgn * (w / 2f + side)
                if (VillageLayout.spots.values.none { VillageModels.outside(x, z, it) < 1.2f } && VillageLayout.shoreDistance(x, z) > 0.8f) out += VillageModels.ground(x, z)
                t += step
                i++
            }
        }
        return out
    }

    /** Spots on the beach ([inland] m from the water's edge) or in the shallows (negative), round the island. */
    private fun shore(count: Int, inland: Float, salt: Int): List<Vec3> = (0 until count).map { k ->
        val a = (k + 0.5f) / count * 2f * PI.toFloat() + salt * 0.21f + 0.3f * (hash01(k, salt, 83) - 0.5f)
        val r = VillageLayout.islandRadius(a) - inland
        val x = VillageLayout.ISLAND_X + cos(a) * r
        val z = VillageLayout.ISLAND_Z + sin(a) * r
        Vec3(x, if (inland > 0f) VillageModels.height(x, z) else 0f, z)
    }.filter { p ->
        // Clear of the pier, the jetty and the arena.
        kotlin.math.abs(p.x - VillageLayout.pierFrom.x) > 3f && kotlin.math.abs(p.x - VillageLayout.jettyFrom.x) > 3f && p.z < 2f
    }

    // ---- Lagoon: a stone fountain ------------------------------------------------------------------

    private fun MeshBuilder.fountain() {
        val stone = rgb(0xFFD9D2C4)
        basin(2.3f, stone, rgb(0xFF63C7DA))
        turned(c, listOf(0.22f to g + 1.6f, 0.36f to g + 0.45f), stone, 10)
        turned(c, listOf(0.95f to g + 1.78f, 0.62f to g + 1.56f, 0.24f to g + 1.5f), stone, 14)
        paint(rgb(0xFF7BD3E4)) { lathe(listOf(0.01f to g + 1.74f, 0.82f to g + 1.74f), 14, Part.PAINTED, axis) }
        ball(Vec3(c.x, g + 1.95f, c.z), 0.2f, stone)
        // Water falling from the bowl into the basin.
        for (k in 0 until 8) {
            val a = k / 8f * 2f * PI.toFloat()
            for (j in 0 until 4) {
                val u = (j + 1) / 5f
                val r = 0.95f + 0.7f * u
                ball(Vec3(c.x + cos(a) * r, g + 1.72f - 1.2f * u * u, c.z + sin(a) * r), 0.07f, rgb(0xFFBFEFFF), lat = 3, lon = 5)
            }
        }
    }

    // ---- Tier 1 ------------------------------------------------------------------------------------

    /** Kelp Forest: tall kelp swaying out of a mossy pool, round a stone. */
    private fun MeshBuilder.kelpForest() {
        basin(2.4f, rgb(0xFF8FA58A), rgb(0xFF3E9A7E))
        paint(rgb(0xFF7E8C80)) { append(PropModels.rock(3), Mat4.trs(Vec3(c.x, g + 0.35f, c.z), scale = Vec3(0.75f, 0.55f, 0.65f))) }
        for (k in 0 until 7) {
            val a = k / 7f * 2f * PI.toFloat()
            val foot = Vec3(c.x + cos(a) * 1.1f, g + 0.45f, c.z + sin(a) * 1.1f)
            val tall = 2.4f + 1.1f * hash01(k, 1, 87)
            val path = (0..8).map { j -> val u = j / 8f; foot + Vec3(0.25f * sin(u * 5f + k), tall * u, 0.2f * cos(u * 4f + k)) }
            stick(path, { u -> 0.08f * (1f - 0.6f * u) }, rgb(0xFF7FA63A), Part.LEAF)
            for (j in 1..5) {
                val p = path[j + 2]
                val leaf = MeshBuilder().apply { sphere(Vec3(0f, 0.28f, 0f), 1f, Part.LEAF, 4, 6, Vec3(0.05f, 0.3f, 0.15f)) }.build()
                paint(rgb(if (j % 2 == 0) 0xFFB5C94A else 0xFF98B83E)) { append(leaf, Mat4.trs(p, rotY = a + j * 1.3f, rotZ = 0.9f * (if (j % 2 == 0) 1f else -1f))) }
            }
        }
    }

    /** Sandbar: a sand sculpture of a sea turtle on a dune, shells, a starfish, a bucket and spade. */
    private fun MeshBuilder.sandbar() {
        val sand = rgb(0xFFEAD3A0)
        turned(c, listOf(2.5f to g + 0.25f, 2.9f to g - 0.4f), sand, 18)
        paint(sand) { lathe(listOf(0.01f to g + 0.28f, 2.5f to g + 0.25f), 18, Part.PAINTED, axis) }
        val shell = Vec3(c.x, g + 0.7f, c.z)
        ball(shell, 1f, rgb(0xFFD6B279), scale = Vec3(1.3f, 0.62f, 1.05f), lat = 7, lon = 12)
        for (k in 0 until 7) {
            val a = k / 6f * 2f * PI.toFloat()
            val r = if (k == 6) 0f else 0.62f
            ball(shell + Vec3(cos(a) * r, 0.5f - 0.1f * (r / 0.62f), sin(a) * r * 0.8f), 0.3f, rgb(0xFFE8CC93), scale = Vec3(1f, 0.35f, 0.9f))
        }
        ball(shell + Vec3(1.45f, 0.05f, 0f), 0.36f, sand, scale = Vec3(1.2f, 0.9f, 0.9f))
        ball(shell + Vec3(1.75f, 0.15f, 0.18f), 0.05f, rgb(0xFF3B2A1E))
        ball(shell + Vec3(1.75f, 0.15f, -0.18f), 0.05f, rgb(0xFF3B2A1E))
        for ((dx, dz) in listOf(0.7f to 0.95f, 0.7f to -0.95f, -0.75f to 0.85f, -0.75f to -0.85f)) {
            ball(shell + Vec3(dx, -0.35f, dz), 0.45f, sand, scale = Vec3(0.9f, 0.22f, 0.5f))
        }
        // Shells and a starfish on the sand, a bucket and its spade.
        for ((k, a) in listOf(0.4f, 2.0f, 3.6f, 5.2f).withIndex()) {
            val p = Vec3(c.x + cos(a) * 2.1f, g + 0.3f, c.z + sin(a) * 2.1f)
            ball(p, 0.18f, rgb(if (k % 2 == 0) 0xFFF6D6C2 else 0xFFFFC1B0), scale = Vec3(1f, 0.35f, 0.9f))
        }
        val star = Vec3(c.x - 1.6f, g + 0.3f, c.z + 1.2f)
        for (k in 0 until 5) {
            val a = k / 5f * 2f * PI.toFloat()
            stick(listOf(star, star + Vec3(cos(a) * 0.35f, 0f, sin(a) * 0.35f)), { u -> 0.09f * (1f - 0.75f * u) }, rgb(0xFFFF8A4C))
        }
        val bucket = Vec3(c.x + 1.8f, g + 0.25f, c.z - 1.3f)
        turned(bucket, listOf(0.32f to g + 0.85f, 0.24f to g + 0.25f), rgb(0xFFE53935), 12)
        stick(listOf(bucket + Vec3(0.5f, 0f, 0.2f), bucket + Vec3(0.75f, 0.9f, 0.3f)), { 0.03f }, rgb(0xFF6D4C41))
        bx(bucket + Vec3(0.48f, 0.12f, 0.19f), Vec3(0.24f, 0.3f, 0.04f), rgb(0xFF1E88E5), 0.02f, rotZ = 0.25f)
    }

    // ---- Tier 2 ------------------------------------------------------------------------------------

    private fun MeshBuilder.coralClump(at: Vec3, k: Int, scale: Float = 1f) {
        val colors = listOf(0xFFF78FD0, 0xFFFF9E5E, 0xFFB07BFF, 0xFFFF6B8A, 0xFFFFC857)
        for (j in 0 until 5) {
            val a = j * 1.3f + k
            val tip = at + Vec3(cos(a) * 0.4f * scale, (0.8f + 0.5f * hash01(j, k, 89)) * scale, sin(a) * 0.4f * scale)
            val mid = at + Vec3(cos(a) * 0.15f * scale, 0.45f * scale, sin(a) * 0.15f * scale)
            val col = rgb(colors[(j + k) % colors.size])
            stick(listOf(at, mid, tip), { u -> (0.09f - 0.05f * u) * scale }, col)
            ball(tip, 0.07f * scale, VillageModels.shade(col, 1.15f), lat = 3, lon = 5)
        }
    }

    /** Coral Reef: an arch of coral over a pool of anemones; coral clumps round the square. */
    private fun MeshBuilder.coralReef() {
        basin(2.4f, rgb(0xFFE7D5C0), rgb(0xFF2BB3C8))
        val arch = (0..16).map { j -> val a = PI.toFloat() * j / 16f; Vec3(c.x + cos(a) * 1.35f, g + 0.45f + sin(a) * 2.6f, c.z - 0.2f) }
        stick(arch, { u -> 0.24f - 0.06f * sin(u * PI.toFloat()) }, rgb(0xFFF78FD0), sides = 7)
        for ((j, p) in arch.withIndex()) if (j % 2 == 1) {
            val out = Vec3(p.x - c.x, p.y - g - 0.45f, 0f).normalized()
            val col = rgb(listOf(0xFFFF9E5E, 0xFFB07BFF, 0xFFFFC857)[j % 3])
            stick(listOf(p, p + out * 0.5f + Vec3(0f, 0.15f, 0.15f * (j % 3 - 1))), { u -> 0.07f - 0.04f * u }, col)
            ball(p + out * 0.5f + Vec3(0f, 0.15f, 0.15f * (j % 3 - 1)), 0.08f, col, lat = 3, lon = 5)
        }
        for (k in 0 until 6) {
            val a = k / 6f * 2f * PI.toFloat() + 0.4f
            val foot = Vec3(c.x + cos(a) * 1.5f, g + 0.45f, c.z + sin(a) * 1.5f)
            for (j in 0 until 4) {
                val p = foot + Vec3(0.12f * cos(j * 1.7f), 0f, 0.12f * sin(j * 1.7f))
                turned(p, listOf(0.05f to g + 0.8f + 0.1f * j, 0.06f to g + 0.45f), rgb(0xFFFFB13B), 6)
                ball(Vec3(p.x, g + 0.82f + 0.1f * j, p.z), 0.06f, rgb(0xFFFFE07A), Part.BULB, lat = 3, lon = 5)
            }
        }
        for ((k, p) in aroundSquare(6, 5.6f, 2).withIndex()) coralClump(p, k)
    }

    /** Polar Waters: a leaping ice fish on a block of ice; ice blocks, snowmen and snow round the square. */
    private fun MeshBuilder.polar() {
        val snow = rgb(0xFFF4F9FF)
        val ice = rgb(0xFFC9ECFF)
        paint(snow) { lathe(listOf(0.01f to g + 0.06f, 2.9f to g + 0.04f), 18, Part.PAINTED, axis) }
        bx(Vec3(c.x, g + 0.5f, c.z), Vec3(2.2f, 1.0f, 2.2f), ice, 0.12f)
        bx(Vec3(c.x, g + 1.1f, c.z), Vec3(2.35f, 0.18f, 2.35f), snow, 0.08f)
        val fish = listOf(
            -1.0f to 0f, -0.7f to 0.34f, 0f to 0.5f, 0.7f to 0.3f, 1.0f to 0.04f, 1.45f to 0.32f, 1.45f to -0.32f, 1.0f to -0.04f,
            0.7f to -0.3f, 0f to -0.48f, -0.7f to -0.32f,
        )
        paint(VillageModels.shade(ice, 0.95f)) { append(MeshBuilder().apply { extrude(fish, 0.42f, Part.PAINTED) }.build(), Mat4.trs(Vec3(c.x, g + 2.2f, c.z), rotZ = 0.55f, scale = Vec3(1.5f, 1.5f, 1.5f))) }
        stick(listOf(Vec3(c.x - 0.6f, g + 1.2f, c.z), Vec3(c.x - 0.75f, g + 1.75f, c.z)), { 0.12f }, ice)
        // Icicles along the block's top edge.
        for (k in 0 until 12) {
            val side = k / 3
            val t = (k % 3 + 0.5f) / 3f * 2.2f - 1.1f
            val (x, z) = when (side) { 0 -> t to 1.12f; 1 -> t to -1.12f; 2 -> 1.12f to t; else -> -1.12f to t }
            turned(Vec3(c.x + x, 0f, c.z + z), listOf(0.06f to g + 1.02f, 0.01f to g + 0.7f), ice, 5)
        }
        // Round the square: blocks of ice, two snowmen, patches of snow.
        for ((k, p) in aroundSquare(4, 5.4f, 5).withIndex()) bx(p + Vec3(0f, 0.35f, 0f), Vec3(0.8f, 0.7f, 0.7f), ice, 0.08f, rotY = k * 0.7f)
        for ((k, p) in aroundSquare(2, 6.4f, 9).withIndex()) {
            ball(p + Vec3(0f, 0.45f, 0f), 0.5f, snow, lat = 6, lon = 10)
            ball(p + Vec3(0f, 1.15f, 0f), 0.36f, snow, lat = 6, lon = 10)
            ball(p + Vec3(0f, 1.65f, 0f), 0.26f, snow, lat = 6, lon = 10)
            paint(rgb(0xFFFF8A3D)) { append(MeshBuilder().apply { lathe(listOf(0.01f to 0.3f, 0.05f to 0f), 6, Part.PAINTED) }.build(), Mat4.trs(p + Vec3(0f, 1.66f, 0.24f), rotX = PI.toFloat() / 2f)) }
            for (s in listOf(-1f, 1f)) ball(p + Vec3(0.09f * s, 1.75f, 0.22f), 0.035f, rgb(0xFF263238), lat = 3, lon = 4)
            stick(listOf(p + Vec3(0.3f, 1.15f, 0f), p + Vec3(0.75f, 1.45f, 0.1f * k)), { 0.025f }, rgb(0xFF6D4C41))
        }
        for ((k, p) in aroundSquare(8, 7.6f, 13).withIndex()) {
            paint(snow) { lathe(listOf(0.01f to p.y + 0.05f, 0.6f + 0.4f * hash01(k, 1, 91) to p.y + 0.03f), 9, Part.PAINTED, Vec3(p.x, 0f, p.z)) }
        }
    }

    // ---- Tier 3 ------------------------------------------------------------------------------------

    /** Sunset Harbor: a ship's wheel and an anchor on a plinth, lamps on the square and along every path, barrels and crates on the beach. */
    private fun MeshBuilder.sunsetHarbor() {
        val stone = rgb(0xFFD8CFBF)
        val wood = rgb(0xFF8B5A2B)
        turned(c, listOf(1.4f to g + 0.6f, 1.6f to g - 0.6f), stone, 12)
        bx(Vec3(c.x, g + 0.75f, c.z), Vec3(0.5f, 0.3f, 0.5f), VillageModels.shade(stone, 0.9f), 0.05f)
        val hub = Vec3(c.x, g + 2.05f, c.z)
        val ring = (0..24).map { j -> val a = j / 24f * 2f * PI.toFloat(); hub + Vec3(cos(a) * 1.0f, sin(a) * 1.0f, 0f) }
        stick(ring, { 0.08f }, wood, sides = 6)
        stick(listOf(hub + Vec3(0f, -1.25f, 0f), hub), { 0.1f }, wood)
        for (k in 0 until 8) {
            val a = k / 8f * 2f * PI.toFloat()
            stick(listOf(hub, hub + Vec3(cos(a) * 1.35f, sin(a) * 1.35f, 0f)), { u -> 0.05f + 0.03f * (if (u > 0.75f) 1f else 0f) }, wood)
        }
        ball(hub, 0.16f, rgb(0xFFC9A227))
        // The anchor leaning on the plinth.
        val iron = rgb(0xFF3C4448)
        val foot = Vec3(c.x + 1.3f, g + 0.1f, c.z + 0.9f)
        stick(listOf(foot, foot + Vec3(-0.35f, 1.5f, -0.2f)), { 0.07f }, iron)
        stick((0..8).map { j -> val a = PI.toFloat() * (0.1f + 0.8f * j / 8f); foot + Vec3(0.5f * cos(a), 0.45f - 0.45f * sin(a), 0f) }, { 0.06f }, iron)
        with(VillageModels) {
            for (k in 0 until 4) {
                val a = PI.toFloat() / 4f + k * PI.toFloat() / 2f
                lamp(ground(c.x + cos(a) * 3.7f, c.z + sin(a) * 3.7f), 2.8f)
            }
            for (p in alongPaths(7f, 0.6f)) lamp(p, 2.6f)
        }
        for ((k, p) in shore(10, 1.6f, 4).withIndex()) model(if (k % 2 == 0) "barrel" else "crate", p, 0.7f, k * 1.1f)
    }

    /** Koi Pond: a pond of koi among lily pads, stone lanterns, lily pads in the shallows, bamboo. */
    private fun MeshBuilder.koiPond() {
        val stone = rgb(0xFFB7B2A8)
        paint(rgb(0xFF2F7F6E)) { lathe(listOf(0.01f to g + 0.12f, 2.4f to g + 0.12f), 18, Part.PAINTED, axis) }
        for (k in 0 until 16) {
            val a = k / 16f * 2f * PI.toFloat()
            ball(Vec3(c.x + cos(a) * 2.55f, g + 0.1f, c.z + sin(a) * 2.55f), 0.34f + 0.1f * hash01(k, 1, 93), VillageModels.shade(stone, 0.9f + 0.2f * hash01(k, 2, 93)), scale = Vec3(1.2f, 0.6f, 1f))
        }
        for (k in 0 until 9) {
            val a = k * 2.4f
            val r = 0.5f + 1.4f * hash01(k, 3, 93)
            val p = Vec3(c.x + cos(a) * r, g + 0.14f, c.z + sin(a) * r)
            paint(rgb(0xFF5DAA4A)) { lathe(listOf(0.01f to p.y + 0.02f, 0.32f to p.y), 9, Part.PAINTED, Vec3(p.x, 0f, p.z)) }
            if (k % 3 == 0) {
                for (j in 0 until 6) {
                    val b = j / 6f * 2f * PI.toFloat()
                    ball(p + Vec3(cos(b) * 0.1f, 0.1f, sin(b) * 0.1f), 0.09f, rgb(0xFFF6A6C8), scale = Vec3(0.6f, 1f, 0.6f), lat = 3, lon = 5)
                }
                ball(p + Vec3(0f, 0.12f, 0f), 0.06f, rgb(0xFFFFE066), lat = 3, lon = 5)
            }
        }
        for (k in 0 until 4) {
            val a = k * 1.6f + 0.5f
            val p = Vec3(c.x + cos(a) * 1.2f, g + 0.13f, c.z + sin(a) * 1.2f)
            val col = rgb(listOf(0xFFFF8A3D, 0xFFFFFBF2, 0xFFE53935, 0xFFFFB74D)[k])
            ball(p, 0.4f, col, scale = Vec3(1f, 0.28f, 0.34f))
            ball(p + Vec3(-0.45f * cos(0f), 0f, 0f), 0.2f, VillageModels.shade(col, 0.9f), scale = Vec3(0.6f, 0.1f, 1f))
        }
        stoneLantern(VillageModels.ground(c.x + 3.0f, c.z + 0.6f))
        for (p in aroundSquare(3, 5.6f, 7)) stoneLantern(p)
        // Lily pads in the shallows round the island, bamboo on the lawns.
        for ((k, p) in shore(16, -1.8f, 6).withIndex()) {
            paint(rgb(0xFF5DAA4A)) { lathe(listOf(0.01f to 0.06f, 0.45f + 0.2f * hash01(k, 4, 93) to 0.04f), 9, Part.PAINTED, Vec3(p.x, 0f, p.z)) }
            if (k % 4 == 0) ball(Vec3(p.x, 0.15f, p.z), 0.1f, rgb(0xFFF6A6C8), lat = 3, lon = 5)
        }
        for ((k, p) in aroundSquare(3, 9.5f, 11).withIndex()) {
            for (j in 0 until 6) {
                val q = p + Vec3(0.25f * cos(j * 1.9f + k), 0f, 0.25f * sin(j * 1.9f + k))
                val tall = 2.6f + 0.8f * hash01(j, k, 95)
                stick(listOf(q, q + Vec3(0.1f, tall, 0f)), { 0.06f }, rgb(0xFF7DB346), Part.LEAF)
                for (n in 1..3) ball(q + Vec3(0.1f * n / 4f, tall * n / 4f, 0f), 0.07f, rgb(0xFF5E8F33), scale = Vec3(1f, 0.25f, 1f), lat = 3, lon = 5)
            }
        }
    }

    private fun MeshBuilder.stoneLantern(at: Vec3) {
        val stone = rgb(0xFFB7B2A8)
        bx(at + Vec3(0f, 0.12f, 0f), Vec3(0.7f, 0.24f, 0.7f), stone, 0.05f)
        turned(at, listOf(0.12f to at.y + 1.0f, 0.16f to at.y + 0.24f), stone, 8)
        bx(at + Vec3(0f, 1.15f, 0f), Vec3(0.55f, 0.35f, 0.55f), stone, 0.05f)
        bx(at + Vec3(0f, 1.15f, 0f), Vec3(0.3f, 0.22f, 0.58f), rgb(0xFFFFE08A), 0.01f, Part.BULB)
        paint(VillageModels.shade(stone, 0.85f)) { lathe(listOf(0.05f to at.y + 1.75f, 0.6f to at.y + 1.38f, 0.5f to at.y + 1.32f), 8, Part.PAINTED, Vec3(at.x, 0f, at.z)) }
    }

    /** Treasure Cove: a heap of gold with an open chest and gems; coins and barrels round the square; chests and X marks on the beaches, gold glinting along the paths. */
    private fun MeshBuilder.treasureCove() {
        val gold = rgb(0xFFF2C14E)
        turned(c, listOf(0.3f to g + 1.05f, 1.2f to g + 0.7f, 2.1f to g + 0.15f, 2.3f to g - 0.3f), gold, 16)
        for (k in 0 until 30) {
            val a = k * 2.39f
            val r = 0.4f + 1.9f * hash01(k, 1, 97)
            val y = VillageModels.height(c.x, c.z) + 1.05f - 0.42f * r
            coin(Vec3(c.x + cos(a) * r, y + 0.04f, c.z + sin(a) * r), k)
        }
        model("chest", Vec3(c.x + 0.2f, g + 0.85f, c.z - 0.1f), 1.1f, 0.4f)
        for ((k, col) in listOf(0xFFE53958, 0xFF35D07F, 0xFF4FA3FF, 0xFFB07BFF).withIndex()) {
            ball(Vec3(c.x - 0.8f + 0.5f * k, g + 0.95f - 0.1f * (k % 2), c.z + 0.6f), 0.12f, rgb(col), Part.BULB, lat = 4, lon = 6)
        }
        // Two swords crossed in the heap.
        for (s in listOf(-1f, 1f)) {
            bx(Vec3(c.x - 0.9f, g + 1.2f, c.z - 0.5f), Vec3(0.08f, 1.5f, 0.03f), rgb(0xFFD9DEE3), 0.01f, rotZ = 0.5f * s)
            bx(Vec3(c.x - 0.9f - 0.38f * s, g + 1.85f, c.z - 0.5f), Vec3(0.35f, 0.06f, 0.08f), gold, 0.01f, rotZ = 0.5f * s)
        }
        for ((k, p) in aroundSquare(5, 5.4f, 3).withIndex()) for (j in 0 until 6) coin(p + Vec3(0.12f * (j % 2), 0.03f + 0.05f * j, 0.08f * (j % 3)), k * 7 + j)
        for ((k, p) in aroundSquare(2, 6.6f, 8).withIndex()) model("barrel", p, 0.75f, k * 1.7f)
        // On the beaches: chests half in the sand and red crosses; gold glinting along the paths.
        for ((k, p) in shore(3, 2.2f, 2).withIndex()) model("chest", p + Vec3(0f, -0.35f, 0f), 0.9f, k * 2.1f)
        for ((k, p) in shore(3, 3.2f, 7).withIndex()) for (s in listOf(-1f, 1f)) bx(p + Vec3(0f, 0.05f, 0f), Vec3(1.0f, 0.06f, 0.18f), rgb(0xFFD32F2F), 0.01f, rotY = 0.8f * s + k)
        for (p in alongPaths(4.5f, 0.4f)) ball(p + Vec3(0f, 0.08f, 0f), 0.07f, gold, Part.BULB, lat = 3, lon = 5)
    }

    private fun MeshBuilder.coin(at: Vec3, k: Int) =
        paint(rgb(if (k % 3 == 0) 0xFFF7D26B else 0xFFF2C14E)) {
            append(MeshBuilder().apply { tube(listOf(Vec3(0f, -0.02f, 0f), Vec3(0f, 0.02f, 0f)), { 0.12f }, 9, Part.PAINTED) }.build(), Mat4.trs(at, rotX = 0.3f * (hash01(k, 7, 97) - 0.5f), rotZ = 0.3f * (hash01(k, 8, 97) - 0.5f)))
        }

    /** Aurora (the Club's): a crystal obelisk in the northern lights' colours, crystals round the square and on the beaches. */
    private fun MeshBuilder.aurora() {
        val stone = rgb(0xFF5A6473)
        turned(c, listOf(1.5f to g + 0.4f, 1.7f to g - 0.6f), stone, 8)
        val bands = listOf(0xFF5CF2B0, 0xFF4FD8E8, 0xFF7FA8FF, 0xFFB07BFF, 0xFFFF8AD8)
        val tall = 4.4f
        for ((k, col) in bands.withIndex()) {
            val lo = g + 0.4f + k * tall / bands.size
            val hi = lo + tall / bands.size
            val r0 = 0.62f - 0.1f * k
            val r1 = 0.62f - 0.1f * (k + 1)
            turned(c, listOf(maxOf(r1, 0.02f) to hi, r0 to lo), rgb(col), 6, Part.BULB)
        }
        turned(c, listOf(0.01f to g + 0.4f + tall + 0.5f, 0.14f to g + 0.4f + tall), rgb(0xFFFFFFFF), 6, Part.BULB)
        for (k in 0 until 6) crystal(Vec3(c.x + cos(k * 1.05f) * 1.25f, g + 0.4f, c.z + sin(k * 1.05f) * 1.25f), bands[k % bands.size], 0.6f + 0.25f * (k % 2), k)
        for ((k, p) in aroundSquare(5, 5.6f, 4).withIndex()) crystal(p, bands[(k + 2) % bands.size], 0.9f, k)
        // All over the island: crystals on the beaches and glowing stones along the paths.
        for ((k, p) in shore(10, 2.0f, 9).withIndex()) crystal(p, bands[k % bands.size], 0.8f, k)
        for ((k, p) in alongPaths(5f, 0.5f).withIndex()) {
            ball(p + Vec3(0f, 0.12f, 0f), 0.22f, rgb(0xFF5A6473), scale = Vec3(1.2f, 0.6f, 1f))
            crystal(p + Vec3(0f, 0.18f, 0f), bands[k % bands.size], 0.45f, k)
        }
    }

    private fun MeshBuilder.crystal(at: Vec3, color: Long, size: Float, k: Int) {
        for (j in 0 until 4) {
            val tall = size * (1.2f + 0.5f * j % 2)
            val m = MeshBuilder().apply { lathe(listOf(0.01f to tall, 0.18f * size to tall * 0.75f, 0.2f * size to 0.1f * tall, 0.16f * size to 0f), 6, Part.BULB) }.build()
            paint(rgb(color)) { append(m, Mat4.trs(at + Vec3(0.18f * size * cos(j * 2.1f + k), 0f, 0.18f * size * sin(j * 2.1f + k)), rotX = 0.25f * (j - 1), rotZ = 0.2f * (k % 3 - 1))) }
        }
    }

    // ---- Tier 4 ------------------------------------------------------------------------------------

    /** Glow Tide: a giant glowing jellyfish over a dark pool; jelly lanterns, glowing mushrooms along the paths, plankton on the beaches and jellyfish in the shallows. */
    private fun MeshBuilder.glowTide() {
        basin(2.4f, rgb(0xFF3B4D63), rgb(0xFF173A52))
        jelly(Vec3(c.x, g + 2.6f, c.z), 1.6f, 0xFFFF7FD8, 0xFF6FF2FF, 12, 2.0f)
        for ((k, p) in aroundSquare(4, 5.2f, 1).withIndex()) {
            stick(listOf(p, p + Vec3(0f, 2.4f, 0f)), { 0.07f }, rgb(0xFF37474F), sides = 6)
            jelly(p + Vec3(0f, 2.55f, 0f), 0.45f, if (k % 2 == 0) 0xFF6FF2FF else 0xFFB07BFF, 0xFFFF7FD8, 6, 0.6f)
        }
        for ((k, p) in alongPaths(4.5f, 0.5f).withIndex()) {
            turned(p, listOf(0.05f to p.y + 0.3f, 0.07f to p.y), rgb(0xFFF1EDE4), 6)
            turned(p, listOf(0.02f to p.y + 0.48f, 0.26f to p.y + 0.34f, 0.24f to p.y + 0.28f), rgb(if (k % 2 == 0) 0xFF6FF2FF else 0xFFB07BFF), 8, Part.BULB)
        }
        for ((k, p) in shore(36, 0.9f, 3).withIndex()) ball(p + Vec3(0f, 0.08f, 0f), 0.06f + 0.03f * (k % 2), rgb(if (k % 3 == 0) 0xFF6FF2FF else 0xFF5CF2B0), Part.BULB, lat = 3, lon = 4)
        for ((k, p) in shore(10, -3.5f, 5).withIndex()) jelly(Vec3(p.x, 0.35f, p.z), 0.4f, if (k % 2 == 0) 0xFFFF7FD8 else 0xFF6FF2FF, 0xFF6FF2FF, 6, 0.7f)
    }

    private fun MeshBuilder.jelly(at: Vec3, r: Float, bell: Long, tentacles: Long, count: Int, length: Float) {
        turned(at, listOf(0.02f to at.y + 0.75f * r, 0.65f * r to at.y + 0.6f * r, 0.95f * r to at.y + 0.25f * r, r to at.y, 0.75f * r to at.y - 0.05f * r), rgb(bell), 14, Part.BULB)
        for (k in 0 until count) {
            val a = k / count.toFloat() * 2f * PI.toFloat()
            val top = at + Vec3(cos(a) * 0.75f * r, -0.04f * r, sin(a) * 0.75f * r)
            stick((0..6).map { j -> val u = j / 6f; top + Vec3(0.12f * r * sin(u * 6f + k), -length * u, 0.12f * r * cos(u * 5f + k)) }, { u -> 0.05f * r * (1f - 0.7f * u) + 0.01f }, rgb(tentacles), Part.BULB, 4)
        }
        for (k in 0 until 5) ball(at + Vec3(0.45f * r * cos(k * 1.26f), 0.5f * r, 0.45f * r * sin(k * 1.26f)), 0.07f * r, rgb(0xFFFFFFFF), Part.BULB, lat = 3, lon = 4)
    }
}
