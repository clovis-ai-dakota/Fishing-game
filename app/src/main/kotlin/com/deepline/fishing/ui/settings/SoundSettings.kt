package com.deepline.fishing.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.feel.Cue
import com.deepline.fishing.feel.FeelSettings
import com.deepline.fishing.feel.GameFeel
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import kotlin.math.roundToInt

/**
 * Sound, music and haptics: the overall, music and sound effect volumes, vibration, and Reduce
 * motion (no screen shake, slow motion or flashes). Saved on this phone (see [FeelSettings]).
 */
@Composable
fun SoundSettingsSection(vm: GameViewModel) {
    val feel = vm.feel
    val s = feel.settings
    fun change(next: (FeelSettings) -> FeelSettings) = feel.update(next(feel.settings))
    SettingsSection("Sound and feel", Modifier.tourTarget(TourTarget.SETTINGS_SOUND)) {
        VolumeRow(
            title = "Volume",
            value = s.master,
            tag = "settings_master_volume",
            onChange = { v -> change { it.copy(master = v) } },
            onDone = { feel.commit(GameFeel.SETTING_MASTER) },
        )
        VolumeRow(
            title = "Music",
            value = s.music,
            tag = "settings_music_volume",
            onChange = { v -> change { it.copy(music = v) } },
            onDone = { feel.commit(GameFeel.SETTING_MUSIC) },
        )
        VolumeRow(
            title = "Sound effects",
            value = s.sfx,
            tag = "settings_sfx_volume",
            onChange = { v -> change { it.copy(sfx = v) } },
            onDone = {
                feel.commit(GameFeel.SETTING_SFX)
                // Let the player hear the new level.
                feel.previewSfx()
            },
        )
        SettingsSwitchRow(
            title = "Vibration",
            checked = s.haptics,
            onCheckedChange = { on ->
                change { it.copy(haptics = on) }
                feel.commit(GameFeel.SETTING_HAPTICS)
                feel.cue(Cue.UI_TOGGLE)
            },
            tag = "settings_haptics",
            subtitle = if (feel.hapticsAvailable) {
                "A light touch for bumps, catches and big wins."
            } else {
                "This phone can't vibrate."
            },
        )
        SettingsSwitchRow(
            title = "Reduce motion",
            checked = s.reduceMotion,
            onCheckedChange = { on ->
                change { it.copy(reduceMotion = on) }
                feel.commit(GameFeel.SETTING_REDUCE_MOTION)
                feel.cue(Cue.UI_TOGGLE)
            },
            tag = "settings_reduce_motion",
            subtitle = "No screen shake, slow motion or flashes during casts.",
        )
    }
}

/** A volume: a title, the level in percent, and a slider. */
@Composable
private fun VolumeRow(title: String, value: Float, tag: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics { contentDescription = title },
        )
    }
}
