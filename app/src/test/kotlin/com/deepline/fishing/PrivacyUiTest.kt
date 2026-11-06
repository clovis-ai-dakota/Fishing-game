package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.analytics.AnalyticsRules
import com.deepline.fishing.analytics.GameAnalytics
import com.deepline.fishing.analytics.RecordingTelemetry
import com.deepline.fishing.analytics.Telemetry
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.privacy.ConsentStore
import com.deepline.fishing.privacy.PrivacyChoices
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared setup for the privacy UI tests: a recording collector stands in for Firebase, as if
 * this build had a google-services.json.
 *
 * Each class has a single test, which resets the saved settings itself between scenarios: from
 * the second test of a class on, Robolectric builds the view model with the first test's
 * Application (lifecycle's AndroidViewModelFactory is cached statically), whose preferences the
 * per-test clean-up doesn't reach.
 */
abstract class PrivacyUiTestBase : BoatUiTestBase() {
    protected var recorder = RecordingTelemetry()
    protected val store by lazy { SharedPrefsStore(ApplicationProvider.getApplicationContext<Context>()) }

    @After
    fun uninstallRecorder() = Telemetry.uninstall()

    /** Clears every saved setting and installs a fresh recorder (or none: a build without Firebase). */
    protected fun reset(withFirebase: Boolean = true) {
        scenario?.close()
        scenario = null
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("deepline", Context.MODE_PRIVATE)
            .edit().clear().commit()
        Telemetry.uninstall()
        recorder = RecordingTelemetry()
        if (withFirebase) Telemetry.install(recorder)
    }

    /** A first launch: nothing answered, tour not taken. */
    protected fun firstLaunch(progress: PlayerProgress? = null): GameViewModel {
        progress?.let { ProgressRepository(store).save(it) }
        return start()
    }

    /** A returning player who took the tour and answered [choices]. */
    protected fun returningLaunch(choices: PrivacyChoices): GameViewModel {
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        ConsentStore(store).save(choices)
        return start()
    }

    protected fun start(): GameViewModel {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        return vm
    }

    protected fun sheetShowing() = compose.onAllNodes(hasTestTag("consent_sheet")).fetchSemanticsNodes().isNotEmpty()

    protected fun skipTour() {
        compose.click("Skip tour")
        compose.click("No thanks")
        compose.waitForIdle()
    }

    protected fun openPrivacySettings(vm: GameViewModel) {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        assertEquals(Screen.SETTINGS, vm.screen)
        compose.onNodeWithTag("privacy_analytics").performScrollTo()
    }
}

/** First launch: the consent sheet comes before the tour; refusing, or choosing, is one tap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ConsentSheetUiTest : PrivacyUiTestBase() {
    @Test
    fun theSheetComesFirstAndNothingIsSentWithoutConsent() {
        // Refusing.
        reset()
        var vm = firstLaunch()
        assertTrue("the consent sheet is asked first", sheetShowing())
        assertFalse("the tour waits for the answer", isShowing("Welcome to Deep Line!"))
        compose.shot("90_consent_sheet")
        assertTrue("nothing collected before the answer", recorder.isEmpty)
        assertTrue("the SDK was only ever told no", recorder.consents.none { it.any })
        compose.onNodeWithTag("consent_refuse").performClick()
        compose.waitForIdle()
        assertFalse(sheetShowing())
        compose.onNodeWithText("Welcome to Deep Line!").assertExists()
        assertEquals("false", store.getString(ConsentStore.KEY_ANALYTICS))
        assertEquals("false", store.getString(ConsentStore.KEY_CRASH_REPORTS))
        skipTour()
        vm.startCast()
        vm.abandonCast()
        GameAnalytics.problem("privacy_test", "refused")
        compose.waitForIdle()
        assertTrue("nothing is sent after refusing", recorder.isEmpty)
        start()
        assertFalse("asked only once", sheetShowing())

        // Choosing each separately: both start off, crash reports only.
        reset()
        firstLaunch()
        compose.onNodeWithTag("consent_choose").performClick()
        compose.onNodeWithTag("consent_analytics").assertIsOff()
        compose.onNodeWithTag("consent_crash_reports").assertIsOff()
        compose.onNodeWithTag("consent_crash_reports").performClick()
        compose.shot("91_consent_choose")
        compose.onNodeWithTag("consent_save").performClick()
        compose.waitForIdle()
        assertEquals(PrivacyChoices(analytics = false, crashReports = true), recorder.consents.last())
        GameAnalytics.problem("privacy_test", "crash reports only")
        skipTour()
        assertTrue("no statistics", recorder.events.isEmpty() && recorder.screens.isEmpty() && recorder.userProperties.isEmpty())
        assertEquals(1, recorder.errors.size)

        // A build without Firebase has nothing to ask about.
        reset(withFirebase = false)
        vm = firstLaunch()
        assertFalse(sheetShowing())
        compose.onNodeWithText("Welcome to Deep Line!").assertExists()
        skipTour()
        openPrivacySettings(vm)
        assertTrue(isShowing("This version of the game doesn't send", substring = true))
    }
}

/** Allowing: the game's events reach the collector, within Firebase's limits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ConsentAllowUiTest : PrivacyUiTestBase() {
    @Test
    fun allowingSendsGameEventsWithinFirebaseLimits() {
        reset()
        val vm = firstLaunch(PlayerProgress(pointsBalance = 50_000, pointsEarned = 50_000))
        compose.onNodeWithTag("consent_allow").performClick()
        compose.waitForIdle()
        assertEquals(PrivacyChoices.ALL, recorder.consents.last())
        assertEquals("the tour starts once answered", "full", recorder.last(GameAnalytics.TUTORIAL_BEGIN)?.params?.get("tour"))
        skipTour()
        assertEquals("welcome", recorder.last(GameAnalytics.TUTORIAL_SKIP)?.params?.get("step_id"))
        assertTrue("the Fish tab is a screen view", "fish" in recorder.screens)
        assertEquals("user properties arrive with the consent", "lagoon", recorder.userProperties[GameAnalytics.PROP_THEME])

        vm.startCast()
        assertEquals("solo", recorder.last(GameAnalytics.LEVEL_START)?.params?.get("level_name"))
        vm.abandonCast()
        assertEquals("quit", recorder.last(GameAnalytics.LEVEL_END)?.params?.get("end"))
        vm.buyUpgrade(RopeStat.LENGTH)
        assertEquals(1, recorder.last(GameAnalytics.ROPE_UPGRADE)?.params?.get("level"))
        val spend = recorder.last(GameAnalytics.SPEND)!!.params
        assertEquals("rope", spend["source"])
        assertEquals(50_000L - vm.progress.pointsBalance, spend["value"])
        vm.startBattle(BattleMode.HEAVY_HAUL, Opponent.Friend)
        assertEquals("friend", recorder.last(GameAnalytics.BATTLE_START)?.params?.get("opponent"))
        vm.forfeitBattle(0)
        assertEquals("player_2", recorder.last(GameAnalytics.BATTLE_END)?.params?.get("result"))
        GameAnalytics.problem("privacy_test", "allowed")
        assertEquals(1, recorder.errors.count { it.second == "privacy_test" })

        assertEquals("every event fits Firebase's limits", emptyList<String>(), recorder.problems())
        for (name in recorder.names()) assertTrue("$name isn't a known event", name in GameAnalytics.EVENTS)
        assertTrue(recorder.userProperties.size <= AnalyticsRules.MAX_USER_PROPERTIES)
    }
}

/** Settings > Privacy: the switches change the answer any time; the online account can be deleted. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class PrivacySettingsUiTest : PrivacyUiTestBase() {
    @Test
    fun theSwitchesAndAccountDeletion() {
        reset()
        val vm = returningLaunch(PrivacyChoices.NONE)
        assertFalse(sheetShowing())
        openPrivacySettings(vm)
        compose.onNodeWithTag("privacy_analytics").assertIsOff()
        compose.onNodeWithTag("privacy_crash_reports").assertIsOff()
        compose.shot("92_privacy_settings")

        // Turning usage statistics on, then off again.
        compose.onNodeWithTag("privacy_analytics").performClick()
        compose.onNodeWithTag("privacy_analytics").assertIsOn()
        assertTrue(recorder.consents.last().analytics)
        assertEquals("true", store.getString(ConsentStore.KEY_ANALYTICS))
        vm.onBack()
        compose.waitForIdle()
        assertEquals(listOf("fish"), recorder.screens)
        openPrivacySettings(vm)
        compose.onNodeWithTag("privacy_reset_analytics").performScrollTo().performClick()
        assertEquals(1, recorder.resets)
        compose.onNodeWithTag("privacy_analytics").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(recorder.consents.last().analytics)
        assertEquals("withdrawing clears the analytics data", 2, recorder.resets)
        val screens = recorder.screens.size
        vm.onBack()
        vm.startCast()
        vm.abandonCast()
        compose.waitForIdle()
        assertEquals("nothing after withdrawal", screens, recorder.screens.size)
        assertTrue(recorder.names().none { it == GameAnalytics.LEVEL_START })
        assertEquals("false", store.getString(ConsentStore.KEY_ANALYTICS))

        // Deleting the online account: first the server can't be reached, then it confirms.
        val before = vm.playerIdentity
        val deleted = ArrayList<PlayerId>()
        var failNext = true
        vm.accounts = object : AccountClient({ "" }) {
            override suspend fun deleteAccount(who: PlayerId) {
                if (failNext) {
                    failNext = false
                    throw AccountException("Can't reach the server. Check your connection and try again.")
                }
                deleted += who
            }
        }
        openPrivacySettings(vm)
        compose.onNodeWithTag("privacy_delete_account").performScrollTo().performClick()
        compose.onNodeWithText("Delete your online account?").assertExists()
        compose.shot("93_privacy_delete_confirm")
        compose.onNodeWithTag("privacy_delete_confirm").performClick()
        compose.waitUntil(10_000) { vm.privacy.message != null }
        assertTrue(vm.privacy.message!!.contains("wasn't deleted"))
        assertEquals(before, vm.playerIdentity)

        compose.onNodeWithTag("privacy_delete_account").performScrollTo().performClick()
        compose.onNodeWithTag("privacy_delete_confirm").performClick()
        compose.waitUntil(10_000) { vm.privacy.message?.contains("was deleted") == true }
        assertEquals("the server was asked with the old id and secret", listOf(before), deleted)
        assertNotEquals("the game moves on to a new random identity", before, vm.playerIdentity)
        assertEquals(vm.playerIdentity.id, store.getString("account.id"))
        assertTrue(PlayerId.isValid(vm.playerIdentity.id))
        compose.onNodeWithTag("privacy_message").performScrollTo()
        compose.shot("94_privacy_account_deleted")
    }
}
