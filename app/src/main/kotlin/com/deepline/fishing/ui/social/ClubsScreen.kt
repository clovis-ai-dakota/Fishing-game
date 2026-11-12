package com.deepline.fishing.ui.social

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.ClubDto
import com.deepline.fishing.core.social.ClubMember
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.random.Random

/** "1,250 fish": a number of fish caught (club goals and members' counts). */
@Composable
private fun fishLabel(count: Int): String = pluralStringResource(R.plurals.social_clubs_fish, count, formatPoints(count))

/**
 * Picks one of a few generated club names (players never type a club's name). Club names are made
 * from fixed English words and never translated, so every member sees the same name.
 */
@Composable
private fun NamePicker(selected: Pair<Int, Int>?, options: List<Pair<Int, Int>>, onPick: (Pair<Int, Int>) -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = o == selected,
                onClick = { onPick(o) },
                label = { Text(Clubs.name(o.first, o.second) ?: "") },
                modifier = Modifier.testTag("club_name_$i"),
            )
        }
        TextButton(onClick = onShuffle, modifier = Modifier.testTag("club_names_shuffle")) { Text(stringResource(R.string.social_clubs_other_names)) }
    }
}

/**
 * Clubs (behind FeatureFlags.clubs): join with a code or start one, then fish for a weekly goal
 * together. The club's board ranks members by fish caught this week. No chat.
 */
@Composable
fun ClubsScreen(vm: GameViewModel) {
    val social = vm.social
    val mine = social.club
    SubScreen(stringResource(R.string.social_clubs_title), onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SocialStatus(vm) { social.refreshClub() }
            val club = mine?.club
            if (club == null) NoClub(vm) else MyClubCards(vm, club)

            social.topClubs?.takeIf { it.clubs.isNotEmpty() }?.let { top ->
                SocialCard(stringResource(R.string.social_clubs_top_title)) {
                    top.clubs.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RankLabel(c.rank)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (c.mine) stringResource(R.string.social_clubs_yours, c.name) else c.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Hint(pluralStringResource(R.plurals.social_clubs_members, c.members, c.members))
                            }
                            Text(fishLabel(c.weekFish), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoClub(vm: GameViewModel) {
    val social = vm.social
    var code by rememberSaveable { mutableStateOf("") }
    var seed by rememberSaveable { mutableStateOf(1L) }
    val options = remember(seed) { Clubs.suggestions(Random(seed)) }
    var picked by remember(seed) { mutableStateOf(options.first()) }
    SocialCard(stringResource(R.string.social_clubs_join_title), Modifier.tourTarget(TourTarget.CLUBS_JOIN)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(Clubs.CODE_LENGTH + 2) },
                label = { Text(stringResource(R.string.social_clubs_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f).testTag("club_code"),
            )
            Button(onClick = { social.joinClub(code) }, enabled = code.isNotBlank() && !social.busy, modifier = Modifier.testTag("club_join")) {
                Text(stringResource(R.string.social_clubs_join))
            }
        }
        Hint(stringResource(R.string.social_clubs_join_body, Clubs.CODE_LENGTH, GameConfig.CLUB_MAX_MEMBERS))
    }
    SocialCard(stringResource(R.string.social_clubs_start_title)) {
        Hint(stringResource(R.string.social_clubs_start_body))
        NamePicker(picked, options, onPick = { picked = it }, onShuffle = { seed++ })
        Button(onClick = { social.createClub(picked.first, picked.second) }, enabled = !social.busy, modifier = Modifier.testTag("club_create")) {
            Text(stringResource(R.string.social_clubs_start_button, Clubs.name(picked.first, picked.second) ?: ""))
        }
    }
}

@Composable
private fun MyClubCards(vm: GameViewModel, club: ClubDto) {
    val social = vm.social
    val context = LocalContext.current
    val me = club.members.firstOrNull { it.id == vm.playerIdentity.id }
    val owner = me?.role == ClubRole.OWNER
    var confirmLeave by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<ClubMember?>(null) }
    val now = ShopClock.now()
    val clipLabel = stringResource(R.string.social_clip_club_code)
    val shareMessage = stringResource(R.string.social_clubs_share, club.name, club.code)

    SocialCard(club.name, Modifier.testTag("club_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Hint(stringResource(R.string.social_clubs_code))
                Text(
                    club.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("club_code_shown"),
                )
            }
            OutlinedButton(onClick = { copyText(context, clipLabel, club.code) }) { Text(stringResource(R.string.social_copy)) }
            Button(onClick = { shareText(context, shareMessage) }) { Text(stringResource(R.string.social_share)) }
        }
        val members = pluralStringResource(R.plurals.social_clubs_member_count, GameConfig.CLUB_MAX_MEMBERS, club.members.size, GameConfig.CLUB_MAX_MEMBERS)
        Hint(if (owner) members + " " + stringResource(R.string.social_clubs_you_own) else members)
    }

    val tiers = GameConfig.CLUB_GOAL_TIERS
    SocialCard(stringResource(R.string.social_clubs_goal_title)) {
        Text(
            pluralStringResource(R.plurals.social_clubs_goal_body, club.weekFish, formatPoints(club.weekFish), timeLeftLabel(club.endsAtMs, now)),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(progress = { (club.weekFish.toFloat() / tiers.last()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        tiers.forEachIndexed { i, goal ->
            val tier = i + 1
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    // A tick before the goals reached.
                    (if (club.tiersReached >= tier) "✓ " else "") + fishLabel(goal),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ShopText.seasonGrants(Clubs.rewardOf(tier), vm.progress).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = rewardTextColor(),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
        Hint(pluralStringResource(R.plurals.social_clubs_goal_rules, GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY, GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY))
        if (club.claimable.isNotEmpty()) {
            Button(onClick = social::claimClubRewards, modifier = Modifier.testTag("club_claim")) {
                Text(pluralStringResource(R.plurals.social_clubs_claim, club.claimable.size, club.claimable.size))
            }
        }
    }

    SocialCard(stringResource(R.string.social_clubs_members_title)) {
        club.members.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().testTag("club_member_${m.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RankLabel(i + 1)
                Column(Modifier.weight(1f)) {
                    PlayerName(m.name, m.title, m.titleFrame, you = m.id == me?.id)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Hint(m.role.label())
                        OnlineLabel(m.online)
                    }
                }
                Text(fishLabel(m.weekFish), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (owner && m.id != me?.id) {
                    TextButton(onClick = { managing = m }, modifier = Modifier.testTag("club_manage_${m.id}")) {
                        Text(stringResource(R.string.social_clubs_manage))
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (owner) {
            OutlinedButton(onClick = { renaming = true }, modifier = Modifier.testTag("club_rename")) { Text(stringResource(R.string.social_clubs_rename)) }
        }
        OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.testTag("club_leave")) { Text(stringResource(R.string.social_clubs_leave)) }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.social_clubs_leave_title, club.name)) },
            text = {
                Text(
                    stringResource(
                        if (owner && club.members.size > 1) {
                            R.string.social_clubs_leave_owner
                        } else if (club.members.size == 1) {
                            R.string.social_clubs_leave_last
                        } else {
                            R.string.social_clubs_leave_member
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; social.leaveClub() }, modifier = Modifier.testTag("club_leave_confirm")) {
                    Text(stringResource(R.string.social_clubs_leave_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.social_clubs_stay)) } },
        )
    }
    managing?.let { m ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(m.name) },
            text = {
                Text(pluralStringResource(R.plurals.social_clubs_manage_body, GameConfig.CLUB_REJOIN_HOURS, m.name, GameConfig.CLUB_REJOIN_HOURS))
            },
            confirmButton = {
                TextButton(onClick = { managing = null; social.transfer(m) }, modifier = Modifier.testTag("club_transfer_confirm")) {
                    Text(stringResource(R.string.social_clubs_make_owner))
                }
            },
            dismissButton = {
                TextButton(onClick = { managing = null; social.kick(m) }, modifier = Modifier.testTag("club_kick_confirm")) {
                    Text(stringResource(R.string.social_clubs_remove))
                }
            },
        )
    }
    if (renaming) {
        var seed by remember { mutableStateOf(7L) }
        val options = remember(seed) { Clubs.suggestions(Random(seed)) }
        var picked by remember(seed) { mutableStateOf(options.first()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.social_clubs_rename_title)) },
            text = { NamePicker(picked, options, onPick = { picked = it }, onShuffle = { seed++ }) },
            confirmButton = {
                TextButton(onClick = { renaming = false; social.renameClub(picked.first, picked.second) }) {
                    Text(stringResource(R.string.social_clubs_rename))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
