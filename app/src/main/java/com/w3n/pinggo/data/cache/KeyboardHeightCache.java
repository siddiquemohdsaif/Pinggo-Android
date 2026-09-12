package com.w3n.pinggo.data.cache;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.WeakHashMap;

/** Stores the most recently observed software-keyboard height for the whole application. */
public final class KeyboardHeightCache implements Application.ActivityLifecycleCallbacks {
  private static final String PREFERENCES = "keyboard_height_cache";
  private static final String HEIGHT_PORTRAIT = "height_portrait";
  private static final String HEIGHT_LANDSCAPE = "height_landscape";
  private static final float DEFAULT_HEIGHT_DP = 300f;

  private final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> observers =
      new WeakHashMap<>();

  private KeyboardHeightCache() {}

  public static void initialize(Application application) {
    application.registerActivityLifecycleCallbacks(new KeyboardHeightCache());
  }

  public static int get(Context context) {
    int measured = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getInt(preferenceKey(context), 0);
    return measured > 0 ? measured
        : Math.round(DEFAULT_HEIGHT_DP * context.getResources().getDisplayMetrics().density);
  }

  public static void record(Context context, int height) {
    int minimumKeyboardHeight = Math.round(100f * context.getResources().getDisplayMetrics().density);
    if (height < minimumKeyboardHeight) return;
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        .putInt(preferenceKey(context), height).apply();
  }

  private static String preferenceKey(Context context) {
    return context.getResources().getConfiguration().orientation
        == Configuration.ORIENTATION_LANDSCAPE ? HEIGHT_LANDSCAPE : HEIGHT_PORTRAIT;
  }

  private void startObserving(Activity activity) {
    if (observers.containsKey(activity)) return;
    View decor = activity.getWindow().getDecorView();
    ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
      Rect visibleFrame = new Rect();
      decor.getWindowVisibleDisplayFrame(visibleFrame);
      int navigationBarHeight = 0;
      WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
      if (insets != null) {
        Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        navigationBarHeight = navigation.bottom;
      }
      record(activity, decor.getRootView().getHeight() - visibleFrame.bottom - navigationBarHeight);
    };
    observers.put(activity, listener);
    decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
  }

  private void stopObserving(Activity activity) {
    ViewTreeObserver.OnGlobalLayoutListener listener = observers.remove(activity);
    if (listener == null) return;
    View decor = activity.getWindow().getDecorView();
    if (decor.getViewTreeObserver().isAlive()) {
      decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
    }
  }

  @Override public void onActivityResumed(Activity activity) { startObserving(activity); }
  @Override public void onActivityPaused(Activity activity) { stopObserving(activity); }
  @Override public void onActivityDestroyed(Activity activity) { stopObserving(activity); }
  @Override public void onActivityCreated(Activity activity, Bundle state) {}
  @Override public void onActivityStarted(Activity activity) {}
  @Override public void onActivityStopped(Activity activity) {}
  @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
