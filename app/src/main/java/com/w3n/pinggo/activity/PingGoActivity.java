package com.w3n.pinggo.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Common activity configuration shared by PingGo's light, edge-to-edge screens. */
public abstract class PingGoActivity extends AppCompatActivity {
  protected static final int APP_SYSTEM_BAR_COLOR = 0xFFF7F9FB;
  protected static final int AUTH_SYSTEM_BAR_COLOR = 0xFFEBF1F7;

  protected enum SystemBarStyle {
    LIGHT,
    AUTH,
    DARK,
    FULLSCREEN_DARK,
    THEME_MANAGED
  }

  @Override
  protected void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    configureWindowBehavior();
    restoreSystemBars();
  }

  /**
   * Keeps every PingGo activity full-height while an IME is visible. Screens that contain text
   * input apply their own IME insets, so the system must not also resize or pan the activity.
   */
  private void configureWindowBehavior() {
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
  }

  /** Override only when the activity's normal surface is not the standard light app screen. */
  protected SystemBarStyle defaultSystemBarStyle() {
    return SystemBarStyle.LIGHT;
  }

  /** Restores the activity's declared bar style after a preview, cropper or camera overlay. */
  protected final void restoreSystemBars() {
    applySystemBarStyle(defaultSystemBarStyle());
  }

  /** Applies a temporary or permanent system-bar presentation from one centralized policy. */
  protected final void applySystemBarStyle(SystemBarStyle style) {
    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
        getWindow(), getWindow().getDecorView());
    controller.setSystemBarsBehavior(
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

    if (style == SystemBarStyle.THEME_MANAGED) return;
    if (style == SystemBarStyle.FULLSCREEN_DARK) {
      getWindow().setStatusBarColor(0xFF000000);
      getWindow().setNavigationBarColor(0xFF000000);
      controller.setAppearanceLightStatusBars(false);
      controller.setAppearanceLightNavigationBars(false);
      controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
      return;
    }

    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
    boolean light = style == SystemBarStyle.LIGHT || style == SystemBarStyle.AUTH;
    int color = style == SystemBarStyle.AUTH
        ? AUTH_SYSTEM_BAR_COLOR : light ? APP_SYSTEM_BAR_COLOR : 0xFF000000;
    getWindow().setStatusBarColor(color);
    getWindow().setNavigationBarColor(color);
    controller.setAppearanceLightStatusBars(light);
    controller.setAppearanceLightNavigationBars(light);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      getWindow().setNavigationBarContrastEnforced(false);
  }

  /** Convenience for temporary image/video/camera surfaces. */
  protected final void setSystemBarsHidden(boolean hidden) {
    if (hidden) applySystemBarStyle(SystemBarStyle.FULLSCREEN_DARK);
    else restoreSystemBars();
  }
}
