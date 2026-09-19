package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;

import java.io.ByteArrayOutputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Uploads a group icon after the group exists, matching the signup photo flow. */
public final class GroupProfilePhotoHandler {
  private static final int JPEG_QUALITY = 88;
  private static final int MAX_UPLOAD_SIZE_PX = 512;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private GroupProfilePhotoHandler() { }

  public static void upload(AppRestAPI api, String userId, String groupId, Bitmap photo,
      AppFunctionManager.Callback callback) {
    if (photo == null || photo.isRecycled()) {
      callback.onError("Group photo is empty.");
      return;
    }
    if (groupId == null || groupId.trim().isEmpty()) {
      callback.onError("Group id is required.");
      return;
    }
    api.uploadGroupProfilePhoto(body(userId, groupId, photo)).enqueue(
        new Callback<JsonObject>() {
          @Override public void onResponse(@NonNull Call<JsonObject> call,
              @NonNull Response<JsonObject> response) {
            if (!response.isSuccessful()) {
              callback.onError(errorMessage(response));
              return;
            }
            JsonObject value = response.body();
            if (value == null || !bool(value, "success")) {
              callback.onError(string(value, "message", "Group photo upload failed."));
              return;
            }
            callback.onSuccess(value);
          }

          @Override public void onFailure(@NonNull Call<JsonObject> call,
              @NonNull Throwable error) {
            callback.onError(error.getMessage() == null
                ? "Network request failed." : error.getMessage());
          }
        });
  }

  private static RequestBody body(String userId, String groupId, Bitmap photo) {
    Bitmap upload = resize(photo);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    upload.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes);
    if (upload != photo) upload.recycle();
    JsonObject body = new JsonObject();
    body.addProperty("userId", normalize(userId));
    body.addProperty("groupId", groupId.trim());
    body.addProperty("profilePhotoBase64",
        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
    body.addProperty("mimeType", "image/jpeg");
    return RequestBody.create(body.toString(), JSON);
  }

  private static Bitmap resize(Bitmap source) {
    int sourceSize = Math.min(source.getWidth(), source.getHeight());
    if (sourceSize <= MAX_UPLOAD_SIZE_PX) return source;
    return Bitmap.createScaledBitmap(
        source, MAX_UPLOAD_SIZE_PX, MAX_UPLOAD_SIZE_PX, true);
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    return normalized.startsWith("+") ? normalized.substring(1) : normalized;
  }

  private static boolean bool(JsonObject object, String key) {
    JsonElement value = object == null ? null : object.get(key);
    return value != null && !value.isJsonNull() && value.getAsBoolean();
  }

  private static String string(JsonObject object, String key, String fallback) {
    JsonElement value = object == null ? null : object.get(key);
    if (value == null || value.isJsonNull()) return fallback;
    String text = value.getAsString();
    return text == null || text.trim().isEmpty() ? fallback : text;
  }

  private static String errorMessage(Response<JsonObject> response) {
    ResponseBody body = response.errorBody();
    if (body != null) {
      try {
        JsonObject error = com.google.gson.JsonParser.parseString(body.string()).getAsJsonObject();
        return string(error, "message", "Group photo upload failed.");
      } catch (Exception ignored) { }
    }
    return "Group photo upload failed. Code: " + response.code();
  }
}
