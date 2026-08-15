package com.w3n.pinggo.Database.CloudFunction.RestApi;

import androidx.annotation.Keep;

import com.google.gson.JsonObject;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;


@Keep
public interface AppRestAPI {

    @POST("login")
    Call<JsonObject> login(
            @Body RequestBody body
    );

    @POST("signup")
    Call<JsonObject> signup(
            @Body RequestBody body
    );

    @POST("otp/smsSend")
    Call<JsonObject> smsSend(
            @Body RequestBody body
    );

    @POST("otp/smsVerify")
    Call<JsonObject> smsVerify(
            @Body RequestBody body
    );

    @POST("otp/smsResend")
    Call<JsonObject> smsResend(
            @Body RequestBody body
    );

    @POST("otp/emailSend")
    Call<JsonObject> emailSend(
            @Body RequestBody body
    );

    @POST("otp/emailVerify")
    Call<JsonObject> emailVerify(
            @Body RequestBody body
    );

    @POST("otp/emailResend")
    Call<JsonObject> emailResend(
            @Body RequestBody body
    );

    @POST("profile/updateName")
    Call<JsonObject> updateName(
            @Body RequestBody body
    );

    @POST("profile/updateDob")
    Call<JsonObject> updateDob(
            @Body RequestBody body
    );

    @POST("profile/updateEmail")
    Call<JsonObject> updateEmail(
            @Body RequestBody body
    );

    @POST("profile/updateDescription")
    Call<JsonObject> updateDescription(
            @Body RequestBody body
    );

    @POST("profile/uploadProfilePhoto")
    Call<JsonObject> uploadProfilePhoto(
            @Body RequestBody body
    );

    @POST("chats/list")
    Call<JsonObject> getChatList(
            @Body RequestBody body
    );

    @POST("chats/getChat")
    Call<JsonObject> getChat(
            @Body RequestBody body
    );

    @POST("chats/discover")
    Call<JsonObject> discoverContacts(
            @Body RequestBody body
    );

}
