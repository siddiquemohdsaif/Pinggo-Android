package com.w3n.pinggo.call;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

/** Per-call engine picker. The configured feature toggle only controls the preselected item. */
public final class CallEngineChooser {
  public interface Listener { void onSelected(String engine); }

  private static final String TAG = "PingGoCallTrace";

  private CallEngineChooser() {}

  public static void show(Activity activity, String mediaType, String chatId, Listener listener) {
    boolean group = chatId != null && chatId.startsWith("grp_");
    int checked = CallEngineToggle.useLiveKit() ? 0 : 1;
    String[] choices = {
        "LiveKit (1-to-1 and group)",
        "WebRTC / JPEG (1-to-1 only)"
    };
    final int[] selected = {checked};
    Log.i(TAG, "engine_dialog_show media=" + mediaType + " chatId=" + chatId
        + " default=" + CallEngineToggle.selectedEngine() + " group=" + group);
    AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle("Select call connection")
        .setSingleChoiceItems(choices, checked, (value, which) -> selected[0] = which)
        .setNegativeButton("Cancel", (value, which) -> {
          Log.i(TAG, "engine_dialog_cancel media=" + mediaType + " chatId=" + chatId);
          activity.finish();
        })
        .setPositiveButton("Start call", null)
        .setOnCancelListener(value -> {
          Log.i(TAG, "engine_dialog_dismiss media=" + mediaType + " chatId=" + chatId);
          activity.finish();
        })
        .create();
    dialog.setOnShowListener(value -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(button -> {
          String engine = selected[0] == 0 ? CallEngineToggle.LIVEKIT : CallEngineToggle.LEGACY;
          if (group && CallEngineToggle.LEGACY.equals(engine)) {
            Log.w(TAG, "engine_selection_blocked engine=legacy reason=group_call chatId=" + chatId);
            Toast.makeText(activity, "Group calls require LiveKit.", Toast.LENGTH_SHORT).show();
            return;
          }
          Log.i(TAG, "engine_selected engine=" + engine + " media=" + mediaType
              + " chatId=" + chatId);
          dialog.dismiss();
          listener.onSelected(engine);
        }));
    dialog.show();
  }
}
