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

    /** Creating an existing channel again does nothing, so this is safe to call any time. */
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(DAILY, "Daily reward ready", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When today's reward is waiting and you haven't opened the game yet."
                },
                NotificationChannel(STREAK, "Streak at risk", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "In the evening, when your login streak ends at midnight unless you claim today's reward."
                },
                NotificationChannel(EVENTS, "Migrations and events", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "When a migration or a weekend event starts."
                },
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
        val text = ReminderTexts.text(r, p)
        val notification = NotificationCompat.Builder(context, ReminderChannels.of(r.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderTexts.title(r))
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

/** What reminders say. They change from day to day ([Reminder.variant]), so they stay fresh. */
object ReminderTexts {
    fun title(r: Reminder): String = when (r.kind) {
        ReminderKind.DAILY_READY -> listOf("Your daily reward is waiting", "The fish are biting", "A reward is waiting at the dock")[r.variant % 3]
        ReminderKind.STREAK_AT_RISK -> "Keep your ${r.streak}-day streak"
        ReminderKind.EVENT_STARTING -> if (r.migration != null) "A migration has begun" else "The weekend event has started"
    }

    fun text(r: Reminder, p: PlayerProgress): String = when (r.kind) {
        ReminderKind.DAILY_READY -> listOf(
            "Day ${com.deepline.fishing.core.Daily.status(p, LocalDays.today()).nextCalendarDay} of your reward calendar is ready to claim.",
            "Today's reward and three new challenges are waiting for you.",
            "Claim today's reward and see what today's challenges are.",
        )[r.variant % 3]
        ReminderKind.STREAK_AT_RISK ->
            if (p.streakShields > 0) {
                "Claim today's reward before midnight. If you miss today, a streak shield will cover it."
            } else {
                "Claim today's reward before midnight to keep it going."
            }
        ReminderKind.EVENT_STARTING -> {
            val m = r.migration
            val e = r.weekendEvent
            when {
                m != null -> {
                    val names = m.species.map { s ->
                        if (Progression.dexState(p, s.id) == DexState.UNKNOWN) "an unknown ${s.zone.displayName} species" else "the ${s.name}"
                    }
                    val days = GameConfig.MIGRATION_HOURS / 24
                    "For the next $days days, ${names[0]} and ${names[1]} are far more common.".replaceFirstChar { it.uppercase() }
                }
                e != null -> "This weekend's goal: ${e.goal.displayName} (${e.goal.target}). A reward waits when you reach it."
                else -> ""
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
        Analytics.event("notification_sent", mapOf("type" to reminder.kind.name.lowercase()))
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
