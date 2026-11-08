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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ui.hourText
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.shop.timeLeftLabel
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

/** Reminders' promise ("At most one a day, never between 21:00 and 09:00..."), used by the explainer and Settings. */
val reminderPromise: UiText
    get() = UiText.res(
        R.string.daily_reminder_promise,
        hourText(GameConfig.NOTIFY_QUIET_START_HOUR),
        hourText(GameConfig.NOTIFY_QUIET_END_HOUR),
    )

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
        title = { Text(stringResource(R.string.daily_optin_title)) },
        text = { Text(stringResource(R.string.daily_optin_body, reminderPromise.asString())) },
        confirmButton = {
            TextButton(onClick = { vm.daily.answerOptIn(true); turnOn() }, modifier = Modifier.testTag("optin_yes")) {
                Text(stringResource(R.string.daily_optin_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.daily.answerOptIn(false) }, modifier = Modifier.testTag("optin_no")) {
                Text(stringResource(R.string.daily_optin_no))
            }
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
                message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).testTag("daily_message"),
            )
            TextButton(onClick = vm.daily::dismissMessage) { Text(stringResource(R.string.common_ok)) }
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
            if (s.streak > 0) pluralStringResource(R.plurals.daily_streak, s.streak, s.streak) else stringResource(R.string.daily_streak_none),
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
    val next = DailyText.grants(Daily.nextReward(p), p).asString()
    if (s.canClaim) {
        Button(onClick = vm.daily::claimReward, modifier = Modifier.fillMaxWidth().testTag("daily_claim")) {
            Text(stringResource(R.string.daily_claim_button, s.nextCalendarDay, next), textAlign = TextAlign.Center)
        }
    } else {
        val wait = ShopText.timeLeftLabel(LocalDays.msUntilTomorrow(ShopClock.now()))
        Text(
            stringResource(R.string.daily_claim_wait, s.nextCalendarDay, next, wait),
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
                Text(c.difficulty.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(DailyText.challenge(c).asString(), style = MaterialTheme.typography.bodyMedium)
            }
            when {
                c.claimed -> Text(
                    stringResource(R.string.daily_challenge_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                c.claimable -> FilledTonalButton(
                    onClick = { vm.daily.claimChallenge(c.difficulty) },
                    modifier = Modifier.testTag("challenge_claim_${difficultyTag(c.difficulty)}"),
                ) { Text(stringResource(R.string.daily_challenge_claim)) }
                full && vm.daily.canReroll(c.difficulty) -> TextButton(
                    onClick = { vm.daily.reroll(c.difficulty) },
                    modifier = Modifier.testTag("challenge_reroll_${difficultyTag(c.difficulty)}"),
                ) {
                    Icon(DailyIcons.Swap, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.daily_challenge_swap))
                }
            }
        }
        LinearProgressIndicator(progress = { (c.progress.toFloat() / c.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row {
            Text(DailyText.progress(c).asString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (full) {
                Text(
                    DailyText.grants(DailyRewards.challengeReward(c.difficulty), p).asString(),
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
                Text(stringResource(R.string.daily_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
            .clickable(onClickLabel = stringResource(R.string.daily_migrant_open)) { vm.openFishDetail(index) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(zoneBackground(species.zone))) {
            FishPortrait(species, state, Modifier.matchParentSize().padding(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (state == DexState.UNKNOWN) "??? ${dexNumberText(index + 1)}" else species.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.daily_migrant_where, species.zone.label(), species.minDepth.roundToInt(), species.maxDepth.roundToInt()),
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
                    Text(stringResource(R.string.daily_migration_banner_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.daily_migration_banner_body, ShopText.timeLeftLabel(m.msLeft(ShopClock.now()))),
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
                stringResource(R.string.daily_migrating_note, ShopText.timeLeftLabel(m.msLeft(ShopClock.now()))),
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
    SubScreen(stringResource(R.string.daily_title), onBack = vm::onBack) {
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
                        stringResource(
                            R.string.daily_streak_rules,
                            pluralStringResource(R.plurals.daily_best_streak, p.dailyBestStreak, p.dailyBestStreak),
                            s.shields,
                            GameConfig.MAX_STREAK_SHIELDS,
                            DailyRewards.calendar.size,
                        ),
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
                                    label = DailyRewards.calendar[i].map { DailyText.tile(it, p).asString() }.joinToString("\n"),
                                )
                            }
                        }
                    }
                    ClaimOrWait(vm, s)
                }
            }
            Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DAILY_CHALLENGES)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.daily_challenges_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    for (c in vm.daily.challenges) ChallengeRow(vm, c, full = true)
                    val left = vm.daily.rerollsLeft
                    Text(
                        stringResource(
                            R.string.daily_challenges_rules,
                            if (left > 0) pluralStringResource(R.plurals.daily_swaps_left, left, left) else stringResource(R.string.daily_swaps_none),
                        ),
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
                    Text(stringResource(R.string.daily_remind_button))
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
                    if (live) {
                        stringResource(R.string.daily_migration_live, ShopText.timeLeftLabel(m.msLeft(now)))
                    } else {
                        stringResource(R.string.daily_migration_next, ShopText.timeLeftLabel(m.msUntilStart(now)))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            val days = GameConfig.MIGRATION_HOURS / 24
            Text(
                pluralStringResource(R.plurals.daily_migration_about, days, days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (s in m.species) MigrantRow(vm, s)
        }
    }
}
