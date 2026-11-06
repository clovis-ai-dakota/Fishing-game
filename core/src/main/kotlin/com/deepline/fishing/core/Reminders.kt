package com.deepline.fishing.core

/** The kinds of reminder, one notification channel each. */
enum class ReminderKind {
    /** Today's daily reward hasn't been claimed. */
    DAILY_READY,

    /** A streak claimed yesterday ends tonight unless today's reward is claimed. */
    STREAK_AT_RISK,

    /** A migration or a weekend event has started. */
    EVENT_STARTING,
}

/** The player's reminder choices and what was sent (kept on the device, not in the save). */
data class ReminderSettings(
    /** The player opted in (and the phone lets the app post notifications). */
    val enabled: Boolean = false,
    val dailyReady: Boolean = true,
    val streakAtRisk: Boolean = true,
    val events: Boolean = true,
    /** The local day the last reminder was sent (at most one a day). */
    val lastSentDay: Long? = null,
    /** The last migration or weekend event announced (each is announced once). */
    val lastEventKey: String = "",
    /** The local day the game was last opened. */
    val lastOpenDay: Long? = null,
) {
    fun allows(kind: ReminderKind): Boolean = enabled && when (kind) {
        ReminderKind.DAILY_READY -> dailyReady
        ReminderKind.STREAK_AT_RISK -> streakAtRisk
        ReminderKind.EVENT_STARTING -> events
    }
}

/** A reminder to post now. [variant] picks one of the texts, so they change from day to day. */
data class Reminder(
    val kind: ReminderKind,
    val streak: Int = 0,
    val migration: Migration? = null,
    val weekendEvent: WeekendEvent? = null,
    val variant: Int = 0,
) {
    /** The event this reminder announces (saved as [ReminderSettings.lastEventKey]). */
    val eventKey: String get() = migration?.key ?: weekendEvent?.key ?: ""
}

/**
 * Decides whether a reminder is due (the app checks every [GameConfig.NOTIFY_CHECK_HOURS] hours).
 * Kind and rare by design (see docs/research/daily-loop.md): at most one a day, never in quiet
 * hours, nothing once today's reward is claimed, the streak reminder only in the evening for a
 * streak worth keeping (it replaces the daily one), each event announced once, near its start.
 */
object DailyReminders {
    fun isQuietHour(hour: Int): Boolean =
        if (GameConfig.NOTIFY_QUIET_START_HOUR > GameConfig.NOTIFY_QUIET_END_HOUR) {
            hour >= GameConfig.NOTIFY_QUIET_START_HOUR || hour < GameConfig.NOTIFY_QUIET_END_HOUR
        } else {
            hour in GameConfig.NOTIFY_QUIET_START_HOUR until GameConfig.NOTIFY_QUIET_END_HOUR
        }

    fun plan(p: PlayerProgress, s: ReminderSettings, now: Long, offsetMs: Int = LocalDays.offsetMs(now)): Reminder? {
        if (!s.enabled) return null
        val day = LocalDays.dayOf(now, offsetMs)
        val hour = LocalDays.hourOf(now, offsetMs)
        if (isQuietHour(hour)) return null
        val lastSent = s.lastSentDay
        if (lastSent != null && lastSent >= day) return null
        val variant = Math.floorMod(day, 3L).toInt()
        val streak = Daily.status(p, day)
        // A streak worth keeping is reminded in the evening, instead of the daily reward at noon.
        val streakDue = streak.atRisk && streak.streak >= GameConfig.NOTIFY_STREAK_MIN_DAYS && s.allows(ReminderKind.STREAK_AT_RISK)
        if (streakDue && hour >= GameConfig.NOTIFY_STREAK_FROM_HOUR) {
            return Reminder(ReminderKind.STREAK_AT_RISK, streak = streak.streak, variant = variant)
        }
        if (s.allows(ReminderKind.EVENT_STARTING)) {
            val migration = Migrations.at(now)?.takeIf { it.key != s.lastEventKey && now - it.startMs < EVENT_NEWS_MS }
            if (migration != null) return Reminder(ReminderKind.EVENT_STARTING, migration = migration, variant = variant)
            val event = Seasons.eventAt(now)?.takeIf { it.key != s.lastEventKey && now - it.startMs < EVENT_NEWS_MS }
            if (event != null) return Reminder(ReminderKind.EVENT_STARTING, weekendEvent = event, variant = variant)
        }
        val openedToday = s.lastOpenDay?.let { it >= day } ?: false
        if (!streakDue && streak.canClaim && !openedToday && hour >= GameConfig.NOTIFY_DAILY_FROM_HOUR && s.allows(ReminderKind.DAILY_READY)) {
            return Reminder(ReminderKind.DAILY_READY, streak = streak.streak, variant = variant)
        }
        return null
    }

    /** Events are announced within this long of their start (not when the player opts in late). */
    private const val EVENT_NEWS_MS = 24 * LocalDays.HOUR_MS
}
