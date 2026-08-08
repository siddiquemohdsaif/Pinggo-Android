package com.w3n.wavestream.Database.CloudFunction.AppFunction;

import android.content.Context;

import com.w3n.wavestream.AppContextProvider;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.Database.CloudFunction.RestApi.API;
import com.w3n.wavestream.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.wavestream.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.wavestream.Database.CloudFunction.Utils.ChatHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfilePhotoHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfileUpdateHandler;

import android.graphics.Bitmap;

import java.util.List;

public class AppFunctionManager {


    private static AppFunctionManager instance;
    private static AppRestAPI appApi;
    private static final Object lock = new Object();

    private AppFunctionManager() {
        if (AppContextProvider.isDevelopment) {
            appApi = API.devRetrofit.create(AppRestAPI.class);
        }else {
            appApi = API.retrofit.create(AppRestAPI.class);
        }
    }

    public void applyAuth(Context context) {
        String authToken = LoginStateManager.getInstance().getUID(context) + "_" + LoginStateManager.getInstance().getENC(context);
        APIAuth apiAuth = new APIAuth(authToken);
        appApi = apiAuth.getRetrofit().create(AppRestAPI.class);
    }

    public static AppFunctionManager getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (lock) {
            if (instance == null) {
                instance = new AppFunctionManager();
            }
        }
        return instance;
    }


    public void userLogin(String phoneNumber,  Callback callback){
        LoginHandler.login( appApi, phoneNumber,callback);
    }

    public void userSignUp(String name, String phoneNumber, String description, Callback callback){
        LoginHandler.signUp(appApi, name, phoneNumber, description, callback);
    }

    public void sendOtp(String identifier, Callback callback) {
        OtpHandler.sendOtp(appApi, identifier, callback);
    }

    public void sendOtp(String identifier, String provider, Callback callback) {
        OtpHandler.sendOtp(appApi, identifier,provider, callback);
    }


    public void verifyOtp(String reqId, String otp, Callback callback) {
        OtpHandler.verifyOtp(appApi, reqId, otp, callback);
    }

    public void verifyOtp(String reqId, String provider, String otp, Callback callback) {
        OtpHandler.verifyOtp(appApi, reqId, provider, otp, callback);
    }

    public void retryOtp(String reqId, Callback callback) {
        OtpHandler.retryOtp(appApi, reqId, OtpHandler.RETRY_CHANNEL_SMS, callback);
    }

    public void retryOtp(String reqId, String provider, Callback callback) {
        OtpHandler.retryOtp(appApi, reqId, provider, OtpHandler.RETRY_CHANNEL_SMS, callback);
    }

    public void updateUserName(String name, Callback callback) {
        ProfileUpdateHandler.updateName(appApi, name, callback);
    }

    public void updateUserDob(String dob, Callback callback) {
        ProfileUpdateHandler.updateDob(appApi, dob, callback);
    }

    public void updateUserEmail(String email, Callback callback) {
        ProfileUpdateHandler.updateEmail(appApi, email, callback);
    }

    public void updateUserDescription(String description, Callback callback) {
        ProfileUpdateHandler.updateDescription(appApi, description, callback);
    }

    public void uploadProfilePhoto(Bitmap profilePhoto, Callback callback) {
        ProfilePhotoHandler.uploadProfilePhoto(appApi, profilePhoto, callback);
    }

    public void getChatList(String phoneNumber, Callback callback) {
        ChatHandler.getChatList(appApi, phoneNumber, callback);
    }

    public void getChat(String chatId, String phoneNumber, Callback callback) {
        ChatHandler.getChat(appApi, chatId, phoneNumber, callback);
    }

    public void discoverContacts(String phoneNumber, List<String> contacts, Callback callback) {
        ChatHandler.discoverContacts(appApi, phoneNumber, contacts, callback);
    }

    public interface Callback{
        void onSuccess(Object object);
        void onError(String error);
    }

}
