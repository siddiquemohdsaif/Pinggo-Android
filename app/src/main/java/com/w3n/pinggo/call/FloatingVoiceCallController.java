package com.w3n.pinggo.call;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.w3n.pinggo.activity.VoiceCallActivity;
import com.w3n.pinggo.activity.HomeActivity;
import com.w3n.pinggo.activity.ChatActivity;
import java.lang.ref.WeakReference;

/** In-app floating card for a minimized active voice call. */
public final class FloatingVoiceCallController implements Application.ActivityLifecycleCallbacks {
  private static final FloatingVoiceCallController INSTANCE = new FloatingVoiceCallController();
  private Application application;
  private WeakReference<Activity> resumedActivity = new WeakReference<>(null);
  private WeakReference<Activity> previousActivity = new WeakReference<>(null);
  private View overlay;
  private WeakReference<Activity> overlayHost = new WeakReference<>(null);
  private TextView statusView;
  private boolean active, minimized;
  private String phone = "Unknown", profilePath, status = "Calling…";
  private Runnable endAction;

  private FloatingVoiceCallController() {}
  public static FloatingVoiceCallController getInstance() { return INSTANCE; }

  public void initialize(Application app) {
    if (application != null) return;
    application = app;
    app.registerActivityLifecycleCallbacks(this);
  }
  public void begin(String phone, String profilePath, Runnable endAction) {
    this.phone = phone == null || phone.trim().isEmpty() ? "Unknown" : phone;
    this.profilePath = profilePath;
    this.endAction = endAction;
    status = "Calling…";
    active = true;
  }
  public void minimizeAndReturn(Activity callActivity) {
    minimized = true;
    Activity previous = previousActivity.get();
    Intent intent;
    if (previous != null && !previous.isFinishing() && !previous.isDestroyed()) {
      intent = new Intent(callActivity, previous.getClass());
    } else {
      intent = new Intent(callActivity, HomeActivity.class);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    callActivity.startActivity(intent);
  }
  public void updateStatus(String value) {
    status = value == null || value.trim().isEmpty() ? "Calling…" : value;
    if (statusView != null) statusView.setText(status);
  }
  public void clear() { active = false; minimized = false; endAction = null; remove(); }

  private void attach(Activity activity) {
    if (!active || !minimized || activity instanceof VoiceCallActivity) return;
    remove();
    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
    LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 8), dp(activity, 8));
    card.setElevation(dp(activity, 10));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.WHITE); bg.setCornerRadius(dp(activity, 18)); bg.setStroke(dp(activity, 1), 0x22000000);
    card.setBackground(bg);

    ImageView avatar = new ImageView(activity);
    if (profilePath != null && !profilePath.trim().isEmpty()) avatar.setImageBitmap(BitmapFactory.decodeFile(profilePath));
    else { GradientDrawable fallback = new GradientDrawable(); fallback.setShape(GradientDrawable.OVAL); fallback.setColor(0xFFD9F1F7); avatar.setBackground(fallback); }
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    card.addView(avatar, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));

    LinearLayout labels = new LinearLayout(activity);
    labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);
    TextView phoneView = new TextView(activity);
    phoneView.setText(phone); phoneView.setTextColor(0xFF000E1A); phoneView.setTextSize(16); phoneView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    labels.addView(phoneView);
    statusView = new TextView(activity);
    statusView.setText(status); statusView.setTextColor(0xFF019CC4); statusView.setTextSize(13);
    labels.addView(statusView);
    card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    TextView end = new TextView(activity);
    end.setText("End"); end.setTextColor(Color.WHITE); end.setTextSize(14); end.setTypeface(Typeface.DEFAULT, Typeface.BOLD); end.setGravity(Gravity.CENTER);
    GradientDrawable endBg = new GradientDrawable(); endBg.setColor(0xFFE53935); endBg.setCornerRadius(dp(activity, 22)); end.setBackground(endBg);
    end.setOnClickListener(view -> { if (endAction != null) endAction.run(); });
    card.addView(end, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44)));
    card.setOnClickListener(view -> {
      Intent intent = new Intent(activity, VoiceCallActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      activity.startActivity(intent);
    });
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 72), Gravity.TOP);
    params.setMargins(dp(activity, 12), dp(activity, 44), dp(activity, 12), 0);
    decor.addView(card, params);
    overlay = card;
    overlayHost = new WeakReference<>(activity);
    if (activity instanceof ChatActivity) {
      ((ChatActivity) activity).setFloatingCallInset(dp(activity, 92));
    }
  }
  private void remove() {
    Activity host = overlayHost.get();
    if (host instanceof ChatActivity) ((ChatActivity) host).setFloatingCallInset(0);
    if (overlay != null && overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null; statusView = null; overlayHost.clear();
  }
  private static int dp(Activity activity, float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

  @Override public void onActivityResumed(Activity activity) {
    resumedActivity = new WeakReference<>(activity);
    if (activity instanceof VoiceCallActivity) {
      remove();
    } else {
      previousActivity = new WeakReference<>(activity);
      attach(activity);
    }
  }
  @Override public void onActivityDestroyed(Activity activity) { if (resumedActivity.get() == activity) resumedActivity.clear(); }
  @Override public void onActivityCreated(Activity activity, Bundle state) {}
  @Override public void onActivityStarted(Activity activity) {}
  @Override public void onActivityPaused(Activity activity) {}
  @Override public void onActivityStopped(Activity activity) {}
  @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
