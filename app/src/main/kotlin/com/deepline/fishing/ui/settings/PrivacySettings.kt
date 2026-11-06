package com.deepline.fishing.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.deepline.fishing.BuildConfig
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * Privacy: the two consent switches (usage statistics, crash reports), changeable at any time
 * and as easy to turn off as on; resetting the analytics id; the privacy policy; and deleting
 * the online account (Google Play requires an in-app way to do it).
 */
@Composable
fun PrivacySettingsSection(vm: GameViewModel) {
    val privacy = vm.privacy
    val choices = privacy.current
    val uriHandler = LocalUriHandler.current
    SettingsSection("Privacy", modifier = Modifier.tourTarget(TourTarget.SETTINGS_PRIVACY).testTag("settings_privacy")) {
        Text(
            "Usage statistics and crash reports go to Google Firebase, linked to a random ID (never your name), " +
                "and only if you allow them. They're never used for ads.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            title = "Usage statistics",
            subtitle = "How the game is played: screens, casts, battles, purchases.",
            checked = choices.analytics,
            onCheckedChange = privacy::setAnalytics,
            tag = "privacy_analytics",
        )
        SettingsSwitchRow(
            title = "Crash reports",
            subtitle = "What went wrong when the game crashes or something fails.",
            checked = choices.crashReports,
            onCheckedChange = privacy::setCrashReports,
            tag = "privacy_crash_reports",
        )
        if (!privacy.telemetryAvailable) {
            Text(
                "This version of the game doesn't send any statistics or crash reports. Your choices are kept for " +
                    "versions that do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (choices.analytics) {
            OutlinedButton(onClick = privacy::resetAnalyticsData, modifier = Modifier.testTag("privacy_reset_analytics")) {
                Text("Reset my statistics ID")
            }
        }
        TextButton(
            onClick = { runCatching { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
            modifier = Modifier.testTag("privacy_policy"),
        ) { Text("Read the privacy policy") }
        Text(
            "Your online account (player ID, cloud backup, gifts waiting) is kept on the game server. Deleting it " +
                "erases all of that; the progress on this phone stays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = privacy::askToDeleteAccount,
            enabled = !privacy.deleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("privacy_delete_account"),
        ) { Text(if (privacy.deleting) "Deleting your account…" else "Delete my online account") }
        privacy.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("privacy_message"))
        }
    }

    if (privacy.confirmingDeletion) {
        AlertDialog(
            onDismissRequest = privacy::cancelDeletion,
            title = { Text("Delete your online account?") },
            text = {
                Text(
                    "This erases everything the game server keeps for you: your player ID, your cloud backup and gifts " +
                        "waiting for you. Your recovery code stops working, and this can't be undone.\n\n" +
                        "Your progress on this phone stays, and your purchases stay with your Google Play account. " +
                        "A Captain's Club subscription isn't cancelled: manage it in Google Play.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = vm::deleteOnlineAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("privacy_delete_confirm"),
                ) { Text("Delete my account") }
            },
            dismissButton = { TextButton(onClick = privacy::cancelDeletion) { Text("Keep it") } },
        )
    }
}
