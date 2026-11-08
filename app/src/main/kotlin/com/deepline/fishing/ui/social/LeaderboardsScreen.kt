package com.deepline.fishing.ui.social

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.BoardEntry
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.Leaderboards
import com.deepline.fishing.core.social.Weeks
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.description
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.pointsLabel
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** How a board's number reads: points, a depth, or a species with its rarity. */
@Composable
fun boardValue(kind: BoardKind, value: Long, speciesId: String?): String {
    val species = speciesId?.let { FishCatalog.byId(it) }
    return when (kind) {
        BoardKind.BEST_CAST -> pointsLabel(value)
        BoardKind.DEEPEST -> {
            val depth = stringResource(R.string.common_meters, formatPoints(Leaderboards.depthOf(value).toInt()))
            if (species != null) stringResource(R.string.social_leaderboards_deepest_value, depth, species.label()) else depth
        }
        // The stars are the species' rarity, as on its Dex card.
        BoardKind.RAREST -> species?.let { "${it.label()} " + "★".repeat(Progression.rarityStars(it)) } ?: "?"
    }
}

/** The weekly leaderboards: three boards, each for everyone, the player's league, or friends. */
@Composable
fun LeaderboardsScreen(vm: GameViewModel) {
    val social = vm.social
    val now = ShopClock.now()
    SubScreen(stringResource(R.string.social_leaderboards_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.tourTarget(TourTarget.BOARD_KINDS), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (kind in BoardKind.entries) {
                        FilterChip(
                            selected = social.boardKind == kind,
                            onClick = { social.selectBoard(kind) },
                            label = { Text(kind.label()) },
                            modifier = Modifier.testTag("board_${kind.name.lowercase()}"),
                        )
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (view in BoardView.entries) {
                        FilterChip(
                            selected = social.boardView == view,
                            onClick = { social.selectView(view) },
                            label = { Text(view.label()) },
                            modifier = Modifier.testTag("view_${view.name.lowercase()}"),
                        )
                    }
                }
            }
            Hint(
                stringResource(
                    R.string.social_leaderboards_about,
                    social.boardKind.description(),
                    timeLeftLabel(Weeks.endOf(Weeks.indexAt(now)), now),
                    OnlineProtocol.TOURNAMENT_ROPE_LEVEL,
                ),
            )
            SocialStatus(vm) { social.refreshBoard() }

            val board = social.board?.takeIf { it.board == social.boardKind && it.view == social.boardView }
            val me = board?.me
            if (me != null) {
                SocialCard(stringResource(R.string.social_leaderboards_your_rank), Modifier.testTag("board_me"), highlighted = true) {
                    BoardRow(social.boardKind, me)
                    Hint(pluralStringResource(R.plurals.social_leaderboards_players, board.players, formatPoints(board.players)))
                }
            }
            SocialCard(
                when (social.boardView) {
                    BoardView.GLOBAL -> stringResource(R.string.social_leaderboards_top, GameConfig.LEADERBOARD_TOP)
                    BoardView.LEAGUE -> stringResource(R.string.social_leaderboards_league)
                    BoardView.FRIENDS -> stringResource(R.string.social_leaderboards_friends)
                },
            ) {
                when {
                    board == null -> Hint(stringResource(if (social.busy) R.string.social_loading else R.string.social_leaderboards_waiting))
                    board.entries.isEmpty() -> Hint(
                        when (social.boardView) {
                            BoardView.LEAGUE -> pluralStringResource(R.plurals.social_leaderboards_league_empty, GameConfig.LEAGUE_SIZE, GameConfig.LEAGUE_SIZE)
                            BoardView.FRIENDS -> stringResource(R.string.social_leaderboards_friends_empty)
                            BoardView.GLOBAL -> stringResource(R.string.social_leaderboards_global_empty)
                        },
                    )
                    else -> board.entries.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider()
                        BoardRow(social.boardKind, entry)
                    }
                }
                if (social.boardView == BoardView.GLOBAL) {
                    Hint(stringResource(R.string.social_leaderboards_strangers))
                }
            }
        }
    }
}

@Composable
fun BoardRow(kind: BoardKind, entry: BoardEntry) {
    Row(
        Modifier.fillMaxWidth().testTag("board_row_${entry.rank}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RankLabel(entry.rank)
        // Strangers' names are generated from their id and the same in every language (never translated).
        PlayerName(entry.name, entry.title, entry.titleFrame, Modifier.weight(1f), you = entry.you)
        Text(
            boardValue(kind, entry.value, entry.speciesId),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = LocalContentColor.current,
        )
    }
}
