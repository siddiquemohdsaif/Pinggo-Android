package com.w3n.pinggo.notification;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessaging;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.repository.ChatRepository;

public final class FcmTokenManager {
    private static final String PREFS = "PingGoFcmState";
    private static final String TOKEN = "registrationToken";

    private FcmTokenManager() { }

    public static void refreshAndUpload(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                saveAndUpload(appContext, token));
    }

    public static void saveAndUpload(@NonNull Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(TOKEN, token.trim()).apply();
        uploadIfLoggedIn(appContext, token.trim());
    }

    public static void uploadSavedToken(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences =
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        uploadIfLoggedIn(appContext, preferences.getString(TOKEN, null));
    }

    private static void uploadIfLoggedIn(Context context, String token) {
        if (token == null || token.isEmpty()
                || !LoginStateManager.getInstance().isLoggedIn(context)) return;
        AppFunctionManager.getInstance().applyAuth(context);
        ChatRepository.getInstance(context).uploadFcmToken(token);
    }
}
