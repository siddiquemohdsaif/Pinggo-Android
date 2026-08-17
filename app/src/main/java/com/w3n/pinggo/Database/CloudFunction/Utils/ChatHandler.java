package com.w3n.pinggo.Database.CloudFunction.Utils;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatHandler {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    public static void getChatList(AppRestAPI appApi, String phoneNumber, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.getChatList(createJsonBody(jsonObject)), callback);
    }

    public static void getChat(AppRestAPI appApi, String chatId, String phoneNumber, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("chatId", chatId);
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.getChat(createJsonBody(jsonObject)), callback);
    }

    public static void discoverContacts(AppRestAPI appApi, String phoneNumber, List<String> contacts, AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
            JSONArray normalizedContacts = new JSONArray();
            for (String contact : contacts) {
                normalizedContacts.put(normalizePhoneNumber(contact));
            }
            jsonObject.put("contacts", normalizedContacts);
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.discoverContacts(createJsonBody(jsonObject)), callback);
    }

    public static void syncMessages(AppRestAPI appApi, String phoneNumber, long lastSyncTime,
                                    AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("phoneNumber", normalizePhoneNumber(phoneNumber));
            jsonObject.put("lastSyncTime", lastSyncTime);
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.syncChatMessages(createJsonBody(jsonObject)), callback);
    }

    public static void syncPresence(AppRestAPI appApi, List<String> userIds,
                                    AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONArray normalizedIds = new JSONArray();
            for (String userId : userIds) {
                normalizedIds.put(normalizePhoneNumber(userId));
            }
            jsonObject.put("userIds", normalizedIds);
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.syncPresence(createJsonBody(jsonObject)), callback);
    }

    public static void updateFcmToken(AppRestAPI appApi, String token,
                                      AppFunctionManager.Callback callback) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("fcmToken", token == null ? "" : token.trim());
        } catch (JSONException e) {
            handleJsonError(e, callback);
            return;
        }

        enqueueChatCall(appApi.updateFcmToken(createJsonBody(jsonObject)), callback);
    }

    private static void enqueueChatCall(Call<JsonObject> call, AppFunctionManager.Callback callback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                handleChatResponse(response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (callback != null) {
                    callback.onError(getFailureMessage(t));
                }
            }
        });
    }

    private static void handleChatResponse(Response<JsonObject> response, AppFunctionManager.Callback callback) {
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
            callback.onError(getString(responseBody, "message", "Chat request failed."));
            return;
        }

        callback.onSuccess(responseBody);
    }

    private static RequestBody createJsonBody(JSONObject jsonObject) {
        return RequestBody.create(jsonObject.toString(), JSON_MEDIA_TYPE);
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
        return "Chat request failed. Code: " + response.code();
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

    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        return phoneNumber.trim().replaceFirst("^<plus>", "").replaceFirst("^\\+", "");
    }
}
