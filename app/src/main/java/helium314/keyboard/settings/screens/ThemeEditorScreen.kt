// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.preferences.Preference

/**
 * Editor for one complete theme. Theme selection itself is deliberately not included here;
 * this screen edits the theme selected by the pencil button in the Themes picker.
 */
@Composable
fun ThemeEditorScreen(
    theme: String,
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    val title = theme.getStringResourceOrName("theme_name_", context)
    val themeSettings = listOf(
        SettingsWithoutKey.KEYS_STYLE,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_THEME_KEY_BORDERS,
        Settings.PREF_NAVBAR_COLOR,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        Settings.PREF_KEYBOARD_CORNER_RADIUS,
    )

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = title,
        settings = emptyList(),
        content = {
            Preference(
                name = stringResource(R.string.theme_colors),
                description = theme.getStringResourceOrName("theme_name_", context),
                onClick = {
                    SettingsDestination.navigateTo(SettingsDestination.Colors + theme)
                }
            )
            themeSettings.forEach { key ->
                SettingsActivity.settingsContainer[key]?.Preference()
            }
        }
    )
}
