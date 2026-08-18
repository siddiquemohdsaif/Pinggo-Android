package com.w3n.pinggo.views.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

/** Installs task-root back handling backed by {@link ExitAppDialogView}. */
public final class ExitAppController {
  private ExitAppController() {}

  public static void install(
      AppCompatActivity activity, @Nullable FragmentManager fragmentManager) {
    ViewGroup content = activity.findViewById(android.R.id.content);
    ExitAppDialogView dialogView = new ExitAppDialogView(activity, activity::finishAndRemoveTask);
    content.addView(
        dialogView,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    activity
        .getOnBackPressedDispatcher()
        .addCallback(
            activity,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (dialogView.dismissIfShowing()) return;
                if (hideKeyboardIfVisible(activity, content)) return;
                if (fragmentManager != null && fragmentManager.getBackStackEntryCount() > 0) {
                  fragmentManager.popBackStack();
                  return;
                }
                if (!activity.isTaskRoot()) {
                  activity.finish();
                  return;
                }
                dialogView.showDialog();
              }
            });
  }

  private static boolean hideKeyboardIfVisible(AppCompatActivity activity, ViewGroup content) {
    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
    if (insets == null || !insets.isVisible(WindowInsetsCompat.Type.ime())) {
      return false;
    }

    View focusedView = activity.getCurrentFocus();
    View windowView = focusedView != null ? focusedView : content;
    InputMethodManager keyboard =
        (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) {
      keyboard.hideSoftInputFromWindow(windowView.getWindowToken(), 0);
    }
    if (focusedView != null) focusedView.clearFocus();
    return true;
  }
}
