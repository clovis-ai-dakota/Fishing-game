package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The hidden "last bump" mechanic: discovery popup, results, and the Fishing secrets screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SecretUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Saved settings can outlive a test in the same class: every test starts from a clean slate. */
    @org.junit.Before
    fun clearSavedState() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("deepline", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun ComposeTestRule.click(text: String) {
        val matcher = hasText(text)
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        }
        val node = onNode(matcher)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun ComposeTestRule.advanceSeconds(seconds: Float) {
        mainClock.autoAdvance = false
        repeat((seconds * 60).toInt()) { mainClock.advanceTimeBy(16) }
        mainClock.autoAdvance = true
    }

    private fun ComposeTestRule.shot(name: String) {
        waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            v.draw(canvas)
            // Dialogs live in their own window: draw the open one on top, dimmed and centered.
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            if (dialog != null && dialog.isShowing) {
                val d = dialog.window!!.decorView
                canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
                canvas.save()
                canvas.translate((v.width - d.width) / 2f, (v.height - d.height) / 2f)
                d.draw(canvas)
                canvas.restore()
            }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isShowing(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    /** Three pieces of driftwood, then a rock, straight under the hook: the 4th bump snags the rock. */
    private fun snagWater(specs: com.deepline.fishing.core.RopeSpecs, lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        val obstacles = listOf(40f, 50f, 60f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.DRIFTWOOD, c, d, 1f) } +
            Obstacle(3, ObstacleKind.ROCK, c, 70f, 1.2f)
        return CastSession(specs, lantern, World(emptyList(), obstacles))
    }

    @Test
    fun theLastBumpSecretIsExplainedOnceThenListedInFishingSecrets() {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        vm.castFactory = ::snagWater

        assertTrue("no secrets button before any discovery", !isShowing("Secrets"))

        // First snag: the cast pauses and the secret is explained.
        compose.click("Cast line")
        compose.advanceSeconds(5f)
        compose.onNodeWithText("Fishing secret discovered!").assertExists()
        compose.shot("27_secret_discovered")
        compose.click("No thanks")
        compose.advanceSeconds(12f)
        compose.onNodeWithText("Cast complete").assertExists()
        assertTrue(isShowing("hooked by the last bump", substring = true))
        compose.shot("28_snag_results")
        compose.click("Collect")

        assertTrue(Secrets.LAST_BUMP in vm.progress.discoveredSecrets)
        assertTrue(Secrets.LAST_BUMP in vm.progress.mutedSecretPopups)
        assertEquals(1, vm.progress.objectsSnagged)

        // The secret is now listed on its own screen.
        compose.click("Secrets")
        compose.onNodeWithText("The last bump hooks what it hits").assertExists()
        compose.shot("29_fishing_secrets")
        compose.click("‹ Back")

        // Second snag: the player said "No thanks", so no popup this time.
        compose.click("Cast line")
        compose.advanceSeconds(5f)
        assertTrue(!isShowing("Fishing secret discovered!"))
        compose.advanceSeconds(12f)
        compose.onNodeWithText("Cast complete").assertExists()
    }
}
