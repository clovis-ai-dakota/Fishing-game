package com.deepline.fishing.ui.social

import com.deepline.fishing.ui.i18n.asString
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.ui.TitleBadge

/** A titled glass card, like the other menus' cards. */
@Composable
fun SocialCard(title: String?, modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = if (highlighted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        // Secondary text on a highlighted card uses the card's own ink, for contrast.
        val hint = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        CompositionLocalProvider(LocalHintColor provides hint) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}

/** The colour of secondary text in the card being drawn. */
val LocalHintColor = compositionLocalOf<Color?> { null }

/** Secondary text in a card (readable in light and dark themes). */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalHintColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * The state shared by every social screen: a progress bar while a request runs, what just
 * happened, and an offline card with a retry button when the server can't be reached.
 */
@Composable
fun SocialStatus(vm: GameViewModel, retry: () -> Unit) {
    val social = vm.social
    if (social.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("social_busy"))
    social.message?.let { text ->
        SocialCard(null, Modifier.testTag("social_message"), highlighted = true) {
            Text(text.asString(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = social::dismissMessage, modifier = Modifier.align(Alignment.End)) { Text("OK") }
        }
    }
    if (social.offline) {
        SocialCard("Can't reach the server", Modifier.testTag("social_offline")) {
            Hint(
                "Friends, leaderboards, the tournament and clubs need the battle server. Check your connection, " +
                    "or the server address in Online battle, then try again. Everything else in the game works offline.",
            )
            OutlinedButton(onClick = retry, modifier = Modifier.testTag("social_retry")) { Text("Try again") }
        }
    }
}

/** A player's name with their title badge, if they wear one. */
@Composable
fun PlayerName(name: String, title: String?, titleFrame: String?, modifier: Modifier = Modifier, you: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (you) "$name (you)" else name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (title != null) TitleBadge(title, frame = TitleFrame.of(titleFrame))
    }
}

/** "Online" with a dot, or "Offline": a word as well as a colour, so it reads without colour. */
@Composable
fun OnlineLabel(online: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            if (online) "Online" else "Offline",
            style = MaterialTheme.typography.labelSmall,
            color = if (online) MaterialTheme.colorScheme.primary else LocalHintColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A rank number in a fixed-width column. */
@Composable
fun RankLabel(rank: Int) {
    Text(
        "#$rank",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Black,
        color = if (rank <= 3) com.deepline.fishing.ui.rewardTextColor() else LocalHintColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp),
    )
}

/** "3 d 4 h", "5 h 20 min", "12 min": time left until [endMs]. */
fun timeLeft(endMs: Long, now: Long): String {
    val minutes = ((endMs - now).coerceAtLeast(0L) / 60_000L)
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    return when {
        days > 0 -> "$days d $hours h"
        hours > 0 -> "$hours h ${minutes % 60} min"
        else -> "${minutes.coerceAtLeast(1)} min"
    }
}

/** Copies [text] to the clipboard. */
fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Opens the phone's share sheet with [text]. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The player's id with copy and share buttons (the friend code). */
@Composable
fun PlayerIdRow(vm: GameViewModel) {
    val context = LocalContext.current
    val id = vm.playerIdentity.id
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            id.chunked(4).joinToString(" "),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("my_player_id"),
        )
        OutlinedButton(onClick = { copyText(context, "Deep Line player id", id) }) { Text("Copy") }
        Button(onClick = { shareText(context, "Add me as a friend in Deep Line: my player id is $id.") }) { Text("Share") }
    }
}
