package com.deepline.fishing.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ReminderKind
import com.deepline.fishing.ui.daily.rememberReminderOptIn
import com.deepline.fishing.ui.daily.reminderPromise
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/**
 * Notifications: the reminders of the daily loop (local, opt-in, at most one a day, quiet hours),
 * one switch for all of them and one per kind (each also has its own Android channel).
 */
@Composable
fun NotificationSettingsSection(vm: GameViewModel) {
    val daily = vm.daily
    val r = daily.reminders
    val turnOn = rememberReminderOptIn(vm, "settings")
    val context = LocalContext.current
    SettingsSection("Notifications", Modifier.tourTarget(TourTarget.SETTINGS_NOTIFICATIONS)) {
        SettingsSwitchRow(
            title = "Reminders",
            subtitle = "When your daily reward is ready, your streak is about to end or a migration starts. $reminderPromise",
            checked = r.enabled,
            onCheckedChange = { on -> if (on) turnOn() else daily.setEnabled(false, "settings") },
            tag = "notif_master",
        )
        if (r.enabled) {
            SettingsSwitchRow(
                title = "Daily reward ready",
                subtitle = "From ${"%02d:00".format(GameConfig.NOTIFY_DAILY_FROM_HOUR)}, if you haven't opened the game yet that day.",
                checked = r.dailyReady,
                onCheckedChange = { daily.setKind(ReminderKind.DAILY_READY, it) },
                tag = "notif_daily",
            )
            SettingsSwitchRow(
                title = "Streak at risk",
                subtitle = "In the evening, when a streak of ${GameConfig.NOTIFY_STREAK_MIN_DAYS} days or more would end at midnight.",
                checked = r.streakAtRisk,
                onCheckedChange = { daily.setKind(ReminderKind.STREAK_AT_RISK, it) },
                tag = "notif_streak",
            )
            SettingsSwitchRow(
                title = "Migrations and events",
                subtitle = "When a migration or a weekend event starts.",
                checked = r.events,
                onCheckedChange = { daily.setKind(ReminderKind.EVENT_STARTING, it) },
                tag = "notif_events",
            )
            if (daily.blockedByPhone) {
                Text(
                    "Notifications for Deep Line are turned off on this phone, so no reminder can arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedButton(
                    onClick = { context.startActivity(appNotificationSettings(context.packageName)) },
                    modifier = Modifier.testTag("notif_phone_settings"),
                ) { Text("Open phone settings") }
            }
        }
    }
}

/** The phone's notification settings for this app. */
private fun appNotificationSettings(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
