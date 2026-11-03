package com.deepline.fishing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

fun formatStat(stat: RopeStat, value: Float): String = when (stat) {
    RopeStat.LENGTH -> "%.0f m".format(value)
    RopeStat.STRENGTH -> "%.0f kg".format(value)
    RopeStat.AGILITY -> "%.1f m/s".format(value)
}

@Composable
fun UpgradesScreen(vm: GameViewModel) {
    val p = vm.progress
    SubScreen(title = "Rope upgrades", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Balance: ${formatPoints(p.pointsBalance)} pts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(RopeStat.entries) { stat ->
                val level = p.level(stat)
                val cost = Progression.upgradeCost(p, stat)
                val tagged = if (stat == RopeStat.entries.first()) Modifier.tourTarget(TourTarget.UPGRADES_ROPE) else Modifier
                Card(Modifier.fillMaxWidth().then(tagged)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stat.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("Lv $level / ${stat.maxLevel}", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(stat.description, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { level.toFloat() / stat.maxLevel },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val current = formatStat(stat, stat.valueAt(level))
                        Progression.downgradeRefund(p, stat)?.let { refund ->
                            // Stepping down refunds exactly what the level cost.
                            TextButton(onClick = { vm.downgrade(stat) }) {
                                Text("Downgrade to ${formatStat(stat, stat.valueAt(level - 1))} · refund ${formatPoints(refund)} pts")
                            }
                        }
                        if (cost == null) {
                            Text("$current · maxed out", fontWeight = FontWeight.SemiBold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$current → ${formatStat(stat, stat.valueAt(level + 1))}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = { vm.buyUpgrade(stat) },
                                    enabled = p.pointsBalance >= cost,
                                ) { Text("${formatPoints(cost)} pts") }
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lantern", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Lights up more of the dark water below ${GameConfig.DARK_DEPTH.toInt()} m for one cast. " +
                                    "You have ${p.lanterns}.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = vm::buyLantern,
                            enabled = p.pointsBalance >= GameConfig.LANTERN_PRICE,
                        ) { Text("${formatPoints(GameConfig.LANTERN_PRICE)} pts") }
                    }
                }
            }
        }
    }
}
