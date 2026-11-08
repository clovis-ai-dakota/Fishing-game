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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepline.fishing.BuildConfig
import com.deepline.fishing.R
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
                Text(stringResource(R.string.privacy_consent_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.privacy_consent_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.privacy_consent_promise), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
                    modifier = Modifier.testTag("consent_policy"),
                ) { Text(stringResource(R.string.privacy_read_policy)) }
                if (choosing) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.privacy_analytics_title),
                        subtitle = stringResource(R.string.privacy_analytics_subtitle),
                        checked = analytics,
                        onCheckedChange = { analytics = it },
                        tag = "consent_analytics",
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.privacy_crash_title),
                        subtitle = stringResource(R.string.privacy_crash_subtitle),
                        checked = crashReports,
                        onCheckedChange = { crashReports = it },
                        tag = "consent_crash_reports",
                    )
                    Button(
                        onClick = { onAnswer(PrivacyChoices(analytics = analytics, crashReports = crashReports)) },
                        modifier = Modifier.fillMaxWidth().testTag("consent_save"),
                    ) { Text(stringResource(R.string.privacy_consent_save)) }
                } else {
                    TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("consent_choose")) {
                        Text(stringResource(R.string.privacy_consent_choose))
                    }
                }
                // The two answers look exactly the same: refusing is as easy as accepting.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAnswer(PrivacyChoices.NONE) },
                        modifier = Modifier.weight(1f).testTag("consent_refuse"),
                    ) { Text(stringResource(R.string.privacy_consent_refuse)) }
                    Button(
                        onClick = { onAnswer(PrivacyChoices.ALL) },
                        modifier = Modifier.weight(1f).testTag("consent_allow"),
                    ) { Text(stringResource(R.string.privacy_consent_allow)) }
                }
            }
        }
    }
}
