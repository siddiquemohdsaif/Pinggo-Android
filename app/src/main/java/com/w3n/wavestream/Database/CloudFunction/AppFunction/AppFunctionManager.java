package com.w3n.wavestream.Database.CloudFunction.AppFunction;

import android.content.Context;

import com.w3n.wavestream.AppContextProvider;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.Database.CloudFunction.RestApi.API;
import com.w3n.wavestream.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.wavestream.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfilePhotoHandler;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfileUpdateHandler;

import android.graphics.Bitmap;

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

    public interface Callback{
        void onSuccess(Object object);
        void onError(String error);
    }

}
