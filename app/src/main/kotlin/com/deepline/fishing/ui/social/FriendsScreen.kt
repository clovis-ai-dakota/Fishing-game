package com.deepline.fishing.ui.social

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
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
    SubScreen("Friends", onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.refreshFriends() }
            SocialCard("Add a friend", Modifier.tourTarget(TourTarget.FRIENDS_ADD)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(PlayerId.ID_LENGTH + 4) },
                        label = { Text("Their player id") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f).testTag("friend_id"),
                    )
                    Button(
                        onClick = {
                            social.sendFriendRequest(typed)
                            typed = ""
                        },
                        enabled = typed.isNotBlank() && !social.busy,
                        modifier = Modifier.testTag("friend_send"),
                    ) { Text("Send") }
                }
                Hint(
                    "They'll see your request next time they open Friends, and you'll be friends once they accept. " +
                        "Your own id is ${vm.playerIdentity.id}.",
                )
            }

            if (f != null && f.challenges.isNotEmpty()) {
                SocialCard("Challenges", highlighted = true) {
                    for (c in f.challenges) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${c.fromName} challenges you to ${c.mode.title}.", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "The room closes in ${timeLeft(c.expiresAtMs, ShopClock.now())}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { vm.acceptChallenge(c) }, modifier = Modifier.testTag("challenge_join_${c.code}")) { Text("Join") }
                        }
                    }
                }
            }

            if (f != null && (f.incoming.isNotEmpty() || f.outgoing.isNotEmpty())) {
                SocialCard("Requests") {
                    for (card in f.incoming) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                PlayerName(card.name, card.title, card.titleFrame)
                                Hint("Wants to be your friend (${card.id}).")
                            }
                            Button(onClick = { social.accept(card) }, modifier = Modifier.testTag("friend_accept_${card.id}")) { Text("Accept") }
                            TextButton(onClick = { social.decline(card) }, modifier = Modifier.testTag("friend_decline_${card.id}")) { Text("Decline") }
                        }
                    }
                    for (card in f.outgoing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Hint("Waiting for ${card.id} to accept.", Modifier.weight(1f))
                            TextButton(onClick = { social.decline(card) }, modifier = Modifier.testTag("friend_cancel_${card.id}")) { Text("Cancel") }
                        }
                    }
                }
            }

            SocialCard(if (f == null || f.friends.isEmpty()) "Your friends" else "Your friends (${f.friends.size} of ${GameConfig.MAX_FRIENDS})") {
                when {
                    f == null -> Hint("Your friends appear here once the server answers.")
                    f.friends.isEmpty() -> Hint("No friends yet. Share your player id, or ask for theirs.")
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
            title = { Text("Challenge ${friend.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Pick a mode: a battle room opens and ${friend.name} gets your challenge for " +
                            "${GameConfig.CHALLENGE_MINUTES} minutes. Both of you use the standard tournament rope.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    for (mode in BattleMode.entries) {
                        TextButton(
                            onClick = {
                                challenging = null
                                vm.challengeFriend(friend, mode)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("challenge_mode_${mode.name.lowercase()}"),
                        ) { Text(mode.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { challenging = null }) { Text("Cancel") } },
        )
    }
    removing?.let { friend ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${friend.name}?") },
            text = { Text("You'll no longer be friends. They aren't told, and you can add each other again later.") },
            confirmButton = {
                TextButton(onClick = { removing = null; vm.social.remove(friend) }, modifier = Modifier.testTag("friend_remove_confirm")) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
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
        val week = listOfNotNull(
            friend.weekBestCast?.let { "best cast ${formatPoints(it)} pts" },
            friend.weekDeepestM?.let { "deepest catch ${it.toInt()} m" },
            friend.weekTournament?.let { "tournament ${formatPoints(it)} pts" },
        )
        Hint(if (week.isEmpty()) "No scores this week yet." else "This week: " + week.joinToString(", ") + ".")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onChallenge, modifier = Modifier.testTag("friend_challenge_${friend.id}")) { Text("Challenge") }
            OutlinedButton(onClick = onRemove, modifier = Modifier.testTag("friend_remove_${friend.id}")) { Text("Remove") }
        }
    }
}
