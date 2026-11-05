package com.deepline.fishing.render3d

import com.deepline.fishing.core.BotBrain
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.Camera3d
import com.deepline.fishing.ui.render3d.FishModels
import com.deepline.fishing.ui.render3d.Mat4
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Meshes
import com.deepline.fishing.ui.render3d.Part
import com.deepline.fishing.ui.render3d.PropModels
import com.deepline.fishing.ui.render3d.SceneBuilder
import com.deepline.fishing.ui.render3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class Render3dTest {
    /** The 3D camera puts the play plane exactly where the 2D renderer (and touch input) put it. */
    @Test
    fun theCameraMapsThePlayPlaneLikeThe2dView() {
        for ((w, h) in listOf(1080 to 2400, 720 to 1280, 1600 to 2560)) {
            for (hookDepth in listOf(0f, 14f, 250f, 600f)) {
                val cam = Camera3d.forHook(w, h, hookDepth)
                val scale = w / GameConfig.WORLD_WIDTH
                val viewHeight = h / scale
                val top = maxOf(hookDepth - viewHeight * 0.4f, -viewHeight * 0.2f)
                for ((x, depth) in listOf(0f to top, 20f to top + 10f, 7.3f to hookDepth, 13f to top + viewHeight * 0.9f)) {
                    val (sx, sy) = cam.toScreen(Vec3(x, -depth, 0f))
                    assertEquals("x at $x,$depth on ${w}x$h", x * scale, sx, 0.5f)
                    assertEquals("y at $x,$depth on ${w}x$h", (depth - top) * scale, sy, 0.5f)
                }
            }
        }
    }

    @Test
    fun normalMatrixIsTheInverseTranspose() {
        val m = Mat4.trs(Vec3(3f, -2f, 1f), rotY = 0.7f, rotZ = 0.3f, scale = Vec3(2f, 1f, 0.5f))
        val nm = Mat4.normalMatrix(m)
        // A normal transformed by the normal matrix stays perpendicular to transformed tangents.
        val t = Vec3(1f, 2f, 0f)
        val n = Vec3(2f, -1f, 0.3f).let { val k = (it dot t) / (t dot t); (it - t * k) } // perpendicular to t
        val tt = Mat4.transformPoint(m, t) - Mat4.transformPoint(m, Vec3.ZERO)
        val nn = Vec3(nm[0] * n.x + nm[3] * n.y + nm[6] * n.z, nm[1] * n.x + nm[4] * n.y + nm[7] * n.z, nm[2] * n.x + nm[5] * n.y + nm[8] * n.z)
        assertEquals(0f, nn dot tt, 1e-4f)
    }

    private fun assertWindingMatchesNormals(name: String, m: MeshData) {
        var bad = 0
        for (t in 0 until m.triangleCount) {
            val i = IntArray(3) { m.indices[t * 3 + it] }
            fun p(k: Int) = Vec3(m.positions[k * 3], m.positions[k * 3 + 1], m.positions[k * 3 + 2])
            fun n(k: Int) = Vec3(m.normals[k * 3], m.normals[k * 3 + 1], m.normals[k * 3 + 2])
            val face = (p(i[1]) - p(i[0])) cross (p(i[2]) - p(i[0]))
            // Skip (near-)degenerate triangles, e.g. at sphere poles.
            if (face.length() < 1e-6f * ((p(i[1]) - p(i[0])).length() + (p(i[2]) - p(i[0])).length() + 1e-6f)) continue
            val avg = n(i[0]) + n(i[1]) + n(i[2])
            if ((face dot avg) < 0f) bad++
        }
        assertTrue("$name: $bad of ${m.triangleCount} triangles face inwards", bad <= m.triangleCount / 50)
        for (k in m.indices) assertTrue("$name: index out of range", k in 0 until m.vertexCount)
    }

    @Test
    fun everyMeshIsClosedTheRightWayRound() {
        for (sp in FishCatalog.all) assertWindingMatchesNormals(sp.id, FishModels.mesh(sp))
        val props = listOf("hook:null", "worm", "sinker", "rock:0", "rock:5", "wood:1", "jelly", "boat:null", "kelp:2", "decor:1", "coral:3") +
            Cosmetic.entries.filter { it.slot == CosmeticSlot.HOOK || it.slot == CosmeticSlot.BOAT }.map { (if (it.slot == CosmeticSlot.HOOK) "hook:" else "boat:") + it.name }
        for (key in props) assertWindingMatchesNormals(key, Meshes.get(key))
        // Every hand-made boat combination.
        for (sail in com.deepline.fishing.core.SailStyle.entries) for (flag in com.deepline.fishing.core.FlagStyle.entries) {
            for (emblem in com.deepline.fishing.core.Emblem.entries) {
                assertWindingMatchesNormals("boatd $sail $flag $emblem", Meshes.get("boatd:${sail.name}:${flag.name}:${emblem.name}"))
            }
        }
        // Fish in the Nightmare atmosphere (spines, fangs).
        for (sp in FishCatalog.all) assertWindingMatchesNormals("${sp.id} nightmare", Meshes.get("fish:${sp.id}:NIGHTMARE"))
    }

    /** Every emblem is really drawn (an outline the triangulator can't fill would vanish). */
    @Test
    fun everyEmblemAddsTriangles() {
        val bare = Meshes.get("boatd:TRIANGLE:PENNANT:NONE").indices.size
        for (emblem in com.deepline.fishing.core.Emblem.entries - com.deepline.fishing.core.Emblem.NONE) {
            val n = Meshes.get("boatd:TRIANGLE:PENNANT:${emblem.name}").indices.size
            assertTrue("$emblem draws nothing", n > bare + 3 * 8)
        }
    }

    /** The drawn body matches the collision shape: 1 long and heightRatio tall, nose at +0.5. */
    @Test
    fun fishBodiesMatchTheirHitbox() {
        for (sp in FishCatalog.all) {
            val m = FishModels.mesh(sp)
            var minX = 9f; var maxX = -9f; var minY = 9f; var maxY = -9f
            for (i in 0 until m.vertexCount) {
                if (m.parts[i] != Part.BODY) continue
                minX = minOf(minX, m.positions[i * 3]); maxX = maxOf(maxX, m.positions[i * 3])
                minY = minOf(minY, m.positions[i * 3 + 1]); maxY = maxOf(maxY, m.positions[i * 3 + 1])
            }
            assertTrue("${sp.id} nose at $maxX", abs(maxX - 0.5f) < 0.06f)
            assertTrue("${sp.id} body from $minX", minX > -0.52f)
            assertTrue("${sp.id} height ${maxY - minY} vs ${sp.heightRatio}", maxY - minY in sp.heightRatio * 0.85f..sp.heightRatio * 1.15f)
        }
    }

    /** Hook skins change the look only: the hook itself has the same shape and size. */
    @Test
    fun hookSkinsKeepTheHookSize() {
        fun metalBounds(m: MeshData): List<Float> {
            val pts = (0 until m.vertexCount).filter { m.parts[it] == Part.METAL }
            return listOf(0, 1, 2).flatMap { a -> listOf(pts.minOf { m.positions[it * 3 + a] }, pts.maxOf { m.positions[it * 3 + a] }) }
        }
        val classic = metalBounds(PropModels.hook(null))
        for (c in Cosmetic.entries.filter { it.slot == CosmeticSlot.HOOK }) {
            assertEquals(c.name, classic, metalBounds(PropModels.hook(c)))
        }
    }

    /** Builds a frame for every step of whole casts, with every skin set: nothing throws. */
    @Test
    fun wholeCastsBuildFramesWithEverySkin() {
        val sets = listOf(Skins.CLASSIC) + listOf("Giant-slayer", "Prismatic", "Sovereign").map { Skins.ofSet(it) }
        for ((i, skins) in sets.withIndex()) {
            for (level in listOf(0, 10)) {
                val specs = RopeSpecs.fromLevels(RopeStat.entries.associateWith { level })
                val seed = 40L + i * 7 + level
                val session = CastSession.create(specs, lanternActive = level == 10, random = Random(seed))
                val brain = BotBrain(BotDifficulty.HARD, Random(seed))
                val builder = SceneBuilder()
                var t = 0f
                var frames = 0
                while (session.phase != CastPhase.SURFACED && frames < 20_000) {
                    session.update(1f / 60f, brain.steer(session, 1f / 60f))
                    t += 1f / 60f
                    if (frames % 6 == 0) {
                        val frame = builder.build(session, t, 360, 800, skins)
                        for (item in frame.items) {
                            val m = item.dynamicMesh ?: Meshes.get(item.meshKey!!)
                            assertTrue(m.indices.all { it < m.vertexCount })
                            for (v in item.uniforms.values) assertTrue(v.size in setOf(1, 2, 3, 4, 9, 16))
                        }
                    }
                    frames++
                }
            }
        }
    }
}
