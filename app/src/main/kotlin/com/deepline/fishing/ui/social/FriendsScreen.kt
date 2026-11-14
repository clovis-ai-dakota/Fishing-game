package com.deepline.fishing.ui.social

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.ButtonSize
import com.deepline.fishing.ui.kit.CoinAmount
import com.deepline.fishing.ui.kit.GameButton
import com.deepline.fishing.ui.kit.KitIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.pointsLabel
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** Friends: add one by player id, answer requests, join challenges, see friends' week. */
@Composable
fun FriendsScreen(vm: GameViewModel) {
    val social = vm.social
    var typed by rememberSaveable { mutableStateOf("") }
    var challenging by remember { mutableStateOf<FriendDto?>(null) }
    var removing by remember { mutableStateOf<FriendDto?>(null) }
    val f = social.friends
    SubScreen(stringResource(R.string.social_friends_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.refreshFriends() }
            SocialCard(
                stringResource(R.string.social_friends_add_title),
                Modifier.tourTarget(TourTarget.FRIENDS_ADD),
                info = stringResource(R.string.social_friends_add_body, vm.playerIdentity.id),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(PlayerId.ID_LENGTH + 4) },
                        label = { Text(stringResource(R.string.social_friends_id_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f).testTag("friend_id"),
                    )
                    GameButton(
                        stringResource(R.string.social_friends_send),
                        onClick = {
                            social.sendFriendRequest(typed)
                            typed = ""
                        },
                        enabled = typed.isNotBlank() && !social.busy,
                        modifier = Modifier.testTag("friend_send"),
                        icon = KitIcons.Plus,
                    )
                }
            }

            if (f != null && f.challenges.isNotEmpty()) {
                SocialCard(stringResource(R.string.social_friends_challenges_title), highlighted = true) {
                    for (c in f.challenges) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.social_friends_challenged, c.fromName, c.mode.label()), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.social_friends_room_closes, timeLeftLabel(c.expiresAtMs, ShopClock.now())),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            GameButton(
                                stringResource(R.string.social_friends_join),
                                onClick = { vm.acceptChallenge(c) },
                                modifier = Modifier.testTag("challenge_join_${c.code}"),
                                role = ActionRole.PLAY,
                                icon = GameIcons.Battle,
                            )
                        }
                    }
                }
            }

            if (f != null && (f.incoming.isNotEmpty() || f.outgoing.isNotEmpty())) {
                SocialCard(stringResource(R.string.social_friends_requests_title)) {
                    for (card in f.incoming) {
                        // The answers under the name, so a long name or answer is never squeezed.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PlayerName(card.name, card.title, card.titleFrame)
                            Hint(stringResource(R.string.social_friends_request_in, card.id))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GameButton(
                                    stringResource(R.string.social_friends_accept),
                                    onClick = { social.accept(card) },
                                    modifier = Modifier.testTag("friend_accept_${card.id}"),
                                    role = ActionRole.CLAIM,
                                    size = ButtonSize.SMALL,
                                    icon = KitIcons.Check,
                                )
                                TextButton(onClick = { social.decline(card) }, modifier = Modifier.testTag("friend_decline_${card.id}")) {
                                    Text(stringResource(R.string.social_friends_decline))
                                }
                            }
                        }
                    }
                    for (card in f.outgoing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Hint(stringResource(R.string.social_friends_request_out, card.id), Modifier.weight(1f))
                            TextButton(onClick = { social.decline(card) }, modifier = Modifier.testTag("friend_cancel_${card.id}")) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    }
                }
            }

            SocialCard(
                if (f == null || f.friends.isEmpty()) {
                    stringResource(R.string.social_friends_list_title)
                } else {
                    stringResource(R.string.social_friends_list_title_count, f.friends.size, GameConfig.MAX_FRIENDS)
                },
            ) {
                when {
                    f == null -> Hint(stringResource(R.string.social_friends_list_waiting))
                    f.friends.isEmpty() -> Hint(stringResource(R.string.social_friends_list_none))
                    else -> f.friends.forEachIndexed { i, friend ->
                        if (i > 0) HorizontalDivider()
                        FriendRow(friend, onChallenge = { challenging = friend }, onRemove = { removing = friend })
                    }
                }
            }
        }
    }

    challenging?.let { friend ->
        AlertDialog(
            onDismissRequest = { challenging = null },
            title = { Text(stringResource(R.string.social_friends_challenge_title, friend.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        pluralStringResource(R.plurals.social_friends_challenge_body, GameConfig.CHALLENGE_MINUTES, friend.name, GameConfig.CHALLENGE_MINUTES),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    for (mode in BattleMode.entries) {
                        TextButton(
                            onClick = {
                                challenging = null
                                vm.challengeFriend(friend, mode)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("challenge_mode_${mode.name.lowercase()}"),
                        ) { Text(mode.label()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { challenging = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    removing?.let { friend ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.social_friends_remove_title, friend.name)) },
            text = { Text(stringResource(R.string.social_friends_remove_body)) },
            confirmButton = {
                TextButton(onClick = { removing = null; vm.social.remove(friend) }, modifier = Modifier.testTag("friend_remove_confirm")) {
                    Text(stringResource(R.string.social_friends_remove))
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.social_friends_keep)) } },
        )
    }
}

@Composable
private fun FriendRow(friend: FriendDto, onChallenge: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("friend_${friend.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerName(friend.name, friend.title, friend.titleFrame, Modifier.weight(1f))
            OnlineLabel(friend.online)
        }
        // This week's bests as pictograms: best cast, deepest catch, tournament. The sentence is read aloud.
        val week = listOfNotNull(
            friend.weekBestCast?.let { stringResource(R.string.social_friends_week_best_cast, pointsLabel(it.toLong())) },
            friend.weekDeepestM?.let { stringResource(R.string.social_friends_week_deepest, stringResource(R.string.common_meters, formatPoints(it.toInt()))) },
            friend.weekTournament?.let { stringResource(R.string.social_friends_week_tournament, pointsLabel(it.toLong())) },
        )
        val sentence = if (week.isEmpty()) stringResource(R.string.social_friends_week_none) else stringResource(R.string.social_friends_week, week.joinToString(", "))
        Row(
            Modifier.semantics { contentDescription = sentence }.testTag("friend_week_${friend.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                friend.weekBestCast?.let { CoinAmount(formatPoints(it), color = ink, iconSize = 14.dp) }
                friend.weekDeepestM?.let { WeekFact(KitIcons.Depth, stringResource(R.string.common_meters, formatPoints(it.toInt()))) }
                friend.weekTournament?.let { WeekFact(GameIcons.Trophy, formatPoints(it)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(
                stringResource(R.string.social_friends_challenge),
                onClick = onChallenge,
                modifier = Modifier.testTag("friend_challenge_${friend.id}"),
                size = ButtonSize.SMALL,
                icon = GameIcons.Battle,
            )
            TextButton(onClick = onRemove, modifier = Modifier.testTag("friend_remove_${friend.id}")) {
                Text(stringResource(R.string.social_friends_remove))
            }
        }
    }
}

/** A number with its pictogram, in a friend's row. */
@Composable
private fun WeekFact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
