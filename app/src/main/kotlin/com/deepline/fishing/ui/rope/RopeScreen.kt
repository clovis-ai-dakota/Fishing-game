package com.deepline.fishing.ui.rope

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.compactPoints
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.formatStat
import com.deepline.fishing.ui.i18n.description
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.rewardBalance
import com.deepline.fishing.ui.shop.CoinPrice
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * The rope workshop: the rope's three upgrades, bought with points (never with euros), each one
 * level back for a full refund. Its numbers and what each stat does are behind the (i).
 */
@Composable
fun RopeScreen(vm: GameViewModel) {
    val p = vm.progress
    SubScreen(stringResource(R.string.shop_rope_title), onBack = vm::onBack) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("rope_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val balance = stringResource(R.string.shop_balance, formatPoints(p.pointsBalance))
                    com.deepline.fishing.ui.kit.ScenePill(Modifier.rewardBalance().semantics { contentDescription = balance }.testTag("rope_balance")) {
                        com.deepline.fishing.ui.kit.CoinAmount(formatPoints(p.pointsBalance), iconSize = 18.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    com.deepline.fishing.ui.kit.ScenePill {
                        com.deepline.fishing.ui.kit.InfoButton(
                            stringResource(R.string.shop_rope_title),
                            stringResource(R.string.shop_rope_note),
                            Modifier.size(22.dp),
                            tint = com.deepline.fishing.ui.kit.GameColors.OnDeep,
                            tag = "rope_info_note",
                        )
                    }
                }
            }
            items(RopeStat.entries, key = { it.name }) { stat ->
                val tagged = if (stat == RopeStat.entries.first()) Modifier.tourTarget(TourTarget.ROPE_FIRST) else Modifier
                RopeCard(vm, stat, tagged)
            }
        }
    }
}

@Composable
private fun RopeCard(vm: GameViewModel, stat: RopeStat, modifier: Modifier) {
    val p = vm.progress
    val level = p.level(stat)
    val cost = Progression.upgradeCost(p, stat)
    val current = formatStat(stat, stat.valueAt(level))
    Card(Modifier.fillMaxWidth().then(modifier)) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(stat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stat.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val levelText = stringResource(R.string.shop_rope_level, level, stat.maxLevel)
                    com.deepline.fishing.ui.kit.Pips(level, stat.maxLevel, Modifier.semantics { contentDescription = levelText })
                }
                com.deepline.fishing.ui.kit.InfoButton(stat.label(), stat.description())
            }
            Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cost == null) {
                    Text(stringResource(R.string.shop_rope_maxed, current), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                } else {
                    Text("$current → ${formatStat(stat, stat.valueAt(level + 1))}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                // One level back, fully refunded: the arrow, where it goes back to and what comes back.
                Progression.downgradeRefund(p, stat)?.let { refund ->
                    val label = stringResource(R.string.shop_rope_downgrade, formatStat(stat, stat.valueAt(level - 1)), formatPoints(refund))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.downgrade(stat) }
                            .semantics { contentDescription = label }
                            .testTag("downgrade_${stat.name.lowercase()}")
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(com.deepline.fishing.ui.kit.KitIcons.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        com.deepline.fishing.ui.kit.CoinAmount("+" + compactPoints(refund.toLong()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (cost != null) {
                    val price = stringResource(R.string.common_points, formatPoints(cost))
                    GameButton(
                        onClick = { vm.buyUpgrade(stat) },
                        modifier = Modifier.testTag("upgrade_${stat.name.lowercase()}").semantics { contentDescription = price },
                        role = ActionRole.POINTS,
                        enabled = p.pointsBalance >= cost,
                    ) { CoinPrice(formatPoints(cost)) }
                }
            }
        }
    }
}

private val RopeStat.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        RopeStat.LENGTH -> com.deepline.fishing.ui.kit.KitIcons.Depth
        RopeStat.STRENGTH -> com.deepline.fishing.ui.kit.KitIcons.Weight
        RopeStat.AGILITY -> com.deepline.fishing.ui.kit.KitIcons.Speed
    }

