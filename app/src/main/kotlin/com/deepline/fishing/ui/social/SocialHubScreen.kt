package com.deepline.fishing.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.kit.ActionRole
import com.deepline.fishing.ui.kit.CountBadge
import com.deepline.fishing.ui.kit.InfoButton
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.pressTilt
import com.deepline.fishing.ui.kit.roleColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** The Battle tab's way into friends, leaderboards, the tournament (and clubs): a button with what's waiting. */
@Composable
fun SocialEntryButton(vm: GameViewModel, modifier: Modifier = Modifier) {
    val f = vm.social.friends
    val waiting = (f?.incoming?.size ?: 0) + (f?.challenges?.size ?: 0)
    com.deepline.fishing.ui.kit.RoundIconButton(
        com.deepline.fishing.ui.kit.KitIcons.Friends,
        stringResource(R.string.social_hub_title),
        onClick = { vm.openSocial() },
        modifier = modifier.tourTarget(TourTarget.BATTLE_SOCIAL).testTag("battle_social"),
        badge = waiting,
    )
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
            SocialCard(stringResource(R.string.social_hub_id_title), Modifier.tourTarget(TourTarget.SOCIAL_ID), info = stringResource(R.string.social_hub_id_body)) {
                PlayerIdRow(vm)
            }

            // One big tile per feature: its pictogram, what's waiting, and the rest behind its (i).
            val f = social.friends
            val week = Weeks.indexAt(now)
            val t = social.tournament
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HubTile(
                    KitIcons.Friends, stringResource(R.string.social_friends_title), stringResource(R.string.social_hub_friends_button), friendsSummary(f),
                    onClick = { vm.openSocial(Screen.FRIENDS) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("social_friends"),
                    badge = (f?.incoming?.size ?: 0) + (f?.challenges?.size ?: 0),
                ) {
                    if (f != null) HubFact(KitIcons.Friends, stringResource(R.string.common_times, f.friends.size))
                }
                HubTile(
                    KitIcons.Podium, stringResource(R.string.social_leaderboards_title), stringResource(R.string.social_hub_leaderboards_button),
                    stringResource(R.string.social_hub_leaderboards_body, timeLeftLabel(Weeks.endOf(week), now)),
                    onClick = { vm.openSocial(Screen.LEADERBOARDS) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("social_leaderboards"),
                ) {
                    HubFact(KitIcons.Clock, timeLeftLabel(Weeks.endOf(week), now))
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val about = pluralStringResource(R.plurals.social_hub_tournament_about, GameConfig.TOURNAMENT_ATTEMPTS, GameConfig.TOURNAMENT_ATTEMPTS)
                val tournamentInfo = if (t == null) {
                    about
                } else {
                    val left = t.attemptsMax - t.attemptsUsed
                    listOfNotNull(
                        pluralStringResource(R.plurals.social_hub_attempts_left, left, left),
                        t.best?.let { best ->
                            val points = pointsLabel(best.toLong())
                            t.rank?.let { r -> stringResource(R.string.social_hub_best_ranked, points, formatPoints(r), formatPoints(t.participants)) }
                                ?: stringResource(R.string.social_hub_best, points)
                        },
                        if (t.rewards.isNotEmpty()) stringResource(R.string.social_hub_reward_waiting) else null,
                    ).joinToString("\n\n")
                }
                HubTile(
                    GameIcons.Trophy, stringResource(R.string.social_tournament_title), stringResource(R.string.social_hub_tournament_button), tournamentInfo,
                    onClick = { vm.openSocial(Screen.TOURNAMENT) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("social_tournament"),
                    badge = t?.rewards?.size ?: 0,
                    role = if (t?.rewards?.isNotEmpty() == true) ActionRole.CLAIM else ActionRole.NAV,
                ) {
                    if (t != null) {
                        // Attempts left, and the best rank so far.
                        HubFact(KitIcons.Hook, stringResource(R.string.common_fraction, t.attemptsMax - t.attemptsUsed, t.attemptsMax))
                        t.rank?.let { r -> HubFact(KitIcons.Podium, stringResource(R.string.social_rank, r)) }
                    }
                }
                if (FeatureFlags.clubs) {
                    val c = social.club?.club
                    HubTile(
                        KitIcons.Flag, stringResource(R.string.social_clubs_title), stringResource(R.string.social_hub_clubs_button),
                        if (c != null) {
                            pluralStringResource(R.plurals.social_hub_club_body, c.weekFish, c.name, formatPoints(c.weekFish))
                        } else {
                            stringResource(R.string.social_hub_clubs_body)
                        },
                        onClick = { vm.openSocial(Screen.CLUBS) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("social_clubs"),
                    ) {
                        if (c != null) HubFact(GameIcons.Fish, formatPoints(c.weekFish))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
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

/**
 * One feature of the social hub as a big tile: its pictogram (with a red count when something
 * waits), its name, one or two facts as pictograms and numbers, and the sentences behind the (i).
 * [action] is what tapping it does, for screen readers.
 */
@Composable
private fun HubTile(
    icon: ImageVector,
    title: String,
    action: String,
    info: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    role: ActionRole = ActionRole.NAV,
    facts: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = roleColors(role)
    Card(onClick = onClick, modifier = modifier.pressTilt(sheen = false).semantics { contentDescription = action }, shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    Box(
                        Modifier.size(64.dp).background(if (role == ActionRole.NAV) scheme.secondaryContainer else colors.top, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = if (role == ActionRole.NAV) scheme.primary else colors.content, modifier = Modifier.size(34.dp))
                    }
                    CountBadge(badge, Modifier.align(Alignment.TopEnd))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(hyphens = Hyphens.Auto),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { facts() }
            }
            InfoButton(title, info, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** A number with its pictogram, in a hub tile. */
@Composable
private fun HubFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
