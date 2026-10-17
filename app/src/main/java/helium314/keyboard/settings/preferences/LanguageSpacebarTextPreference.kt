// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.contentTextDirectionStyle
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

@Composable
fun LanguageSpacebarTextPreference() {
    val context = LocalContext.current
    val prefs = context.prefs()
    val legacyText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT, null)
    val arabicText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT_ARABIC, null)
        ?: legacyText
        ?: Defaults.PREF_SPACE_BAR_TEXT
    val englishText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT_ENGLISH, null)
        ?: legacyText
        ?: Defaults.PREF_SPACE_BAR_TEXT
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        name = stringResource(R.string.prefs_space_bar_text),
        description = listOf(arabicText, englishText)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" / ")
            .takeIf { it.isNotEmpty() },
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SpacebarTextDialog(
            initialArabicText = arabicText,
            initialEnglishText = englishText,
            onDismissRequest = { showDialog = false },
            onConfirmed = { newArabicText, newEnglishText ->
                prefs.edit {
                    putString(Settings.PREF_SPACE_BAR_TEXT_ARABIC, newArabicText)
                    putString(Settings.PREF_SPACE_BAR_TEXT_ENGLISH, newEnglishText)
                }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                showDialog = false
            },
            onDefault = {
                prefs.edit {
                    remove(Settings.PREF_SPACE_BAR_TEXT_ARABIC)
                    remove(Settings.PREF_SPACE_BAR_TEXT_ENGLISH)
                }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                showDialog = false
            }
        )
    }
}

@Composable
private fun SpacebarTextDialog(
    initialArabicText: String,
    initialEnglishText: String,
    onDismissRequest: () -> Unit,
    onConfirmed: (arabicText: String, englishText: String) -> Unit,
    onDefault: () -> Unit,
) {
    var arabicText by rememberSaveable { mutableStateOf(initialArabicText) }
    var englishText by rememberSaveable { mutableStateOf(initialEnglishText) }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(arabicText, englishText) },
        title = { Text(stringResource(R.string.prefs_space_bar_text)) },
        neutralButtonText = stringResource(R.string.button_default),
        onNeutral = onDefault,
        content = {
            Column {
                Text(stringResource(R.string.space_bar_text_arabic))
                OutlinedTextField(
                    value = arabicText,
                    onValueChange = { arabicText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = contentTextDirectionStyle,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.space_bar_text_english))
                OutlinedTextField(
                    value = englishText,
                    onValueChange = { englishText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = contentTextDirectionStyle,
                )
            }
        }
    )
}
