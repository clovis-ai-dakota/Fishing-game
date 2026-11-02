package com.deepline.fishing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import kotlin.math.roundToInt

@Composable
fun BattleScreen(vm: GameViewModel) {
    val b = vm.battle ?: return
    // Battle objects are not observable; reading the version makes this screen recompose.
    @Suppress("UNUSED_VARIABLE")
    val version = vm.battleVersion
    val outcome = b.outcome()
    val next = b.nextTurn()
    var confirmForfeit by remember { mutableStateOf(false) }

    BackHandler {
        if (outcome != null) vm.leaveBattle() else confirmForfeit = true
    }

    SubScreen(
        title = b.mode.title,
        onBack = { if (outcome != null) vm.leaveBattle() else confirmForfeit = true },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(b.mode.summary, style = MaterialTheme.typography.bodyMedium) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0..1) {
                        PlayerCard(b, i, highlighted = next?.playerIndex == i, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (b.mode == BattleMode.BEST_OF_FIVE && b.rounds.isNotEmpty()) {
                item { Text("Rounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(b.rounds) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Round ${r.number}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${formatPoints(r.points[0])}  vs  ${formatPoints(r.points[1])}")
                            Text(
                                r.winner?.let { "  → ${b.players[it].name}" } ?: "  → tie",
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            item {
                if (outcome != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val winner = outcome.winner
                            Text(
                                when {
                                    winner == null -> "It's a draw!"
                                    else -> "${b.players[winner].name} wins!"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                            )
                            if (outcome.byForfeit) {
                                Text("by forfeit", style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = vm::rematch, modifier = Modifier.fillMaxWidth()) { Text("Rematch") }
                            OutlinedButton(onClick = vm::leaveBattle, modifier = Modifier.fillMaxWidth()) {
                                Text("Back to menu")
                            }
                        }
                    }
                } else if (next != null) {
                    val player = b.players[next.playerIndex]
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val what = buildString {
                            append("Up next: ${player.name}")
                            val timeLeft = next.timeLimitSec
                            val perPlayer = b.mode.castsPerPlayer
                            when {
                                next.round > 0 -> append(" · round ${next.round}")
                                timeLeft != null -> append(" · ${formatClock(timeLeft)} left")
                                perPlayer != null -> append(" · cast ${next.castNumber}/$perPlayer")
                            }
                        }
                        Text(what, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!player.isBot && b.players.none { it.isBot }) {
                            Text(
                                "Hand the phone to ${player.name}.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = vm::playNextTurn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                if (player.isBot) "Let ${player.name} fish" else "Cast, ${player.name}!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(onClick = { confirmForfeit = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Forfeit")
                        }
                    }
                }
            }
        }
    }

    if (confirmForfeit) {
        AlertDialog(
            onDismissRequest = { confirmForfeit = false },
            title = { Text("Forfeit the battle?") },
            text = { Text("Your opponent will be declared the winner.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForfeit = false
                    vm.forfeitBattle(next?.playerIndex ?: 0)
                }) { Text("Forfeit") }
            },
            dismissButton = { TextButton(onClick = { confirmForfeit = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun PlayerCard(b: Battle, index: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    val player = b.players[index]
    val score = b.score(index)
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (highlighted) "▶ " else "") + player.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                score.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            b.mode.timeLimitSec?.let { limit ->
                val left = (limit - b.timeUsed(index)).coerceAtLeast(0f)
                Text("Time left ${formatClock(left)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Total ${formatPoints(b.totalPoints(index))} pts", style = MaterialTheme.typography.bodySmall)
            b.castsOf(index).forEachIndexed { i, c -> Text(castLine(i, c), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun castLine(i: Int, c: BattleCast): String {
    val deepest = c.result.catches.maxOfOrNull { it.depth }?.roundToInt()
    return "#${i + 1}: ${formatPoints(c.points)} pts · ${c.result.catches.size} fish · %.1f kg".format(c.result.totalWeightKg) +
        (deepest?.let { " · ${it} m" } ?: "")
}

fun formatClock(seconds: Float): String {
    val s = seconds.coerceAtLeast(0f).roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}
