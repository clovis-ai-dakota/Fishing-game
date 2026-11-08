package com.deepline.fishing.ui.social

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.TitleFrame
import com.deepline.fishing.ui.TitleBadge
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.rewardTextColor
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.shop.timeLeftLabel

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
            TextButton(onClick = social::dismissMessage, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.common_ok)) }
        }
    }
    if (social.offline) {
        SocialCard(stringResource(R.string.social_offline_title), Modifier.testTag("social_offline")) {
            Hint(stringResource(if (FeatureFlags.clubs) R.string.social_offline_body else R.string.social_offline_body_no_clubs))
            OutlinedButton(onClick = retry, modifier = Modifier.testTag("social_retry")) { Text(stringResource(R.string.social_offline_retry)) }
        }
    }
}

/** A player's name with their title badge, if they wear one. */
@Composable
fun PlayerName(name: String, title: String?, titleFrame: String?, modifier: Modifier = Modifier, you: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (you) stringResource(R.string.social_player_you, name) else name,
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
            stringResource(if (online) R.string.social_status_online else R.string.social_status_offline),
            style = MaterialTheme.typography.labelSmall,
            color = if (online) MaterialTheme.colorScheme.primary else LocalHintColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A rank number in a fixed-width column. */
@Composable
fun RankLabel(rank: Int) {
    Text(
        stringResource(R.string.social_rank, rank),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Black,
        color = if (rank <= 3) rewardTextColor() else LocalHintColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp),
    )
}

/** "3 d 4 h", "5 h 20 min", "12 min": time left until [endMs] (at least a minute). */
@Composable
fun timeLeftLabel(endMs: Long, now: Long): String = ShopText.timeLeftLabel((endMs - now).coerceAtLeast(0L))

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
    val clipLabel = stringResource(R.string.social_clip_player_id)
    val shareMessage = stringResource(R.string.social_share_player_id, id)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            id.chunked(4).joinToString(" "),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).testTag("my_player_id"),
        )
        OutlinedButton(onClick = { copyText(context, clipLabel, id) }) { Text(stringResource(R.string.social_copy)) }
        Button(onClick = { shareText(context, shareMessage) }) { Text(stringResource(R.string.social_share)) }
    }
}
