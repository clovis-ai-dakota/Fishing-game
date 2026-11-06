package com.deepline.fishing

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.deepline.fishing.core.ChallengeDifficulty
import com.deepline.fishing.core.ChallengeKind
import com.deepline.fishing.core.DailyChallenge
import com.deepline.fishing.core.DailyRewards
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.Migrations
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.daily.ReminderNotifications
import com.deepline.fishing.daily.ReminderPrefs
import com.deepline.fishing.daily.ReminderScheduler
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** Shared setup: a fixed clock (during a migration) in UTC, and WorkManager in test mode. */
abstract class DailyUiTestBase : BoatUiTestBase() {
    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Wednesday 10:00 UTC, in the middle of a migration. */
    protected val now = Migrations.of(46).startMs + 34 * LocalDays.HOUR_MS
    protected val today = LocalDays.dayOf(now, 0)

    @Before
    fun fixTheClock() {
        LocalDays.zoneOverride = TimeZone.getTimeZone("UTC")
        ShopClock.override = now
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @After
    fun releaseTheClock() {
        LocalDays.zoneOverride = null
        ShopClock.override = null
    }

    protected fun grantNotifications() = shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    protected val store get() = SharedPrefsStore(context)

    protected fun reminderWork(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get()

    /** A mid-game angler who has seen every species, with today's challenges: the easy one done. */
    protected fun player() = PlayerProgress(
        pointsBalance = 50_000,
        pointsEarned = 50_000,
        ropeLevels = mapOf(RopeStat.LENGTH to 4, RopeStat.STRENGTH to 3),
        seenSpecies = FishCatalog.all.map { it.id }.toSet(),
        challengeDay = today,
        dailyChallenges = listOf(
            DailyChallenge(ChallengeDifficulty.EASY, ChallengeKind.CASTS, 3, progress = 3),
            DailyChallenge(ChallengeDifficulty.MEDIUM, ChallengeKind.CATCH_FISH, 18, progress = 7),
            DailyChallenge(ChallengeDifficulty.HARD, ChallengeKind.SPARKLING, 2),
        ),
    )
}

/** The Fish tab's daily card and the Daily screen: claiming, challenges, a swap, the explainer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class DailyUiTest : DailyUiTestBase() {
    @Test
    fun claimTheDailyRewardThenAChallengeAndSwapAnother() {
        val vm = launch(player())
        compose.onNodeWithTag("daily_card").assertExists()
        // A migration is under way: its banner is on the Fish tab.
        compose.onNodeWithTag("migration_banner").assertExists()
        compose.shot("90_daily_card")

        val before = vm.progress
        compose.onNodeWithTag("daily_claim").performScrollTo().performClick()
        assertEquals(1, vm.progress.dailyStreak)
        assertEquals(before.pointsBalance + DailyRewards.pointsFor(before, 0.2f), vm.progress.pointsBalance)
        // Claimed for today.
        assertFalse(isShowing("Claim day", substring = true))
        // The first claim is the moment to offer reminders, with an explainer first.
        compose.onNodeWithText("Want a reminder?").assertExists()
        compose.shot("91_daily_reminder_explainer")
        compose.onNodeWithTag("optin_no").performClick()
        assertFalse(vm.daily.reminders.enabled)
        assertTrue(ReminderPrefs.asked(store))
        assertTrue(reminderWork().isEmpty())

        // The Daily screen: the calendar, challenges and the migration.
        compose.onNodeWithText("Daily").performClick()
        assertEquals(Screen.DAILY, vm.screen)
        compose.shot("92_daily_screen")
        val detectors = vm.progress.detectors
        compose.onNodeWithTag("challenge_claim_easy").performScrollTo().performClick()
        assertEquals(detectors + 1, vm.progress.detectors)
        assertTrue(vm.progress.dailyChallenges.first().claimed)
        // Today's free swap, for the hard one.
        val hard = vm.progress.dailyChallenges.last()
        compose.onNodeWithTag("challenge_reroll_hard").performScrollTo().performClick()
        val swapped = vm.progress.dailyChallenges.last()
        assertNotEquals(hard.kind, swapped.kind)
        assertEquals(ChallengeDifficulty.HARD, swapped.difficulty)
        assertEquals(0, vm.daily.rerollsLeft)
        assertFalse(isShowing("Swap"))
        // Readable at night too.
        vm.setNightMode(com.deepline.fishing.core.NightMode.NIGHT)
        compose.waitForIdle()
        compose.shot("93_daily_screen_night")
        vm.setMenuTheme(com.deepline.fishing.core.MenuTheme.LAGOON)
        vm.setNightMode(com.deepline.fishing.core.NightMode.DAY)
        vm.onBack()
        assertEquals(Screen.HOME, vm.screen)
        // Everything is saved.
        val saved = ProgressRepository(store).load()
        assertEquals(1, saved.dailyStreak)
        assertEquals(swapped, saved.dailyChallenges.last())
    }
}

/** Saying yes to the explainer turns reminders on and schedules the check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ReminderOptInUiTest : DailyUiTestBase() {
    @Test
    fun turningRemindersOnFromTheExplainer() {
        grantNotifications()
        val vm = launch(player())
        compose.onNodeWithTag("daily_claim").performScrollTo().performClick()
        compose.onNodeWithTag("optin_yes").performClick()
        assertTrue(vm.daily.reminders.enabled)
        assertEquals(WorkInfo.State.ENQUEUED, reminderWork().single().state)
        // One channel per kind of reminder.
        val channels = context.getSystemService(NotificationManager::class.java).notificationChannels.map { it.id }.toSet()
        assertEquals(setOf("daily_reward", "streak", "events"), channels)
        // Not asked again.
        compose.onNodeWithText("Want a reminder?").assertDoesNotExist()
    }

    @Test
    fun aRefusedPermissionKeepsRemindersOff() {
        val vm = launch(player())
        // The system dialog said no (the launcher's answer).
        vm.daily.onPermissionResult(false, "explainer")
        assertFalse(vm.daily.reminders.enabled)
        assertTrue(vm.daily.message!!.contains("blocked"))
        assertTrue(reminderWork().isEmpty())
    }
}

/** Settings > Notifications: the switch for all reminders and one per kind. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class NotificationSettingsUiTest : DailyUiTestBase() {
    @Test
    fun remindersAreTurnedOnAndTunedInSettings() {
        grantNotifications()
        val vm = launch(player())
        compose.onNodeWithContentDescription("Settings").performClick()
        assertEquals(Screen.SETTINGS, vm.screen)
        compose.onNodeWithTag("notif_master").performScrollTo().performClick()
        assertTrue(vm.daily.reminders.enabled)
        assertEquals(WorkInfo.State.ENQUEUED, reminderWork().single().state)
        compose.onNodeWithTag("notif_daily").performScrollTo().assertIsOn().performClick()
        assertFalse(vm.daily.reminders.dailyReady)
        assertFalse(ReminderPrefs.load(store).dailyReady)
        compose.onNodeWithTag("notif_streak").assertIsOn()
        compose.onNodeWithTag("notif_events").assertIsOn()
        compose.shot("94_settings_notifications")
        vm.setNightMode(com.deepline.fishing.core.NightMode.NIGHT)
        compose.waitForIdle()
        compose.shot("94b_settings_notifications_night")
        compose.onNodeWithTag("notif_master").performScrollTo().performClick()
        assertFalse(vm.daily.reminders.enabled)
        assertEquals(WorkInfo.State.CANCELLED, reminderWork().single().state)
        assertFalse(isShowing("Daily reward ready"))
    }
}

/** Tapping a reminder opens the screen it talks about. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class ReminderDeepLinkUiTest : DailyUiTestBase() {
    private fun openWith(key: String): GameViewModel {
        ProgressRepository(store).save(player())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        val intent = Intent(context, MainActivity::class.java).putExtra(ReminderNotifications.EXTRA_OPEN, key)
        scenario = ActivityScenario.launch(intent)
        lateinit var vm: GameViewModel
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        compose.waitForIdle()
        return vm
    }

    @Test
    fun aDailyReminderOpensTheDailyScreen() {
        val vm = openWith("daily")
        assertEquals(Screen.DAILY, vm.screen)
        compose.onNodeWithText("Today's challenges").assertExists()
    }

    @Test
    fun anEventReminderOpensThePassesPage() {
        val vm = openWith("event")
        assertEquals(Screen.SHOP, vm.screen)
        assertEquals(ShopPage.PASSES, vm.shopPage)
        // And a reminder delivered while the app is open does the same.
        scenario!!.onActivity { it.onNewIntentForTest(Intent(it, MainActivity::class.java).putExtra(ReminderNotifications.EXTRA_OPEN, "daily")) }
        compose.waitForIdle()
        assertEquals(Screen.DAILY, vm.screen)
        compose.onNode(hasTestTag("daily_claim")).assertExists()
    }
}

private fun MainActivity.onNewIntentForTest(intent: Intent) {
    val m = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
    m.isAccessible = true
    m.invoke(this, intent)
}
