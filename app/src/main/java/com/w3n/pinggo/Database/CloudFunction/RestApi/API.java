package com.w3n.pinggo.Database.CloudFunction.RestApi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public interface API {
    String BASE_URL = "https://function.cloudsw3.com/pinggo-app-api/";
    String DEV_URL = "https://function.cloudsw3.com/pinggo-app-api_dev/";

    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    Retrofit devRetrofit = new Retrofit.Builder()
            .baseUrl(DEV_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
}