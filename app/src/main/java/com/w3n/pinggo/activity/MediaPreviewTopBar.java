package com.w3n.pinggo.activity;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;

/** Shared media-preview chrome sized from the 1080 px chat selection-header design. */
final class MediaPreviewTopBar {
  private MediaPreviewTopBar() {}

  static FrameLayout add(
      Activity activity,
      FrameLayout root,
      String phoneNumber,
      TextView speedControl,
      Runnable forwardAction) {
    float scale = activity.getResources().getDisplayMetrics().widthPixels / 1080f;
    int barHeight = Math.max(1, Math.round(170f * scale));
    int statusBarResource = activity.getResources().getIdentifier(
        "status_bar_height", "dimen", "android");
    int initialStatusInset = statusBarResource == 0
        ? 0 : activity.getResources().getDimensionPixelSize(statusBarResource);
    final int[] retainedStatusInset = {initialStatusInset};
    View statusBarScrim = new View(activity);
    statusBarScrim.setBackgroundColor(0x40000000);
    root.addView(statusBarScrim, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, initialStatusInset, Gravity.TOP));
    FrameLayout bar = new FrameLayout(activity);
    bar.setTag(statusBarScrim);
    bar.setBackgroundColor(0x40000000);
    FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, barHeight, Gravity.TOP);
    barParams.topMargin = initialStatusInset;
    root.addView(
        bar,
        barParams);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, windowInsets) -> {
          Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
          if (statusBars.top > 0) {
            retainedStatusInset[0] = Math.max(retainedStatusInset[0], statusBars.top);
          }
          FrameLayout.LayoutParams scrimParams =
              (FrameLayout.LayoutParams) statusBarScrim.getLayoutParams();
          if (scrimParams.height != retainedStatusInset[0]) {
            scrimParams.height = retainedStatusInset[0];
            statusBarScrim.setLayoutParams(scrimParams);
          }
          FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bar.getLayoutParams();
          if (params.topMargin != retainedStatusInset[0]) {
            params.topMargin = retainedStatusInset[0];
            bar.setLayoutParams(params);
          }
          return windowInsets;
        });
    ViewCompat.requestApplyInsets(root);
    root.post(() -> ViewCompat.requestApplyInsets(root));

    ImageButton back = iconButton(activity, R.drawable.conversation_back);
    addAt(bar, back, 25f, 34f, 103f, 103f, scale);
    back.setPadding(Math.round(26f * scale), Math.round(26f * scale),
        Math.round(26f * scale), Math.round(26f * scale));
    back.setOnClickListener(view -> activity.finish());

    TextView phone = new TextView(activity);
    phone.setText(phoneNumber == null ? "" : phoneNumber);
    phone.setTextColor(Color.WHITE);
    phone.setTextSize(TypedValue.COMPLEX_UNIT_PX, 50f * scale);
    phone.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    phone.setGravity(Gravity.CENTER_VERTICAL);
    phone.setSingleLine(true);
    phone.setEllipsize(android.text.TextUtils.TruncateAt.END);
    addAt(bar, phone, 165f, 42f, speedControl == null ? 760f : 590f, 85f, scale);

    if (speedControl != null) {
      speedControl.setTextColor(Color.WHITE);
      speedControl.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38f * scale);
      speedControl.setGravity(Gravity.CENTER);
      speedControl.setBackgroundColor(Color.TRANSPARENT);
      speedControl.setSingleLine(true);
      addAt(bar, speedControl, 770f, 34f, 130f, 103f, scale);
    }

    ImageButton forward = iconButton(activity, R.drawable.conversation_selection_forward);
    addAt(bar, forward, 926f, 34f, 98f, 103f, scale);
    forward.setPadding(Math.round(23.5f * scale), Math.round(26f * scale),
        Math.round(23.5f * scale), Math.round(26f * scale));
    forward.setOnClickListener(view -> forwardAction.run());
    return bar;
  }

  static void setStatusBarShade(ViewGroup topBar, int color) {
    if (topBar == null) return;
    Object value = topBar.getTag();
    if (value instanceof View) ((View) value).setBackgroundColor(color);
  }

  private static ImageButton iconButton(Activity activity, int resource) {
    ImageButton button = new ImageButton(activity);
    button.setImageDrawable(ContextCompat.getDrawable(activity, resource));
    button.setColorFilter(Color.WHITE);
    button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
    button.setBackgroundColor(Color.TRANSPARENT);
    return button;
  }

  private static void addAt(
      FrameLayout parent,
      View child,
      float left,
      float top,
      float width,
      float height,
      float scale) {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
    params.leftMargin = Math.round(left * scale);
    params.topMargin = Math.round(top * scale);
    parent.addView(child, params);
  }
}
