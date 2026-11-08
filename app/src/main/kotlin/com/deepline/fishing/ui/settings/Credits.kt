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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.audio.AudioCredits

/** Credits and licences: where every sound and music track comes from (see [AudioCredits]). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CreditsSection(vm: GameViewModel) {
    val uri = LocalUriHandler.current
    SettingsSection(stringResource(R.string.settings_credits_title), Modifier.testTag("settings_credits")) {
        Text(
            stringResource(R.string.settings_credits_body),
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
                    stringResource(if (credit.music) R.string.settings_credits_music else R.string.settings_credits_sounds, credit.title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.settings_credits_by, credit.author, credit.licence), style = MaterialTheme.typography.bodySmall)
                Text(credit.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
