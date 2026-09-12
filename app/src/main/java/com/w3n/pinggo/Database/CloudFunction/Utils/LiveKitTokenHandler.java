package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.util.Log;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okhttp3.RequestBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class LiveKitTokenHandler {
  private static final String TAG = "PingGoLiveKit";
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private LiveKitTokenHandler() {}

  public static void getToken(AppRestAPI api, String callId, String chatId, String mediaType,
      AppFunctionManager.Callback callback) {
    try {
      JSONObject body = new JSONObject();
      body.put("callId", callId);
      body.put("chatId", chatId);
      body.put("mediaType", "video".equals(mediaType) ? "video" : "audio");
      Log.i(TAG, "token_http_request callId=" + callId + " chatId=" + chatId
          + " media=" + mediaType);
      api.getLiveKitToken(RequestBody.create(body.toString(), JSON)).enqueue(new Callback<JsonObject>() {
        @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
          JsonObject value = response.body();
          Log.i(TAG, "token_http_response callId=" + callId + " status=" + response.code()
              + " success=" + response.isSuccessful());
          if (response.isSuccessful() && value != null && value.has("participantToken")) {
            callback.onSuccess(value);
          } else {
            String message = "Unable to authorize the LiveKit call (HTTP "
                + response.code() + ").";
            try {
              ResponseBody errorBody = response.errorBody();
              if (errorBody != null) {
                String raw = errorBody.string();
                String serverMessage = new JSONObject(raw).optString("message", "").trim();
                if (!serverMessage.isEmpty()) message = serverMessage;
                Log.e(TAG, "token_http_error callId=" + callId + " status="
                    + response.code() + " body=" + raw);
              }
            } catch (Exception parseError) {
              Log.e(TAG, "token_http_error_parse_failed callId=" + callId, parseError);
            }
            callback.onError(message);
          }
        }
        @Override public void onFailure(Call<JsonObject> call, Throwable error) {
          Log.e(TAG, "token_http_failure callId=" + callId, error);
          callback.onError(error.getMessage() == null ? "LiveKit authorization failed." : error.getMessage());
        }
      });
    } catch (Exception error) {
      Log.e(TAG, "token_request_build_failure callId=" + callId, error);
      callback.onError(error.getMessage());
    }
  }
}
