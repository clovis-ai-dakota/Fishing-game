package com.deepline.fishing.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.Weeks
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** On the Battle tab: the way to friends, the leaderboards, the tournament (and clubs). */
@Composable
fun SocialEntryCard(vm: GameViewModel) {
    val f = vm.social.friends
    val waiting = (f?.incoming?.size ?: 0) + (f?.challenges?.size ?: 0)
    SocialCard("Friends and rankings", Modifier.tourTarget(TourTarget.BATTLE_SOCIAL)) {
        Hint(
            if (FeatureFlags.clubs) {
                "Add friends, climb the weekly leaderboards, fish the weekly tournament and join a club."
            } else {
                "Add friends, climb the weekly leaderboards and fish the weekly tournament."
            },
        )
        BadgedBox(badge = { if (waiting > 0) Badge { Text("$waiting") } }) {
            FilledTonalButton(onClick = { vm.openSocial() }, modifier = Modifier.testTag("battle_social")) { Text("Open friends and rankings") }
        }
    }
}

/** The social hub: the player's id (their friend code), and a card for each social feature. */
@Composable
fun SocialHubScreen(vm: GameViewModel) {
    val social = vm.social
    val now = ShopClock.now()
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    SubScreen("Friends and rankings", onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.onOpen(Screen.SOCIAL) }
            SocialCard("Your player id", Modifier.tourTarget(TourTarget.SOCIAL_ID)) {
                PlayerIdRow(vm)
                Hint(
                    "This is your friend code: share it so friends can add you, and ask for theirs. Players can't be " +
                        "found by name. Friends see the name you use online, your title and whether you're online.",
                )
            }

            val f = social.friends
            SocialCard("Friends") {
                Hint(
                    when {
                        f == null -> "Friends you add appear here, with their best catches of the week."
                        f.friends.isEmpty() && f.incoming.isEmpty() -> "No friends yet. Add one with their player id."
                        else -> buildString {
                            append(if (f.friends.size == 1) "1 friend" else "${f.friends.size} friends")
                            if (f.incoming.isNotEmpty()) append(", ${f.incoming.size} waiting for your answer")
                            if (f.challenges.isNotEmpty()) append(", ${f.challenges.size} challenging you")
                            append(".")
                        }
                    },
                )
                Button(onClick = { vm.openSocial(Screen.FRIENDS) }, modifier = Modifier.testTag("social_friends")) { Text("Friends") }
            }

            val week = Weeks.indexAt(now)
            SocialCard("Weekly leaderboards") {
                Hint(
                    "Best cast, deepest catch and rarest catch of the week, for everyone, your league and your friends. " +
                        "They reset on Monday at 00:00 UTC, in ${timeLeft(Weeks.endOf(week), now)}.",
                )
                Button(onClick = { vm.openSocial(Screen.LEADERBOARDS) }, modifier = Modifier.testTag("social_leaderboards")) { Text("Leaderboards") }
            }

            val t = social.tournament
            SocialCard("Weekly tournament") {
                if (t != null) {
                    val left = t.attemptsMax - t.attemptsUsed
                    Text(
                        if (left == 1) "1 attempt left this week." else "$left attempts left this week.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    t.best?.let { Hint("Your best: ${formatPoints(it)} pts" + (t.rank?.let { r -> ", rank $r of ${t.participants}." } ?: ".")) }
                    if (t.rewards.isNotEmpty()) {
                        Text("A reward is waiting for you.", color = rewardTextColor(), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Hint("Everyone fishes the same water all week, with ${GameConfig.TOURNAMENT_ATTEMPTS} attempts each. Your best attempt counts.")
                }
                Button(onClick = { vm.openSocial(Screen.TOURNAMENT) }, modifier = Modifier.testTag("social_tournament")) { Text("Tournament") }
            }

            if (FeatureFlags.clubs) {
                val c = social.club?.club
                SocialCard("Clubs") {
                    Hint(
                        if (c != null) {
                            "You're in the ${c.name}: ${formatPoints(c.weekFish)} fish caught together this week."
                        } else {
                            "Join a club with its code, or start one, and fish for a weekly goal together."
                        },
                    )
                    Button(onClick = { vm.openSocial(Screen.CLUBS) }, modifier = Modifier.testTag("social_clubs")) { Text("Clubs") }
                }
            }

            TextButton(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth().testTag("social_forget")) {
                Text("Delete my social data from the server", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Delete your social data?") },
            text = {
                Text(
                    "This removes your friends (on both sides), friend requests, leaderboard and tournament entries " +
                        "and club membership from the server. Your progress, purchases and gifts stay on this phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; social.forgetMe() }, modifier = Modifier.testTag("social_forget_confirm")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}
