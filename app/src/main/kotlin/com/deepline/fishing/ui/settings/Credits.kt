package com.deepline.fishing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.audio.AudioCredits

/** Credits and licences: where every sound and music track comes from (see [AudioCredits]). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CreditsSection(vm: GameViewModel) {
    val uri = LocalUriHandler.current
    SettingsSection("Credits", Modifier.testTag("settings_credits")) {
        Text(
            "All the sounds and music are free works dedicated to the public domain (CC0) and shared on " +
                "OpenGameArt.org. Thank you to their authors.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (credit in AudioCredits.all) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { uri.openUri(credit.url) } },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    if (credit.music) "Music: ${credit.title}" else "Sounds: ${credit.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("By ${credit.author}. Licence: ${credit.licence}.", style = MaterialTheme.typography.bodySmall)
                Text(credit.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
