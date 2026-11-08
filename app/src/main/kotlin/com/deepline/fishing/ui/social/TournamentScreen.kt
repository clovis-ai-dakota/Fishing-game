package com.deepline.fishing.ui.social

import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.i18n.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.TournamentTier
import com.deepline.fishing.ui.SightTackleChips
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** "1st", "2nd", "23rd". */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** Who gets which band of a finished tournament ("The top 10%"). */
fun tierRule(tier: TournamentTier): String = when (tier) {
    TournamentTier.CHAMPION -> "The winner and the top 1%"
    TournamentTier.GOLD -> "The top 10%"
    TournamentTier.SILVER -> "The top 25%"
    TournamentTier.BRONZE -> "The top half"
    TournamentTier.TOOK_PART -> "Everyone else who took part"
}

/** The weekly tournament: enter, attempts left, your best, the standings and the rewards. */
@Composable
fun TournamentScreen(vm: GameViewModel) {
    val social = vm.social
    val t = social.tournament
    val now = ShopClock.now()
    SubScreen("Weekly tournament", onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.refreshTournament() }

            for (r in t?.rewards.orEmpty()) {
                SocialCard("A reward is waiting", Modifier.testTag("tournament_reward"), highlighted = true) {
                    Text(
                        "You finished ${ordinal(r.rank)} of ${r.participants} with ${formatPoints(r.best)} pts: ${r.tier.label()}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Reward: " + ShopText.seasonGrants(r.tier.grants, vm.progress).asString() + ".",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = { social.claimReward(r) }, modifier = Modifier.testTag("tournament_claim")) { Text("Claim") }
                }
            }

            SocialCard("This week's water", Modifier.tourTarget(TourTarget.TOURNAMENT_ENTER)) {
                Hint(
                    "Everyone fishes the very same water all week, with the standard rope (level " +
                        "${OnlineProtocol.TOURNAMENT_ROPE_LEVEL} in every stat) and ${GameConfig.DESCENT_TOUCH_LIMIT} bumps. " +
                        "You have ${GameConfig.TOURNAMENT_ATTEMPTS} attempts and your best one counts. Starting an attempt " +
                        "uses it, even if you quit. Lanterns and detectors work, as in battles; tournament catches don't count " +
                        "for your points or fish log.",
                )
                if (t != null) {
                    val left = t.attemptsMax - t.attemptsUsed
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("Attempts left", "$left of ${t.attemptsMax}", Modifier.testTag("tournament_attempts"))
                        Stat("Ends in", timeLeft(t.endsAtMs, now))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("Your best", t.best?.let { "${formatPoints(it)} pts" } ?: "No score yet", Modifier.testTag("tournament_best"))
                        Stat("Your rank", t.rank?.let { "${ordinal(it)} of ${t.participants}" } ?: "Not ranked yet")
                    }
                    SightTackleChips(vm)
                    Button(
                        onClick = vm::startTournamentAttempt,
                        enabled = left > 0 && !social.busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("tournament_start"),
                    ) {
                        Text(
                            if (left > 0) "Start attempt ${t.attemptsUsed + 1} of ${t.attemptsMax}" else "No attempts left: see you on Monday",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Hint("Your attempts and best score appear here once the server answers.")
                }
            }

            social.lastAttempt?.let { a ->
                SocialCard("Attempt ${a.attempt}", Modifier.testTag("tournament_last")) {
                    Text("${formatPoints(a.points)} pts.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Hint("Your best this week: ${formatPoints(a.best)} pts, ${ordinal(a.rank)} of ${a.participants}.")
                }
            }

            if (t != null) {
                SocialCard("Standings") {
                    if (t.top.isEmpty()) {
                        Hint("No scores yet this week: yours could be the first.")
                    } else {
                        t.top.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider()
                            BoardRow(BoardKind.BEST_CAST, e)
                        }
                    }
                }
            }

            SocialCard("Rewards") {
                Hint("Rewards go by your final rank once the week is over, and are claimed here.")
                for (tier in TournamentTier.entries) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tierRule(tier), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            ShopText.seasonGrants(tier.grants, vm.progress).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = rewardTextColor(),
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Hint(label)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
