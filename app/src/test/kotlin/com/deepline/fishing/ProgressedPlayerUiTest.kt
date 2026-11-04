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
 * A player well into the game: home, Dex, upgrades, milestones and a deep cast with a Lantern.
 * In its own class (so its own JVM): saved progress from an earlier test can't leak into it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ProgressedPlayerUiTest {
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
    fun deepCastWithLanternAndFullerLog() {
        val caught = FishCatalog.all.take(9).associate { it.id to CollectionEntry(3, 1, it.minDepth + 10f, it.basePoints * 3) }
        launch(
            PlayerProgress(
                pointsBalance = 12_345,
                pointsEarned = 30_000,
                ropeLevels = mapOf(RopeStat.LENGTH to 10, RopeStat.STRENGTH to 8, RopeStat.AGILITY to 6),
                lanterns = 3,
                collection = caught,
                castsMade = 42,
            ),
        )
        compose.shot("04_home_progressed")

        compose.tab("dex")
        compose.onNodeWithText("Seen 9 · Caught 9 / ${FishCatalog.all.size}").assertExists()
        compose.shot("05_fish_log")

        compose.tab("upgrades")
        compose.shot("06_upgrades")

        compose.tab("fish")
        compose.click("Milestones")
        compose.shot("07_milestones")
        compose.click("‹ Back")

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        // Sink towards the dark zone (it may turn back early after 4 bumps).
        compose.advanceSeconds(16f)
        compose.shot("08_cast_deep")
        compose.mainClock.autoAdvance = true
    }
}
