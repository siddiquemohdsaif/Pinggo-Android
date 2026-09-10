package com.w3n.pinggo.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.API;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.DeviceIdentityManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.UserData;
import com.w3n.pinggo.notification.FcmTokenManager;
import com.w3n.pinggo.views.linkeddevice.NativeCompanionLinkView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Displays a short-lived QR and resumes pairing while its secret is still valid. */
public final class CompanionLinkActivity extends AppCompatActivity {
    private static final String PREFS = "PendingDeviceLink";
    private static final String REQUEST_ID = "requestId";
    private static final String SECRET = "secret";
    private static final String EXPIRES = "expiresAt";
    private static final long POLL_MS = 2000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = this::poll;
    private AppRestAPI api;
    private NativeCompanionLinkView companionView;
    private String requestId = "";
    private String secret = "";
    private long expiresAt;
    private boolean requestRunning;
    private boolean completing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        api = (AppContextProvider.isDevelopment ? API.devRetrofit : API.retrofit)
                .create(AppRestAPI.class);
        companionView = new NativeCompanionLinkView(this,
                getString(R.string.waiting_for_approval), this::finish);
        setContentView(companionView);
        ViewCompat.setOnApplyWindowInsetsListener(companionView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            companionView.setInsets(bars.top, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(companionView);
        restoreOrCreate();
    }

    private void restoreOrCreate() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        requestId = prefs.getString(REQUEST_ID, "");
        secret = prefs.getString(SECRET, "");
        expiresAt = prefs.getLong(EXPIRES, 0L);
        if (!requestId.isEmpty() && !secret.isEmpty() && expiresAt > System.currentTimeMillis()) {
            showQr();
            schedulePoll(0);
        } else {
            clearPending();
            createRequest();
        }
    }

    private void createRequest() {
        if (requestRunning) return;
        requestRunning = true;
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", DeviceIdentityManager.getDeviceId(this));
        body.addProperty("deviceName", DeviceIdentityManager.getDeviceName());
        body.addProperty("platform", "android");
        api.createDeviceLink(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                requestRunning = false;
                JsonObject value = response.body();
                if (!response.isSuccessful() || value == null) {
                    fail(getString(R.string.invalid_link_qr));
                    return;
                }
                requestId = string(value, "linkRequestId");
                secret = string(value, "pairingSecret");
                expiresAt = number(value, "expiresAt");
                if (requestId.isEmpty() || secret.isEmpty() || expiresAt == 0) {
                    fail(getString(R.string.invalid_link_qr));
                    return;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(REQUEST_ID, requestId).putString(SECRET, secret)
                        .putLong(EXPIRES, expiresAt).commit();
                showQr();
                schedulePoll(POLL_MS);
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                requestRunning = false;
                fail(error.getMessage());
            }
        });
    }

    private void showQr() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "pinggo_device_link");
        payload.addProperty("version", 1);
        payload.addProperty("linkRequestId", requestId);
        payload.addProperty("pairingSecret", secret);
        payload.addProperty("expiresAt", expiresAt);
        try {
            int size = Math.max(600, Math.min(1000, getResources().getDisplayMetrics().widthPixels));
            BitMatrix matrix = new MultiFormatWriter().encode(payload.toString(), BarcodeFormat.QR_CODE,
                    size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            companionView.setQrBitmap(bitmap);
            companionView.setStatus(getString(R.string.waiting_for_approval));
            companionView.setLoading(true);
        } catch (Exception error) {
            fail(error.getMessage());
        }
    }

    private void poll() {
        if (isFinishing() || completing || requestRunning) return;
        if (System.currentTimeMillis() >= expiresAt) {
            companionView.setStatus(getString(R.string.link_expired_retrying));
            clearPending();
            createRequest();
            return;
        }
        JsonObject body = secretBody();
        api.getDeviceLinkStatus(requestId, body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                JsonObject value = response.body();
                if (response.code() == 410) {
                    clearPending(); createRequest(); return;
                }
                if (response.isSuccessful() && value != null
                        && "approved".equals(string(value, "status"))) {
                    complete();
                } else schedulePoll(POLL_MS);
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                schedulePoll(POLL_MS);
            }
        });
    }

    private void complete() {
        if (completing) return;
        completing = true;
        companionView.setStatus("Finishing device link…");
        api.completeDeviceLink(requestId, secretBody()).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                JsonObject value = response.body();
                if (!response.isSuccessful() || value == null) {
                    completing = false;
                    companionView.setStatus(getString(R.string.waiting_for_approval));
                    schedulePoll(POLL_MS);
                    return;
                }
                String accountId = string(value, "accountId");
                String credential = string(value, "encryptedCredential");
                if (accountId.isEmpty() || credential.isEmpty()) {
                    completing = false;
                    companionView.setStatus(getString(R.string.waiting_for_approval));
                    schedulePoll(POLL_MS);
                    return;
                }
                UserData userData = value.has("userData") && value.get("userData").isJsonObject()
                        ? UserData.fromJson(value.getAsJsonObject("userData").toString()) : null;
                // Persist the new session before any history request. If sync fails,
                // the normal socket reconnect path resumes it without another scan.
                LoginStateManager.getInstance().setCompanionLogin(
                        CompanionLinkActivity.this, accountId, credential, userData);
                clearPending();
                AppFunctionManager.getInstance().applyAuth(CompanionLinkActivity.this);
                FcmTokenManager.refreshAndUpload(getApplicationContext());
                ProfilePhotoLocalStore.downloadAndStore(getApplicationContext(),
                        LoginStateManager.getInstance().getUserDataModal(CompanionLinkActivity.this));
                ChatRepository.getInstance(getApplicationContext()).connect();
                Intent home = new Intent(CompanionLinkActivity.this, HomeActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(home);
                finish();
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                completing = false;
                companionView.setStatus(getString(R.string.waiting_for_approval));
                schedulePoll(POLL_MS);
            }
        });
    }

    private JsonObject secretBody() {
        JsonObject body = new JsonObject(); body.addProperty("pairingSecret", secret); return body;
    }
    private void schedulePoll(long delay) { handler.removeCallbacks(pollTask); handler.postDelayed(pollTask, delay); }
    private void clearPending() { getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().commit(); requestId = ""; secret = ""; expiresAt = 0; }
    private void fail(String message) {
        String value = message == null ? "Link failed" : message;
        if (companionView != null) companionView.setStatus(value);
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (companionView != null) companionView.release();
        super.onDestroy();
    }
    private static String string(JsonObject value, String key) { try { return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : ""; } catch (Exception ignored) { return ""; } }
    private static long number(JsonObject value, String key) { try { return value.get(key).getAsLong(); } catch (Exception ignored) { return 0; } }
}
