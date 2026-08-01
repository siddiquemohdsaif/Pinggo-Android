package com.w3n.wavestream.Database.CloudFunction.Utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.wavestream.AppContextProvider;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.wavestream.modals.UserData;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileUpdateHandler {

    public static void updateName(AppRestAPI appApi, String name, AppFunctionManager.Callback callback) {
        enqueueUpdate(appApi.updateName(createBody("name", name)), "name", name, callback);
    }

    public static void updateDob(AppRestAPI appApi, String dob, AppFunctionManager.Callback callback) {
        enqueueUpdate(appApi.updateDob(createBody("dob", dob)), "dob", dob, callback);
    }

    public static void updateEmail(AppRestAPI appApi, String email, AppFunctionManager.Callback callback) {
        enqueueUpdate(appApi.updateEmail(createBody("email", email)), "email", email, callback);
    }

    private static RequestBody createBody(String key, String value) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(key, value);
        } catch (JSONException ignored) {
        }
        return RequestBody.create(jsonObject.toString(), MediaType.parse("application/json; charset=utf-8"));
    }

    private static void enqueueUpdate(Call<JsonObject> call, String field, String value, AppFunctionManager.Callback callback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleUpdateResponse(response, field, value, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static void handleUpdateResponse(Response<JsonObject> response, String field, String value, AppFunctionManager.Callback callback) {
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
            callback.onError(getString(responseBody, "message", "Update failed."));
            return;
        }

        Context context = AppContextProvider.getAppContext();
        if (context == null) {
            callback.onError("App context is not available.");
            return;
        }

        UserData userData = null;
        JsonObject responseUserData = getObject(responseBody, "userData");
        if (responseUserData != null) {
            userData = UserData.fromJson(responseUserData.toString());
        }

        if (userData == null) {
            userData = patchStoredUserData(context, field, value);
        }

        LoginStateManager.getInstance().setUserData(context, userData);
        callback.onSuccess(userData);
    }

    private static UserData patchStoredUserData(Context context, String field, String value) {
        LoginStateManager loginStateManager = LoginStateManager.getInstance();
        UserData userData = loginStateManager.getUserDataModal(context);
        if (userData == null) {
            userData = new UserData();
            userData.setId(loginStateManager.getUID(context));
            userData.setPhoneNumber(loginStateManager.getUID(context));
            userData.setEncryptedCredential(loginStateManager.getENC(context));
        }

        UserData.ProfileData profileData = userData.getProfileData();
        if (profileData == null) {
            profileData = new UserData.ProfileData();
            profileData.setPhoneNumber(userData.getPhoneNumber());
            userData.setProfileData(profileData);
        }

        if ("name".equals(field)) {
            profileData.setName(value);
        } else if ("dob".equals(field)) {
            profileData.setDob(value);
        } else if ("email".equals(field)) {
            profileData.setEmail(value);
        }
        return userData;
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
        return "Update failed. Code: " + response.code();
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
}
