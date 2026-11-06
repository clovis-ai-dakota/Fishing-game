package com.deepline.fishing.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepline.fishing.BuildConfig
import com.deepline.fishing.ui.settings.SettingsSwitchRow

/**
 * Asked once, before anything is collected (and before the tour, which waits for the answer):
 * may the game send usage statistics and crash reports? "Don't allow" and "Allow" look the same
 * and sit side by side, nothing is pre-ticked, and "Choose for each" sets the two separately.
 * It can't be dismissed without an answer; the answer can be changed any time in Settings.
 */
@Composable
fun ConsentSheet(onAnswer: (PrivacyChoices) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var analytics by rememberSaveable { mutableStateOf(false) }
    var crashReports by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().testTag("consent_sheet"),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Help make Deep Line better?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "With your permission, the game sends usage statistics (which screens you open, how your casts " +
                        "and battles go, what you buy) and crash reports to Google Firebase. They help us fix bugs " +
                        "and balance the game.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "They're linked to a random ID, never to your name, and never used for ads. Nothing is sent " +
                        "unless you allow it, and you can change your mind at any time in Settings, under Privacy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
                    modifier = Modifier.testTag("consent_policy"),
                ) { Text("Read the privacy policy") }
                if (choosing) {
                    SettingsSwitchRow(
                        title = "Usage statistics",
                        subtitle = "How the game is played: screens, casts, battles, purchases.",
                        checked = analytics,
                        onCheckedChange = { analytics = it },
                        tag = "consent_analytics",
                    )
                    SettingsSwitchRow(
                        title = "Crash reports",
                        subtitle = "What went wrong when the game crashes or something fails.",
                        checked = crashReports,
                        onCheckedChange = { crashReports = it },
                        tag = "consent_crash_reports",
                    )
                    Button(
                        onClick = { onAnswer(PrivacyChoices(analytics = analytics, crashReports = crashReports)) },
                        modifier = Modifier.fillMaxWidth().testTag("consent_save"),
                    ) { Text("Save my choices") }
                } else {
                    TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("consent_choose")) {
                        Text("Choose for each")
                    }
                }
                // The two answers look exactly the same: refusing is as easy as accepting.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAnswer(PrivacyChoices.NONE) },
                        modifier = Modifier.weight(1f).testTag("consent_refuse"),
                    ) { Text("Don't allow") }
                    Button(
                        onClick = { onAnswer(PrivacyChoices.ALL) },
                        modifier = Modifier.weight(1f).testTag("consent_allow"),
                    ) { Text("Allow") }
                }
            }
        }
    }
}
