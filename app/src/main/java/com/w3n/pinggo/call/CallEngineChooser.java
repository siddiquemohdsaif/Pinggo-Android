package com.w3n.pinggo.call;

import android.app.Activity;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.w3n.pinggo.views.common.NativePromptDialogView;
import java.util.ArrayList;
import java.util.List;

/** Native AAR per-call connection picker. */
public final class CallEngineChooser {
  private static final String VIEW_TAG = "pinggo_call_engine_chooser";

  public interface Listener {
    void onSelected(String engine);
  }

  private CallEngineChooser() { }

  public static void show(
      Activity activity, String mediaType, String chatId, Listener listener) {
    boolean group = chatId != null && chatId.startsWith("grp_");
    show(activity, mediaType, !group, listener);
  }

  public static void show(
      Activity activity, String mediaType, boolean allowLegacy, Listener listener) {
    if (activity == null || listener == null
        || activity.isFinishing() || activity.isDestroyed()) return;
    ViewGroup root = activity.findViewById(android.R.id.content);
    if (root == null || root.findViewWithTag(VIEW_TAG) != null) return;

    String callType = "video".equals(mediaType) ? "video" : "voice";
    List<String> choices = new ArrayList<>();
    choices.add("LiveKit " + callType + " call");
    if (allowLegacy) choices.add("Legacy WebRTC " + callType + " call");
    choices.add("Cancel");

    NativePromptDialogView[] current = {null};
    OnBackPressedCallback[] back = {null};
    LifecycleEventObserver[] observer = {null};
    Runnable dismiss = () -> {
      NativePromptDialogView view = current[0];
      current[0] = null;
      if (back[0] != null) back[0].remove();
      if (activity instanceof ComponentActivity && observer[0] != null) {
        ((ComponentActivity) activity).getLifecycle().removeObserver(observer[0]);
      }
      if (view != null) {
        if (view.getParent() instanceof ViewGroup) {
          ((ViewGroup) view.getParent()).removeView(view);
        }
        view.release();
      }
    };
    current[0] = NativePromptDialogView.actions(activity, choices, index -> {
      if (index == choices.size() - 1) return;
      listener.onSelected(index == 0 ? CallEngineToggle.LIVEKIT : CallEngineToggle.LEGACY);
    }, dismiss);
    current[0].setTag(VIEW_TAG);
    root.addView(current[0], new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (activity instanceof ComponentActivity) {
      ComponentActivity owner = (ComponentActivity) activity;
      back[0] = new OnBackPressedCallback(true) {
        @Override public void handleOnBackPressed() {
          dismiss.run();
        }
      };
      owner.getOnBackPressedDispatcher().addCallback(owner, back[0]);
      observer[0] = (source, event) -> {
        if (event == Lifecycle.Event.ON_DESTROY) dismiss.run();
      };
      owner.getLifecycle().addObserver(observer[0]);
    }
  }
}
