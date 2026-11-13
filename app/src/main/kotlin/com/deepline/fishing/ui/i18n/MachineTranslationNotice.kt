package com.deepline.fishing.ui.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.R

/**
 * A one-time card on the Fish tab when the game is shown in a machine-translated language
 * ([AppLanguage.machineTranslated]): the translation was generated automatically and may be
 * approximate. Dismissed once per language; Settings > Language keeps saying it.
 */
@Composable
fun MachineTranslationNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(AppLanguages.machineNoticePending(context)) }
    if (!pending) return
    Card(modifier.fillMaxWidth().testTag("machine_translation_notice"), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.settings_language_machine_notice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.settings_language_machine_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    AppLanguages.dismissMachineNotice(context)
                    pending = false
                },
                modifier = Modifier.align(Alignment.End).testTag("machine_translation_ok"),
            ) { Text(stringResource(R.string.common_ok)) }
        }
    }
}
