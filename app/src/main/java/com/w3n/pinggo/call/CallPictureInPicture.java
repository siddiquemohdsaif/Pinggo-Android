package com.w3n.pinggo.call;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import android.util.Rational;
import com.w3n.pinggo.R;
import com.w3n.pinggo.notification.CallPipActionReceiver;
import java.util.Collections;

/** Shared system Picture-in-Picture policy for voice and video call activities. */
public final class CallPictureInPicture {
  public static final String ACTION_HANG_UP = "com.w3n.pinggo.action.PIP_HANG_UP";
  public static final long DISMISS_CONFIRMATION_MS = 150L;
  public static final int DISMISS_CONFIRMATION_MAX_ATTEMPTS = 40;
  private static final String TAG = "PingGoCallPiP";
  private static final Rational PORTRAIT_ASPECT_RATIO = new Rational(9, 16);

  private CallPictureInPicture() {}

  public static boolean isSupported(Activity activity) {
    return activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && activity.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE);
  }

  /** Updates automatic Home/recents handling on Android 12 and later. */
  public static void configure(Activity activity, boolean enabled) {
    if (!isSupported(activity)) return;
    try {
      Api26Impl.configure(activity, enabled);
    } catch (RuntimeException error) {
      Log.w(TAG, "Unable to configure Picture-in-Picture", error);
    }
  }

  public static boolean enter(Activity activity) {
    if (!isSupported(activity) || activity.isFinishing() || activity.isDestroyed()
        || isActive(activity)) return false;
    try {
      return Api26Impl.enter(activity);
    } catch (IllegalArgumentException | IllegalStateException error) {
      Log.w(TAG, "Unable to enter Picture-in-Picture", error);
      return false;
    }
  }

  public static boolean isActive(Activity activity) {
    return activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && Api26Impl.isActive(activity);
  }

  /** Isolates API 26 classes so Android 7 devices can still load the outer helper. */
  private static final class Api26Impl {
    static void configure(Activity activity, boolean enabled) {
      android.app.PictureInPictureParams.Builder builder = baseParams(activity, enabled);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(enabled);
        builder.setSeamlessResizeEnabled(true);
      }
      activity.setPictureInPictureParams(builder.build());
    }

    static boolean enter(Activity activity) {
      android.app.PictureInPictureParams.Builder builder = baseParams(activity, true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(true);
        builder.setSeamlessResizeEnabled(true);
      }
      return activity.enterPictureInPictureMode(builder.build());
    }

    static boolean isActive(Activity activity) {
      return activity.isInPictureInPictureMode();
    }

    private static android.app.PictureInPictureParams.Builder baseParams(
        Activity activity, boolean enabled) {
      android.app.PictureInPictureParams.Builder builder =
          new android.app.PictureInPictureParams.Builder()
              .setAspectRatio(PORTRAIT_ASPECT_RATIO);
      if (!enabled) return builder.setActions(Collections.emptyList());
      Intent intent = new Intent(activity, CallPipActionReceiver.class)
          .setAction(ACTION_HANG_UP);
      PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, 9017, intent,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      String label = activity.getString(R.string.pip_hang_up);
      RemoteAction action = new RemoteAction(
          Icon.createWithResource(activity, R.drawable.ic_call_end),
          label, label, pendingIntent);
      return builder.setActions(Collections.singletonList(action));
    }
  }
}
