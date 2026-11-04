package com.deepline.fishing.render3d

import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.render3d.Frame
import com.deepline.fishing.ui.render3d.SceneBuilder
import com.deepline.fishing.ui.render3d.Vec3
import org.junit.Test
import java.io.File

/**
 * Exports a few 3D scenes as JSON for the WebGL preview (`tools/render3d-preview/render.cjs`),
 * which draws them with the app's shaders into PNG files next to the JSON in build/render3d.
 */
class Render3dExportTest {
    private val out = File("build/render3d")
    private val w = 540
    private val h = 1200

    private fun export(name: String, frame: Frame) = Render3dExport.write(frame, File(out, "$name.json"))

    @Test
    fun nearSurface() {
        val c = GameConfig.WORLD_WIDTH / 2f
        val world = World(
            fish = listOf(
                FishEntity(0, FishCatalog.byId("coral_darter")!!, false, c - 4f, 22f, 1, 0f),
                FishEntity(1, FishCatalog.byId("reed_perch")!!, false, c + 5f, 31f, -1, 0f),
                FishEntity(2, FishCatalog.byId("silver_sprat")!!, true, c + 2f, 27f, 1, 0f),
                FishEntity(3, FishCatalog.byId("sun_bream")!!, false, c - 5f, 36f, -1, 0f),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.DRIFTWOOD, c - 6f, 34f, 1.4f)),
        )
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, world)
        while (session.hookDepth < 14f) session.update(1f / 60f, null)
        export("near_surface", SceneBuilder().build(session, 1.2f, w, h))
    }

    @Test
    fun reelingUp() {
        val c = GameConfig.WORLD_WIDTH / 2f
        fun headOn(id: Int, sp: String, depth: Float): FishEntity {
            val s = FishCatalog.byId(sp)!!
            return FishEntity(id, s, id == 1, c - s.bodyLengthM / 2f * 0.8f, depth, 1, 0f)
        }
        val world = World(
            fish = listOf(
                headOn(0, "dusk_snapper", 112f),
                headOn(1, "ribbon_eel", 106f),
                FishEntity(2, FishCatalog.byId("reef_grouper")!!, false, c + 5f, 96f, -1, 0f),
                FishEntity(3, FishCatalog.byId("veil_ray")!!, false, c - 6f, 92f, 1, 0f),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.ROCK, c + 6f, 101f, 1.3f), Obstacle(1, ObstacleKind.JELLY, c - 5f, 99f, 1.2f)),
        )
        val session = CastSession(RopeSpecs(120f, 20f, 9f), false, world)
        while (session.phase != CastPhase.ASCENDING || session.hookDepth > 104.5f) session.update(1f / 60f, null)
        export("reeling_up", SceneBuilder().build(session, 2f, w, h))
    }

    private fun deepSession(lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        fun fish(id: Int, sp: String, x: Float, depth: Float, dir: Int, sparkling: Boolean = false) =
            FishEntity(id, FishCatalog.byId(sp)!!, sparkling, x, depth, dir, 0f)
        val world = World(
            fish = listOf(
                fish(0, "glowfin_angler", c + 3f, 352f, -1),
                fish(1, "ghost_hake", c - 4f, 340f, 1),
                fish(2, "ink_cuttle", c + 5f, 365f, -1, sparkling = true),
                fish(3, "needle_viper", c - 6f, 372f, 1),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.JELLY, c - 2f, 360f, 1.2f)),
        )
        val session = CastSession(RopeSpecs(500f, 20f, 9f), lantern, world)
        while (session.phase == CastPhase.DESCENDING && session.hookDepth < 345f) session.update(1f / 60f, null)
        return session
    }

    @Test
    fun darkWater() = export("dark_no_lantern", SceneBuilder().build(deepSession(false), 3f, w, h))

    @Test
    fun darkWaterLantern() = export("dark_lantern", SceneBuilder().build(deepSession(true), 3f, w, h))

    private fun surfaceSession(): CastSession {
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList()))
        while (session.hookDepth < 7f) session.update(1f / 60f, 2f)
        return session
    }

    @Test
    fun skinSets() {
        for (set in listOf("Giant-slayer", "Prismatic", "Sovereign")) {
            export("skins_${set.lowercase()}", SceneBuilder().build(surfaceSession(), 1.3f, w, h, Skins.ofSet(set), cameraTop = -7f))
        }
    }

    /** Dex portraits: framing for different body plans, in each look. */
    @Test
    fun portraits() {
        val ids = listOf("silver_sprat", "sun_bream", "ribbon_eel", "glowfin_angler", "ink_cuttle", "deep_sovereign", "pale_leviathan")
        for (id in ids) {
            val sp = FishCatalog.byId(id)!!
            for (look in com.deepline.fishing.ui.render3d.PortraitLook.entries) {
                export("portrait_${id}_${look.name.lowercase()}", com.deepline.fishing.ui.render3d.PortraitScene.build(sp, look, 384, 240, SceneBuilder.FACING_RIGHT, 0.4f))
            }
        }
    }

    /** Hand-made species and a sample of generated ones, side by side. */
    @Test
    fun fishGallery() {
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList()))
        while (session.hookDepth < 30f) session.update(1f / 60f, null)
        val base = SceneBuilder().build(session, 0.6f, w, h, cameraTop = 40f)
        val species = FishCatalog.all.take(16) + FishCatalog.all.drop(16).filterIndexed { i, _ -> i % 23 == 0 } + FishCatalog.sovereign
        val cols = 3
        val rows = (species.size + cols - 1) / cols
        val viewH = GameConfig.WORLD_WIDTH / (w / h.toFloat())
        val extra = species.mapIndexed { i, sp ->
            val x = GameConfig.WORLD_WIDTH * (i % cols + 0.5f) / cols
            val depth = 40f + viewH * (i / cols + 0.5f) / rows
            // Same size for every fish so each fits its cell.
            SceneBuilder.fishItem(sp, Vec3(x, -depth, 0f), SceneBuilder.FACING_RIGHT, 0f, i.toFloat(), 1f, i == 4, i.toFloat(), len = 5f)
        }
        val items = base.items.filter { it.meshKey == "fullscreen" && it.program.name == "BACKGROUND" } + extra
        export("fish_gallery", Frame(w, h, base.globals, items))
    }
}
