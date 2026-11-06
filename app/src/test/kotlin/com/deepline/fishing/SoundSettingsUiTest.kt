package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.audio.AudioEngines
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.feel.FeelSettings
import com.deepline.fishing.feel.Haptics
import com.deepline.fishing.feel.RecordingAudio
import com.deepline.fishing.feel.RecordingHaptics
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

/**
 * Settings > Sound and feel: the volumes, vibration and Reduce motion, saved on the phone. (One
 * test per class: Robolectric hands a class's later tests a view model made with the first test's
 * application, so its saved settings would live somewhere else.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SoundSettingsUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val store by lazy { SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>()) }
    private val audio = RecordingAudio()
    private val haptics = RecordingHaptics()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deepline", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", com.deepline.fishing.ui.tour.Tour.VERSION.toString())
        AudioEngines.factory = { audio }
        Haptics.factory = { haptics }
    }

    @After
    fun tearDown() {
        scenario?.close()
        AudioEngines.factory = null
        Haptics.factory = null
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
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

    private fun openSettings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Sound and feel").assertExists()
    }

    @Test
    fun soundSettingsAreSavedOnThePhone() {
        launch()
        // The menus play the menu music.
        assertEquals(mapOf(MusicTrack.MENU to 1f), audio.mix)
        openSettings()
        compose.shot("40_settings_sound")
        compose.onNodeWithTag("settings_haptics").performScrollTo().assertIsOn()
        compose.onNodeWithTag("settings_reduce_motion").performScrollTo().assertIsOff()

        compose.onNodeWithTag("settings_music_volume").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        compose.onNodeWithTag("settings_sfx_volume").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithTag("settings_haptics").performScrollTo().performClick()
        compose.onNodeWithTag("settings_reduce_motion").performScrollTo().performClick()
        // Values set directly (as accessibility services do) are saved after a short pause.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(0.3f, store.getString(FeelSettings.KEY_MUSIC)!!.toFloat(), 0.01f)
        assertEquals(0.5f, store.getString(FeelSettings.KEY_SFX)!!.toFloat(), 0.01f)
        assertEquals("false", store.getString(FeelSettings.KEY_HAPTICS))
        assertEquals("true", store.getString(FeelSettings.KEY_REDUCE_MOTION))
        // The engine got the new levels, and letting go of the effects slider plays a sample.
        assertEquals(0.3f, audio.volumes.second, 0.01f)
        assertEquals(0.5f, audio.volumes.third, 0.01f)
        assertTrue(Sfx.CATCH in audio.played)
        assertTrue(Sfx.UI_TOGGLE in audio.played)

        // Credits name every source.
        compose.onNodeWithTag("settings_credits").performScrollTo()
        compose.onNode(hasText("Music: Underwater Theme")).assertExists()
        compose.onNode(hasText("Sounds: Interface Sounds")).assertExists()
        compose.shot("41_settings_credits")

        // Next launch: the same settings.
        launch()
        openSettings()
        compose.onNodeWithTag("settings_haptics").performScrollTo().assertIsOff()
        compose.onNodeWithTag("settings_reduce_motion").performScrollTo().assertIsOn()
    }
}
