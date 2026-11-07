package com.deepline.fishing.ui.social

import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.i18n.asString
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.ClubDto
import com.deepline.fishing.core.social.ClubMember
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.random.Random

/** Picks one of a few generated club names (players never type a club's name). */
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
        TextButton(onClick = onShuffle, modifier = Modifier.testTag("club_names_shuffle")) { Text("Other names") }
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
    SubScreen("Clubs", onBack = vm::onBack) {
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
                SocialCard("Most active clubs this week") {
                    top.clubs.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RankLabel(c.rank)
                            Column(Modifier.weight(1f)) {
                                Text(if (c.mine) "${c.name} (yours)" else c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Hint(if (c.members == 1) "1 member" else "${c.members} members")
                            }
                            Text("${formatPoints(c.weekFish)} fish", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
    SocialCard("Join a club", Modifier.tourTarget(TourTarget.CLUBS_JOIN)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(Clubs.CODE_LENGTH + 2) },
                label = { Text("Club code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f).testTag("club_code"),
            )
            Button(onClick = { social.joinClub(code) }, enabled = code.isNotBlank() && !social.busy, modifier = Modifier.testTag("club_join")) { Text("Join") }
        }
        Hint(
            "Ask a club's members for its ${Clubs.CODE_LENGTH}-character code. A club has up to ${GameConfig.CLUB_MAX_MEMBERS} " +
                "anglers who fish for a weekly goal together. There's no chat.",
        )
    }
    SocialCard("Start a club") {
        Hint("Pick a name: club names are made from fishing words, so they're friendly for everyone.")
        NamePicker(picked, options, onPick = { picked = it }, onShuffle = { seed++ })
        Button(onClick = { social.createClub(picked.first, picked.second) }, enabled = !social.busy, modifier = Modifier.testTag("club_create")) {
            Text("Start the ${Clubs.name(picked.first, picked.second)}")
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

    SocialCard(club.name, Modifier.testTag("club_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Hint("Club code")
                Text(
                    club.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("club_code_shown"),
                )
            }
            OutlinedButton(onClick = { copyText(context, "Deep Line club code", club.code) }) { Text("Copy") }
            Button(onClick = { shareText(context, "Join my club in Deep Line, the ${club.name}: the code is ${club.code}.") }) { Text("Share") }
        }
        Hint("${club.members.size} of ${GameConfig.CLUB_MAX_MEMBERS} members." + if (owner) " You own this club." else "")
    }

    val tiers = GameConfig.CLUB_GOAL_TIERS
    SocialCard("This week's goal") {
        Text(
            "${formatPoints(club.weekFish)} fish caught together. The goal resets on Monday at 00:00 UTC, in ${timeLeft(club.endsAtMs, now)}.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(progress = { (club.weekFish.toFloat() / tiers.last()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        tiers.forEachIndexed { i, goal ->
            val tier = i + 1
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (club.tiersReached >= tier) "✓ " else "") + "${formatPoints(goal)} fish",
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
        Hint("Fish from your solo casts count, up to ${GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY} a day. Every member who helped gets each reward reached.")
        if (club.claimable.isNotEmpty()) {
            Button(onClick = social::claimClubRewards, modifier = Modifier.testTag("club_claim")) {
                Text(if (club.claimable.size == 1) "Claim 1 reward" else "Claim ${club.claimable.size} rewards")
            }
        }
    }

    SocialCard("Members this week") {
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
                Text("${formatPoints(m.weekFish)} fish", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (owner && m.id != me?.id) {
                    TextButton(onClick = { managing = m }, modifier = Modifier.testTag("club_manage_${m.id}")) { Text("Manage") }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (owner) OutlinedButton(onClick = { renaming = true }, modifier = Modifier.testTag("club_rename")) { Text("Rename") }
        OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.testTag("club_leave")) { Text("Leave the club") }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the ${club.name}?") },
            text = {
                Text(
                    if (owner && club.members.size > 1) {
                        "The member who has been in the club the longest becomes the owner. Your fish this week stay in the club's goal."
                    } else if (club.members.size == 1) {
                        "You're the last member, so the club closes."
                    } else {
                        "Your fish this week stay in the club's goal. You can join another club straight away."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; social.leaveClub() }, modifier = Modifier.testTag("club_leave_confirm")) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
    managing?.let { m ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(m.name) },
            text = {
                Text(
                    "Make ${m.name} the owner (you become a member), or remove them from the club. A removed member can rejoin " +
                        "after ${GameConfig.CLUB_REJOIN_HOURS} hours.",
                )
            },
            confirmButton = {
                TextButton(onClick = { managing = null; social.transfer(m) }, modifier = Modifier.testTag("club_transfer_confirm")) { Text("Make owner") }
            },
            dismissButton = {
                TextButton(onClick = { managing = null; social.kick(m) }, modifier = Modifier.testTag("club_kick_confirm")) { Text("Remove") }
            },
        )
    }
    if (renaming) {
        var seed by remember { mutableStateOf(7L) }
        val options = remember(seed) { Clubs.suggestions(Random(seed)) }
        var picked by remember(seed) { mutableStateOf(options.first()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename the club") },
            text = { NamePicker(picked, options, onPick = { picked = it }, onShuffle = { seed++ }) },
            confirmButton = {
                TextButton(onClick = { renaming = false; social.renameClub(picked.first, picked.second) }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
