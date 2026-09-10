package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.DeviceIdentityManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.linkeddevice.LinkedDeviceAccountSwitchGuard;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.linkeddevice.NativeLinkedDevicesView;
import com.w3n.pinggo.notification.FcmTokenManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Lists the installations registered to the account and allows remote logout. */
public final class LinkedDevicesActivity extends AppCompatActivity {
    private final ActivityResultLauncher<Intent> qrScanner = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    onPairingCodeScanned(data.getStringExtra(QrScannerActivity.EXTRA_RESULT));
                }
            });
    private NativeLinkedDevicesView linkedView;
    private BlockingProgressView blockingProgress;
    private AppRestAPI api;
    private String currentDeviceId;

    /** Successful camera scans enter here. This approves the companion; it does not log out primary. */
    public void onPairingCodeScanned(String rawValue) {
        JsonObject payload;
        try {
            JsonElement parsed = JsonParser.parseString(rawValue == null ? "" : rawValue);
            payload = parsed.getAsJsonObject();
        } catch (Exception ignored) {
            showError(getString(R.string.invalid_link_qr));
            return;
        }
        String type = value(payload, "type");
        String linkRequestId = value(payload, "linkRequestId");
        String pairingSecret = value(payload, "pairingSecret");
        if (!"pinggo_device_link".equals(type) || number(payload, "version") != 1
                || linkRequestId.isEmpty() || pairingSecret.isEmpty()
                || number(payload, "expiresAt") <= System.currentTimeMillis()) {
            showError(getString(R.string.invalid_link_qr));
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("pairingSecret", pairingSecret);
        body.addProperty("approvingDeviceId", currentDeviceId);
        api.approveDeviceLink(linkRequestId, body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LinkedDevicesActivity.this,
                            "Device linked. This phone remains signed in.", Toast.LENGTH_LONG).show();
                    load();
                } else {
                    showError(response.code() == 410
                            ? getString(R.string.link_expired_retrying)
                            : "Could not approve this linking code.");
                }
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call,
                    @NonNull Throwable error) {
                showError("Could not approve this linking code.");
            }
        });
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (LoginStateManager.getInstance().isCompanionDevice(this)) {
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        currentDeviceId = DeviceIdentityManager.getDeviceId(this);
        String token = LoginStateManager.getInstance().getUID(this) + "_"
                + LoginStateManager.getInstance().getENC(this);
        api = new APIAuth(token).getRetrofit().create(AppRestAPI.class);

        linkedView = new NativeLinkedDevicesView(this, new NativeLinkedDevicesView.Listener() {
            @Override public void onBack() { finish(); }
            @Override public void onScanQr() { startQrScanner(); }
            @Override public void onUseAsCompanion() {
                LinkedDeviceAccountSwitchGuard.proceedToCompanionLink(
                        LinkedDevicesActivity.this, LinkedDevicesActivity.this::launchCompanionMode);
            }
            @Override public void onLogoutDevice(String deviceId) { unlink(deviceId); }
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(linkedView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        blockingProgress = new BlockingProgressView(this);
        frame.addView(blockingProgress, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        ViewCompat.setOnApplyWindowInsetsListener(frame, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            linkedView.setInsets(bars.top, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(frame);
        registerAndLoad();
    }

    @Override protected void onResume() {
        super.onResume();
        ChatRepository.getInstance(getApplicationContext())
                .setDeviceEventListener(this::load);
    }

    @Override protected void onPause() {
        ChatRepository.getInstance(getApplicationContext()).setDeviceEventListener(null);
        super.onPause();
    }

    private void startQrScanner() {
        qrScanner.launch(new Intent(this, QrScannerActivity.class));
    }

    private void launchCompanionMode() {
        Intent intent = new Intent(this, CompanionLinkActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void registerAndLoad() {
        JsonObject body = new JsonObject();
        body.addProperty("deviceId", currentDeviceId);
        body.addProperty("name", DeviceIdentityManager.getDeviceName());
        body.addProperty("platform", "android");
        String fcmToken = FcmTokenManager.getSavedToken(this);
        if (!fcmToken.isEmpty()) body.addProperty("fcmToken", fcmToken);
        api.registerLinkedDevice(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) { load(); }
            @Override public void onFailure(@NonNull Call<JsonObject> call,
                    @NonNull Throwable error) { load(); }
        });
    }

    private void load() {
        linkedView.showLoading(true);
        api.getLinkedDevices().enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    linkedView.showLoading(false);
                    showError("Could not load linked devices.");
                    return;
                }
                render(response.body().getAsJsonArray("devices"));
            }

            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                linkedView.showLoading(false);
                showError(error.getMessage() == null ? "Could not load linked devices." : error.getMessage());
            }
        });
    }

    private void render(JsonArray devices) {
        List<NativeLinkedDevicesView.DeviceItem> items = new ArrayList<>();
        if (devices != null) for (JsonElement element : devices) {
            if (!element.isJsonObject()) continue;
            JsonObject device = element.getAsJsonObject();
            String id = value(device, "deviceId");
            boolean current = currentDeviceId.equals(id);
            long lastSeen = number(device, "lastSeenAt");
            String detail = value(device, "role") + " • "
                    + (lastSeen == 0 ? "Never active" : "Last active "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(lastSeen)));
            items.add(new NativeLinkedDevicesView.DeviceItem(id, value(device, "name"), detail, current));
        }
        linkedView.submitDevices(items);
    }

    private void unlink(String deviceId) {
        blockingProgress.setLoading(true);
        api.unlinkDevice(deviceId).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call,
                    @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(LinkedDevicesActivity.this, "Device logged out", Toast.LENGTH_SHORT).show();
                    blockingProgress.setLoading(false);
                    load();
                } else {
                    blockingProgress.setLoading(false);
                    showError("Could not log out that device.");
                }
            }
            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                blockingProgress.setLoading(false);
                showError("Could not log out that device.");
            }
        });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (linkedView != null) linkedView.release();
        super.onDestroy();
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static long number(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsLong() : 0; }
        catch (Exception ignored) { return 0; }
    }
}
