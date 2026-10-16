// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.TextInputDialog

@Composable
fun LanguageSpacebarTextPreference(label: String, key: String) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val legacyText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT, null)
    val currentText = prefs.getString(key, null) ?: legacyText ?: Defaults.PREF_SPACE_BAR_TEXT
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        name = label,
        description = currentText.takeIf { it.isNotEmpty() },
        onClick = { showDialog = true }
    )

    if (showDialog) {
        TextInputDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = {
                prefs.edit { putString(key, it) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                showDialog = false
            },
            initialText = currentText,
            title = { Text(label) },
            onNeutral = { prefs.edit { putString(key, Defaults.PREF_SPACE_BAR_TEXT) }; showDialog = false },
            neutralButtonText = stringResource(R.string.button_default)
        )
    }
}
