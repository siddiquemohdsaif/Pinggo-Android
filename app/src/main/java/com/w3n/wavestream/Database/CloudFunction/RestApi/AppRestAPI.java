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

    @POST("updateName")
    Call<JsonObject> updateName(
            @Body RequestBody body
    );

    @POST("updateDob")
    Call<JsonObject> updateDob(
            @Body RequestBody body
    );

    @POST("updateEmail")
    Call<JsonObject> updateEmail(
            @Body RequestBody body
    );

    @POST("updateDescription")
    Call<JsonObject> updateDescription(
            @Body RequestBody body
    );

    @POST("uploadProfilePhoto")
    Call<JsonObject> uploadProfilePhoto(
            @Body RequestBody body
    );

}
