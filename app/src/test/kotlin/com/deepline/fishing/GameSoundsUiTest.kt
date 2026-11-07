package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.audio.AudioEngines
import com.deepline.fishing.audio.MusicTrack
import com.deepline.fishing.audio.Sfx
import com.deepline.fishing.feel.Haptics
import com.deepline.fishing.feel.RecordingAudio
import com.deepline.fishing.feel.RecordingHaptics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sounds of play in the real app: tab taps, a cast's splash, bubbles and depth music. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class GameSoundsUiTest {
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

    @Test
    fun tabsTapAndCastsSplash() {
        launch()
        compose.onNodeWithTag("tab_dex").performClick()
        compose.waitForIdle()
        assertTrue(Sfx.UI_TAP in audio.played)
        compose.onNodeWithTag("tab_fish").performClick()
        compose.onNodeWithText("Cast line").performClick()
        compose.mainClock.autoAdvance = false
        repeat(4 * 60) { compose.mainClock.advanceTimeBy(16) }
        val played = audio.played
        assertTrue(Sfx.CAST_SPLASH in played)
        assertTrue(Sfx.BUBBLE in played)
        assertTrue("the cast's music follows the depth", MusicTrack.SHALLOWS in audio.mix.keys)
        compose.mainClock.autoAdvance = true
    }
}
