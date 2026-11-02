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
import com.deepline.fishing.core.online.OnlineProtocol

private const val FRIEND = "FRIEND"
private const val ONLINE = "ONLINE"

@Composable
fun BattleSetupScreen(vm: GameViewModel) {
    var mode by rememberSaveable { mutableStateOf(BattleMode.BEST_OF_FIVE) }
    // A BotDifficulty name, FRIEND (pass and play) or ONLINE.
    var opponentKey by rememberSaveable { mutableStateOf(BotDifficulty.NORMAL.name) }
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
                            selected = opponentKey == d.name,
                            onClick = { opponentKey = d.name },
                            label = { Text(d.displayName) },
                        )
                    }
                }
            }
            item {
                FilterChip(
                    selected = opponentKey == FRIEND,
                    onClick = { opponentKey = FRIEND },
                    label = { Text("Friend on this device (pass and play)") },
                )
            }
            item {
                FilterChip(
                    selected = opponentKey == ONLINE,
                    onClick = { opponentKey = ONLINE },
                    label = { Text("Online (room code or quick match)") },
                )
            }
            item {
                Text(
                    if (opponentKey == ONLINE) {
                        "Online, both players fish at the same time on their own phones, on the very same " +
                            "water, with the same standard tournament rope (level ${OnlineProtocol.TOURNAMENT_ROPE_LEVEL} " +
                            "in every stat)."
                    } else {
                        "Both players use your current rope and fish the very same water for each cast, " +
                            "so skill decides."
                    } + " No lanterns in battles. Battle catches don't count for your points or fish log.",
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
                        val opponent = when (opponentKey) {
                            FRIEND -> Opponent.Friend
                            ONLINE -> Opponent.Online
                            else -> Opponent.Computer(BotDifficulty.valueOf(opponentKey))
                        }
                        vm.startBattle(mode, opponent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(if (opponentKey == ONLINE) "Go online" else "Start battle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
