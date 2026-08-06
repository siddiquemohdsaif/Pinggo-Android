package com.w3n.wavestream.Database.CloudFunction.RestApi;

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

    @POST("otp/send")
    Call<JsonObject> sendOtp(
            @Body RequestBody body
    );

    @POST("otp/verify")
    Call<JsonObject> verifyOtp(
            @Body RequestBody body
    );

    @POST("otp/retry")
    Call<JsonObject> retryOtp(
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

}
