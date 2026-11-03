package com.deepline.fishing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishEntity
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.DeepLineTheme
import com.deepline.fishing.ui.drawCastScene
import com.deepline.fishing.ui.drawFish
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the underwater scene below 300 m, with and without a Lantern. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SceneRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun deepSession(lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        fun fish(id: Int, sp: String, x: Float, depth: Float, dir: Int, sparkling: Boolean = false) =
            FishEntity(id, FishCatalog.byId(sp)!!, sparkling, x, depth, dir, 0f)
        val world = World(
            fish = listOf(
                fish(0, "ghost_hake", c - 5f, 338f, 1),
                fish(1, "needle_viper", c + 4f, 342f, -1, sparkling = true),
                fish(2, "ink_cuttle", c - 3f, 352f, -1),
                fish(3, "iron_sturgeon", c + 3f, 358f, 1),
                fish(4, "ghost_hake", c - 7f, 330f, 1),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.JELLY, c + 6f, 349f, 1.4f)),
        )
        val session = CastSession(RopeSpecs(620f, 30f, 12f), lantern, world)
        while (session.phase == CastPhase.DESCENDING && session.hookDepth < 345f) session.update(1f / 60f, null)
        return session
    }

    private fun render(name: String, session: CastSession) {
        compose.setContent {
            DeepLineTheme {
                val measurer = rememberTextMeasurer()
                androidx.compose.foundation.Canvas(Modifier.size(400.dp, 860.dp)) { drawCastScene(session, 1f, measurer) }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderCanvas(name: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        compose.setContent {
            DeepLineTheme {
                androidx.compose.foundation.Canvas(Modifier.size(400.dp, 860.dp)) { draw() }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Every species, large, to review the fish art. */
    @Test
    fun fishGallery() {
        renderCanvas("19_fish_gallery") {
            drawRect(androidx.compose.ui.graphics.Color(0xFF0B4F7A))
            val all = FishCatalog.all
            val rowH = size.height / 8f
            all.forEachIndexed { i, sp ->
                val col = i % 2
                val row = i / 2
                val len = size.width * 0.40f * (if (sp.heightRatio < 0.2f) 1.1f else 1f)
                val c = androidx.compose.ui.geometry.Offset(size.width * (0.27f + col * 0.48f), rowH * (row + 0.5f))
                drawFish(sp, c, len, if (col == 0) 1 else -1, sparkling = i == 4, timeSeconds = 0.4f, phase = i.toFloat())
            }
        }
    }

    /** Near the surface: boat, rod, line, bump-free shield and the 30 m marker. */
    @Test
    fun nearSurfaceDescending() {
        val c = GameConfig.WORLD_WIDTH / 2f
        val sp = FishCatalog.byId("coral_darter")!!
        val world = World(
            fish = listOf(
                FishEntity(0, sp, false, c - 4f, 22f, 1, 0f),
                FishEntity(1, FishCatalog.byId("reed_perch")!!, false, c + 5f, 31f, -1, 0f),
                FishEntity(2, FishCatalog.byId("silver_sprat")!!, true, c + 2f, 27f, 1, 0f),
            ),
            obstacles = listOf(Obstacle(0, ObstacleKind.DRIFTWOOD, c - 6f, 34f, 1.4f)),
        )
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, world)
        while (session.hookDepth < 14f) session.update(1f / 60f, null)
        render("20_near_surface", session)
    }

    /** Reeling up with fish on the hook (rod bent, catch burst). */
    @Test
    fun reelingUpWithCatches() {
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
        render("21_reeling_up", session)
    }

    @Test
    fun darkWaterWithoutLantern() {
        val s = deepSession(lantern = false)
        assertTrue(s.hookDepth > GameConfig.DARK_DEPTH)
        render("14_dark_no_lantern", s)
    }

    @Test
    fun darkWaterWithLantern() {
        render("15_dark_with_lantern", deepSession(lantern = true))
    }
}
