package com.deepline.fishing.ui.daily

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.ChallengeDifficulty
import com.deepline.fishing.core.Daily
import com.deepline.fishing.core.DailyChallenge
import com.deepline.fishing.core.DailyRewards
import com.deepline.fishing.core.DexState
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.LocalDays
import com.deepline.fishing.core.Migration
import com.deepline.fishing.core.Migrations
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.StreakStatus
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.dex.FishPortrait
import com.deepline.fishing.ui.dex.dexNumberText
import com.deepline.fishing.ui.dex.zoneBackground
import com.deepline.fishing.ui.dex.zoneTextColor
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.math.roundToInt

/** "21:00" and "09:00": reminders' quiet hours, from GameConfig. */
private fun hourText(h: Int) = "%02d:00".format(h)

/** Reminders' promise, used by the explainer and Settings. */
val reminderPromise: String
    get() = "At most one a day, never between ${hourText(GameConfig.NOTIFY_QUIET_START_HOUR)} and " +
        "${hourText(GameConfig.NOTIFY_QUIET_END_HOUR)}, and you can turn them off any time in Settings."

/**
 * Turning reminders on: Android 13+ shows its permission dialog first (when it's still needed).
 * Keep the returned function's composable on screen while the dialog is up: it gets the answer.
 */
@Composable
fun rememberReminderOptIn(vm: GameViewModel, source: String): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.daily.onPermissionResult(granted, source)
    }
    return remember(launcher, source) {
        {
            if (vm.daily.needsPermission()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.daily.setEnabled(true, source)
        }
    }
}

/** The explainer shown once, right after the first daily reward: why, how often, how to stop. */
@Composable
private fun ReminderOptInDialog(vm: GameViewModel, turnOn: () -> Unit) {
    if (!vm.daily.askOptIn) return
    AlertDialog(
        onDismissRequest = { vm.daily.answerOptIn(false) },
        icon = { Icon(DailyIcons.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Want a reminder?") },
        text = {
            Text(
                "Deep Line can let you know when your daily reward is ready, when your streak is about to end and when a " +
                    "migration starts. $reminderPromise",
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.daily.answerOptIn(true); turnOn() }, modifier = Modifier.testTag("optin_yes")) { Text("Turn on") }
        },
        dismissButton = {
            TextButton(onClick = { vm.daily.answerOptIn(false) }, modifier = Modifier.testTag("optin_no")) { Text("Not now") }
        },
    )
}

/** What the last daily action did, with a way to dismiss it. */
@Composable
private fun DailyMessage(vm: GameViewModel) {
    val message = vm.daily.message ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).testTag("daily_message"),
            )
            TextButton(onClick = vm.daily::dismissMessage) { Text("OK") }
        }
    }
}

/** The streak: a flame and the number of days, with the shields held. */
@Composable
private fun StreakLine(s: StreakStatus, large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            DailyIcons.Flame,
            contentDescription = null,
            tint = if (s.streak > 0) rewardTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (large) 28.dp else 18.dp),
        )
        Text(
            when {
                s.streak == 1 -> "1-day streak"
                s.streak > 1 -> "${s.streak}-day streak"
                else -> "No streak yet"
            },
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("daily_streak"),
        )
        Spacer(Modifier.width(4.dp))
        repeat(s.shields) {
            Icon(DailyIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (large) 20.dp else 14.dp))
        }
    }
}

private enum class TileState { CLAIMED, TODAY, LATER }

/** Calendar days already claimed in the current week of the calendar. */
private fun claimedInCycle(p: PlayerProgress, s: StreakStatus): Int {
    val n = DailyRewards.calendar.size
    return if (s.claimedToday && p.dailyClaims > 0) (p.dailyClaims - 1) % n + 1 else p.dailyClaims % n
}

private fun tileState(day: Int, p: PlayerProgress, s: StreakStatus): TileState {
    val claimed = claimedInCycle(p, s)
    return when {
        day <= claimed -> TileState.CLAIMED
        day == claimed + 1 && s.canClaim -> TileState.TODAY
        else -> TileState.LATER
    }
}

/** One day of the calendar: claimed (filled), today's (outlined) or later. */
@Composable
private fun CalendarDay(day: Int, state: TileState, modifier: Modifier = Modifier, label: String? = null) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        TileState.CLAIMED -> c.primary to c.onPrimary
        TileState.TODAY -> c.secondaryContainer to c.onSecondaryContainer
        TileState.LATER -> c.surfaceVariant to c.onSurfaceVariant
    }
    val last = day == DailyRewards.calendar.size
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .size(if (label != null) 40.dp else 30.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (state == TileState.TODAY) Modifier.border(BorderStroke(2.dp, c.primary), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (last && state != TileState.CLAIMED) {
                Icon(GameIcons.Star, contentDescription = null, tint = rewardTextColor(), modifier = Modifier.size(18.dp))
            } else {
                Text("$day", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold)
            }
        }
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (last) rewardTextColor() else c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    }
}

/** The claim button, or when the next reward comes. */
@Composable
private fun ClaimOrWait(vm: GameViewModel, s: StreakStatus) {
    val p = vm.progress
    val next = DailyText.grants(Daily.nextReward(p), p)
    if (s.canClaim) {
        Button(onClick = vm.daily::claimReward, modifier = Modifier.fillMaxWidth().testTag("daily_claim")) {
            Text("Claim day ${s.nextCalendarDay}: $next", textAlign = TextAlign.Center)
        }
    } else {
        val wait = ShopText.timeLeft(LocalDays.msUntilTomorrow(ShopClock.now()))
        Text(
            "Today's reward is claimed. Day ${s.nextCalendarDay} ($next) is ready in $wait.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun difficultyTag(d: ChallengeDifficulty) = d.name.lowercase()

/** A challenge: what to do, how far along, and its claim (and, on the Daily screen, swap) button. */
@Composable
private fun ChallengeRow(vm: GameViewModel, c: DailyChallenge, full: Boolean) {
    val p = vm.progress
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(c.difficulty.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(DailyText.challenge(c), style = MaterialTheme.typography.bodyMedium)
            }
            when {
                c.claimed -> Text("Done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                c.claimable -> FilledTonalButton(
                    onClick = { vm.daily.claimChallenge(c.difficulty) },
                    modifier = Modifier.testTag("challenge_claim_${difficultyTag(c.difficulty)}"),
                ) { Text("Claim") }
                full && vm.daily.canReroll(c.difficulty) -> TextButton(
                    onClick = { vm.daily.reroll(c.difficulty) },
                    modifier = Modifier.testTag("challenge_reroll_${difficultyTag(c.difficulty)}"),
                ) {
                    Icon(DailyIcons.Swap, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Swap")
                }
            }
        }
        LinearProgressIndicator(progress = { (c.progress.toFloat() / c.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row {
            Text(DailyText.progress(c), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (full) {
                Text(
                    DailyText.grants(DailyRewards.challengeReward(c.difficulty), p),
                    style = MaterialTheme.typography.labelSmall,
                    color = rewardTextColor(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The Fish tab's daily card: the streak, the week's calendar, the claim button and today's
 * challenges. Tapping it opens the Daily screen.
 */
@Composable
fun DailyCard(vm: GameViewModel, modifier: Modifier = Modifier) {
    val p = vm.progress
    val s = Daily.status(p, vm.daily.today)
    val turnOn = rememberReminderOptIn(vm, "explainer")
    Card(onClick = vm::openDaily, modifier = modifier.fillMaxWidth().testTag("daily_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StreakLine(s)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (day in 1..DailyRewards.calendar.size) CalendarDay(day, tileState(day, p, s))
            }
            ClaimOrWait(vm, s)
            for (c in vm.daily.challenges) ChallengeRow(vm, c, full = false)
            DailyMessage(vm)
        }
    }
    ReminderOptInDialog(vm, turnOn)
}

/** The species of a migration, as the Dex knows them (unknown ones stay "???"). */
@Composable
private fun MigrantRow(vm: GameViewModel, species: FishSpecies) {
    val state = Progression.dexState(vm.progress, species.id)
    val index = FishCatalog.all.indexOf(species)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Open its Dex page") { vm.openFishDetail(index) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(zoneBackground(species.zone))) {
            FishPortrait(species, state, Modifier.matchParentSize().padding(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (state == DexState.UNKNOWN) "??? ${dexNumberText(index + 1)}" else species.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${species.zone.displayName} · ${species.minDepth.roundToInt()}–${species.maxDepth.roundToInt()} m",
                style = MaterialTheme.typography.labelSmall,
                color = zoneTextColor(species.zone),
            )
        }
    }
}

/** A migration under way: shown on the Fish tab and in the Dex (nothing between migrations). */
@Composable
fun MigrationBanner(vm: GameViewModel, modifier: Modifier = Modifier) {
    val m = vm.daily.migration ?: return
    Card(modifier.fillMaxWidth().testTag("migration_banner")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(DailyIcons.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Migration under way", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "These two are far more common in solo casts for ${ShopText.timeLeft(m.msLeft(ShopClock.now()))}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (s in m.species) MigrantRow(vm, s)
        }
    }
}

/** On a species' Dex page: it's migrating right now. */
@Composable
fun MigratingNote(species: FishSpecies) {
    val m = Migrations.at(ShopClock.now())?.takeIf { species in it.species } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(DailyIcons.School, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Migrating now: far more common in solo casts for ${ShopText.timeLeft(m.msLeft(ShopClock.now()))}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The Daily screen: the reward calendar in full, today's challenges, migrations and reminders. */
@Composable
fun DailyScreen(vm: GameViewModel) {
    val p = vm.progress
    val s = Daily.status(p, vm.daily.today)
    val turnOn = rememberReminderOptIn(vm, "explainer")
    val turnOnHere = rememberReminderOptIn(vm, "daily_screen")
    SubScreen("Daily", onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DailyMessage(vm)
            Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DAILY_CALENDAR)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreakLine(s, large = true)
                    Text(
                        (if (p.dailyBestStreak == 1) "Best streak: 1 day. " else "Best streak: ${p.dailyBestStreak} days. ") +
                            "Streak shields: ${s.shields} of ${GameConfig.MAX_STREAK_SHIELDS}. " +
                            "A shield covers a missed day by itself, and day ${DailyRewards.calendar.size} brings a new one. " +
                            "Missing a day never sets the calendar back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Two rows (days 1 to 4, then 5 to 7), the last day twice as wide: it's the big one.
                    val days = DailyRewards.calendar.indices.toList()
                    for (row in days.chunked((days.size + 1) / 2)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in row) {
                                CalendarDay(
                                    i + 1,
                                    tileState(i + 1, p, s),
                                    Modifier.weight(if (i == days.last()) 2f else 1f),
                                    label = DailyRewards.calendar[i].joinToString("\n") { DailyText.tile(it, p) },
                                )
                            }
                        }
                    }
                    ClaimOrWait(vm, s)
                }
            }
            Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DAILY_CHALLENGES)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Today's challenges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    for (c in vm.daily.challenges) ChallengeRow(vm, c, full = true)
                    Text(
                        when (val left = vm.daily.rerollsLeft) {
                            0 -> "No free swaps left today."
                            1 -> "1 free swap left today for a challenge you don't fancy."
                            else -> "$left free swaps left today for challenges you don't fancy."
                        } + " Only solo casts count. New challenges come at midnight, and finished ones are collected for you if you forget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MigrationCard(vm, vm.daily.nextMigration, Modifier.tourTarget(TourTarget.DAILY_MIGRATION))
            if (!vm.daily.reminders.enabled) {
                OutlinedButton(onClick = turnOnHere, modifier = Modifier.fillMaxWidth().testTag("daily_reminders")) {
                    Icon(DailyIcons.Bell, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remind me when my reward is ready")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    ReminderOptInDialog(vm, turnOn)
}

/** The migration under way, or the next one and when it starts. */
@Composable
private fun MigrationCard(vm: GameViewModel, m: Migration, modifier: Modifier = Modifier) {
    val now = ShopClock.now()
    val live = m.isLive(now)
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(DailyIcons.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (live) "Migration: ${ShopText.timeLeft(m.msLeft(now))} left" else "Next migration in ${ShopText.timeLeft(m.msUntilStart(now))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Every week, for ${GameConfig.MIGRATION_HOURS / 24} days, two less common species become far more common where " +
                    "they live: one near the surface and one in the deep. Solo casts only: battles never change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (s in m.species) MigrantRow(vm, s)
        }
    }
}
