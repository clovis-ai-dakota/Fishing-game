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
