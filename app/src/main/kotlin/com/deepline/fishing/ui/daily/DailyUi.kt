package com.deepline.fishing.ui.daily

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.deepline.fishing.ui.kit.LocalCalmMotion
import com.deepline.fishing.ui.kit.deepColor
import kotlinx.coroutines.delay
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deepline.fishing.core.DailyGrant
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.compactPoints
import com.deepline.fishing.ui.dockBackground
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.ButtonSize
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.InfoButton
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.Pips
import com.deepline.fishing.ui.kit.RewardChest
import com.deepline.fishing.ui.kit.RingProgress
import com.deepline.fishing.ui.kit.RoundIconButton
import com.deepline.fishing.ui.kit.SectionTitle
import com.deepline.fishing.ui.shop.icon
import com.deepline.fishing.ui.theme.LocalMenuPalette
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

/** How long the chest's opening, the reward's tiles and the coins' flight take, in milliseconds. */
private const val REVEAL_MS = 1600

/** The same with "Reduce motion": nothing moves, the reward is just left on screen for a moment. */
private const val REVEAL_CALM_MS = 700

/**
 * The explainer shown once, after the first daily reward: why, how often, how to stop. It waits
 * for the reward to be revealed: asked at once, it covered the chest opening and the coins (seen
 * on a phone), on the very first claim, the one moment made to delight.
 */
@Composable
private fun ReminderOptInDialog(vm: GameViewModel, turnOn: () -> Unit) {
    val ask = vm.daily.askOptIn
    val calm = LocalCalmMotion.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(ask) {
        revealed = false
        if (ask) {
            // Frame time, like the reveal itself (and like every animation, it follows a test's clock).
            Animatable(0f).animateTo(1f, tween(if (calm) REVEAL_CALM_MS else REVEAL_MS, easing = LinearEasing))
            revealed = true
        }
    }
    if (!ask || !revealed) return
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

/**
 * What the last daily action did, with a way to dismiss it. After a claim, [got] is what was
 * received: it is shown as big tiles popping in one after the other (the reward itself, before
 * the sentence), the way the most played games reveal a chest's contents.
 */
@Composable
private fun DailyMessage(vm: GameViewModel, got: List<DailyGrant>? = null, onDismiss: () -> Unit = {}) {
    val message = vm.daily.message ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (got != null) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.testTag("daily_got"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    got.forEachIndexed { i, g -> GotTile(g, vm.progress, i) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).testTag("daily_message"),
                    )
                    TextButton(onClick = { onDismiss(); vm.daily.dismissMessage() }) { Text(stringResource(R.string.common_ok)) }
                }
            }
            return@Surface
        }
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

/** One thing just received: its pictogram, big, and how much, popping in after the one before. */
@Composable
private fun GotTile(g: DailyGrant, p: PlayerProgress, index: Int) {
    val calm = LocalCalmMotion.current
    val scale = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!calm) {
            delay(120L * index)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val description = DailyText.tile(g, p).asString()
    Column(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .size(width = 76.dp, height = 72.dp)
            .background(deepColor(), RoundedCornerShape(14.dp))
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val (icon, tint, amount) = when (g) {
            is DailyGrant.Points -> Triple(GameIcons.Coin, SparkleGold, "+" + compactPoints(DailyRewards.pointsFor(p, g.casts)))
            is DailyGrant.Tackle -> Triple(g.tackle.icon, Color(0xFF8FE3F5), stringResource(R.string.common_times, g.count))
            DailyGrant.Shield -> Triple(DailyIcons.Shield, Color(0xFF8FE3F5), stringResource(R.string.common_times, 1))
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        Text(amount, color = GameColors.OnDeep, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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

/** One reward as a pictogram and an amount (the coin, a tackle's icon, the shield); its name is read aloud. */
@Composable
private fun GrantChip(g: DailyGrant, p: PlayerProgress, modifier: Modifier = Modifier, large: Boolean = false) {
    val description = DailyText.tile(g, p).asString()
    val icon = if (large) 20.dp else 14.dp
    val style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium
    Row(
        modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (g) {
            is DailyGrant.Points -> {
                Icon(GameIcons.Coin, contentDescription = null, tint = SparkleGold, modifier = Modifier.size(icon))
                Text(compactPoints(DailyRewards.pointsFor(p, g.casts)), style = style, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            is DailyGrant.Tackle -> {
                Icon(g.tackle.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(icon))
                Text(stringResource(R.string.common_times, g.count), style = style, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            DailyGrant.Shield -> Icon(DailyIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(icon))
        }
    }
}

/** One day of the calendar: claimed (ticked), today's (outlined) or later, with its rewards as pictograms. */
@Composable
private fun CalendarDay(day: Int, state: TileState, modifier: Modifier = Modifier, grants: List<DailyGrant> = emptyList(), p: PlayerProgress? = null) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        TileState.CLAIMED -> c.primary to c.onPrimary
        TileState.TODAY -> c.secondaryContainer to c.onSecondaryContainer
        TileState.LATER -> c.surfaceVariant to c.onSurfaceVariant
    }
    val last = day == DailyRewards.calendar.size
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier
                .size(if (p != null) 36.dp else 30.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (state == TileState.TODAY) Modifier.border(BorderStroke(2.5.dp, GameColors.of(ActionRole.CLAIM, LocalMenuPalette.current).bottom), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state == TileState.CLAIMED -> Icon(KitIcons.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                last -> Icon(GameIcons.Star, contentDescription = null, tint = rewardTextColor(), modifier = Modifier.size(18.dp))
                else -> Text("$day", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold) // i18n-ignore: digits
            }
        }
        if (p != null) for (g in grants) GrantChip(g, p)
    }
}

/**
 * The reward itself: a chest (restless while today's reward waits, open once it's claimed), what
 * the next claim gives, and when it comes if today's is taken.
 */
@Composable
private fun ChestRow(vm: GameViewModel, s: StreakStatus) {
    val p = vm.progress
    val next = Daily.nextReward(p)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RewardChest(open = s.claimedToday, ready = s.canClaim, modifier = Modifier.size(width = 104.dp, height = 96.dp).testTag("daily_chest"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDay(s.nextCalendarDay, if (s.canClaim) TileState.TODAY else TileState.LATER)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { for (g in next) GrantChip(g, p, large = true) }
            }
            if (!s.canClaim) {
                // "Today's reward is claimed. Day 3 (...) is ready in 5 h": a clock and the time left.
                val wait = ShopText.timeLeftLabel(LocalDays.msUntilTomorrow(ShopClock.now()))
                val sentence = stringResource(R.string.daily_claim_wait, s.nextCalendarDay, DailyText.grants(next, p).asString(), wait)
                Row(
                    Modifier.semantics { contentDescription = sentence }.testTag("daily_wait"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(KitIcons.Clock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(wait, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun difficultyTag(d: ChallengeDifficulty) = d.name.lowercase()

/**
 * A challenge: a ring for how far along, its difficulty as pips, what to do, its reward as
 * pictograms and its action (claim in green, or swap).
 */
@Composable
private fun ChallengeRow(vm: GameViewModel, c: DailyChallenge, onClaimed: (List<DailyGrant>) -> Unit) {
    val p = vm.progress
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RingProgress((c.progress.toFloat() / c.target).coerceIn(0f, 1f), diameter = 44.dp, color = if (c.claimed) scheme.secondary else scheme.primary) {
            if (c.claimed) Icon(KitIcons.Check, contentDescription = stringResource(R.string.daily_challenge_done), tint = scheme.secondary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(DailyText.challenge(c).asString(), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Easy, medium or hard: one, two or three pips.
                val difficulty = c.difficulty.label()
                Pips(c.difficulty.ordinal + 1, ChallengeDifficulty.entries.size, Modifier.semantics { contentDescription = difficulty }, color = scheme.secondary)
                Text(DailyText.progress(c).asString(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!c.claimed) for (g in DailyRewards.challengeReward(c.difficulty)) GrantChip(g, p)
            when {
                c.claimed -> Unit
                c.claimable -> GameButton(
                    stringResource(R.string.daily_challenge_claim),
                    onClick = {
                        onClaimed(DailyRewards.challengeReward(c.difficulty))
                        vm.daily.claimChallenge(c.difficulty)
                    },
                    modifier = Modifier.testTag("challenge_claim_${difficultyTag(c.difficulty)}"),
                    role = ActionRole.CLAIM,
                    size = ButtonSize.SMALL,
                )
                vm.daily.canReroll(c.difficulty) -> RoundIconButton(
                    DailyIcons.Swap,
                    stringResource(R.string.daily_challenge_swap),
                    onClick = {
                        onClaimed(emptyList())
                        vm.daily.reroll(c.difficulty)
                    },
                    modifier = Modifier.testTag("challenge_reroll_${difficultyTag(c.difficulty)}"),
                    diameter = 34.dp,
                )
            }
        }
    }
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

/** A migration under way, in the Dex (nothing between migrations): the two species, the rest behind the (i). */
@Composable
fun MigrationBanner(vm: GameViewModel, modifier: Modifier = Modifier) {
    val m = vm.daily.migration ?: return
    Card(modifier.fillMaxWidth().testTag("migration_banner")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(
                stringResource(R.string.daily_migration_banner_title),
                info = stringResource(R.string.daily_migration_banner_body, ShopText.timeLeftLabel(m.msLeft(ShopClock.now()))),
                icon = DailyIcons.School,
            )
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

/**
 * The Daily screen: the streak, the week's rewards as pictograms around the chest, today's
 * challenges and the migration. The claim is the screen's one main action, docked at the bottom;
 * the rules are behind the (i) buttons.
 */
@Composable
fun DailyScreen(vm: GameViewModel) {
    val p = vm.progress
    val s = Daily.status(p, vm.daily.today)
    val turnOn = rememberReminderOptIn(vm, "explainer")
    val turnOnHere = rememberReminderOptIn(vm, "daily_screen")
    // What the last claim gave, shown as tiles until dismissed (empty: a plain message).
    var got by remember { mutableStateOf<List<DailyGrant>>(emptyList()) }
    SubScreen(stringResource(R.string.daily_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DailyMessage(vm, got.takeIf { it.isNotEmpty() }, onDismiss = { got = emptyList() })
            Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DAILY_CALENDAR)) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { StreakLine(s, large = true) }
                        InfoButton(
                            stringResource(R.string.daily_title),
                            stringResource(
                                R.string.daily_streak_rules,
                                pluralStringResource(R.plurals.daily_best_streak, p.dailyBestStreak, p.dailyBestStreak),
                                s.shields,
                                GameConfig.MAX_STREAK_SHIELDS,
                                DailyRewards.calendar.size,
                            ),
                            tag = "daily_info",
                        )
                    }
                    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChestRow(vm, s)
                        // Two rows (days 1 to 4, then 5 to 7), the last day twice as wide: it's the big one.
                        val days = DailyRewards.calendar.indices.toList()
                        for (row in days.chunked((days.size + 1) / 2)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in row) {
                                    CalendarDay(i + 1, tileState(i + 1, p, s), Modifier.weight(if (i == days.last()) 2f else 1f), grants = DailyRewards.calendar[i], p = p)
                                }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().tourTarget(TourTarget.DAILY_CHALLENGES)) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val left = vm.daily.rerollsLeft
                    SectionTitle(
                        stringResource(R.string.daily_challenges_title),
                        info = stringResource(
                            R.string.daily_challenges_rules,
                            if (left > 0) pluralStringResource(R.plurals.daily_swaps_left, left, left) else stringResource(R.string.daily_swaps_none),
                        ),
                        trailing = {
                            // Free swaps left today.
                            if (left > 0) {
                                Icon(DailyIcons.Swap, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.common_times, left), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (c in vm.daily.challenges) ChallengeRow(vm, c, onClaimed = { got = it })
                    }
                }
            }
            MigrationCard(vm, vm.daily.nextMigration, Modifier.tourTarget(TourTarget.DAILY_MIGRATION))
            if (!vm.daily.reminders.enabled) {
                GameButton(onClick = turnOnHere, modifier = Modifier.fillMaxWidth().testTag("daily_reminders")) {
                    Icon(DailyIcons.Bell, contentDescription = null, modifier = Modifier.size(18.dp))
                    // Balanced lines when it needs two (long in Portuguese or German).
                    Text(
                        stringResource(R.string.daily_remind_button),
                        modifier = Modifier.weight(1f, fill = false),
                        style = androidx.compose.material3.LocalTextStyle.current.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // The one main action: today's reward, in the thumb zone.
        if (s.canClaim) {
            val sentence = stringResource(R.string.daily_claim_button, s.nextCalendarDay, DailyText.grants(Daily.nextReward(p), p).asString())
            Box(Modifier.fillMaxWidth().dockBackground().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                GameButton(
                    stringResource(R.string.daily_challenge_claim),
                    onClick = {
                        got = Daily.nextReward(p)
                        vm.daily.claimReward()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("daily_claim").semantics { contentDescription = sentence },
                    role = ActionRole.CLAIM,
                    size = ButtonSize.HERO,
                    icon = KitIcons.Gift,
                )
            }
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
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val days = GameConfig.MIGRATION_HOURS / 24
            SectionTitle(
                if (live) {
                    stringResource(R.string.daily_migration_live, ShopText.timeLeftLabel(m.msLeft(now)))
                } else {
                    stringResource(R.string.daily_migration_next, ShopText.timeLeftLabel(m.msUntilStart(now)))
                },
                info = pluralStringResource(R.plurals.daily_migration_about, days, days),
                icon = DailyIcons.School,
            )
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in m.species) MigrantRow(vm, s)
            }
        }
    }
}
