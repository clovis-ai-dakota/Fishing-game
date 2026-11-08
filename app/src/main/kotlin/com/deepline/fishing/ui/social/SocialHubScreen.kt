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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.Weeks
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.pointsLabel
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** On the Battle tab: the way to friends, the leaderboards, the tournament (and clubs). */
@Composable
fun SocialEntryCard(vm: GameViewModel) {
    val f = vm.social.friends
    val waiting = (f?.incoming?.size ?: 0) + (f?.challenges?.size ?: 0)
    SocialCard(stringResource(R.string.social_hub_title), Modifier.tourTarget(TourTarget.BATTLE_SOCIAL)) {
        Hint(stringResource(if (FeatureFlags.clubs) R.string.social_entry_body else R.string.social_entry_body_no_clubs))
        BadgedBox(badge = { if (waiting > 0) Badge { Text(formatPoints(waiting)) } }) {
            FilledTonalButton(onClick = { vm.openSocial() }, modifier = Modifier.testTag("battle_social")) {
                Text(stringResource(R.string.social_entry_open))
            }
        }
    }
}

/** The Friends card's summary: "You have 2 friends. 1 friend request is waiting for your answer." */
@Composable
private fun friendsSummary(f: FriendsResponse?): String = when {
    f == null -> stringResource(R.string.social_hub_friends_unknown)
    f.friends.isEmpty() && f.incoming.isEmpty() -> stringResource(R.string.social_hub_friends_none)
    else -> listOfNotNull(
        pluralStringResource(R.plurals.social_hub_friend_count, f.friends.size, f.friends.size),
        f.incoming.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.social_hub_requests_waiting, it, it) },
        f.challenges.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.social_hub_challenges_waiting, it, it) },
    ).joinToString(" ")
}

/** The social hub: the player's id (their friend code), and a card for each social feature. */
@Composable
fun SocialHubScreen(vm: GameViewModel) {
    val social = vm.social
    val now = ShopClock.now()
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    SubScreen(stringResource(R.string.social_hub_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.onOpen(Screen.SOCIAL) }
            SocialCard(stringResource(R.string.social_hub_id_title), Modifier.tourTarget(TourTarget.SOCIAL_ID)) {
                PlayerIdRow(vm)
                Hint(stringResource(R.string.social_hub_id_body))
            }

            SocialCard(stringResource(R.string.social_friends_title)) {
                Hint(friendsSummary(social.friends))
                Button(onClick = { vm.openSocial(Screen.FRIENDS) }, modifier = Modifier.testTag("social_friends")) {
                    Text(stringResource(R.string.social_hub_friends_button))
                }
            }

            val week = Weeks.indexAt(now)
            SocialCard(stringResource(R.string.social_leaderboards_title)) {
                Hint(stringResource(R.string.social_hub_leaderboards_body, timeLeftLabel(Weeks.endOf(week), now)))
                Button(onClick = { vm.openSocial(Screen.LEADERBOARDS) }, modifier = Modifier.testTag("social_leaderboards")) {
                    Text(stringResource(R.string.social_hub_leaderboards_button))
                }
            }

            val t = social.tournament
            SocialCard(stringResource(R.string.social_tournament_title)) {
                if (t != null) {
                    val left = t.attemptsMax - t.attemptsUsed
                    Text(pluralStringResource(R.plurals.social_hub_attempts_left, left, left), style = MaterialTheme.typography.bodyMedium)
                    t.best?.let { best ->
                        val points = pointsLabel(best.toLong())
                        Hint(
                            t.rank?.let { r -> stringResource(R.string.social_hub_best_ranked, points, formatPoints(r), formatPoints(t.participants)) }
                                ?: stringResource(R.string.social_hub_best, points),
                        )
                    }
                    if (t.rewards.isNotEmpty()) {
                        Text(stringResource(R.string.social_hub_reward_waiting), color = rewardTextColor(), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Hint(pluralStringResource(R.plurals.social_hub_tournament_about, GameConfig.TOURNAMENT_ATTEMPTS, GameConfig.TOURNAMENT_ATTEMPTS))
                }
                Button(onClick = { vm.openSocial(Screen.TOURNAMENT) }, modifier = Modifier.testTag("social_tournament")) {
                    Text(stringResource(R.string.social_hub_tournament_button))
                }
            }

            if (FeatureFlags.clubs) {
                val c = social.club?.club
                SocialCard(stringResource(R.string.social_clubs_title)) {
                    Hint(
                        if (c != null) {
                            pluralStringResource(R.plurals.social_hub_club_body, c.weekFish, c.name, formatPoints(c.weekFish))
                        } else {
                            stringResource(R.string.social_hub_clubs_body)
                        },
                    )
                    Button(onClick = { vm.openSocial(Screen.CLUBS) }, modifier = Modifier.testTag("social_clubs")) {
                        Text(stringResource(R.string.social_hub_clubs_button))
                    }
                }
            }

            TextButton(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth().testTag("social_forget")) {
                Text(stringResource(R.string.social_forget_button), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.social_forget_title)) },
            text = { Text(stringResource(R.string.social_forget_body)) },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; social.forgetMe() }, modifier = Modifier.testTag("social_forget_confirm")) {
                    Text(stringResource(R.string.social_forget_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
