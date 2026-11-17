package com.deepline.fishing.render3d

import com.deepline.fishing.Building
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Meshes
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.theme.MenuPalettes
import com.deepline.fishing.ui.village.IslandDress
import com.deepline.fishing.ui.village.VillageCamera
import com.deepline.fishing.ui.village.VillageLayout
import com.deepline.fishing.ui.village.VillageModels
import com.deepline.fishing.ui.village.VillageScene
import org.junit.Assert.assertTrue
import org.junit.Test

/** The village's 3D scene: its meshes are sound, and a frame builds in every theme, by day and night. */
class VillageSceneTest {
    private fun assertSound(name: String, m: MeshData) {
        assertTrue("$name is empty", m.triangleCount > 0)
        for (k in m.indices) assertTrue("$name: index out of range", k in 0 until m.vertexCount)
        for (v in m.positions) assertTrue("$name: a vertex isn't finite", v.isFinite())
        assertTrue("$name has no colours (the painted material needs them)", m.colors != null)
    }

    @Test
    fun theVillageMeshesAreSoundAndFitTheBudget() {
        var triangles = 0
        for (key in listOf("village:terrain", "village:sea", "village:town", "village:glass")) {
            val m = Meshes.get(key)
            assertSound(key, m)
            triangles += m.triangleCount
        }
        // The decorations of the richest look: the bunting and the biggest monument.
        val dress = (listOf("village:dress:bunting") + IslandDress.legends.map { "village:dress:$it" }).map { key ->
            Meshes.get(key).also { assertSound(key, it) }.triangleCount
        }
        triangles += dress[0] + dress.drop(1).max()
        // A mid-range phone's GPU is the limit (docs/ui-design.md): keep the whole village small.
        assertTrue("The village has $triangles triangles", triangles < 150_000)
    }

    @Test
    fun framesBuildInEveryLookOfTheIsland() {
        val scene = VillageScene()
        val camera = VillageCamera().apply { fit(1080f, 2156f, floatArrayOf(220f, 394f, 42f, 95f)) }
        val looks = listOf(
            IslandDress.CLASSIC,
            IslandDress(1, flag = 0xFFE53935L),
            IslandDress(2, flag = 0xFFE53935L, trim = 0xFFFFFFFFL),
            IslandDress(3, 0xFF1565C0L, 0xFFE53935L, 0xFFFFFFFFL),
        ) + IslandDress.legends.map { IslandDress(4, 0xFF6A3FA0L, 0xFFF2C14EL, 0xFFF2C14EL, it) }
        for (dress in looks) {
            val frame = scene.build(720, 1437, 3.2f, camera, VillageScene.Looks(MenuPalettes.of(MenuTheme.LAGOON, false), Skins.CLASSIC, emptyList(), dress))
            val keys = frame.items.mapNotNull { it.meshKey }
            assertTrue(dress.bunting == ("village:dress:bunting" in keys))
            dress.legend?.let { assertTrue("village:dress:$it" in keys) }
            for (item in frame.items) {
                val m = item.dynamicMesh ?: Meshes.get(item.meshKey!!)
                assertTrue(m.indices.all { it < m.vertexCount })
                for (v in item.uniforms.values) assertTrue(v.size in setOf(1, 2, 3, 4, 9, 16))
            }
        }
    }

    @Test
    fun theVillageIsAnIslandWithItsPlacesOnLandOrOverTheWater() {
        // All round the island: dry a few metres inland, under water a few metres out.
        for (k in 0 until 36) {
            val a = k / 36f * 2f * Math.PI.toFloat()
            val r = VillageLayout.islandRadius(a)
            fun at(d: Float) = VillageModels.height(VillageLayout.ISLAND_X + kotlin.math.cos(a) * d, VillageLayout.ISLAND_Z + kotlin.math.sin(a) * d)
            assertTrue("inland at angle $a", at(r - 3f) > 0f)
            assertTrue("out at sea at angle $a", at(r + 6f) < 0f)
        }
        // Sea everywhere around it.
        for ((x, z) in listOf(-45f to -18f, 45f to -18f, 0f to -60f, 0f to 40f)) assertTrue(VillageModels.height(x, z) < 0f)
        // The lighthouse, the halls and the pavilion stand on land.
        for (b in listOf(Building.DEX, Building.SHOP, Building.ROPE, Building.AQUARIUM)) {
            val c = VillageLayout.spots.getValue(b).center
            assertTrue("$b stands in the sea", VillageModels.height(c.x, c.z) > 0f)
        }
        // The pier and the duelling platform reach out over the water; their feet are on the beach.
        assertTrue(VillageModels.height(VillageLayout.pierTo.x, VillageLayout.pierTo.z) < 0f)
        val duel = VillageLayout.spots.getValue(Building.DUEL).center
        assertTrue(VillageModels.height(duel.x, duel.z) < 0f)
        assertTrue(VillageLayout.shoreDistance(VillageLayout.pierFrom.x, VillageLayout.pierFrom.z) > 0f)
        assertTrue(VillageLayout.shoreDistance(VillageLayout.jettyFrom.x, VillageLayout.jettyFrom.z) > 0f)
    }

    @Test
    fun framesBuildInEveryThemeByDayAndNight() {
        val scene = VillageScene()
        val camera = VillageCamera().apply { fit(1080f, 2156f, floatArrayOf(220f, 394f, 42f, 95f)) }
        val tank = FishCatalog.all.take(6)
        for (theme in MenuTheme.entries) for (night in listOf(false, true)) {
            val looks = VillageScene.Looks(MenuPalettes.of(theme, night), Skins.CLASSIC, tank)
            val frame = scene.build(720, 1437, 3.2f, camera, looks)
            for (item in frame.items) {
                val m = item.dynamicMesh ?: Meshes.get(item.meshKey!!)
                assertTrue(m.indices.all { it < m.vertexCount })
                for (v in item.uniforms.values) assertTrue(v.size in setOf(1, 2, 3, 4, 9, 16))
            }
            for (v in frame.globals.values) assertTrue(v.size in setOf(1, 2, 3, 4, 9, 16))
        }
    }

    @Test
    fun theCameraNeverSeesPastTheGround() {
        // The view always looks down at the ground or the sea: no sky to draw, at any zoom.
        val camera = VillageCamera().apply { fit(1080f, 2156f, floatArrayOf(220f, 394f, 42f, 95f)) }
        for (d in listOf(camera.minDistance, camera.maxDistance)) {
            camera.distance = d
            assertTrue(camera.pitch - camera.fovY / 2f > 0.2f)
            val top = Vec3(camera.targetX, 0f, camera.targetZ - 400f)
            assertTrue(camera.toScreen(top, 1080f, 2156f)!!.second < 0f)
        }
    }
}

/** The 3D aquarium: every piece of decor builds, in both tanks, in a card and full screen. */
class AquariumSceneTest {
    @Test
    fun framesBuildWithEveryDecorInBothTanks() {
        val scene = com.deepline.fishing.ui.render3d.AquariumScene()
        val fish = FishCatalog.all.take(12)
        for (big in listOf(false, true)) for ((w, h) in listOf(1080 to 720, 2340 to 1080)) {
            val frame = scene.build(w, h, 4.2f, fish, com.deepline.fishing.core.Decor.entries, big)
            for (item in frame.items) {
                val m = item.dynamicMesh ?: Meshes.get(item.meshKey!!)
                assertTrue(m.indices.all { it < m.vertexCount })
                for (v in item.uniforms.values) assertTrue(v.size in setOf(1, 2, 3, 4, 9, 16))
            }
        }
        // An empty tank is fine too.
        scene.build(1080, 720, 0f, emptyList(), emptyList(), false)
    }
}
