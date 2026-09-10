package com.w3n.pinggo.data.local;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.activity.LoginActivity;
import com.w3n.pinggo.notification.PingGoNotificationManager;

import java.util.concurrent.atomic.AtomicBoolean;

/** Applies local logout once for server revocation, remote logout, or user logout. */
public final class SessionLogoutManager {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final String INSTALLATION_PREFS = "PinggoDeviceIdentity";
    private static final String PENDING_LOGOUT_MESSAGE = "pendingLogoutMessage";

    private SessionLogoutManager() {}

    public static void forceLogout(Context context) {
        forceLogout(context, "");
    }

    public static void forceLogout(Context context, String message) {
        if (context == null || !RUNNING.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        String logoutMessage = message == null ? "" : message.trim();
        if (!logoutMessage.isEmpty()) {
            appContext.getSharedPreferences(INSTALLATION_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(PENDING_LOGOUT_MESSAGE, logoutMessage).commit();
        }
        // Lock the UI out synchronously. Room/file cleanup may take longer, but
        // revoked content must disappear as soon as WebSocket or FCM reports it.
        LoginStateManager.getInstance().logOut(appContext);
        NotificationManager notifications =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancelAll();
        Intent login = new Intent(appContext, LoginActivity.class);
        if (!logoutMessage.isEmpty()) {
            login.putExtra(LoginActivity.EXTRA_LOGOUT_MESSAGE, logoutMessage);
        }
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try { appContext.startActivity(login); } catch (RuntimeException ignored) {}
        if (!logoutMessage.isEmpty()) {
            PingGoNotificationManager.showSessionLogoutNotification(appContext, logoutMessage);
        }
        new Thread(() -> {
            try {
                LogoutDataCleaner.clear(appContext);
            } finally {
                RUNNING.set(false);
            }
        }, "pinggo-session-logout").start();
    }

    public static String consumeLogoutMessage(Context context) {
        if (context == null) return "";
        android.content.SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(INSTALLATION_PREFS, Context.MODE_PRIVATE);
        String message = preferences.getString(PENDING_LOGOUT_MESSAGE, "");
        preferences.edit().remove(PENDING_LOGOUT_MESSAGE).apply();
        return message == null ? "" : message.trim();
    }
}
