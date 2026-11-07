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
import androidx.compose.ui.res.stringResource
import com.deepline.fishing.BuildConfig
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.asString
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
    SettingsSection(stringResource(R.string.privacy_settings_title), modifier = Modifier.tourTarget(TourTarget.SETTINGS_PRIVACY).testTag("settings_privacy")) {
        Text(
            stringResource(R.string.privacy_settings_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.privacy_analytics_title),
            subtitle = stringResource(R.string.privacy_analytics_subtitle),
            checked = choices.analytics,
            onCheckedChange = privacy::setAnalytics,
            tag = "privacy_analytics",
        )
        SettingsSwitchRow(
            title = stringResource(R.string.privacy_crash_title),
            subtitle = stringResource(R.string.privacy_crash_subtitle),
            checked = choices.crashReports,
            onCheckedChange = privacy::setCrashReports,
            tag = "privacy_crash_reports",
        )
        if (!privacy.telemetryAvailable) {
            Text(
                stringResource(R.string.privacy_settings_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (choices.analytics) {
            OutlinedButton(onClick = privacy::resetAnalyticsData, modifier = Modifier.testTag("privacy_reset_analytics")) {
                Text(stringResource(R.string.privacy_reset_id))
            }
        }
        TextButton(
            onClick = { runCatching { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) } },
            modifier = Modifier.testTag("privacy_policy"),
        ) { Text(stringResource(R.string.privacy_read_policy)) }
        Text(
            stringResource(R.string.privacy_account_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = privacy::askToDeleteAccount,
            enabled = !privacy.deleting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("privacy_delete_account"),
        ) { Text(stringResource(if (privacy.deleting) R.string.privacy_deleting else R.string.privacy_delete_account)) }
        privacy.message?.let { message ->
            Text(message.asString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("privacy_message"))
        }
    }

    if (privacy.confirmingDeletion) {
        AlertDialog(
            onDismissRequest = privacy::cancelDeletion,
            title = { Text(stringResource(R.string.privacy_delete_confirm_title)) },
            text = { Text(stringResource(R.string.privacy_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = vm::deleteOnlineAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("privacy_delete_confirm"),
                ) { Text(stringResource(R.string.privacy_delete_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = privacy::cancelDeletion) { Text(stringResource(R.string.privacy_delete_confirm_no)) } },
        )
    }
}
