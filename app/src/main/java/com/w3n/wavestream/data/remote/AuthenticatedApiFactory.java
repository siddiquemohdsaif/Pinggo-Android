package com.w3n.wavestream.data.remote;

import android.content.Context;

import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AuthenticatedApiFactory {
    private AuthenticatedApiFactory() {
    }

    public static ChatApiService createChatApi(Context context) {
        String token = LoginStateManager.getInstance().getUID(context)
                + "_"
                + LoginStateManager.getInstance().getENC(context);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer " + token)
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();

        return new Retrofit.Builder()
                .baseUrl(RealtimeConfig.REST_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApiService.class);
    }
}
