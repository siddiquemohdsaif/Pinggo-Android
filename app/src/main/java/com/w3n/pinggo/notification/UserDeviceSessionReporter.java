package com.w3n.pinggo.notification;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.DeviceIdentityManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Reports account-device login separately from companion pairing. */
public final class UserDeviceSessionReporter {
    private UserDeviceSessionReporter() { }
    public static void onLogin(Context context) {
        Context app = context.getApplicationContext();
        LoginStateManager state = LoginStateManager.getInstance();
        if (!state.isLoggedIn(app)) return;
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", DeviceIdentityManager.getDeviceId(app));
        body.addProperty("name", DeviceIdentityManager.getDeviceName());
        body.addProperty("platform", "android");
        try {
            body.addProperty("appVersion", app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0).versionName);
        } catch (Exception ignored) { }
        String token = FcmTokenManager.getSavedToken(app);
        if (!token.isEmpty()) body.addProperty("fcmToken", token);
        AppRestAPI api = new APIAuth(state.getUID(app) + "_" + state.getENC(app))
                .getRetrofit().create(AppRestAPI.class);
        api.recordDeviceLogin(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) Log.w("UserDeviceInfo", "Login reporting failed: " + response.code());
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call,
                    @NonNull Throwable error) {
                Log.w("UserDeviceInfo", "Login reporting unavailable");
            }
        });
    }
}
