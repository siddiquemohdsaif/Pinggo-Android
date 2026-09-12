package com.w3n.pinggo.call;

import android.content.Context;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.modals.AppConfiguration;

/** Central feature switch. Legacy remains the safe default if config is absent or invalid. */
public final class CallEngineToggle {
  public static final String LEGACY = "legacy";
  public static final String LIVEKIT = "livekit";

  private CallEngineToggle() {}

  public static String selectedEngine() {
    Context context = AppContextProvider.getAppContext();
    if (context != null) {
      String override = context.getSharedPreferences("call_engine", Context.MODE_PRIVATE)
          .getString("override", "");
      if (LIVEKIT.equals(override) || LEGACY.equals(override)) return override;
    }
    AppConfiguration config = AppContextProvider.getParsedAppConfig();
    return config == null ? LEGACY : config.getCallEngine();
  }

  /** Development/QA override. Pass null to resume using remote app configuration. */
  public static void setOverride(Context context, String engine) {
    if (context == null) return;
    android.content.SharedPreferences.Editor editor = context.getSharedPreferences(
        "call_engine", Context.MODE_PRIVATE).edit();
    if (LIVEKIT.equals(engine) || LEGACY.equals(engine)) editor.putString("override", engine);
    else editor.remove("override");
    editor.apply();
  }

  public static boolean useLiveKit() {
    return LIVEKIT.equals(selectedEngine());
  }
}
