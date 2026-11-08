package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class TourUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val store by lazy { SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>()) }

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

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
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

    private fun ComposeTestRule.shot(name: String) {
        waitForIdle()
        lateinit var bitmap: Bitmap
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun isShowing(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun firstLaunchShowsTheFullTourThenAsksAboutNextTime() {
        launch()
        compose.onNodeWithText("Welcome to Deep Line!").assertExists()
        compose.shot("22_tour_welcome")

        val shots = mapOf(
            "tabs" to "22b_tour_tabs",
            "descent" to "23_tour_bumps",
            "upgrades" to "24_tour_upgrades",
            "dex" to "24b_tour_dex",
            "opponents" to "25_tour_opponents",
            "two_prices" to "24c_tour_two_prices",
            "passes" to "24d_tour_passes",
            "battle_tackle" to "25b_tour_battle_tackle",
        )
        Tour.steps.forEachIndexed { i, step ->
            assertEquals("step ${step.id} is showing", true, isShowing(step.title.text))
            compose.onNodeWithText("${i + 1} / ${Tour.steps.size}").assertExists()
            shots[step.id]?.let { compose.shot(it) }
            compose.click(if (i == Tour.steps.lastIndex) "Finish" else "Next")
        }

        compose.onNodeWithText("Show the tour next time?").assertExists()
        compose.shot("26_tour_ask_next_time")
        compose.click("No thanks")
        compose.onNodeWithText("Cast line").assertExists()
        assertEquals("false", store.getString("tour.showAgain"))

        // Next launch: no tour.
        launch()
        assertEquals(false, isShowing("Welcome to Deep Line!"))
    }

    @Test
    fun tourCanBeTakenFromTheMenuSkippedAndReopenedNextTime() {
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        launch()
        assertEquals(false, isShowing("Welcome to Deep Line!"))

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings_tour").performClick()
        compose.onNodeWithText("Welcome to Deep Line!").assertExists()
        compose.click("Next")
        compose.click("Skip tour")
        compose.onNodeWithText("Show the tour next time?").assertExists()
        compose.click("Yes, next time")

        launch()
        compose.onNodeWithText("Welcome to Deep Line!").assertExists()
    }
}
