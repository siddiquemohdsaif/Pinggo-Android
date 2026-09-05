package com.w3n.pinggo.Database.CloudFunction.Utils;

import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.JsonObject;

public final class CallLogHandler {
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private CallLogHandler() {}

  public static void getCallList(AppRestAPI api, String phoneNumber,
                                 AppFunctionManager.Callback callback) {
    try {
      JSONObject body = new JSONObject();
      body.put("phoneNumber", normalize(phoneNumber));
      api.getCallList(RequestBody.create(body.toString(), JSON)).enqueue(new Callback<JsonObject>() {
        @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
          if (response.isSuccessful() && response.body() != null) callback.onSuccess(response.body());
          else callback.onError("Unable to load call history.");
        }
        @Override public void onFailure(Call<JsonObject> call, Throwable error) {
          callback.onError(error.getMessage());
        }
      });
    } catch (JSONException error) {
      callback.onError(error.getMessage());
    }
  }

  private static String normalize(String value) {
    String result = value == null ? "" : value.trim();
    if (result.startsWith("<plus>")) result = result.substring(6);
    return result.startsWith("+") ? result.substring(1) : result;
  }
}
