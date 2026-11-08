package com.deepline.fishing

import android.app.LocaleManager
import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.ui.i18n.AppLanguage
import com.deepline.fishing.ui.i18n.AppLanguages
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

/**
 * Settings > Language: every language is offered in its own words, the choice goes to the system
 * (Android 13+: the same setting as the system's per-app language) and the screen comes back in
 * the chosen language. Robolectric doesn't recreate the activity itself after a language change
 * the way Android does, so the test does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class LanguageUiTest {
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
        context.getSystemService(LocaleManager::class.java).applicationLocales = android.os.LocaleList.getEmptyLocaleList()
    }

    private fun openLanguages() {
        compose.onNodeWithContentDescription(str(R.string.home_cd_settings)).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_system"))
    }

    private fun pick(tag: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_$tag"))
        compose.onNodeWithTag("language_$tag").performClick()
        compose.waitForIdle()
    }

    /** A string as the activity shows it now. */
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
    fun pickingALanguageSwitchesTheGameAndTheSystemSetting() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        openLanguages()
        // The phone's language by default, and every language named in itself.
        compose.onNodeWithTag("language_system").assertIsSelected()
        for (language in AppLanguage.supported) {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(language.endonym))
            compose.onNodeWithText(language.endonym).assertExists()
        }

        pick("fr")
        assertEquals(AppLanguage.FRENCH, AppLanguages.current(context))
        val system = context.getSystemService(LocaleManager::class.java).applicationLocales
        assertEquals("fr", system[0].toLanguageTag())
        // Android recreates the activity in the new language.
        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals("fr", locale().language)
        assertEquals("fr", java.util.Locale.getDefault().language)

        // Back to the phone's language (Settings is still open: the screen survives recreation).
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("language_fr"))
        compose.onNodeWithTag("language_fr").assertIsSelected()
        pick("system")
        assertEquals(AppLanguage.SYSTEM, AppLanguages.current(context))
        assertTrue(context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals("en", locale().language)
        compose.onNodeWithText(str(R.string.settings_title)).assertExists()
    }

    @Test
    fun thePseudoLocaleShowsEveryConvertedText() {
        // Debug builds offer English (XA): accented, longer text in brackets. Text that stays plain
        // English there was never put in a resource.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        openLanguages()
        pick("en-XA")
        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals("XA", locale().country)
        // The Settings screen is still open, now in English (XA).
        assertEquals(0, compose.onAllNodes(hasText("Settings")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("Sound and feel")).fetchSemanticsNodes().size)
        val title = shown(R.string.settings_title)
        assertTrue("pseudo-localized: $title", title != "Settings" && title.startsWith("["))
        compose.onNodeWithText(title).assertExists()
    }
}
