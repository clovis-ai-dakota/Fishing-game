package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.ui.i18n.AppLanguage
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun freshStart() {
    val context: Context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
    SharedPrefsStore(context).apply {
        putString("tour.showAgain", "false")
        putString("tour.seenVersion", Tour.VERSION.toString())
    }
}

/**
 * A phone in German (a machine translation): the Fish tab says once that the translation was
 * generated automatically, and Settings > Language keeps saying it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "de-w400dp-h860dp-xxhdpi")
class MachineTranslationUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = freshStart()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun theNoticeShowsOnceAndSettingsKeepsTheNote() {
        // Exactly the languages without a native review are flagged (French is reviewed by the developer).
        assertEquals(
            setOf(AppLanguage.SPANISH, AppLanguage.GERMAN, AppLanguage.PORTUGUESE_BR, AppLanguage.TURKISH),
            AppLanguage.entries.filter { it.machineTranslated }.toSet(),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithTag("machine_translation_notice").assertExists()
        scenario!!.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val dir = java.io.File("build/screenshots").apply { mkdirs() }
            java.io.File(dir, "machine_translation_notice_de.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("machine_translation_ok").performClick()
        compose.waitForIdle()
        assertEquals(0, count("machine_translation_notice"))
        // Dismissed for good: not back after the screen is rebuilt.
        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals(0, count("machine_translation_notice"))
        // Settings > Language still says it, in German.
        lateinit var settings: String
        scenario!!.onActivity { settings = it.getString(R.string.home_cd_settings) }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(settings)).onFirst().performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_machine_note"))
        compose.onNodeWithTag("language_machine_note").assertExists()
    }
}

/** A phone in French (reviewed): no notice and no note. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fr-w400dp-h860dp-xxhdpi")
class ReviewedTranslationUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = freshStart()

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun frenchShowsNoMachineTranslationNotice() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("machine_translation_notice")).fetchSemanticsNodes().size)
        lateinit var settings: String
        scenario!!.onActivity { settings = it.getString(R.string.home_cd_settings) }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(settings)).onFirst().performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("settings_language"))
        assertEquals(0, compose.onAllNodes(hasTestTag("language_machine_note")).fetchSemanticsNodes().size)
    }
}
