package com.w3n.pinggo.Database.CloudFunction.AppFunction;

import android.content.Context;
import android.graphics.Bitmap;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.RestApi.API;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.EmailOtpHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.GoogleAuthHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfileUpdateHandler;
import java.util.List;

public class AppFunctionManager {

  private static AppFunctionManager instance;
  private static AppRestAPI appApi;
  private static final Object lock = new Object();

  private AppFunctionManager() {
    if (AppContextProvider.isDevelopment) {
      appApi = API.devRetrofit.create(AppRestAPI.class);
    } else {
      appApi = API.retrofit.create(AppRestAPI.class);
    }
  }

  public void applyAuth(Context context) {
    String authToken =
        LoginStateManager.getInstance().getUID(context)
            + "_"
            + LoginStateManager.getInstance().getENC(context);
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

  public void checkUserExists(String phoneNumber, Callback callback) {
    LoginHandler.checkUserExists(appApi, phoneNumber, callback);
  }

  public void userLogin(String phoneNumber, Callback callback) {
    LoginHandler.login(appApi, phoneNumber, callback);
  }

  public void userSignUp(String name, String phoneNumber, String email, Callback callback) {
    LoginHandler.signUp(appApi, name, phoneNumber, email, callback);
  }

  public void smsSend(String identifier, Callback callback) {
    OtpHandler.sendSms(appApi, identifier, callback);
  }

  public void smsSend(String identifier, String provider, Callback callback) {
    OtpHandler.sendSms(appApi, identifier, provider, callback);
  }

  public void smsVerify(String reqId, String otp, Callback callback) {
    OtpHandler.verifySms(appApi, reqId, otp, callback);
  }

  public void smsVerify(String reqId, String provider, String otp, Callback callback) {
    OtpHandler.verifySms(appApi, reqId, provider, otp, callback);
  }

  public void smsResend(String reqId, Callback callback) {
    OtpHandler.resendSms(appApi, reqId, OtpHandler.RETRY_CHANNEL_SMS, callback);
  }

  public void smsResend(String reqId, String provider, Callback callback) {
    OtpHandler.resendSms(appApi, reqId, provider, OtpHandler.RETRY_CHANNEL_SMS, callback);
  }

  public void emailSend(String email, Callback callback) {
    EmailOtpHandler.send(appApi, email, callback);
  }

  public void emailVerify(String email, String code, Callback callback) {
    EmailOtpHandler.verify(appApi, email, code, callback);
  }

  public void emailResend(String email, Callback callback) {
    EmailOtpHandler.resend(appApi, email, callback);
  }

  public void verifyGoogleIdToken(String idToken, Callback callback) {
    GoogleAuthHandler.verify(appApi, idToken, callback);
  }

  public void updateUserName(String name, Callback callback) {
    ProfileUpdateHandler.updateName(appApi, name, callback);
  }

  public void updateUserEmail(String email, Callback callback) {
    ProfileUpdateHandler.updateEmail(appApi, email, callback);
  }

  public void uploadProfilePhoto(Bitmap profilePhoto, Callback callback) {
    ProfilePhotoHandler.uploadProfilePhoto(appApi, profilePhoto, callback);
  }

  public void getChatList(String phoneNumber, Callback callback) {
    ChatHandler.getChatList(appApi, phoneNumber, callback);
  }

  public void getChatList(String phoneNumber, int pageSize, String cursor, Callback callback) {
    ChatHandler.getChatList(appApi, phoneNumber, pageSize, cursor, callback);
  }

  public void getChat(String chatId, String phoneNumber, Callback callback) {
    ChatHandler.getChat(appApi, chatId, phoneNumber, callback);
  }

  public void getChat(String chatId, String phoneNumber, int pageSize, String cursor,
                      Callback callback) {
    ChatHandler.getChat(appApi, chatId, phoneNumber, pageSize, cursor, callback);
  }

  public void discoverContacts(String phoneNumber, List<String> contacts, Callback callback) {
    ChatHandler.discoverContacts(appApi, phoneNumber, contacts, callback);
  }

  public void syncChatMessages(String phoneNumber, long lastSyncTime, Callback callback) {
    ChatHandler.syncMessages(appApi, phoneNumber, lastSyncTime, callback);
  }

  public void updateChatSettings(String phoneNumber, String chatId, String setting,
                                 long value, Callback callback) {
    ChatHandler.updateChatSettings(appApi, phoneNumber, chatId, setting, value, callback);
  }

  public void updateChatSettingsBulk(String phoneNumber, List<String> chatIds, String setting,
                                     long value, Callback callback) {
    ChatHandler.updateChatSettingsBulk(
        appApi, phoneNumber, chatIds, setting, value, callback);
  }

  public void syncPresence(List<String> userIds, Callback callback) {
    ChatHandler.syncPresence(appApi, userIds, callback);
  }

  public void updateFcmToken(String token, Callback callback) {
    ChatHandler.updateFcmToken(appApi, token, callback);
  }

  public interface Callback {
    void onSuccess(Object object);

    void onError(String error);
  }
}
