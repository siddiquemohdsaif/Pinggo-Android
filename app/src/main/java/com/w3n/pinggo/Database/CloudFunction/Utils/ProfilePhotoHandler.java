package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.modals.UserData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfilePhotoHandler {
    private static final int JPEG_QUALITY = 88;
    private static final int MAX_UPLOAD_SIZE_PX = 512;

    public static void uploadProfilePhoto(AppRestAPI appApi, Bitmap profilePhoto, AppFunctionManager.Callback callback) {
        if (profilePhoto == null) {
            if (callback != null) {
                callback.onError("Profile photo is empty.");
            }
            return;
        }

        appApi.uploadProfilePhoto(createBody(profilePhoto)).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleUploadResponse(response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static RequestBody createBody(Bitmap profilePhoto) {
        Bitmap uploadBitmap = resizeForUpload(profilePhoto);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        uploadBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
        String imageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("profilePhotoBase64", imageBase64);
            jsonObject.put("mimeType", "image/jpeg");
        } catch (JSONException ignored) {
        }

        return RequestBody.create(jsonObject.toString(), MediaType.parse("application/json; charset=utf-8"));
    }

    private static Bitmap resizeForUpload(Bitmap bitmap) {
        int sourceSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        if (sourceSize <= MAX_UPLOAD_SIZE_PX) {
            return bitmap;
        }

        return Bitmap.createScaledBitmap(bitmap, MAX_UPLOAD_SIZE_PX, MAX_UPLOAD_SIZE_PX, true);
    }

    private static void handleUploadResponse(Response<JsonObject> response, AppFunctionManager.Callback callback) {
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
            callback.onError(getString(responseBody, "message", "Profile photo upload failed."));
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

        String profilePhotoUrl = getString(responseBody, "profilePhotoUrl", null);
        if (userData == null) {
            userData = LoginStateManager.getInstance().getUserDataModal(context);
        }
        patchProfilePhotoUrl(userData, profilePhotoUrl);

        LoginStateManager.getInstance().setUserData(context, userData);
        callback.onSuccess(userData);
    }

    private static void patchProfilePhotoUrl(UserData userData, String profilePhotoUrl) {
        if (userData == null || profilePhotoUrl == null) {
            return;
        }

        UserData.ProfileData profileData = userData.getProfileData();
        if (profileData == null) {
            profileData = new UserData.ProfileData();
            profileData.setPhoneNumber(userData.getPhoneNumber());
            userData.setProfileData(profileData);
        }
        profileData.setProfilePhotoUrl(profilePhotoUrl);
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
        return "Profile photo upload failed. Code: " + response.code();
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
