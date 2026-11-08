package com.deepline.fishing.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.AppLanguage
import com.deepline.fishing.ui.i18n.AppLanguages
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import java.util.Locale

/**
 * The game's language: the phone's (the default) or one of the game's languages, each written in
 * its own language. On Android 13+ this is the same setting as the system's per-app language
 * (see [AppLanguages]); changing it recreates the screen in the new language.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun LanguageSettingsSection(vm: GameViewModel) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(AppLanguages.current(context)) }
    SettingsSection(
        stringResource(R.string.settings_language_title),
        Modifier.tourTarget(TourTarget.SETTINGS_LANGUAGE).testTag("settings_language"),
    ) {
        Text(
            stringResource(R.string.settings_language_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            for (language in AppLanguages.choices) {
                LanguageRow(
                    language = language,
                    selected = language == chosen,
                    onSelect = {
                        if (language != chosen) {
                            chosen = language
                            context.findActivity()?.let { AppLanguages.set(it, language) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    // Each language is named in itself, so anyone can find theirs whatever the game shows now.
    val locale = language.locale
    val phone = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp)
            .testTag("language_" + (language.tag ?: "system")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            if (locale == null) {
                Text(stringResource(R.string.settings_language_system), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_language_system_now, phone.getDisplayName(phone).replaceFirstChar { it.titlecase(phone) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    language.endonym,
                    // Written (and read aloud by screen readers) in its own language.
                    style = MaterialTheme.typography.bodyLarge.copy(localeList = LocaleList(language.tag!!)),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
