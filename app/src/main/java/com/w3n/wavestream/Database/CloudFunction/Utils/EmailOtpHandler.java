package com.w3n.wavestream.Database.CloudFunction.Utils;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.RestApi.AppRestAPI;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Handles the email OTP API contract. */
public final class EmailOtpHandler {
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private EmailOtpHandler() {
    }

    public static void send(AppRestAPI appApi, String email,
                            AppFunctionManager.Callback callback) {
        enqueue(createCall(appApi, email, null, Action.SEND, callback), callback);
    }

    public static void verify(AppRestAPI appApi, String email, String code,
                              AppFunctionManager.Callback callback) {
        enqueue(createCall(appApi, email, code, Action.VERIFY, callback), callback);
    }

    public static void resend(AppRestAPI appApi, String email,
                              AppFunctionManager.Callback callback) {
        enqueue(createCall(appApi, email, null, Action.RESEND, callback), callback);
    }

    private static Call<JsonObject> createCall(AppRestAPI appApi, String email,
                                                String code, Action action,
                                                AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("email", email == null ? "" : email.trim());
            if (action == Action.VERIFY) {
                jsonObject.put("code", code == null ? "" : code.trim());
            }
        } catch (JSONException error) {
            if (callback != null) callback.onError("json error:" + error);
            return null;
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), JSON_MEDIA_TYPE);
        if (action == Action.VERIFY) return appApi.emailVerify(body);
        if (action == Action.RESEND) return appApi.emailResend(body);
        return appApi.emailSend(body);
    }

    private static void enqueue(Call<JsonObject> call,
                                AppFunctionManager.Callback callback) {
        if (call == null) return;
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (callback == null) return;
                if (!response.isSuccessful()) {
                    callback.onError(getErrorMessage(response));
                    return;
                }

                JsonObject body = response.body();
                if (body == null) {
                    callback.onError("Empty server response.");
                    return;
                }
                if (!getBoolean(body, "success")) {
                    callback.onError(getString(body, "message", "Email OTP request failed."));
                    return;
                }
                callback.onSuccess(body);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                if (callback != null) {
                    String message = error.getMessage();
                    callback.onError(message == null || message.isEmpty()
                            ? "Network request failed." : message);
                }
            }
        });
    }

    private static String getErrorMessage(Response<JsonObject> response) {
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                JSONObject error = new JSONObject(errorBody.string());
                String message = error.optString("message");
                if (!message.isEmpty()) return message;
            } catch (Exception ignored) {
            }
        }

        String message = response.message();
        return message == null || message.isEmpty()
                ? "Request failed. Code: " + response.code() : message;
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        String value = element.getAsString();
        return value == null || value.isEmpty() ? fallback : value;
    }

    private enum Action {
        SEND,
        VERIFY,
        RESEND
    }
}
