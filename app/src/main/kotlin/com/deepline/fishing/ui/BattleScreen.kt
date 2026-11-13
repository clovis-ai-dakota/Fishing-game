package com.deepline.fishing.ui

import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.rules
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.label
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
        title = b.mode.label(),
        onBack = { if (outcome != null) vm.leaveBattle() else confirmForfeit = true },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(b.mode.rules(), style = MaterialTheme.typography.bodyMedium) }

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
                item { Text(stringResource(R.string.battle_rounds_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(view.rounds) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.battle_round, r.number), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.battle_round_score, formatPoints(r.points[0]), formatPoints(r.points[1])))
                            Text(
                                r.winnerName?.let { stringResource(R.string.battle_round_winner, it) } ?: stringResource(R.string.battle_round_tie),
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
                            // Against the computer, the player is "You": "You win!", not "You wins!".
                            val youWon = winner != null && !b.players[winner].isBot && b.players.any { it.isBot }
                            Text(
                                when {
                                    winner == null -> stringResource(R.string.battle_outcome_draw)
                                    youWon -> stringResource(R.string.battle_outcome_you_win)
                                    else -> stringResource(R.string.battle_outcome_wins, b.players[winner].name)
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                            )
                            if (outcome.byForfeit) {
                                Text(
                                    online?.overReason?.let { OnlineText.overReason(it).asString() } ?: stringResource(R.string.battle_by_forfeit),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            vm.lastWagerResult?.let { w ->
                                Text(
                                    when (w.won) {
                                        true -> stringResource(R.string.battle_wager_won, stringResource(R.string.common_points_plus, formatPoints(w.payout)))
                                        null -> stringResource(R.string.battle_wager_refunded, pointsLabel(w.stake))
                                        false -> stringResource(R.string.battle_wager_lost, pointsLabel(w.stake))
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Button(onClick = vm::rematch, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (online != null) R.string.battle_new_online else R.string.battle_rematch))
                            }
                            // A good game? Send the opponent a gift (looks or tackle).
                            val opponentId = online?.playerIds?.getOrNull(online.opponentIndex)
                            if (online != null && opponentId != null) {
                                val name = b.players[online.opponentIndex].name
                                OutlinedButton(
                                    onClick = { vm.openGifts(GameViewModel.GiftTarget(opponentId, name)) },
                                    modifier = Modifier.fillMaxWidth().testTag("send_gift"),
                                ) { Text(stringResource(R.string.battle_send_gift, name)) }
                            }
                            OutlinedButton(onClick = vm::leaveBattle, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.battle_back_to_menu))
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
                            if (online.awaitingConfirmation != null) stringResource(R.string.battle_sending_cast)
                            else stringResource(R.string.battle_waiting_for, b.players[online.opponentIndex].name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        opponentLiveText(vm)?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = { confirmForfeit = true }) { Text(stringResource(R.string.battle_forfeit)) }
                    }
                } else if (next != null) {
                    val player = b.players[next.playerIndex]
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val timeLeft = next.timeLimitSec
                        val perPlayer = b.mode.castsPerPlayer
                        val what = when {
                            next.round > 0 -> stringResource(R.string.battle_up_next_round, player.name, next.round)
                            timeLeft != null -> stringResource(R.string.battle_up_next_time, player.name, formatClock(timeLeft))
                            perPlayer != null -> stringResource(R.string.battle_up_next_cast, player.name, next.castNumber, perPlayer)
                            else -> stringResource(R.string.battle_up_next, player.name)
                        }
                        Text(what, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (online != null) {
                            opponentLiveText(vm)?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall) }
                        } else if (!player.isBot && b.players.none { it.isBot }) {
                            Text(
                                stringResource(R.string.battle_hand_phone, player.name),
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
                                    player.isBot -> stringResource(R.string.battle_let_fish, player.name)
                                    online != null -> stringResource(R.string.battle_cast)
                                    else -> stringResource(R.string.battle_cast_player, player.name)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(onClick = { confirmForfeit = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.battle_forfeit))
                        }
                    }
                }
            }
        }
    }

    if (confirmForfeit) {
        AlertDialog(
            onDismissRequest = { confirmForfeit = false },
            title = { Text(stringResource(R.string.battle_forfeit_title)) },
            text = { Text(stringResource(R.string.battle_forfeit_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForfeit = false
                    vm.forfeitBattle(next?.playerIndex ?: 0)
                }) { Text(stringResource(R.string.battle_forfeit)) }
            },
            dismissButton = { TextButton(onClick = { confirmForfeit = false }) { Text(stringResource(R.string.battle_keep_playing)) } },
        )
    }
}

/** Plain snapshot of a battle for display. */
private data class PlayerView(
    val name: String,
    val title: String?,
    val frame: com.deepline.fishing.core.TitleFrame,
    val scoreLabel: com.deepline.fishing.ui.i18n.UiText,
    val totalPoints: Int,
    val timeLeft: Float?,
    val castLines: List<UiText>,
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
                    scoreLabel = b.score(i).labelText(b.mode),
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
            val name = if (isMe) stringResource(R.string.battle_player_me, player.name) else player.name
            Text(
                if (highlighted) "▶ $name" else name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            player.title?.let { TitleBadge(it, frame = player.frame) }
            Text(
                player.scoreLabel.asString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            player.timeLeft?.let {
                Text(stringResource(R.string.battle_time_left, formatClock(it)), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.battle_total, pointsLabel(player.totalPoints.toLong())), style = MaterialTheme.typography.bodySmall)
            player.castLines.forEach { Text(it.asString(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** "#2: 450 pts · 3 fish · 4.5 kg · 230 m": one cast on a player's card. */
private fun castLine(i: Int, c: BattleCast): UiText {
    val fish = c.result.catches.size
    val deepest = c.result.catches.maxOfOrNull { it.depth }?.roundToInt()
    val parts = listOfNotNull(
        pointsText(c.points.toLong()),
        UiText.plural(R.plurals.battle_fish, fish),
        castWeightText(c.result.totalWeightKg),
        deepest?.let { UiText.res(R.string.common_meters, UiText.number(it)) },
    )
    return UiText.res(R.string.battle_cast_line, i + 1, UiText.join(parts, ITEM_SEPARATOR))
}

/** "Ana is fishing: cast 2 · 230 m ↑ · 3 fish · 450 pts", while the online opponent is mid-cast. */
fun opponentLiveText(vm: GameViewModel): UiText? {
    val match = vm.onlineMatch ?: return null
    val live = match.opponentLive ?: return null
    val name = match.battle.players[match.opponentIndex].name
    val arrow = if (live.ascending) "↑" else "↓"
    val depth = UiText.res(R.string.common_meters, UiText.number(live.depth.roundToInt()))
    val details = listOf(
        UiText.res(R.string.battle_opponent_live_cast, live.castNumber),
        UiText.join(listOf(depth, UiText.raw(arrow)), " "),
        UiText.plural(R.plurals.battle_fish, live.fish),
        pointsText(live.points.toLong()),
    )
    return UiText.res(R.string.battle_opponent_live, name, UiText.join(details, ITEM_SEPARATOR))
}

/** "2:05": a battle clock (minutes and seconds). */
fun clockText(seconds: Float): UiText {
    val s = seconds.coerceAtLeast(0f).roundToInt()
    return UiText.res(R.string.battle_clock, s / 60, s % 60)
}

@Composable
@ReadOnlyComposable
fun formatClock(seconds: Float): String = clockText(seconds).asString()
