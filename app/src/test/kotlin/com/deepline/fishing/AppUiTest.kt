package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
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

    @Test
    fun freshPlayerCastsAndCollects() {
        launch()
        compose.onNodeWithText("Deep Line").assertExists()
        compose.shot("01_home")

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        compose.advanceSeconds(1.5f)
        compose.shot("02_cast_start")
        compose.advanceSeconds(60f) // long enough for any cast with the starting rope to finish
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Cast complete").assertExists()
        compose.onNodeWithText("Surfaced").assertExists()
        compose.shot("03_cast_results")

        compose.click("Collect")
        compose.onNodeWithText("Casts: 1", substring = true).assertExists()
    }

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

        compose.click("Log ", substring = true)
        compose.shot("05_fish_log")
        compose.click("‹ Back")

        compose.click("Upgrades")
        compose.shot("06_upgrades")
        compose.click("‹ Back")

        compose.click("Rewards")
        compose.shot("07_milestones")
        compose.click("‹ Back")

        compose.click("Cast line")
        compose.mainClock.autoAdvance = false
        // Sink towards the dark zone (it may turn back early after 4 bumps).
        compose.advanceSeconds(16f)
        compose.shot("08_cast_deep")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun battleAgainstTheComputer() {
        launch()
        compose.click("1v1 Battle")
        compose.shot("09_battle_setup")
        compose.click("Start battle")
        compose.shot("10_battle_hub")

        compose.click("Cast, You!")
        compose.mainClock.autoAdvance = false
        compose.advanceSeconds(2f)
        compose.shot("11_battle_cast")
        compose.advanceSeconds(60f)
        compose.mainClock.autoAdvance = true
        compose.click("Continue")

        compose.click("Let Rival", substring = true)
        compose.onNodeWithText("Round 1").assertExists()
        compose.shot("12_battle_after_round")
    }

    @Test
    fun onlineLobby() {
        launch()
        compose.click("1v1 Battle")
        compose.click("Online (room code or quick match)")
        compose.click("Go online")
        compose.onNodeWithText("Create room").assertExists()
        compose.shot("13_online_lobby")
    }
}
