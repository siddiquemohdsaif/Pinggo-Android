package com.w3n.pinggo.linkeddevice;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.DeviceIdentityManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.LogoutDataCleaner;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guards entry into companion mode when this installation already has an account.
 * Scanning is performed on the primary phone and must never call this guard.
 */
public final class LinkedDeviceAccountSwitchGuard {
    private static final AtomicBoolean LOGOUT_IN_PROGRESS = new AtomicBoolean(false);

    private LinkedDeviceAccountSwitchGuard() {}

    public static void proceedToCompanionLink(Activity activity, Runnable completeLink) {
        if (activity == null || activity.isFinishing() || completeLink == null) return;
        if (!LoginStateManager.getInstance().isLoggedIn(activity)) {
            completeLink.run();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.link_device_logout_title)
                .setMessage(R.string.link_device_logout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout_and_link,
                        (dialog, which) -> logoutThenContinue(activity, completeLink))
                .show();
    }

    private static void logoutThenContinue(Activity activity, Runnable completeLink) {
        if (!LOGOUT_IN_PROGRESS.compareAndSet(false, true)) return;
        String uid = LoginStateManager.getInstance().getUID(activity);
        String encryptedCredential = LoginStateManager.getInstance().getENC(activity);
        String deviceId = DeviceIdentityManager.getDeviceId(activity);
        new Thread(() -> {
            try {
                // Remove this installation from the old account before discarding
                // its credential. This also closes its old server-side socket and
                // stops old-account push delivery to the installation.
                AppRestAPI oldAccountApi = new APIAuth(uid + "_" + encryptedCredential)
                        .getRetrofit().create(AppRestAPI.class);
                retrofit2.Response<JsonObject> revoke = oldAccountApi.unlinkDevice(deviceId).execute();
                if (!revoke.isSuccessful() && revoke.code() != 404) {
                    activity.runOnUiThread(() -> android.widget.Toast.makeText(activity,
                            R.string.link_device_logout_failed,
                            android.widget.Toast.LENGTH_LONG).show());
                    return;
                }
                // Disconnect the old account before deleting its Room rows and session.
                LogoutDataCleaner.clear(activity.getApplicationContext());
                LoginStateManager.getInstance().logOut(activity.getApplicationContext());
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) completeLink.run();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> android.widget.Toast.makeText(activity,
                        R.string.link_device_logout_failed,
                        android.widget.Toast.LENGTH_LONG).show());
            } finally {
                LOGOUT_IN_PROGRESS.set(false);
            }
        }, "pinggo-link-account-switch").start();
    }
}
