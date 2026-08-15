package com.w3n.pinggo.Database.Firestore.Util;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public interface ApiService {
    String BASE_URL = "https://pinggo-roxu.cloudsw3.com/rest-api/";
    String DEV_URL = "https://pinggo-roxu.cloudsw3.com/rest-api/";

    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    Retrofit devRetrofit = new Retrofit.Builder()
            .baseUrl(DEV_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
}