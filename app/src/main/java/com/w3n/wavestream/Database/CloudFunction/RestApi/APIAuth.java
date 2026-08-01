package com.w3n.wavestream.Database.CloudFunction.RestApi;

import com.w3n.wavestream.AppContextProvider;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class APIAuth {
    private static final String BASE_URL = "https://function.cloudsw3.com/pinggo-app-api/";
    private static final String DEV_URL = "https://function.cloudsw3.com/pinggo-app-api_dev/";

    private Retrofit retrofit;

    public APIAuth(String authToken) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
            @Override
            public okhttp3.Response intercept(Interceptor.Chain chain) throws IOException {
                Request newRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build();
                return chain.proceed(newRequest);
            }
        }).build();

        if (AppContextProvider.isDevelopment){
            retrofit = new Retrofit.Builder()
                    .baseUrl(DEV_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }else {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
    }

    public Retrofit getRetrofit() {
        return retrofit;
    }
}
