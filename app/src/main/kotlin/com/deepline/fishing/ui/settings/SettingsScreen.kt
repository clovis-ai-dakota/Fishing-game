package com.deepline.fishing.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.BuildConfig
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * Settings, opened by the gear on the Fish tab. Each feature owns one section, in its own file
 * (sound, notifications, privacy, language, credits), so they can grow independently. A section
 * with nothing to show draws nothing.
 */
@Composable
fun SettingsScreen(vm: GameViewModel) {
    SubScreen("Settings", onBack = vm::onBack) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection("Help") {
                Text(
                    "The tour shows how to play and what everything does.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = vm::startTour,
                    modifier = Modifier.testTag("settings_tour").tourTarget(TourTarget.SETTINGS_TOUR),
                ) { Text("Take the tour") }
            }
            SoundSettingsSection(vm)
            NotificationSettingsSection(vm)
            LanguageSettingsSection(vm)
            PrivacySettingsSection(vm)
            CreditsSection(vm)
            Text(
                "Deep Line ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** A titled glass card for one group of settings. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** A setting that is on or off: a title, an optional explanation, and a switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
