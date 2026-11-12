package com.deepline.fishing.daily

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deepline.fishing.MainActivity
import com.deepline.fishing.R
import com.deepline.fishing.SharedPrefsStore
import com.deepline.fishing.analytics.Analytics
import com.deepline.fishing.core.Daily
import com.deepline.fishing.core.DailyReminders
import com.deepline.fishing.core.DexState
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.Reminder
import com.deepline.fishing.core.ReminderKind
import com.deepline.fishing.core.ReminderSettings
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.ui.i18n.AppLanguages
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import java.util.concurrent.TimeUnit

/**
 * The player's reminder choices, kept on this device only (in the app's key-value store, not in
 * the save, so a cloud restore never turns notifications on somewhere else).
 */
object ReminderPrefs {
    private const val ENABLED = "notif.enabled"
    private const val ASKED = "notif.asked"
    private const val DAILY = "notif.daily"
    private const val STREAK = "notif.streak"
    private const val EVENTS = "notif.events"
    private const val LAST_SENT = "notif.lastSentDay"
    private const val LAST_EVENT = "notif.lastEventKey"
    private const val LAST_OPEN = "notif.lastOpenDay"

    private fun KeyValueStore.flag(key: String, default: Boolean) = getString(key)?.toBooleanStrictOrNull() ?: default

    fun load(store: KeyValueStore) = ReminderSettings(
        enabled = store.flag(ENABLED, false),
        dailyReady = store.flag(DAILY, true),
        streakAtRisk = store.flag(STREAK, true),
        events = store.flag(EVENTS, true),
        lastSentDay = store.getString(LAST_SENT)?.toLongOrNull(),
        lastEventKey = store.getString(LAST_EVENT).orEmpty(),
        lastOpenDay = store.getString(LAST_OPEN)?.toLongOrNull(),
    )

    fun save(store: KeyValueStore, s: ReminderSettings) {
        store.putString(ENABLED, s.enabled.toString())
        store.putString(DAILY, s.dailyReady.toString())
        store.putString(STREAK, s.streakAtRisk.toString())
        store.putString(EVENTS, s.events.toString())
        store.putString(LAST_SENT, s.lastSentDay?.toString().orEmpty())
        store.putString(LAST_EVENT, s.lastEventKey)
        store.putString(LAST_OPEN, s.lastOpenDay?.toString().orEmpty())
    }

    /** Whether the player was already asked about reminders (asked once, never nagged). */
    fun asked(store: KeyValueStore): Boolean = store.flag(ASKED, false)

    fun markAsked(store: KeyValueStore) = store.putString(ASKED, "true")
}

/** One notification channel per kind of reminder, so players can mute each in system settings. */
object ReminderChannels {
    const val DAILY = "daily_reward"
    const val STREAK = "streak"
    const val EVENTS = "events"

    fun of(kind: ReminderKind): String = when (kind) {
        ReminderKind.DAILY_READY -> DAILY
        ReminderKind.STREAK_AT_RISK -> STREAK
        ReminderKind.EVENT_STARTING -> EVENTS
    }

    /**
     * Creating an existing channel again only updates its name and description (in the game's
     * language, which may have changed since), so this is safe to call any time.
     */
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val res = AppLanguages.localized(context).resources
        fun channel(id: String, name: Int, description: Int, importance: Int) =
            NotificationChannel(id, res.getString(name), importance).apply { this.description = res.getString(description) }
        manager.createNotificationChannels(
            listOf(
                channel(DAILY, R.string.daily_channel_daily_name, R.string.daily_channel_daily_description, NotificationManager.IMPORTANCE_DEFAULT),
                channel(STREAK, R.string.daily_channel_streak_name, R.string.daily_channel_streak_description, NotificationManager.IMPORTANCE_DEFAULT),
                channel(EVENTS, R.string.daily_channel_events_name, R.string.daily_channel_events_description, NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}

/** Posting reminders, and where tapping one leads. */
object ReminderNotifications {
    /** The intent extra naming the screen a notification opens (see [ReminderTarget]). */
    const val EXTRA_OPEN = "com.deepline.fishing.OPEN"

    /** One id for every reminder: a new one replaces the last, so there's never a pile. */
    const val NOTIFICATION_ID = 7_001

    /** Whether Android lets the app post (the player can turn notifications off any time). */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun targetOf(r: Reminder): ReminderTarget = when {
        r.kind != ReminderKind.EVENT_STARTING -> ReminderTarget.DAILY
        r.migration != null -> ReminderTarget.MIGRATION
        else -> ReminderTarget.EVENT
    }

    /** Posts [r]; false when Android doesn't allow it. */
    fun post(context: Context, r: Reminder, p: PlayerProgress): Boolean {
        if (!canPost(context)) return false
        ReminderChannels.create(context)
        val target = targetOf(r)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, target.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, target.ordinal, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Built outside the activity: only this context has the game's language on Android 12 and lower.
        val res = AppLanguages.localized(context).resources
        val text = ReminderTexts.text(r, p).resolve(res)
        val notification = NotificationCompat.Builder(context, ReminderChannels.of(r.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderTexts.title(r).resolve(res))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (r.kind == ReminderKind.EVENT_STARTING) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

/** Where a reminder leads when tapped. */
enum class ReminderTarget(val key: String) {
    /** The Daily screen (daily reward and streak reminders). */
    DAILY("daily"),

    /** The Fish tab, with the migration banner. */
    MIGRATION("migration"),

    /** The Shop's Passes page, where the weekend event is. */
    EVENT("event");

    companion object {
        fun of(key: String?): ReminderTarget? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What reminders say (strings_daily.xml), resolved by [ReminderNotifications.post] in the game's
 * language. They change from day to day ([Reminder.variant]), so they stay fresh.
 */
object ReminderTexts {
    private val readyTitles = listOf(R.string.daily_reminder_ready_title_1, R.string.daily_reminder_ready_title_2, R.string.daily_reminder_ready_title_3)

    fun title(r: Reminder): UiText = when (r.kind) {
        ReminderKind.DAILY_READY -> UiText.res(readyTitles[r.variant % readyTitles.size])
        ReminderKind.STREAK_AT_RISK -> UiText.plural(R.plurals.daily_reminder_streak_title, r.streak)
        ReminderKind.EVENT_STARTING -> UiText.res(if (r.migration != null) R.string.daily_reminder_migration_title else R.string.daily_reminder_event_title)
    }

    fun text(r: Reminder, p: PlayerProgress): UiText = when (r.kind) {
        ReminderKind.DAILY_READY -> when (r.variant % 3) {
            0 -> UiText.res(R.string.daily_reminder_ready_text_1, Daily.status(p, LocalDays.today()).nextCalendarDay)
            1 -> UiText.res(R.string.daily_reminder_ready_text_2)
            else -> UiText.res(R.string.daily_reminder_ready_text_3)
        }
        ReminderKind.STREAK_AT_RISK ->
            UiText.res(if (p.streakShields > 0) R.string.daily_reminder_streak_text_shield else R.string.daily_reminder_streak_text)
        ReminderKind.EVENT_STARTING -> {
            val m = r.migration
            val e = r.weekendEvent
            when {
                m != null -> {
                    // A species the player hasn't met keeps its name a secret: only its zone is told.
                    val names = m.species.map { s ->
                        if (Progression.dexState(p, s.id) == DexState.UNKNOWN) UiText.res(R.string.daily_reminder_unknown_species, s.zone.labelText()) else s.nameText()
                    }
                    val days = GameConfig.MIGRATION_HOURS / 24
                    UiText.plural(R.plurals.daily_reminder_migration_text, days, days, UiText.list(names))
                }
                e != null -> UiText.res(R.string.daily_reminder_event_text, e.goal.labelText(), e.goal.target)
                else -> UiText.raw("")
            }
        }
    }
}

/** Checks every few hours whether a reminder is due (see `DailyReminders.plan`). */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = SharedPrefsStore(applicationContext)
        val settings = ReminderPrefs.load(store)
        if (!settings.enabled) return Result.success()
        val progress = ProgressRepository(store).load()
        val now = ShopClock.now()
        val reminder = DailyReminders.plan(progress, settings, now) ?: return Result.success()
        if (!ReminderNotifications.post(applicationContext, reminder, progress)) return Result.success()
        ReminderPrefs.save(
            store,
            settings.copy(lastSentDay = LocalDays.dayOf(now), lastEventKey = reminder.eventKey.ifEmpty { settings.lastEventKey }),
        )
        Analytics.event("reminder_sent", mapOf("type" to reminder.kind.name.lowercase()))
        return Result.success()
    }
}

/** The periodic reminder check (WorkManager: it survives restarts and respects Doze). */
object ReminderScheduler {
    const val WORK_NAME = "deepline_reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(GameConfig.NOTIFY_CHECK_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
