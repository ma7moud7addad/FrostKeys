package helium314.keyboard.keyboard;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import helium314.keyboard.latin.settings.Defaults;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;
public final class KeyRadiusSettings {
 private KeyRadiusSettings() {}
 private static int dp(Context c,int v){return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,c.getResources().getDisplayMetrics());}
 public static float radiusPx(Context c,int type,boolean pressed,boolean popup){
  SharedPreferences p=KtxKt.prefs(c); int v;
  if(popup&&pressed)v=p.getInt(Settings.PREF_POPUP_SELECTED_KEY_RADIUS,Defaults.PREF_POPUP_SELECTED_KEY_RADIUS);
  else if(popup)v=p.getInt(Settings.PREF_POPUP_KEY_RADIUS,Defaults.PREF_POPUP_KEY_RADIUS);
  else switch(type){case Key.BACKGROUND_TYPE_FUNCTIONAL:v=p.getInt(Settings.PREF_FUNCTION_KEY_RADIUS,Defaults.PREF_FUNCTION_KEY_RADIUS);break;case Key.BACKGROUND_TYPE_ACTION:v=p.getInt(Settings.PREF_ACTION_KEY_RADIUS,Defaults.PREF_ACTION_KEY_RADIUS);break;case Key.BACKGROUND_TYPE_SPACEBAR:v=p.getInt(Settings.PREF_SPACEBAR_RADIUS,Defaults.PREF_SPACEBAR_RADIUS);break;default:v=p.getInt(Settings.PREF_NORMAL_KEY_RADIUS,Defaults.PREF_NORMAL_KEY_RADIUS);}
  return dp(c,v);
 }
 public static float popupBackgroundRadiusPx(Context c){return dp(c,KtxKt.prefs(c).getInt(Settings.PREF_POPUP_BACKGROUND_RADIUS,Defaults.PREF_POPUP_BACKGROUND_RADIUS));}
}
