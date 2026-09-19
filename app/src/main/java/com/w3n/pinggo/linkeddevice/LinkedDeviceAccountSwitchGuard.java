package com.w3n.pinggo.linkeddevice;

import android.app.Activity;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.w3n.pinggo.views.common.NativePromptDialogView;

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

        ViewGroup root = activity.findViewById(android.R.id.content);
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
                root.removeView(view);
                view.release();
            }
        };
        current[0] = NativePromptDialogView.confirm(activity,
                activity.getString(R.string.link_device_logout_title),
                activity.getString(R.string.link_device_logout_message),
                activity.getString(R.string.logout_and_link),
                () -> logoutThenContinue(activity, completeLink), dismiss);
        root.addView(current[0], new ViewGroup.LayoutParams(-1, -1));
        if (activity instanceof ComponentActivity) {
            ComponentActivity owner = (ComponentActivity) activity;
            back[0] = new OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() { dismiss.run(); }
            };
            owner.getOnBackPressedDispatcher().addCallback(owner, back[0]);
            observer[0] = (source, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) dismiss.run();
            };
            owner.getLifecycle().addObserver(observer[0]);
        }
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
