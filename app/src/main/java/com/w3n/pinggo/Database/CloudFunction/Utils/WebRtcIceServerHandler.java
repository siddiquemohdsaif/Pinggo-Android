package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.util.Log;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Loads authenticated STUN/TURN configuration for a legacy WebRTC call. */
public final class WebRtcIceServerHandler {
  private static final String TAG = "PingGoRtcSignal";

  private WebRtcIceServerHandler() {}

  public static void getServers(AppRestAPI api, AppFunctionManager.Callback callback) {
    api.getWebRtcIceServers().enqueue(new Callback<JsonObject>() {
      @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
        JsonObject value = response.body();
        if (response.isSuccessful() && value != null && value.has("iceServers")) {
          callback.onSuccess(value);
          return;
        }
        callback.onError("ICE configuration unavailable (HTTP " + response.code() + ").");
      }

      @Override public void onFailure(Call<JsonObject> call, Throwable error) {
        Log.w(TAG, "ICE configuration request failed; using STUN fallback", error);
        callback.onError(error.getMessage() == null
            ? "ICE configuration unavailable." : error.getMessage());
      }
    });
  }
}
