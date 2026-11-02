package com.deepline.fishing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Opponent
import com.deepline.fishing.Screen
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty

@Composable
fun BattleSetupScreen(vm: GameViewModel) {
    var mode by rememberSaveable { mutableStateOf(BattleMode.BEST_OF_FIVE) }
    // null = friend on this device, otherwise the computer's difficulty.
    var difficulty by rememberSaveable { mutableStateOf<BotDifficulty?>(BotDifficulty.NORMAL) }
    val p = vm.progress

    SubScreen(title = "1v1 Battle", onBack = { vm.navigate(Screen.HOME) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(BattleMode.entries) { m ->
                val selected = m == mode
                Card(
                    onClick = { mode = m },
                    modifier = Modifier.fillMaxWidth(),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { mode = m })
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(m.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text(
                    "Opponent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Computer:", style = MaterialTheme.typography.bodyMedium)
                    for (d in BotDifficulty.entries) {
                        FilterChip(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            label = { Text(d.displayName) },
                        )
                    }
                }
            }
            item {
                FilterChip(
                    selected = difficulty == null,
                    onClick = { difficulty = null },
                    label = { Text("Friend on this device (pass and play)") },
                )
            }
            item {
                Text(
                    "Both players use your current rope and fish the very same water for each cast, " +
                        "so skill decides. No lanterns in battles. Battle catches don't count for your " +
                        "points or fish log.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    "Record vs computer: ${p.battleWins} W · ${p.battleLosses} L · ${p.battleDraws} D",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                Button(
                    onClick = {
                        val opponent = difficulty?.let { Opponent.Computer(it) } ?: Opponent.Friend
                        vm.startBattle(mode, opponent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Start battle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
