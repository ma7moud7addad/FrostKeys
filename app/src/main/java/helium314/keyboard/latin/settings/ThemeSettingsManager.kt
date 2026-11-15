// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import java.io.File

/** Keeps the non-color Theme settings isolated while the keyboard still consumes its legacy keys. */
object ThemeSettingsManager {
    private const val STORAGE_PREFIX = "theme_scope_v1_"
    private const val INITIALIZED_SUFFIX = "__initialized"

    private val themePreferenceKeys = listOf(
        Settings.PREF_THEME_STYLE,
        Settings.PREF_THEME_KEY_BORDERS,
        Settings.PREF_NAVBAR_COLOR,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_KEYBOARD_CORNER_RADIUS,
        Settings.PREF_KEYS_RADIUS,
        Settings.PREF_NORMAL_KEY_RADIUS,
        Settings.PREF_FUNCTION_KEY_RADIUS,
        Settings.PREF_ACTION_KEY_RADIUS,
        Settings.PREF_DELETE_KEY_RADIUS,
        Settings.PREF_POPUP_BACKGROUND_RADIUS,
        Settings.PREF_POPUP_KEY_RADIUS,
        Settings.PREF_POPUP_SELECTED_KEY_RADIUS,
    )

    @JvmStatic
    fun ensureActiveTheme(context: Context) {
        val preferences = context.prefs()
        val theme = activeTheme(preferences)
        val marker = markerKey(theme)
        if (!preferences.getBoolean(marker, false)) {
            migrateLegacyImages(context)
            snapshot(preferences, theme)
            preferences.edit().putBoolean(marker, true).apply()
        }
    }

    @JvmStatic
    fun switchTheme(context: Context, theme: String) {
        val preferences = context.prefs()
        ensureActiveTheme(context)
        val oldTheme = activeTheme(preferences)
        if (oldTheme == theme) return
        snapshot(preferences, oldTheme)
        restore(preferences, theme)
        preferences.edit()
            .putString(Settings.PREF_THEME_COLORS, theme)
            .putBoolean(markerKey(theme), true)
            .apply()
        Settings.clearCachedBackgroundImages()
    }

    @JvmStatic
    fun activeThemeSuffix(context: Context): String {
        return "_theme_" + safeName(activeTheme(context.prefs()))
    }

    private fun activeTheme(preferences: SharedPreferences): String =
        preferences.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) ?: Defaults.PREF_THEME_COLORS

    private fun safeName(theme: String): String =
        theme.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "default" }

    private fun prefix(theme: String) = STORAGE_PREFIX + safeName(theme) + "_"
    private fun markerKey(theme: String) = prefix(theme) + INITIALIZED_SUFFIX

    private fun snapshot(preferences: SharedPreferences, theme: String) {
        val prefix = prefix(theme)
        val editor = preferences.edit()
        themePreferenceKeys.forEach { key ->
            val value = preferences.all[key] ?: return@forEach
            when (value) {
                is Boolean -> editor.putBoolean(prefix + key, value)
                is Float -> editor.putFloat(prefix + key, value)
                is Int -> editor.putInt(prefix + key, value)
                is Long -> editor.putLong(prefix + key, value)
                is String -> editor.putString(prefix + key, value)
                is Set<*> -> editor.putStringSet(prefix + key, value.filterIsInstance<String>().toSet())
            }
            editor.putBoolean(prefix + key + "__present", true)
        }
        editor.apply()
    }

    private fun restore(preferences: SharedPreferences, theme: String) {
        val prefix = prefix(theme)
        val editor = preferences.edit()
        themePreferenceKeys.forEach { key -> editor.remove(key) }
        editor.apply()

        val values = preferences.all
        val restoreEditor = preferences.edit()
        themePreferenceKeys.forEach { key ->
            if (!values.containsKey(prefix + key + "__present")) return@forEach
            when (val value = values[prefix + key]) {
                is Boolean -> restoreEditor.putBoolean(key, value)
                is Float -> restoreEditor.putFloat(key, value)
                is Int -> restoreEditor.putInt(key, value)
                is Long -> restoreEditor.putLong(key, value)
                is String -> restoreEditor.putString(key, value)
                is Set<*> -> restoreEditor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        restoreEditor.apply()
    }

    private fun migrateLegacyImages(context: Context) {
        val keySuffixes = listOf(
            "", "_pressed", "_delete", "_delete_pressed", "_enter", "_enter_pressed",
            "_spacebar", "_spacebar_pressed", "_function", "_function_pressed",
            "_popup_preview", "_long_press_popup", "_popup_key", "_popup_key_selected"
        )
        keySuffixes.forEach { suffix ->
            val oldFile = File(context.filesDir, "custom_key_background$suffix")
            val newFile = Settings.getCustomKeyBackgroundFile(context, suffix)
            copyIfNeeded(oldFile, newFile)
        }
        listOf("", "_landscape").forEach { layoutSuffix ->
            listOf("", "_night").forEach { nightSuffix ->
                val oldFile = File(
                    DeviceProtectedUtils.getFilesDir(context),
                    "custom_background_image$layoutSuffix$nightSuffix"
                )
                val newFile = Settings.getCustomBackgroundFile(
                    context,
                    nightSuffix == "_night",
                    layoutSuffix == "_landscape"
                )
                copyIfNeeded(oldFile, newFile)
            }
        }
    }

    private fun copyIfNeeded(source: File, destination: File) {
        if (source.exists() && !destination.exists()) {
            runCatching { source.copyTo(destination, overwrite = false) }
        }
    }
}
