package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.modals.UserData;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginHandler {
    private static final String TAG = "LoginHandler";

    public static void checkUserExists(AppRestAPI appApi, String phoneNumber, AppFunctionManager.Callback callback){

        JSONObject jsonObject = new JSONObject();
        String body;
        try {
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
            body = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onError("json error:"+e);
            return;
        }


        Call<JsonObject> login = appApi.checkUserExists(RequestBody.create(body , MediaType.parse("application/json; charset=utf-8")));
        login.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleCheckUserExistsResponse(response, callback);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static void handleCheckUserExistsResponse(
            Response<JsonObject> response,
            AppFunctionManager.Callback callback) {
        if (callback == null) return;
        if (!response.isSuccessful()) {
            callback.onError(getErrorMessage(response));
            return;
        }

        JsonObject responseBody = response.body();
        if (responseBody == null) {
            callback.onError("Empty server response.");
            return;
        }
        if (!getBoolean(responseBody, "success")) {
            callback.onError(getString(responseBody, "message", "Request failed."));
            return;
        }

        boolean exists = getBoolean(responseBody, "exists");
        String email = getString(responseBody, "email", null);
        callback.onSuccess(new CheckUserExistsResult(exists, email));
    }

    public static final class CheckUserExistsResult {
        private final boolean exists;
        private final String email;

        public CheckUserExistsResult(boolean exists, String email) {
            this.exists = exists;
            this.email = email == null ? "" : email.trim();
        }

        public boolean exists() {
            return exists;
        }

        public String getEmail() {
            return email;
        }
    }

    public static void login(AppRestAPI appApi, String phoneNumber, AppFunctionManager.Callback callback){

        JSONObject jsonObject = new JSONObject();
        String body;
        try {
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
            body = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onError("json error:"+e);
            return;
        }


        Call<JsonObject> login = appApi.login(RequestBody.create(body , MediaType.parse("application/json; charset=utf-8")));
        login.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleUserResponse(response, callback);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    public static void signUp(AppRestAPI appApi, String name, String phoneNumber, String email,
                              String description, AppFunctionManager.Callback callback){

        JSONObject jsonObject = new JSONObject();
        String body;
        try {
            jsonObject.put("name",name);
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
            if (email != null && !email.trim().isEmpty()) {
                jsonObject.put("email", email.trim());
            }
            jsonObject.put("description",description);
            body = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onError("json error:"+e);
            return;
        }


        Call<JsonObject> signup = appApi.signup(RequestBody.create(body , MediaType.parse("application/json; charset=utf-8")));
        signup.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleUserResponse(response, callback);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static void handleUserResponse(Response<JsonObject> response, AppFunctionManager.Callback callback) {
        if (callback == null) {
            return;
        }

        if (!response.isSuccessful()) {
            callback.onError(getErrorMessage(response));
            return;
        }

        JsonObject responseBody = response.body();
        if (responseBody == null) {
            callback.onError("Empty server response.");
            return;
        }

        if (!getBoolean(responseBody, "success")) {
            callback.onError(getString(responseBody, "message", "Request failed."));
            return;
        }

        JsonObject userData = getObject(responseBody, "userData");
        if (userData == null) {
            callback.onError("Missing user data.");
            return;
        }

        String uid = getString(userData, "_id", getString(userData, "phoneNumber", null));
        String encryptedCredential = getString(userData, "encryptedCredential", null);
        if (uid == null || encryptedCredential == null) {
            callback.onError("Invalid user data received.");
            return;
        }

        Context context = AppContextProvider.getAppContext();
        if (context == null) {
            callback.onError("App context is not available.");
            return;
        }

        UserData parsedUserData = UserData.fromJson(userData.toString());
        LoginStateManager.getInstance().setLogin(context, uid, encryptedCredential, parsedUserData);
        AppFunctionManager.getInstance().applyAuth(context);
        Log.d(TAG, "Login/signup success stored. Starting profile photo background download if URL exists.");
        ProfilePhotoLocalStore.downloadAndStore(context, parsedUserData);
        callback.onSuccess(userData);
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

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        String value = element.getAsString();
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        return phoneNumber.trim().replaceFirst("^<plus>", "").replaceFirst("^\\+", "");
    }
}
