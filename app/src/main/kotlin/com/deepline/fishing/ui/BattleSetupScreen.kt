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
import com.deepline.fishing.ui.kit.pressTilt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Opponent
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.BattleMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.ButtonSize
import com.deepline.fishing.ui.kit.CoinAmount
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.InfoButton
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.SectionTitle
import com.deepline.fishing.ui.kit.TackleTile
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

private const val FRIEND = "FRIEND" // i18n-ignore: a saved key, not shown
private const val ONLINE = "ONLINE" // i18n-ignore: a saved key, not shown

/**
 * The tackle allowed in battles (Lantern, rare fish detector) as tiles: lit when in use, with
 * what's left; an empty tile leads to the Shop. Also on the battle screen before each cast.
 */
@Composable
fun SightTackleChips(vm: GameViewModel, showShopLink: Boolean = false) {
    val p = vm.progress
    val toShop: (() -> Unit)? = if (showShopLink) ({ vm.openShop(ShopPage.TACKLE) }) else null
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        TackleTile(
            GameIcons.Lantern, p.lanterns, vm.useLantern, vm::toggleLantern,
            stringResource(R.string.battle_tackle_lantern, p.lanterns),
            Modifier.testTag("battle_lantern"),
            onEmpty = toShop,
        )
        TackleTile(
            KitIcons.Radar, p.detectors, vm.useDetector, vm::toggleDetector,
            stringResource(R.string.battle_tackle_detector, p.detectors),
            Modifier.testTag("battle_detector"),
            onEmpty = toShop,
        )
    }
}

private val BattleMode.icon: ImageVector
    get() = when (this) {
        BattleMode.BEST_OF_FIVE -> GameIcons.Trophy
        BattleMode.TIME_ATTACK -> KitIcons.Clock
        BattleMode.HEAVY_HAUL -> KitIcons.Weight
        BattleMode.SPECIES_HUNT -> GameIcons.Fish
        BattleMode.DEEP_DIVE -> KitIcons.Depth
    }

/**
 * A choice among a few: a pictogram and a short name, lit when chosen. [info] is what the tile
 * no longer spells out (a mode's rules), behind its (i).
 */
@Composable
private fun ChoiceTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
    description: String? = null,
    vertical: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.pressTilt(sheen = false).semantics { this.selected = selected; if (description != null) contentDescription = description },
        border = if (selected) BorderStroke(2.5.dp, GameColors.of(ActionRole.PLAY, com.deepline.fishing.ui.theme.LocalMenuPalette.current).bottom) else null,
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.secondaryContainer else scheme.surface),
    ) {
        if (vertical) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                Text(label, style = MaterialTheme.typography.labelLarge.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            }
        } else {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                // Long names ("3-Minuten-Rausch") break at syllables rather than anywhere.
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph),
                    fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                )
                if (info != null) InfoButton(label, info) else Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/**
 * The Battle tab: a mode, an opponent, the tackle and (against the computer) a wager, each as
 * tiles with pictograms; the one main action, Start, stays in the dock at the bottom. Rules and
 * explanations are behind (i) buttons and in the tour.
 */
@Composable
fun BattleSetupScreen(vm: GameViewModel) {
    var mode by rememberSaveable { mutableStateOf(BattleMode.BEST_OF_FIVE) }
    // A BotDifficulty name, FRIEND (pass and play) or ONLINE.
    var opponentKey by rememberSaveable { mutableStateOf(BotDifficulty.NORMAL.name) }
    var stake by rememberSaveable { mutableStateOf(0L) }
    val p = vm.progress

    TabScreen(
        title = stringResource(R.string.battle_setup_title),
        actions = { com.deepline.fishing.ui.social.SocialEntryButton(vm, Modifier.padding(end = 8.dp)) },
    ) {
        // A plain scrolling column (not a lazy list) so every section exists for the tour to scroll to.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.tourTarget(TourTarget.BATTLE_MODES), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.battle_setup_mode), info = null)
                for (pair in BattleMode.entries.chunked(2)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in pair) {
                            ChoiceTile(m.icon, m.label(), m == mode, { mode = m }, Modifier.weight(1f).fillMaxHeight().testTag("mode_${m.name.lowercase()}"), info = m.rules())
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Column(Modifier.tourTarget(TourTarget.BATTLE_OPPONENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(
                    stringResource(R.string.battle_setup_opponent),
                    info = if (opponentKey == ONLINE) {
                        stringResource(R.string.battle_setup_info_online, OnlineProtocol.TOURNAMENT_ROPE_LEVEL)
                    } else {
                        stringResource(R.string.battle_setup_info_local)
                    },
                )
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in BotDifficulty.entries) {
                        ChoiceTile(
                            KitIcons.Robot, d.label(), opponentKey == d.name, { opponentKey = d.name },
                            Modifier.weight(1f).fillMaxHeight().testTag("opponent_${d.name.lowercase()}"), vertical = true,
                        )
                    }
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceTile(
                        KitIcons.Phone, stringResource(R.string.battle_setup_friend_short), opponentKey == FRIEND, { opponentKey = FRIEND },
                        Modifier.weight(1f).fillMaxHeight().testTag("opponent_friend"), description = stringResource(R.string.battle_setup_friend), vertical = true,
                    )
                    ChoiceTile(
                        KitIcons.Globe, stringResource(R.string.battle_setup_online_short), opponentKey == ONLINE, { opponentKey = ONLINE },
                        Modifier.weight(1f).fillMaxHeight().testTag("opponent_online"), description = stringResource(R.string.battle_setup_online), vertical = true,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.tourTarget(TourTarget.BATTLE_TACKLE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.battle_setup_tackle_title), info = stringResource(R.string.battle_setup_tackle_info), modifier = Modifier.width(IntrinsicSize.Max))
                    SightTackleChips(vm, showShopLink = true)
                }
            }
            // Wagers: only against the computer.
            val rival = BotDifficulty.entries.firstOrNull { it.name == opponentKey }
            if (rival != null) {
                val payout = GameConfig.wagerPayout(rival)
                val multiplier = formatDecimal(payout.toDouble(), 2)
                Column(Modifier.tourTarget(TourTarget.BATTLE_WAGER), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle(
                        stringResource(R.string.battle_setup_wager_title),
                        info = stringResource(R.string.battle_setup_wager_hint, rival.label(), multiplier),
                        trailing = {
                            // What a win brings back, once something is staked.
                            if (stake > 0) {
                                val terms = stringResource(R.string.battle_setup_wager_terms, formatPoints((stake * payout).toLong()), multiplier, rival.label())
                                CoinAmount("+" + formatPoints((stake * payout).toLong()), Modifier.semantics { contentDescription = terms }.testTag("wager_win"), color = rewardTextColor())
                            }
                        },
                    )
                    // Wraps rather than squeezing the last stake ("100 k") into two lines.
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = stake == 0L, onClick = { stake = 0L }, label = { Text(stringResource(R.string.battle_setup_wager_none)) })
                        for (s in GameConfig.WAGER_STAKES) {
                            FilterChip(
                                selected = stake == s,
                                onClick = { stake = s },
                                enabled = p.pointsBalance >= s,
                                label = { Text(compactPoints(s), maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.battle_setup_record, p.battleWins, p.battleLosses, p.battleDraws),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The one main action, always in reach.
        Box(Modifier.fillMaxWidth().dockBackground().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
            GameButton(
                onClick = {
                    val opponent = when (opponentKey) {
                        FRIEND -> Opponent.Friend
                        ONLINE -> Opponent.Online
                        else -> Opponent.Computer(BotDifficulty.valueOf(opponentKey))
                    }
                    vm.startBattle(mode, opponent, if (opponent is Opponent.Computer && p.pointsBalance >= stake) stake else 0L)
                },
                modifier = Modifier.fillMaxWidth().testTag("battle_start"),
                role = ActionRole.PLAY,
                size = ButtonSize.HERO,
            ) {
                Icon(GameIcons.Battle, contentDescription = null, modifier = Modifier.size(26.dp))
                Text(stringResource(if (opponentKey == ONLINE) R.string.battle_setup_go_online else R.string.battle_setup_start))
            }
        }
    }
}
