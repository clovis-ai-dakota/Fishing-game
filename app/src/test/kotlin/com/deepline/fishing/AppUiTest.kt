package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.CollectionEntry
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeStat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Runs the real app on the JVM (Robolectric), taps through it, and saves screenshots to
 * app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class AppUiTest {
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

    private fun launch(progress: PlayerProgress? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPrefsStore(context)
        progress?.let { ProgressRepository(store).save(it) }
        // These tests are about the game itself: start with the tour already taken.
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", com.deepline.fishing.ui.tour.Tour.VERSION.toString())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun ComposeTestRule.shot(name: String) {
        if (mainClock.autoAdvance) waitForIdle() else mainClock.advanceTimeByFrame()
        // Draw the activity's views straight into a bitmap. This also works while the test
        // clock is paused (captureToImage waits for a new frame, which a paused clock never sends).
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Plays the current cast to its end (up to [maxSeconds] of game time). If the last bump hooks
     * something for the first time, the cast pauses to explain the secret: answer it and go on.
     */
    private fun ComposeTestRule.playOutCast(endText: String, maxSeconds: Int = 150) {
        var elapsed = 0
        while (elapsed < maxSeconds) {
            mainClock.autoAdvance = false
            repeat(2 * 60) { mainClock.advanceTimeBy(16) }
            elapsed += 2
            if (onAllNodes(hasText("Fishing secret discovered!")).fetchSemanticsNodes().isNotEmpty()) {
                // The cast is paused behind the dialog. Answer it, then let the dialog window
                // close for a few frames so it isn't mistaken for a new one.
                if (onAllNodes(hasText("No thanks")).fetchSemanticsNodes().isNotEmpty()) {
                    onNode(hasText("No thanks")).performClick()
                }
                repeat(10) { mainClock.advanceTimeByFrame() }
                continue
            }
            if (onAllNodes(hasText(endText)).fetchSemanticsNodes().isNotEmpty()) break
        }
        mainClock.autoAdvance = true
    }

    private fun ComposeTestRule.advanceSeconds(seconds: Float) {
        val steps = (seconds * 60).toInt()
        repeat(steps) { mainClock.advanceTimeBy(16) }
    }

    /** Taps the node with [text], scrolling a (lazy) list to it first when needed. */
    private fun ComposeTestRule.click(text: String, substring: Boolean = false) {
        val matcher = hasText(text, substring = substring)
        if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        }
        val node = onNode(matcher)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Taps a tab of the bottom navigation bar. */
    private fun ComposeTestRule.tab(name: String) = onNodeWithTag("tab_$name").performClick()

    @Test
    fun freshPlayerCastsAndCollects() {
        launch()
        compose.onNodeWithText("Deep Line").assertExists()
        compose.shot("01_home")

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        compose.advanceSeconds(1.5f)
        compose.shot("02_cast_start")
        compose.playOutCast("Cast complete")
        compose.onNodeWithText("Cast complete").assertExists()
        compose.onNodeWithText("Surfaced").assertExists()
        compose.shot("03_cast_results")

        compose.click("Collect")
        compose.onNodeWithText("Casts: 1", substring = true).assertExists()
    }

    @Test
    fun battleAgainstTheComputer() {
        launch()
        compose.tab("battle")
        compose.shot("09_battle_setup")
        compose.click("Start battle")
        compose.shot("10_battle_hub")

        compose.click("Cast, You!")
        compose.mainClock.autoAdvance = false
        compose.advanceSeconds(2f)
        compose.shot("11_battle_cast")
        compose.playOutCast("Continue")
        compose.click("Continue")

        compose.click("Let Rival", substring = true)
        compose.onNodeWithText("Round 1").assertExists()
        compose.shot("12_battle_after_round")
    }

    @Test
    fun onlineLobby() {
        launch()
        compose.tab("battle")
        compose.click("Online (room code or quick match)")
        compose.click("Go online")
        compose.onNodeWithText("Create room").assertExists()
        compose.shot("13_online_lobby")
    }
}
