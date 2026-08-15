package com.w3n.pinggo.Database.CloudFunction.Utils;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Sends a Google ID token to PingGo's server and returns only server-verified identity data. */
public final class GoogleAuthHandler {
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private GoogleAuthHandler() {
    }

    public static void verify(AppRestAPI appApi, String idToken,
                              AppFunctionManager.Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("idToken", idToken == null ? "" : idToken.trim());
        } catch (Exception error) {
            if (callback != null) callback.onError("Could not create Google auth request.");
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        appApi.verifyGoogleIdToken(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (callback == null) return;
                JsonObject responseBody = response.body();
                if (!response.isSuccessful() || responseBody == null
                        || !getBoolean(responseBody, "success")) {
                    callback.onError(errorMessage(response, responseBody));
                    return;
                }

                String email = getString(responseBody, "email");
                String googleSubject = getString(responseBody, "googleSubject");
                if (email.isEmpty() || googleSubject.isEmpty()) {
                    callback.onError("Server returned incomplete Google account data.");
                    return;
                }
                callback.onSuccess(new VerifiedGoogleAccount(email, googleSubject));
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable error) {
                if (callback == null) return;
                String message = error.getMessage();
                callback.onError(message == null || message.isEmpty()
                        ? "Google verification request failed." : message);
            }
        });
    }

    private static String errorMessage(Response<JsonObject> response, JsonObject body) {
        String message = getString(body, "message");
        if (!message.isEmpty()) return message;
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                String serverMessage = new JSONObject(errorBody.string()).optString("message");
                if (!serverMessage.isEmpty()) return serverMessage;
            } catch (Exception ignored) {
            }
        }
        return "Google authentication failed.";
    }

    private static boolean getBoolean(JsonObject object, String key) {
        if (object == null) return false;
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static String getString(JsonObject object, String key) {
        if (object == null) return "";
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    public static final class VerifiedGoogleAccount {
        private final String email;
        private final String googleSubject;

        VerifiedGoogleAccount(String email, String googleSubject) {
            this.email = email;
            this.googleSubject = googleSubject;
        }

        public String getEmail() {
            return email;
        }

        public String getGoogleSubject() {
            return googleSubject;
        }
    }
}
