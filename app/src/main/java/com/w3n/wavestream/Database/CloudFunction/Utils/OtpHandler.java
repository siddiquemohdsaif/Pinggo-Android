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

public class OtpHandler {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    public static final int RETRY_CHANNEL_SMS = 11;

    public static class OtpResult {
        private final String reqId;
        private final String provider;
        private final String verificationToken;

        public OtpResult(String reqId, String provider, String verificationToken) {
            this.reqId = reqId;
            this.provider = provider;
            this.verificationToken = verificationToken;
        }

        public String getReqId() {
            return reqId;
        }

        public String getProvider() {
            return provider;
        }

        public String getVerificationToken() {
            return verificationToken;
        }
    }

    public static void sendOtp(AppRestAPI appApi, String identifier, AppFunctionManager.Callback callback) {
        sendOtp(appApi,identifier,null,callback);
    }

    public static void sendOtp(AppRestAPI appApi, String identifier, String provider, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("identifier", normalizeIdentifier(identifier));
            if (provider != null && !provider.trim().isEmpty()) {
                jsonObject.put("provider", provider.trim());
            }
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueOtpCall("sendOtp", appApi.sendOtp(createJsonBody(jsonObject)), callback);
    }

    public static void verifyOtp(AppRestAPI appApi, String reqId, String otp, AppFunctionManager.Callback callback) {
        verifyOtp(appApi, reqId, null, otp, callback);
    }

    public static void verifyOtp(AppRestAPI appApi, String reqId, String provider, String otp, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("reqId", reqId);
            if (provider != null && !provider.trim().isEmpty()) {
                jsonObject.put("provider", provider.trim());
            }
            jsonObject.put("otp", otp);
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueOtpCall("verifyOtp", appApi.verifyOtp(createJsonBody(jsonObject)), callback);
    }

    public static void retryOtp(AppRestAPI appApi, String reqId, int retryChannel, AppFunctionManager.Callback callback) {
        retryOtp(appApi, reqId, null, retryChannel, callback);
    }

    public static void retryOtp(AppRestAPI appApi, String reqId, String provider, int retryChannel, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("reqId", reqId);
            if (provider != null && !provider.trim().isEmpty()) {
                jsonObject.put("provider", provider.trim());
            }
            jsonObject.put("retryChannel", retryChannel);
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueOtpCall("retryOtp", appApi.retryOtp(createJsonBody(jsonObject)), callback);
    }

    private static void enqueueOtpCall(String action, Call<JsonObject> call, AppFunctionManager.Callback callback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleOtpResponse(response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static void handleOtpResponse(Response<JsonObject> response, AppFunctionManager.Callback callback) {
        if (callback == null) {
            return;
        }

        if (!response.isSuccessful()) {
            String errorMessage = getErrorMessage(response);
            callback.onError(errorMessage);
            return;
        }

        JsonObject responseBody = response.body();
        if (responseBody == null) {
            callback.onError("Empty server response.");
            return;
        }

        if (!getBoolean(responseBody, "success")) {
            String errorMessage = getString(responseBody, "message", "OTP request failed.");
            callback.onError(errorMessage);
            return;
        }

        callback.onSuccess(parseOtpResult(responseBody));
    }

    private static RequestBody createJsonBody(JSONObject jsonObject) {
        return RequestBody.create(jsonObject.toString(), JSON_MEDIA_TYPE);
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String normalized = identifier.trim();
        if (normalized.startsWith("+")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private static void handleJsonError(JSONException e, AppFunctionManager.Callback callback) {
        if (callback != null) {
            callback.onError("json error:" + e);
        }
    }

    private static String getErrorMessage(Response<JsonObject> response) {
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                String errorJson = errorBody.string();
                JSONObject jsonObject = new JSONObject(errorJson);
                String message = jsonObject.optString("message");
                if (!message.isEmpty()) {
                    return message;
                }
            } catch (Exception ignored) {
            }
        }

        String message = response.message();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return "Request failed. Code: " + response.code();
    }

    private static String getFailureMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isEmpty()) {
            return "Network request failed.";
        }
        return throwable.getMessage();
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        String value = element.getAsString();
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static OtpResult parseOtpResult(JsonObject object) {
        return new OtpResult(
                getString(object, "reqId", getString(object, "requestId", getString(object, "message", ""))),
                getString(object, "provider", ""),
                getString(object, "verificationToken", "")
        );
    }

}
