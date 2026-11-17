package com.deepline.fishing.render3d

import com.deepline.fishing.Building
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.MeshData
import com.deepline.fishing.ui.render3d.Meshes
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.theme.MenuPalettes
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
        // A mid-range phone's GPU is the limit (docs/ui-design.md): keep the whole village small.
        assertTrue("The village has $triangles triangles", triangles < 150_000)
    }

    @Test
    fun theSeaCoversTheBeachesEdgeAndTheGroundRisesInland() {
        // The ground dips under the sea a little way past the shore, and the buildings stand on dry land.
        for (x in listOf(-20f, 0f, 20f)) {
            assertTrue(VillageModels.height(x, VillageModels.shoreZ(x) + 6f) < 0f)
            assertTrue(VillageModels.height(x, VillageModels.shoreZ(x) - 3f) > 0f)
        }
        for (b in listOf(Building.DEX, Building.SHOP, Building.ROPE, Building.AQUARIUM)) {
            val c = VillageLayout.spots.getValue(b).center
            assertTrue("$b stands in the sea", VillageModels.height(c.x, c.z) > 0f)
        }
        // The pier and the duelling platform reach out over the water.
        assertTrue(VillageModels.height(VillageLayout.pierTo.x, VillageLayout.pierTo.z) < 0f)
        val duel = VillageLayout.spots.getValue(Building.DUEL).center
        assertTrue(VillageModels.height(duel.x, duel.z) < 0f)
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
