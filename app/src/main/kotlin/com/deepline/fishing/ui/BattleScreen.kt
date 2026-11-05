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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
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
    // Battle objects are not observable: reading the version makes this screen recompose, and
    // everything shown is copied into plain data so the children recompose when it changes.
    val version = vm.battleVersion
    val online = vm.onlineMatch?.takeIf { vm.isOnlineBattle }
    // Titles (a legendary cosmetic): online players send theirs; locally, yours is on player 1.
    val titles = online?.titles ?: listOf(vm.title, null)
    val frames = online?.frames ?: listOf(vm.titleFrame.name, null)
    val view = remember(b, version, titles, frames) { BattleView.of(b, titles, frames) }
    val outcome = if (online != null) online.outcome else b.outcome()
    val next = vm.battleNextTurn()
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
                        PlayerCard(
                            view.players[i],
                            highlighted = next?.playerIndex == i,
                            isMe = online?.myIndex == i,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (b.mode == BattleMode.BEST_OF_FIVE && view.rounds.isNotEmpty()) {
                item { Text("Rounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(view.rounds) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Round ${r.number}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${formatPoints(r.points[0])}  vs  ${formatPoints(r.points[1])}")
                            Text(
                                r.winnerName?.let { "  → $it" } ?: "  → tie",
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
                                Text(online?.overReason ?: "by forfeit", style = MaterialTheme.typography.bodyMedium)
                            }
                            vm.lastWagerResult?.let { w ->
                                Text(
                                    when (w.won) {
                                        true -> "Wager won: +${formatPoints(w.payout)} pts"
                                        null -> "Wager refunded: ${formatPoints(w.stake)} pts"
                                        false -> "Wager lost: ${formatPoints(w.stake)} pts"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Button(onClick = vm::rematch, modifier = Modifier.fillMaxWidth()) {
                                Text(if (online != null) "New online battle" else "Rematch")
                            }
                            // A good game? Send the opponent a gift (looks or tackle).
                            val opponentId = online?.playerIds?.getOrNull(online.opponentIndex)
                            if (online != null && opponentId != null) {
                                val name = b.players[online.opponentIndex].name
                                OutlinedButton(
                                    onClick = { vm.openGifts(GameViewModel.GiftTarget(opponentId, name)) },
                                    modifier = Modifier.fillMaxWidth().testTag("send_gift"),
                                ) { Text("Send $name a gift") }
                            }
                            OutlinedButton(onClick = vm::leaveBattle, modifier = Modifier.fillMaxWidth()) {
                                Text("Back to menu")
                            }
                        }
                    }
                } else if (online != null && next == null) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (online.awaitingConfirmation != null) "Sending your cast…"
                            else "Waiting for ${b.players[online.opponentIndex].name}…",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        opponentLiveText(vm)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = { confirmForfeit = true }) { Text("Forfeit") }
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
                        if (online != null) {
                            opponentLiveText(vm)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        } else if (!player.isBot && b.players.none { it.isBot }) {
                            Text(
                                "Hand the phone to ${player.name}.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        // Sight tackle can be switched on before each of the device owner's casts.
                        if (!player.isBot) SightTackleChips(vm)
                        Button(
                            onClick = vm::playNextTurn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                when {
                                    player.isBot -> "Let ${player.name} fish"
                                    online != null -> "Cast!"
                                    else -> "Cast, ${player.name}!"
                                },
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

/** Plain snapshot of a battle for display. */
private data class PlayerView(
    val name: String,
    val title: String?,
    val frame: com.deepline.fishing.core.TitleFrame,
    val scoreLabel: String,
    val totalPoints: Int,
    val timeLeft: Float?,
    val castLines: List<String>,
)

private data class RoundView(val number: Int, val points: List<Int>, val winnerName: String?)

private data class BattleView(val players: List<PlayerView>, val rounds: List<RoundView>) {
    companion object {
        fun of(b: Battle, titles: List<String?>, frames: List<String?>) = BattleView(
            players = (0..1).map { i ->
                PlayerView(
                    name = b.players[i].name,
                    title = titles.getOrNull(i),
                    frame = com.deepline.fishing.core.TitleFrame.of(frames.getOrNull(i)),
                    scoreLabel = b.score(i).label,
                    totalPoints = b.totalPoints(i),
                    timeLeft = b.mode.timeLimitSec?.let { (it - b.timeUsed(i)).coerceAtLeast(0f) },
                    castLines = b.castsOf(i).mapIndexed(::castLine),
                )
            },
            rounds = b.rounds.map { r -> RoundView(r.number, r.points, r.winner?.let { b.players[it].name }) },
        )
    }
}

@Composable
private fun PlayerCard(player: PlayerView, highlighted: Boolean, isMe: Boolean, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (highlighted) "▶ " else "") + player.name + (if (isMe) " (you)" else ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            player.title?.let { TitleBadge(it, frame = player.frame) }
            Text(
                player.scoreLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            player.timeLeft?.let {
                Text("Time left ${formatClock(it)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Total ${formatPoints(player.totalPoints)} pts", style = MaterialTheme.typography.bodySmall)
            player.castLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun castLine(i: Int, c: BattleCast): String {
    val deepest = c.result.catches.maxOfOrNull { it.depth }?.roundToInt()
    return "#${i + 1}: ${formatPoints(c.points)} pts · ${c.result.catches.size} fish · %.1f kg".format(c.result.totalWeightKg) +
        (deepest?.let { " · ${it} m" } ?: "")
}

/** "Ana is fishing: cast 2 · 230 m ↑ · 3 fish · 450 pts", while the online opponent is mid-cast. */
fun opponentLiveText(vm: GameViewModel): String? {
    val match = vm.onlineMatch ?: return null
    val live = match.opponentLive ?: return null
    val name = match.battle.players[match.opponentIndex].name
    val arrow = if (live.ascending) "↑" else "↓"
    return "$name is fishing: cast ${live.castNumber} · ${live.depth.roundToInt()} m $arrow · " +
        "${live.fish} fish · ${formatPoints(live.points)} pts"
}

fun formatClock(seconds: Float): String {
    val s = seconds.coerceAtLeast(0f).roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}
