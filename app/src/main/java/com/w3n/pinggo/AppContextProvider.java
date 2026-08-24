package com.w3n.pinggo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.activity.VoiceCallActivity;
import com.w3n.pinggo.activity.VideoCallActivity;
import com.w3n.pinggo.call.FloatingVoiceCallController;
import com.w3n.pinggo.call.FloatingVideoCallController;
import com.w3n.pinggo.call.WebRTCCallClient;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.AppConfiguration;
import com.w3n.pinggo.views.common.NativeMessageView;

import org.json.JSONObject;


public class AppContextProvider extends Application implements ChatRepository.IncomingCallListener {
    private static Context appContext;
    private static volatile JSONObject appConfig;
    private static volatile AppConfiguration parsedAppConfig;
    public static boolean isDevelopment = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the app context when the application starts
        appContext = getApplicationContext();
        FloatingVoiceCallController.getInstance().initialize(this);
        FloatingVideoCallController.getInstance().initialize(this);

        // Keep one authenticated WebSocket for the complete logged-in app
        // session instead of waiting for an individual chat screen to open.
        ChatRepository repository = ChatRepository.getInstance(this);
        repository.setIncomingCallListener(this);
        if (LoginStateManager.getInstance().isLoggedIn(this)) repository.connect();

        isDevelopment = false;
        devOverlay(isDevelopment);


    }

    @Override
    public void onIncomingCall(JsonObject event) {
        JsonObject sdp = event.has("sdp") && event.get("sdp").isJsonObject()
                ? event.getAsJsonObject("sdp") : null;
        if (sdp == null) return;
        String callerId = JsonParserUtil.getString(event, "callerId");
        boolean video = "video".equals(JsonParserUtil.getString(event, "mediaType"));
        Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
                callerId.isEmpty() ? "Unknown" : "+" + callerId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID,
                JsonParserUtil.getString(event, "callId"));
        intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, callerId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID,
                JsonParserUtil.getString(event, "chatId"));
        intent.putExtra(VoiceCallActivity.EXTRA_SDP_OFFER,
                WebRTCCallClient.decodeSdp(sdp));
        startActivity(intent);
    }

    private void devOverlay(boolean isDevelopment) {
        if (isDevelopment) {
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    addDevOverlay(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityResumed(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        }
    }

    public static Context getAppContext() {
        return appContext;
    }

    public static JSONObject getAppConfig() {
        return appConfig;
    }

    public static AppConfiguration getParsedAppConfig() {
        return parsedAppConfig;
    }

    public static boolean setAppConfig(JSONObject appConfig) {
        try {
            AppConfiguration parsedConfig = new Gson().fromJson(
                    appConfig.toString(), AppConfiguration.class);
            if (parsedConfig == null || parsedConfig.getLoginOption() == null) return false;
            AppContextProvider.appConfig = appConfig;
            parsedAppConfig = parsedConfig;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void addDevOverlay(Activity activity) {
        FrameLayout decorView = (FrameLayout) activity.getWindow().getDecorView();

        // Check if already added
        final String tag = "DEV_OVERLAY_TAG";
        View existingView = decorView.findViewWithTag(tag);
        if (existingView != null) {
            return; // already added
        }

        NativeMessageView devLabel = new NativeMessageView(activity, "DEV", Color.WHITE, 18f);
        devLabel.setTag(tag);
        devLabel.setBackgroundColor(Color.RED);
        devLabel.setAlpha(0.8f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.round(72 * activity.getResources().getDisplayMetrics().density),
                Math.round(40 * activity.getResources().getDisplayMetrics().density)
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(16, 50, 16, 16); // adjust top margin if needed

        // Post to ensure it's added after layout is ready
        decorView.post(() -> decorView.addView(devLabel, params));
    }

}
