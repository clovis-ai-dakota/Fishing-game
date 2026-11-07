package com.deepline.fishing.daily

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deepline.fishing.analytics.Analytics
import com.deepline.fishing.core.ChallengeDifficulty
import com.deepline.fishing.core.Daily
import com.deepline.fishing.core.DailyChallenge
import com.deepline.fishing.core.DailyRewards
import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.Migration
import com.deepline.fishing.core.Migrations
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ReminderKind
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.StreakStatus
import com.deepline.fishing.ui.daily.DailyText

/**
 * The daily loop on this phone: claiming the daily reward and challenges, rerolls, and the
 * reminder opt-in. Game progress (streak, challenges) lives in [PlayerProgress] through
 * [progress] and [update]; reminder choices are device-only ([ReminderPrefs]).
 */
class DailyController(
    private val context: Context,
    private val store: KeyValueStore,
    private val progress: () -> PlayerProgress,
    private val update: (PlayerProgress) -> Unit,
) {
    /** What the last daily action did, shown on the Daily card and screen until dismissed. */
    var message by mutableStateOf<String?>(null)
        private set

    /** The explainer asking whether to turn reminders on is showing. */
    var askOptIn by mutableStateOf(false)
        private set

    var reminders by mutableStateOf(ReminderPrefs.load(store))
        private set

    val today: Long get() = LocalDays.today()

    val status: StreakStatus get() = Daily.status(progress(), today)

    /** Today's challenges (made on the fly if the day changed while the app was open). */
    val challenges: List<DailyChallenge>
        get() = progress().let { p -> if (p.challengeDay == today) p.dailyChallenges else Daily.refresh(p, today).dailyChallenges }

    /** The daily reward and finished challenges waiting to be claimed. */
    val readyCount: Int get() = (if (status.canClaim) 1 else 0) + challenges.count { it.claimable }

    val migration: Migration? get() = Migrations.at(ShopClock.now())

    /** The migration under way, or the next one. */
    val nextMigration: Migration get() = Migrations.currentOrNext(ShopClock.now())

    /** At launch and whenever the app comes back: a new day brings new challenges. */
    fun onAppOpened() {
        refresh()
        val day = today
        if (reminders.lastOpenDay != day) saveReminders(reminders.copy(lastOpenDay = day))
        if (reminders.enabled) ReminderScheduler.schedule(context)
    }

    /** Moves the challenges to today, collecting finished ones from an earlier day. */
    fun refresh() {
        val p = progress()
        val r = Daily.rollover(p, today)
        if (r.progress != p) update(r.progress)
        if (r.collected.isNotEmpty()) {
            message = DailyText.collected(DailyText.grants(r.collected.flatMap { DailyRewards.challengeReward(it.difficulty) }, p))
        }
    }

    fun claimReward() {
        val p = progress()
        val s = Daily.status(p, today)
        val next = Daily.claimReward(p, today) ?: return
        val grants = Daily.nextReward(p)
        update(next)
        // Shields cover missed days when the streak is still alive (see Daily.claimReward).
        val shieldsUsed = if (s.streak > 0) s.missedDays else 0
        message = DailyText.claimed(
            day = s.nextCalendarDay,
            rewards = DailyText.grants(grants, p),
            streak = next.dailyStreak,
            shieldsUsed = shieldsUsed,
            streakRestarted = p.dailyLastClaimDay != null && s.streak == 0,
        )
        Analytics.event(
            "daily_reward_claim",
            mapOf("streak" to next.dailyStreak, "calendar_day" to s.nextCalendarDay, "shields_used" to shieldsUsed),
        )
        // A meaningful moment to offer reminders: once, never at first launch.
        if (!reminders.enabled && !ReminderPrefs.asked(store)) askOptIn = true
    }

    fun claimChallenge(difficulty: ChallengeDifficulty) {
        val p = progress()
        val c = challenges.firstOrNull { it.difficulty == difficulty } ?: return
        val next = Daily.claimChallenge(p, difficulty, today) ?: return
        update(next)
        message = "Challenge complete! You get ${DailyText.grants(DailyRewards.challengeReward(difficulty), p)}."
        Analytics.event("daily_challenge_claim", mapOf("kind" to c.kind.name.lowercase(), "difficulty" to difficulty.name.lowercase()))
    }

    fun canReroll(difficulty: ChallengeDifficulty): Boolean = Daily.canReroll(progress(), difficulty, today)

    val rerollsLeft: Int get() = Daily.rerollsLeft(progress(), today)

    fun reroll(difficulty: ChallengeDifficulty) {
        val next = Daily.reroll(progress(), difficulty, today) ?: return
        update(next)
        val fresh = next.dailyChallenges.first { it.difficulty == difficulty }
        message = "New ${difficulty.label.lowercase()} challenge: ${DailyText.challenge(fresh)}"
        Analytics.event("daily_challenge_reroll", mapOf("difficulty" to difficulty.name.lowercase(), "kind" to fresh.kind.name.lowercase()))
    }

    /** After a solo cast: logs the challenges it finished. */
    fun afterCast(before: PlayerProgress, after: PlayerProgress) {
        val was = before.dailyChallenges.filter { it.done }.map { it.difficulty }.toSet()
        for (c in after.dailyChallenges) {
            if (c.done && (c.difficulty !in was || before.challengeDay != after.challengeDay)) {
                Analytics.event("daily_challenge_complete", mapOf("kind" to c.kind.name.lowercase(), "difficulty" to c.difficulty.name.lowercase()))
            }
        }
    }

    fun dismissMessage() {
        message = null
    }

    // ---- Reminders ----------------------------------------------------------------------------

    /** Android 13+ asks for the notification permission before the first reminder. */
    fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Reminders are on in the game, but the phone blocks the app's notifications. */
    val blockedByPhone: Boolean
        get() = reminders.enabled && (needsPermission() || !NotificationManagerCompat.from(context).areNotificationsEnabled())

    /** The answer to the explainer: "Not now" is final (Settings has the switch). */
    fun answerOptIn(turnOn: Boolean) {
        askOptIn = false
        ReminderPrefs.markAsked(store)
        if (!turnOn) Analytics.event("reminder_opt_out", mapOf("source" to "explainer"))
    }

    /** The system dialog's answer (or true straight away when no permission is needed). */
    fun onPermissionResult(granted: Boolean, source: String) {
        Analytics.event("reminder_permission", mapOf("granted" to granted, "source" to source))
        if (granted) {
            setEnabled(true, source)
        } else {
            message = "Reminders stay off: notifications for Deep Line are blocked on this phone. You can allow them in the phone's settings."
        }
    }

    fun setEnabled(on: Boolean, source: String) {
        ReminderPrefs.markAsked(store)
        if (on == reminders.enabled) return
        saveReminders(reminders.copy(enabled = on))
        if (on) {
            ReminderChannels.create(context)
            ReminderScheduler.schedule(context)
            Analytics.event("reminder_opt_in", mapOf("source" to source))
        } else {
            ReminderScheduler.cancel(context)
            Analytics.event("reminder_opt_out", mapOf("source" to source))
        }
    }

    fun setKind(kind: ReminderKind, on: Boolean) {
        saveReminders(
            when (kind) {
                ReminderKind.DAILY_READY -> reminders.copy(dailyReady = on)
                ReminderKind.STREAK_AT_RISK -> reminders.copy(streakAtRisk = on)
                ReminderKind.EVENT_STARTING -> reminders.copy(events = on)
            },
        )
        Analytics.event("reminder_type_toggle", mapOf("type" to kind.name.lowercase(), "on" to on))
    }

    /** A reminder was tapped. */
    fun onNotificationOpened(target: ReminderTarget) {
        Analytics.event("reminder_open", mapOf("type" to target.key))
    }

    private fun saveReminders(s: ReminderSettings) {
        reminders = s
        ReminderPrefs.save(store, s)
    }
}

private typealias ReminderSettings = com.deepline.fishing.core.ReminderSettings
