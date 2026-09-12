package com.w3n.pinggo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.activity.VoiceCallActivity;
import com.w3n.pinggo.activity.VideoCallActivity;
import com.w3n.pinggo.activity.LiveKitCallActivity;
import com.w3n.pinggo.call.CallEngineToggle;
import com.w3n.pinggo.call.FloatingVoiceCallController;
import com.w3n.pinggo.call.FloatingVideoCallController;
import com.w3n.pinggo.call.WebRTCCallClient;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.data.cache.KeyboardHeightCache;
import com.w3n.pinggo.modals.AppConfiguration;
import com.w3n.pinggo.notification.PingGoNotificationManager;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import java.util.ArrayList;
import com.w3n.pinggo.views.common.NativeMessageView;

import org.json.JSONObject;


public class AppContextProvider extends Application implements ChatRepository.IncomingCallListener {
    private static final FigmaConfig FIGMA_CONFIG = new FigmaConfig(1080f);
    private static Context appContext;
    private static volatile JSONObject appConfig;
    private static volatile AppConfiguration parsedAppConfig;
    public static boolean isDevelopment = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the app context when the application starts
        appContext = getApplicationContext();
        KeyboardHeightCache.initialize(this);
        PingGoNotificationManager.createChannels(this);
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
        boolean liveKit = "livekit".equals(JsonParserUtil.getString(event, "engine"));
        if (!liveKit && sdp == null) {
            Log.e("PingGoCallTrace", "invite_rejected_missing_sdp event=" + event);
            return;
        }
        String chatId = JsonParserUtil.getString(event, "chatId");
        String callId = JsonParserUtil.getString(event, "callId");
        String requestedAction = PingGoNotificationManager.consumeCallAction(this, callId);
        Log.i("PingGoCallTrace", "invite_received callId=" + callId + " chatId=" + chatId
                + " engine=" + (liveKit ? "livekit" : "legacy")
                + " requestedAction=" + requestedAction + " activeChat="
                + ChatRepository.getInstance(this).isActiveChat(chatId));
        if (PingGoNotificationManager.ACTION_CALL_DECLINE.equals(requestedAction)) return;
        boolean answerRequested = PingGoNotificationManager.ACTION_CALL_ANSWER.equals(requestedAction);
        boolean openRequested = PingGoNotificationManager.ACTION_CALL_OPEN.equals(requestedAction);
        if (!answerRequested && !openRequested
                && !ChatRepository.getInstance(this).isActiveChat(chatId)) {
            PingGoNotificationManager.showIncomingCallNotification(this, event);
            Log.i("PingGoCallTrace", "invite_routed_to_notification callId=" + callId);
            return;
        }
        String callerId = JsonParserUtil.getString(event, "callerId");
        boolean video = "video".equals(JsonParserUtil.getString(event, "mediaType"));
        boolean conference = "group".equals(JsonParserUtil.getString(event, "callMode"))
                || JsonParserUtil.getBoolean(event, "conference");
        Intent intent = new Intent(this, liveKit ? LiveKitCallActivity.class
                : (video ? VideoCallActivity.class : VoiceCallActivity.class));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
                DeviceContactResolver.nameOrPhone(this, callerId));
        intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH,
                ChatProfilePhotoStore.getLocalPath(this, callerId));
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID,
                callId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, callerId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID,
                chatId);
        intent.putExtra(VoiceCallActivity.EXTRA_SDP_OFFER,
                sdp == null ? "" : WebRTCCallClient.decodeSdp(sdp));
        intent.putExtra(LiveKitCallActivity.EXTRA_MEDIA_TYPE, video ? "video" : "audio");
        intent.putExtra(LiveKitCallActivity.EXTRA_INCOMING, true);
        intent.putExtra(LiveKitCallActivity.EXTRA_CONFERENCE_CALL, conference);
        if (event.has("participantIds") && event.get("participantIds").isJsonArray()) {
            ArrayList<String> participantIds = new ArrayList<>();
            for (com.google.gson.JsonElement value : event.getAsJsonArray("participantIds"))
                if (value != null && value.isJsonPrimitive()) participantIds.add(value.getAsString());
            intent.putStringArrayListExtra(LiveKitCallActivity.EXTRA_PARTICIPANT_IDS,
                    participantIds);
        }
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_ENGINE,
                liveKit ? CallEngineToggle.LIVEKIT : CallEngineToggle.LEGACY);
        intent.putExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, answerRequested);
        Log.i("PingGoCallTrace", "activity_launch_requested callId=" + callId
                + " media=" + (video ? "video" : "audio") + " autoAccept=" + answerRequested
                + " engine=" + (liveKit ? "livekit" : "legacy")
                + " conference=" + conference
                + " offerLength=" + (sdp == null ? 0 : WebRTCCallClient.decodeSdp(sdp).length()));
        try {
            startActivity(intent);
            Log.i("PingGoCallTrace", "activity_launch_dispatched callId=" + callId);
        } catch (RuntimeException error) {
            Log.e("PingGoCallTrace", "activity_launch_failed callId=" + callId, error);
        }
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
                Math.round(FIGMA_CONFIG.toRuntime(198f,
                        activity.getResources().getDisplayMetrics().widthPixels)),
                Math.round(FIGMA_CONFIG.toRuntime(110f,
                        activity.getResources().getDisplayMetrics().widthPixels))
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(16, 50, 16, 16); // adjust top margin if needed

        // Post to ensure it's added after layout is ready
        decorView.post(() -> decorView.addView(devLabel, params));
    }

}
