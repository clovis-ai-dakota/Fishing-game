package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.ui.i18n.AppLanguage
import com.deepline.fishing.ui.i18n.AppLanguages
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

/**
 * Android 12 and lower have no per-app language in the system: the choice is a preference of the
 * game, applied when the activity is created, and picking a language recreates the activity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w400dp-h860dp-xxhdpi")
class LanguageLegacyUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun freshStart() {
        context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        SharedPrefsStore(context).apply {
            putString("tour.showAgain", "false")
            putString("tour.seenVersion", Tour.VERSION.toString())
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        java.util.Locale.setDefault(java.util.Locale.US)
    }

    /** A string as the activity shows it now (in the chosen language). */
    private fun shown(id: Int): String {
        lateinit var text: String
        scenario!!.onActivity { text = it.getString(id) }
        return text
    }

    private fun locale(): java.util.Locale {
        lateinit var locale: java.util.Locale
        scenario!!.onActivity { locale = it.resources.configuration.locales[0] }
        return locale
    }

    @Test
    fun theChoiceIsSavedAndAppliedByTheGame() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(str(R.string.home_cd_settings)).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_de"))
        compose.onNodeWithTag("language_de").performClick()
        compose.waitForIdle()
        // Saved as the game's preference, and the activity came back in German (recreated by the game).
        assertEquals(AppLanguage.GERMAN, AppLanguages.current(context))
        assertEquals("de", context.getSharedPreferences("deepline", Context.MODE_PRIVATE).getString(AppLanguages.KEY, null))
        assertEquals("de", locale().language)
        assertEquals("de", java.util.Locale.getDefault().language)

        // A new launch keeps it.
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        assertEquals("de", locale().language)

        // The phone's language again.
        compose.onNodeWithContentDescription(shown(R.string.home_cd_settings)).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_system"))
        compose.onNodeWithTag("language_system").performClick()
        compose.waitForIdle()
        assertEquals(AppLanguage.SYSTEM, AppLanguages.current(context))
        assertEquals("en", locale().language)
    }
}
