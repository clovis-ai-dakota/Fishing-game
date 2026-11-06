package com.deepline.fishing

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.Migrations
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.ReminderSettings
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.daily.ReminderNotifications
import com.deepline.fishing.daily.ReminderPrefs
import com.deepline.fishing.daily.ReminderScheduler
import com.deepline.fishing.daily.ReminderWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * The reminder check that WorkManager runs every few hours: it posts at most one reminder a day,
 * never in quiet hours or without the permission, and tapping it opens the right screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderWorkerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store by lazy { SharedPrefsStore(context) }
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    /** A day without migrations or weekend events, so only the daily reminders are in play. */
    private val day: Long = (20_400L..20_430L).first { d ->
        (0 until 24).none { h ->
            val t = d * LocalDays.DAY_MS + h * LocalDays.HOUR_MS
            Migrations.at(t) != null || Seasons.eventAt(t) != null
        }
    }

    private fun at(hour: Int, d: Long = day) = d * LocalDays.DAY_MS + hour * LocalDays.HOUR_MS

    @Before
    fun setUp() {
        context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        LocalDays.zoneOverride = TimeZone.getTimeZone("UTC")
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // A 4-day streak claimed yesterday, not yet today; reminders on.
        ProgressRepository(store).save(PlayerProgress(dailyStreak = 4, dailyBestStreak = 4, dailyLastClaimDay = day - 1, dailyClaims = 4, streakShields = 0))
        ReminderPrefs.save(store, ReminderSettings(enabled = true))
    }

    @After
    fun tearDown() {
        LocalDays.zoneOverride = null
        ShopClock.override = null
    }

    private fun runCheck(time: Long): ListenableWorker.Result {
        ShopClock.override = time
        return TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork()
    }

    @Test
    fun aStreakReminderInTheEveningThenNothingMoreThatDay() {
        assertEquals(ListenableWorker.Result.success(), runCheck(at(13)))
        // Noon: the streak is worth an evening reminder instead.
        assertTrue(notifications.allNotifications.isEmpty())
        runCheck(at(19))
        val posted = notifications.allNotifications.single()
        assertEquals("streak", posted.channelId)
        assertTrue(shadowOf(posted).contentTitle.toString().contains("4-day streak"))
        val open = shadowOf(posted.contentIntent).savedIntent
        assertEquals("daily", open.getStringExtra(ReminderNotifications.EXTRA_OPEN))
        assertEquals(day, ReminderPrefs.load(store).lastSentDay)
        // At most one a day.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        runCheck(at(20))
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun nothingInQuietHoursWhenOffOrWithoutThePermission() {
        runCheck(at(22))
        runCheck(at(4, day + 1))
        assertTrue(notifications.allNotifications.isEmpty())
        ReminderPrefs.save(store, ReminderSettings(enabled = false))
        runCheck(at(19))
        assertTrue(notifications.allNotifications.isEmpty())
        ReminderPrefs.save(store, ReminderSettings(enabled = true))
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runCheck(at(19))
        assertTrue(notifications.allNotifications.isEmpty())
        // Nothing was marked as sent, so tomorrow can still bring one.
        assertEquals(null, ReminderPrefs.load(store).lastSentDay)
    }

    @Test
    fun aMigrationIsAnnouncedOnceAndOpensTheFishTab() {
        val m = Migrations.of(Math.floorDiv(at(12) - Seasons.EPOCH_MS, 7 * LocalDays.DAY_MS) + 1)
        val start = LocalDays.dayOf(m.startMs, 0)
        // Claimed today, so no daily reminder competes.
        ProgressRepository(store).save(PlayerProgress(dailyStreak = 1, dailyLastClaimDay = start, dailyClaims = 1))
        runCheck(m.startMs + 10 * LocalDays.HOUR_MS)
        val posted = notifications.allNotifications.single()
        assertEquals("events", posted.channelId)
        assertEquals("migration", shadowOf(posted.contentIntent).savedIntent.getStringExtra(ReminderNotifications.EXTRA_OPEN))
        assertEquals(m.key, ReminderPrefs.load(store).lastEventKey)
    }

    @Test
    fun workManagerRunsTheCheckPeriodically() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        ShopClock.override = at(19)
        ReminderScheduler.schedule(context)
        // Scheduling again keeps the one check.
        ReminderScheduler.schedule(context)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setPeriodDelayMet(work.id)
        assertEquals("streak", notifications.allNotifications.single().channelId)
        ReminderScheduler.cancel(context)
        assertEquals(
            WorkInfo.State.CANCELLED,
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get().single().state,
        )
    }
}
