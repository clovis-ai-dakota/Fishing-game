package com.deepline.fishing.ui.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deepline.fishing.core.SeasonGrant
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.ActionRole
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.SeasonReward
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.ui.SparkleGold
import com.deepline.fishing.ui.formatPoints

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
                    Text(
                        stringResource(R.string.shop_season_title, season.number, season.label()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(if (pass) R.string.shop_season_ends_pass else R.string.shop_season_ends, ShopText.timeLeftLabel(season.msLeft(now))),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pass) com.deepline.fishing.ui.rewardTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.shop_season_tier, tier, GameConfig.SEASON_TIERS),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )                // How season XP is earned is behind the (i).
                com.deepline.fishing.ui.kit.InfoButton(
                    season.label(),
                    stringResource(
                        R.string.shop_season_xp,
                        GameConfig.SEASON_XP_PER_CAST,
                        GameConfig.SEASON_XP_PER_FISH,
                        GameConfig.SEASON_XP_PER_SECRET,
                        GameConfig.SEASON_XP_PER_TIER,
                    ),
                    tag = "season_info",
                )
            }
            LinearProgressIndicator(
                progress = { Seasons.xpIntoTier(p, now) / GameConfig.SEASON_XP_PER_TIER.toFloat() },
                modifier = Modifier.fillMaxWidth(),
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
                Text(
                    stringResource(R.string.shop_season_premium_row),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.deepline.fishing.ui.rewardTextColor(),
                    modifier = Modifier.weight(1f),
                )
                if (open.isNotEmpty()) {
                    com.deepline.fishing.ui.kit.GameButton(
                        stringResource(R.string.shop_season_claim, open.size),
                        onClick = vm::claimSeasonRewards,
                        modifier = Modifier.testTag("claim_season"),
                        role = com.deepline.fishing.ui.kit.ActionRole.CLAIM,
                        icon = com.deepline.fishing.ui.kit.KitIcons.Gift,
                    )
                }
            }
        }
    }
}

/**
 * One reward of the season track as pictograms (the coin and an amount, a tackle's icon and a
 * count, a boat, a star for a badge or a card style), ticked once claimed. Tapping it says what
 * it is in words.
 */
@Composable
private fun TierCell(vm: GameViewModel, reward: SeasonReward, reached: Boolean, unlocked: Boolean) {
    val claimed = reward.key in vm.progress.seasonClaims && vm.progress.seasonNumber == Seasons.at(ShopClock.now()).number
    val bg = when {
        reward.premium && !unlocked -> Color(0xFF2B2440)
        reward.premium -> Color(0xFF3B2F12)
        else -> Color(0xFF12304A)
    }
    val ink = when {
        !reached || (reward.premium && !unlocked) -> Color.White.copy(alpha = 0.55f)
        reward.premium -> Color(0xFFFFE082)
        else -> Color.White
    }
    val words = ShopText.seasonGrants(reward.grants, vm.progress).asString()
    var shown by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(72.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { shown = true }
            .semantics { contentDescription = words }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                    for (g in reward.grants) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            when (g) {
                                is SeasonGrant.Points -> {
                                    Icon(com.deepline.fishing.ui.GameIcons.Coin, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(com.deepline.fishing.ui.compactPoints(Seasons.pointsFor(vm.progress, g.minutes)), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                                is SeasonGrant.Tackle -> {
                                    Icon(g.tackle.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text(stringResource(R.string.common_times, g.count), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                                is SeasonGrant.Boat -> Icon(com.deepline.fishing.ui.kit.KitIcons.Boat, contentDescription = null, modifier = Modifier.size(22.dp))
                                is SeasonGrant.Unlock -> Icon(com.deepline.fishing.ui.GameIcons.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        if (claimed) {
            Icon(
                com.deepline.fishing.ui.kit.KitIcons.Check,
                contentDescription = null,
                tint = Color(0xFF7CFF9B),
                modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
            )
        }
    }
    if (shown) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { shown = false },
            text = { Text(words, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { shown = false }) { Text(stringResource(R.string.common_got_it)) } },
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
                Text(stringResource(R.string.shop_event_none_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.shop_event_none_body, ShopText.timeLeftLabel(Seasons.nextEventStart(now) - now)),
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }
            val p = Seasons.currentEvent(vm.progress, now)
            val pass = Seasons.hasEventPass(p, event)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shop_event_title, event.goal.label()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.common_progress_of, formatPoints(p.eventProgress), formatPoints(event.goal.target)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            LinearProgressIndicator(progress = { p.eventProgress / event.goal.target.toFloat() }, modifier = Modifier.fillMaxWidth())
            val ends = ShopText.timeLeftLabel(event.msLeft(now))
            val reward = ShopText.seasonGrants(Seasons.eventFree, p).asString()
            Text(
                if (pass) {
                    stringResource(R.string.shop_event_body_pass, ends, reward, ShopText.seasonGrants(Seasons.eventPass, p).asString())
                } else {
                    stringResource(R.string.shop_event_body, ends, reward)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (Seasons.eventClaimable(p, now).isNotEmpty()) {
                GameButton(stringResource(R.string.shop_event_claim), onClick = vm::claimEventReward, modifier = Modifier.testTag("claim_event"), role = ActionRole.CLAIM)
            }
        }
    }
}
