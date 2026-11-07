package com.deepline.fishing.ui.shop

import com.deepline.fishing.ui.shop.timeLeftLabel
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.SeasonReward
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.ui.SparkleGold

/**
 * This season: its name and time left, the tier reached, a button to claim what's open, and the
 * two tracks side by side (free below, the pass's premium track above).
 */
@Composable
fun SeasonCard(vm: GameViewModel, modifier: Modifier = Modifier) {
    val now = ShopClock.now()
    val p = Seasons.current(vm.progress, now)
    val season = Seasons.at(now)
    val tier = Seasons.tier(p, now)
    val pass = Seasons.hasPass(p, season)
    val open = Seasons.claimable(p, now)
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Season ${season.number} · ${season.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Ends in ${ShopText.timeLeftLabel(season.msLeft(now))}" + if (pass) " · Pass active" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pass) com.deepline.fishing.ui.rewardTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Tier $tier / ${GameConfig.SEASON_TIERS}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { Seasons.xpIntoTier(p, now) / GameConfig.SEASON_XP_PER_TIER.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Every solo cast earns season XP: ${GameConfig.SEASON_XP_PER_CAST} per cast, ${GameConfig.SEASON_XP_PER_FISH} per fish " +
                    "and ${GameConfig.SEASON_XP_PER_SECRET} per secret pulled off. ${GameConfig.SEASON_XP_PER_TIER} XP opens a tier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val rewards = Seasons.rewards(season)
            val state = rememberLazyListState(initialFirstVisibleItemIndex = (tier - 1).coerceIn(0, GameConfig.SEASON_TIERS - 1))
            LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..GameConfig.SEASON_TIERS).toList(), key = { it }) { t ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TierCell(vm, rewards.first { it.tier == t && it.premium }, reached = t <= tier, unlocked = pass)
                        TierCell(vm, rewards.first { it.tier == t && !it.premium }, reached = t <= tier, unlocked = true)
                        Text("$t", style = MaterialTheme.typography.labelSmall, color = if (t <= tier) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Top row: the pass's premium track", style = MaterialTheme.typography.labelSmall, color = com.deepline.fishing.ui.rewardTextColor(), modifier = Modifier.weight(1f))
                if (open.isNotEmpty()) {
                    Button(onClick = vm::claimSeasonRewards, modifier = Modifier.testTag("claim_season")) { Text("Claim ${open.size}") }
                }
            }
        }
    }
}

@Composable
private fun TierCell(vm: GameViewModel, reward: SeasonReward, reached: Boolean, unlocked: Boolean) {
    val claimed = reward.key in vm.progress.seasonClaims && vm.progress.seasonNumber == Seasons.at(ShopClock.now()).number
    val bg = when {
        reward.premium && !unlocked -> Color(0xFF2B2440)
        reward.premium -> Color(0xFF3B2F12)
        else -> Color(0xFF12304A)
    }
    Box(
        Modifier
            .width(86.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                claimed -> "✓ " + ShopText.seasonGrants(reward.grants, vm.progress).asString()
                else -> ShopText.seasonGrants(reward.grants, vm.progress).asString()
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !reached || (reward.premium && !unlocked) -> Color.White.copy(alpha = 0.55f)
                reward.premium -> Color(0xFFFFE082)
                else -> Color.White
            },
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** This weekend's event: its goal and progress, and the reward when reached (or when the next one starts). */
@Composable
fun EventCard(vm: GameViewModel, modifier: Modifier = Modifier) {
    val now = ShopClock.now()
    val event = Seasons.eventAt(now)
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (event == null) {
                Text("Weekend events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Every weekend brings a fishing goal with a reward. The next one starts in " +
                        "${ShopText.timeLeftLabel(Seasons.nextEventStart(now) - now)}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }
            val p = Seasons.currentEvent(vm.progress, now)
            val pass = Seasons.hasEventPass(p, event)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weekend event: ${event.goal.label()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${p.eventProgress} / ${event.goal.target}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            }
            LinearProgressIndicator(progress = { p.eventProgress / event.goal.target.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(
                "Ends in ${ShopText.timeLeftLabel(event.msLeft(now))}. Reward: " + ShopText.seasonGrants(Seasons.eventFree, p).asString() +
                    if (pass) "; with your event pass (progress counts double): " + ShopText.seasonGrants(Seasons.eventPass, p).asString() + "." else ".",
                style = MaterialTheme.typography.bodySmall,
            )
            if (Seasons.eventClaimable(p, now).isNotEmpty()) {
                Button(onClick = vm::claimEventReward, modifier = Modifier.testTag("claim_event")) { Text("Claim the reward") }
            }
        }
    }
}
