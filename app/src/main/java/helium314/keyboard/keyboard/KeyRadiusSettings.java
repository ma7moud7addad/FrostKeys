package helium314.keyboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Defaults;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;

public final class KeyRadiusSettings {
    private KeyRadiusSettings() {}

    private static float dp(final Context context, final int value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    public static float radiusPx(final Context context, final Key key, final boolean pressed,
            final boolean popup) {
        final SharedPreferences prefs = KtxKt.prefs(context);
        final int value;
        if (popup && pressed) {
            value = prefs.getInt(Settings.PREF_POPUP_SELECTED_KEY_RADIUS,
                    Defaults.PREF_POPUP_SELECTED_KEY_RADIUS);
        } else if (popup) {
            value = prefs.getInt(Settings.PREF_POPUP_KEY_RADIUS,
                    Defaults.PREF_POPUP_KEY_RADIUS);
        } else if (key.getCode() == KeyCode.DELETE) {
            value = prefs.getInt(Settings.PREF_DELETE_KEY_RADIUS,
                    Defaults.PREF_DELETE_KEY_RADIUS);
        } else if (key.getBackgroundType() == Key.BACKGROUND_TYPE_FUNCTIONAL) {
            value = prefs.getInt(Settings.PREF_FUNCTION_KEY_RADIUS,
                    Defaults.PREF_FUNCTION_KEY_RADIUS);
        } else if (key.getBackgroundType() == Key.BACKGROUND_TYPE_ACTION) {
            value = prefs.getInt(Settings.PREF_ACTION_KEY_RADIUS,
                    Defaults.PREF_ACTION_KEY_RADIUS);
        } else {
            // Spacebar intentionally follows Normal keys; it has no separate preference.
            value = prefs.getInt(Settings.PREF_NORMAL_KEY_RADIUS,
                    Defaults.PREF_NORMAL_KEY_RADIUS);
        }
        return dp(context, value);
    }

    public static float popupBackgroundRadiusPx(final Context context) {
        final SharedPreferences prefs = KtxKt.prefs(context);
        return dp(context, prefs.getInt(Settings.PREF_POPUP_BACKGROUND_RADIUS,
                Defaults.PREF_POPUP_BACKGROUND_RADIUS));
    }

    public static int backgroundColor(final Colors colors, final Key key, final boolean pressed,
            final boolean popup) {
        final ColorType type;
        if (popup) {
            type = key.hasActionKeyBackground()
                    ? ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND
                    : ColorType.POPUP_KEYS_BACKGROUND;
        } else if (key.getBackgroundType() == Key.BACKGROUND_TYPE_FUNCTIONAL) {
            type = ColorType.FUNCTIONAL_KEY_BACKGROUND;
        } else if (key.getBackgroundType() == Key.BACKGROUND_TYPE_ACTION) {
            type = ColorType.ACTION_KEY_BACKGROUND;
        } else if (key.getBackgroundType() == Key.BACKGROUND_TYPE_SPACEBAR) {
            type = ColorType.SPACE_BAR_BACKGROUND;
        } else {
            type = ColorType.KEY_BACKGROUND;
        }
        return pressed ? colors.getPressed(type) : colors.get(type);
    }
}
