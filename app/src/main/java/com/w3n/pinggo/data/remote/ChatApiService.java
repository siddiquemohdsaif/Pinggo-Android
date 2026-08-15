package com.w3n.pinggo.data.remote;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ChatApiService {
    @POST("chats/list")
    Call<JsonObject> getChatList(@Body JsonObject body);

    @POST("chats/getChat")
    Call<JsonObject> getChat(@Body JsonObject body);

    @POST("chats/sync")
    Call<JsonObject> syncMessages(@Body JsonObject body);

    @POST("profile/presence")
    Call<JsonObject> syncPresence(@Body JsonObject body);

    @POST("profile/updateFcmToken")
    Call<JsonObject> updateFcmToken(@Body JsonObject body);
}
