package com.deepline.fishing.ui

import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.rules
import com.deepline.fishing.ui.i18n.label
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.deepline.fishing.ShopPage
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
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

private const val FRIEND = "FRIEND" // i18n-ignore: a saved key, not shown
private const val ONLINE = "ONLINE" // i18n-ignore: a saved key, not shown

/**
 * Switches for the tackle allowed in battles (Lantern, rare fish detector), with what's left. Also
 * on the battle screen before each cast, and on the Fish tab.
 */
@Composable
fun SightTackleChips(vm: GameViewModel, showShopLink: Boolean = false) {
    val p = vm.progress
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = vm.useLantern && p.lanterns > 0,
            onClick = { vm.toggleLantern(!vm.useLantern) },
            enabled = p.lanterns > 0,
            label = { Text(stringResource(R.string.battle_tackle_lantern, p.lanterns)) },
            modifier = Modifier.testTag("battle_lantern"),
        )
        FilterChip(
            selected = vm.useDetector && p.detectors > 0,
            onClick = { vm.toggleDetector(!vm.useDetector) },
            enabled = p.detectors > 0,
            label = { Text(stringResource(R.string.battle_tackle_detector, p.detectors)) },
            modifier = Modifier.testTag("battle_detector"),
        )
        if (showShopLink) TextButton(onClick = { vm.openShop(ShopPage.TACKLE) }) { Text(stringResource(R.string.battle_tackle_get_more)) }
    }
}

@Composable
fun BattleSetupScreen(vm: GameViewModel) {
    var mode by rememberSaveable { mutableStateOf(BattleMode.BEST_OF_FIVE) }
    // A BotDifficulty name, FRIEND (pass and play) or ONLINE.
    var opponentKey by rememberSaveable { mutableStateOf(BotDifficulty.NORMAL.name) }
    var stake by rememberSaveable { mutableStateOf(0L) }
    val p = vm.progress

    TabScreen(title = stringResource(R.string.battle_setup_title)) {
        // A plain scrolling column (not a lazy list) so every section exists for the tour to scroll to.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Friends, the weekly leaderboards and tournament (and clubs) open from here.
            com.deepline.fishing.ui.social.SocialEntryCard(vm)
            Column(Modifier.tourTarget(TourTarget.BATTLE_MODES), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.battle_setup_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                BattleMode.entries.forEach { m ->
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
                                Text(m.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(m.rules(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Column(Modifier.tourTarget(TourTarget.BATTLE_OPPONENT), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.battle_setup_opponent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.battle_setup_computer), style = MaterialTheme.typography.bodyMedium)
                    for (d in BotDifficulty.entries) {
                        FilterChip(
                            selected = opponentKey == d.name,
                            onClick = { opponentKey = d.name },
                            label = { Text(d.label()) },
                        )
                    }
                }
                FilterChip(
                    selected = opponentKey == FRIEND,
                    onClick = { opponentKey = FRIEND },
                    label = { Text(stringResource(R.string.battle_setup_friend)) },
                )
                FilterChip(
                    selected = opponentKey == ONLINE,
                    onClick = { opponentKey = ONLINE },
                    label = { Text(stringResource(R.string.battle_setup_online)) },
                )
            }

            Text(
                if (opponentKey == ONLINE) {
                    stringResource(R.string.battle_setup_info_online, OnlineProtocol.TOURNAMENT_ROPE_LEVEL)
                } else {
                    stringResource(R.string.battle_setup_info_local)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Column(Modifier.tourTarget(TourTarget.BATTLE_TACKLE), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.battle_setup_tackle_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.battle_setup_tackle_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SightTackleChips(vm, showShopLink = true)
            }
            // Wagers: only against the computer.
            val rival = BotDifficulty.entries.firstOrNull { it.name == opponentKey }
            if (rival != null) {
                Column(Modifier.tourTarget(TourTarget.BATTLE_WAGER), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.battle_setup_wager_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = stake == 0L, onClick = { stake = 0L }, label = { Text(stringResource(R.string.battle_setup_wager_none)) })
                        for (s in GameConfig.WAGER_STAKES) {
                            FilterChip(
                                selected = stake == s,
                                onClick = { stake = s },
                                enabled = p.pointsBalance >= s,
                                label = { Text(compactPoints(s)) },
                            )
                        }
                    }
                    val payout = GameConfig.wagerPayout(rival)
                    val multiplier = formatDecimal(payout.toDouble(), 2)
                    Text(
                        if (stake > 0) {
                            stringResource(R.string.battle_setup_wager_terms, formatPoints((stake * payout).toLong()), multiplier, rival.label())
                        } else {
                            stringResource(R.string.battle_setup_wager_hint, rival.label(), multiplier)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.battle_setup_record, p.battleWins, p.battleLosses, p.battleDraws),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Button(
                onClick = {
                    val opponent = when (opponentKey) {
                        FRIEND -> Opponent.Friend
                        ONLINE -> Opponent.Online
                        else -> Opponent.Computer(BotDifficulty.valueOf(opponentKey))
                    }
                    vm.startBattle(mode, opponent, if (opponent is Opponent.Computer && p.pointsBalance >= stake) stake else 0L)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    stringResource(if (opponentKey == ONLINE) R.string.battle_setup_go_online else R.string.battle_setup_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
