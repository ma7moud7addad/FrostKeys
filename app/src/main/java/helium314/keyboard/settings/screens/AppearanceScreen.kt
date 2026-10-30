// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.dialogs.ColorThemePickerDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.KeyBackgroundImagePreference
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.preferences.KeyboardScalePreference
import helium314.keyboard.settings.preferences.LanguageSpacebarTextPreference
import helium314.keyboard.latin.utils.previewDark
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    val items = listOf(
        R.string.settings_screen_theme,
        SettingsWithoutKey.KEYS_STYLE,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_THEME_COLORS,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        Settings.PREF_THEME_KEY_BORDERS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        Settings.PREF_NAVBAR_COLOR,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        Settings.PREF_KEYBOARD_CORNER_RADIUS,
        R.string.settings_category_miscellaneous,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            )
            Settings.PREF_SPLIT_SPACER_SCALE_PREFIX else null,
        if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS))
            Settings.PREF_KEY_GAP_SCALE_PREFIX else null,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX,
        Settings.PREF_BOTTOM_ROW_SCALE_PREFIX,
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        Settings.PREF_SPACE_BAR_TEXT,
        SettingsWithoutKey.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        SettingsWithoutKey.CUSTOM_EMOJI_FONT,
        Settings.PREF_EMOJI_FONT_SCALE,
        if (prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != 1f)
            Settings.PREF_EMOJI_KEY_FIT else null,
        if (prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0) >= 24)
            Settings.PREF_EMOJI_SKIN_TONE else null,
        Settings.PREF_PERSISTENT_EMOJI_ROW,
    )
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            helium314.keyboard.settings.SettingsActivity.activeOverlay = null
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        settings = items
    )
}

fun createAppearanceSettings(context: Context) = listOf(
    Setting(context, SettingsWithoutKey.KEYS_STYLE, R.string.keys_style, R.string.keys_style_summary) { setting ->
        var show by rememberSaveable { mutableStateOf(false) }
        Preference(name = setting.title, description = setting.description, onClick = { show = true })
        if (show) {
            ThreeButtonAlertDialog(
                onDismissRequest = { show = false },
                onConfirmed = { },
                confirmButtonText = null,
                title = { androidx.compose.material3.Text(stringResource(R.string.keys_style)) },
                content = {
                    Column {
                        KeyBackgroundImagePreference(Setting(context, SettingsWithoutKey.KEY_BACKGROUND_IMAGE,
                            R.string.customize_key_background) { })
                        KeyBackgroundImagePreference(Setting(context, SettingsWithoutKey.DELETE_KEY_BACKGROUND_IMAGE,
                            R.string.customize_delete_key_background) { }, "_delete")
                        KeyBackgroundImagePreference(Setting(context, SettingsWithoutKey.ENTER_KEY_BACKGROUND_IMAGE,
                            R.string.customize_enter_key_background) { }, "_enter")
                        KeyBackgroundImagePreference(Setting(context, SettingsWithoutKey.SPACEBAR_KEY_BACKGROUND_IMAGE,
                            R.string.customize_spacebar_key_background) { }, "_spacebar")
                    }
                }
            )
        }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            KeyboardIconsSet.instance.loadIcons(LocalContext.current)
            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = false,
                default = Defaults.PREF_THEME_COLORS
            )
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        val prefs = ctx.prefs()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS_NIGHT)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = true,
                default = Defaults.PREF_THEME_COLORS_NIGHT
            )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, Defaults.PREF_THEME_KEY_BORDERS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_THEME_DAY_NIGHT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_COLOR)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    Setting(context, Settings.PREF_KEYBOARD_CORNER_RADIUS, R.string.prefs_keyboard_corner_radius) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEYBOARD_CORNER_RADIUS,
            range = Settings.KEYBOARD_CORNER_RADIUS_MIN_DP.toFloat()..Settings.KEYBOARD_CORNER_RADIUS_MAX_DP.toFloat(),
            stepSize = 1,
            description = { "$it dp" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        var show by remember { mutableStateOf(false) }
        val prefAndName = listOfNotNull(
            Settings.PREF_ENABLE_SPLIT_KEYBOARD to stringResource(R.string.button_default),
            Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE to stringResource(R.string.landscape),
            if (!FoldableUtils.isFoldable) null else
                Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED to stringResource(R.string.folded),
            if (!FoldableUtils.isFoldable) null else
                Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE to stringResource(R.string.folded) + " / " + stringResource(R.string.landscape)
        )
        Preference(
            name = stringResource(R.string.enable_split_keyboard),
            onClick = { show = true },
            description = prefAndName.filter { LocalContext.current.prefs().getBoolean(it.first, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) }
                .joinToString(", ") { it.second }.takeIf { it.isNotEmpty() }
        )
        if (show) {
            ThreeButtonAlertDialog(
                onDismissRequest = { show = false },
                onConfirmed = {},
                confirmButtonText = null,
                cancelButtonText = stringResource(R.string.dialog_close),
                content = {
                    Column {
                        prefAndName.forEach {
                            SwitchPreference(name = it.second, key = it.first, default = Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
                        }
                    }
                }
            )
        }
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, R.string.split_spacer_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_SPLIT_SPACER_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_GAP_SCALE_PREFIX, R.string.prefs_key_gap_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_KEY_GAP_SCALE,
            range = 0.5f..2.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.prefs_keyboard_height_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
            range = Settings.KEYBOARD_HEIGHT_SCALE_MIN..Settings.KEYBOARD_HEIGHT_SCALE_MAX,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().onKeyboardHeightScaleChanged() }
    },
    Setting(context, Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, R.string.prefs_bottom_row_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_BOTTOM_ROW_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, R.string.prefs_bottom_padding_scale) { setting ->
        val defaults = Defaults.PREF_BOTTOM_PADDING_SCALE
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = defaults,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_PREFIX, R.string.prefs_side_padding_scale) { setting ->
        val defaults = Defaults.PREF_SIDE_PADDING_SCALE
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.split), stringResource(R.string.folded)),
            defaults = defaults,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) {
        LanguageSpacebarTextPreference()
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FONT, R.string.custom_font) {
        CustomFontPreference(it, Settings.getCustomFontFile(LocalContext.current), R.string.custom_font)
    },
    Setting(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_EMOJI_FONT, R.string.custom_emoji_font) {
        CustomFontPreference(it, Settings.getCustomEmojiFontFile(LocalContext.current), R.string.custom_emoji_font)
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_EMOJI_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_KEY_FIT, R.string.prefs_emoji_key_fit) {
        SwitchPreference(it, Defaults.PREF_EMOJI_KEY_FIT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_SKIN_TONE, R.string.prefs_emoji_skin_tone) { setting ->
        val items = listOf(
            stringResource(R.string.prefs_emoji_skin_tone_neutral) to "",
            "\uD83C\uDFFB" to "\uD83C\uDFFB",
            "\uD83C\uDFFC" to "\uD83C\uDFFC",
            "\uD83C\uDFFD" to "\uD83C\uDFFD",
            "\uD83C\uDFFE" to "\uD83C\uDFFE",
            "\uD83C\uDFFF" to "\uD83C\uDFFF"
        )
        ListPreference(setting, items, Defaults.PREF_EMOJI_SKIN_TONE) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_PERSISTENT_EMOJI_ROW, R.string.prefs_persistent_emoji_row) {
        SwitchPreference(it, Defaults.PREF_PERSISTENT_EMOJI_ROW) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AppearanceScreen { }
        }
    }
}
