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
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Obstacle
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.World
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A full line offers the fast reel, which brings the hook up within 1.5 s. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class FastReelUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clearSavedState() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
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

    private fun shot(name: String) {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isShowing(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** Four rocks deep down on a 4 kg line: the last bump snags a 4 kg rock and the line is full. */
    @Suppress("UNUSED_PARAMETER")
    private fun fullLineWater(specs: RopeSpecs, lantern: Boolean): CastSession {
        val c = GameConfig.WORLD_WIDTH / 2f
        val rocks = listOf(200f, 215f, 230f, 245f).mapIndexed { i, d -> Obstacle(i, ObstacleKind.ROCK, c, d, 1f) }
        return CastSession(RopeSpecs(260f, ObstacleKind.ROCK.weightKg, 9f), false, World(emptyList(), rocks))
    }

    /** Plays [seconds] of frames with the clock held, so nothing runs ahead between checks. */
    private fun frames(seconds: Float) = repeat((seconds * 60).toInt()) { compose.mainClock.advanceTimeBy(16) }

    @Test
    fun aFullLineCanBeReeledInFast() {
        val store = SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        vm.castFactory = ::fullLineWater

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        frames(15f)
        // The last bump's explanation pauses the cast the first time.
        if (isShowing("Fishing secret discovered!")) {
            compose.onNodeWithText("No thanks").performClick()
            frames(0.2f)
        }
        frames(1f)
        val session = vm.session!!
        assertEquals(CastPhase.ASCENDING, session.phase)
        assertTrue(session.canFastReel)
        compose.onNodeWithText("Line full · Reel in fast").assertExists()
        shot("59_fast_reel_offered")
        val depth = session.hookDepth
        assertTrue("deep enough for the normal reel to take a while ($depth m)", depth / GameConfig.ASCENT_SPEED > 5f)
        compose.onNodeWithText("Line full · Reel in fast").performClick()
        frames(GameConfig.FAST_REEL_MAX_SECONDS + 0.2f)
        assertEquals(CastPhase.SURFACED, session.phase)
        compose.onNodeWithText("Cast complete").assertExists()
        compose.mainClock.autoAdvance = true
    }
}
