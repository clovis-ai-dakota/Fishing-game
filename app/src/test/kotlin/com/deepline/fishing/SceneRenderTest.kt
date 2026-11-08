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
import com.deepline.fishing.ui.drawBoat
import com.deepline.fishing.ui.drawCastScene
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.drawFishGlow
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

    private fun render(
        name: String,
        session: CastSession,
        skins: com.deepline.fishing.ui.Skins = com.deepline.fishing.ui.Skins.CLASSIC,
        cameraTop: Float? = null,
        time: Float = 1f,
    ) {
        compose.setContent {
            DeepLineTheme {
                val measurer = rememberTextMeasurer()
                val labels = com.deepline.fishing.ui.rememberCastLabels()
                androidx.compose.foundation.Canvas(Modifier.size(400.dp, 860.dp)) {
                    drawCastScene(session, time, measurer, skins = skins, cameraTop = cameraTop, labels = labels)
                }
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

    /** The hand-made species, large, to review the fish art. */
    @Test
    fun fishGallery() {
        renderCanvas("19_fish_gallery") {
            drawRect(androidx.compose.ui.graphics.Color(0xFF0B4F7A))
            val all = FishCatalog.all.take(16)
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

    /** A sample of the generated species (every 4th), then the legendary one at the very bottom. */
    @Test
    fun generatedFishGallery() {
        renderCanvas("19b_generated_fish") {
            drawRect(androidx.compose.ui.graphics.Color(0xFF0B3C5D))
            val sample = FishCatalog.all.drop(16).filterIndexed { i, _ -> i % 4 == 0 }.take(44) + FishCatalog.sovereign
            val cols = 4
            val rowH = size.height / ((sample.size + cols - 1) / cols)
            sample.forEachIndexed { i, sp ->
                val c = androidx.compose.ui.geometry.Offset(size.width * (i % cols + 0.5f) / cols, rowH * (i / cols + 0.5f))
                drawFish(sp, c, size.width * 0.2f, 1, sparkling = false, timeSeconds = 0.4f, phase = i.toFloat())
                drawFishGlow(sp, c, size.width * 0.2f, 1, 1f)
            }
        }
    }

    private fun surfaceSession(): CastSession {
        val session = CastSession(RopeSpecs(120f, 10f, 9f), false, World(emptyList(), emptyList()))
        while (session.hookDepth < 7f) session.update(1f / 60f, 2f)
        return session
    }

    /** Each legendary skin set, worn at the surface: boat, line, hook and trail. */
    private fun renderSet(i: Int, set: String) =
        render("4${i}_skins_${set.lowercase()}", surfaceSession(), com.deepline.fishing.ui.Skins.ofSet(set), cameraTop = -7f, time = 1.3f)

    @Test
    fun giantSlayerSkins() = renderSet(0, "Giant-slayer")

    @Test
    fun prismaticSkins() = renderSet(1, "Prismatic")

    @Test
    fun sovereignSkins() = renderSet(2, "Sovereign")

    /** The classic boat, rod and line up close (the 2D twin of the 3D boat). */
    @Test
    fun classicBoatUpClose() = render("44_classic_boat", surfaceSession(), com.deepline.fishing.ui.Skins.CLASSIC, cameraTop = -7f, time = 1.3f)

    /** Every shop boat and the default design, as the 2D fallback draws them. */
    @Test
    fun shopBoatsUpClose() {
        val designs = listOf(com.deepline.fishing.core.BoatDesign.DEFAULT) + com.deepline.fishing.core.PremiumSkins.all.map { it.design }
        renderCanvas("45_shop_boats") {
            val cols = 2
            val rows = (designs.size + cols - 1) / cols
            val w = size.width / cols
            val h = size.height / rows
            for ((i, d) in designs.withIndex()) {
                val x0 = (i % cols) * w
                val y0 = (i / cols) * h
                val surface = y0 + h * 0.8f
                drawRect(androidx.compose.ui.graphics.Color(0xFFBFE3F2), topLeft = androidx.compose.ui.geometry.Offset(x0, y0), size = androidx.compose.ui.geometry.Size(w, surface - y0))
                drawRect(androidx.compose.ui.graphics.Color(com.deepline.fishing.ui.ColorScript.water(1f)), topLeft = androidx.compose.ui.geometry.Offset(x0, surface), size = androidx.compose.ui.geometry.Size(w, y0 + h - surface))
                drawBoat(x0 + w * 0.5f, surface, kotlin.math.min(w / 6.2f, h / 6.4f), 1.3f + i, null, d)
            }
        }
    }

    /** Skins in dark water: they never light it up (the lit circle is the same as classic). */
    @Test
    fun skinsInDarkWater() {
        render("43_skins_dark", deepSession(lantern = false), com.deepline.fishing.ui.Skins.ofSet("Sovereign"))
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
