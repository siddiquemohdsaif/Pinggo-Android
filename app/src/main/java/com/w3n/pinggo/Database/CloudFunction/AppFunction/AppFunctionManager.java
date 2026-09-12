package com.w3n.pinggo.Database.CloudFunction.AppFunction;

import android.content.Context;
import android.graphics.Bitmap;
import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.RestApi.API;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.CallLogHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.LiveKitTokenHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.EmailOtpHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.GoogleAuthHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.GroupHandler;
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

  public void reactivateAccount(String phoneNumber, String reactivationToken, Callback callback) {
    LoginHandler.reactivateAccount(appApi, phoneNumber, reactivationToken, callback);
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

  public void getCallList(String phoneNumber, Callback callback) {
    CallLogHandler.getCallList(appApi, phoneNumber, callback);
  }

  public void getCallList(String phoneNumber, int pageSize, String cursor, Callback callback) {
    CallLogHandler.getCallList(appApi, phoneNumber, pageSize, cursor, callback);
  }

  public void getCallLogs(String phoneNumber, String chatId, Callback callback) {
    CallLogHandler.getCallLogs(appApi, phoneNumber, chatId, callback);
  }

  public void getLiveKitToken(String callId, String chatId, String mediaType, Callback callback) {
    LiveKitTokenHandler.getToken(appApi, callId, chatId, mediaType, callback);
  }

  public void getCallLogs(String phoneNumber, String chatId, int pageSize, String cursor,
                          Callback callback) {
    CallLogHandler.getCallLogs(appApi, phoneNumber, chatId, pageSize, cursor, callback);
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

  public void clearChat(String phoneNumber, String chatId, Callback callback) {
    ChatHandler.clearChat(appApi, phoneNumber, chatId, callback);
  }

  public void reportChat(String phoneNumber, String chatId, String reason, Callback callback) {
    ChatHandler.reportChat(appApi, phoneNumber, chatId, reason, callback);
  }

  public void updateBlock(String phoneNumber, String chatId, boolean blocked, Callback callback) {
    ChatHandler.updateBlock(appApi, phoneNumber, chatId, blocked, callback);
  }

  public void getBlockStatus(String phoneNumber, String chatId, Callback callback) {
    ChatHandler.getBlockStatus(appApi, phoneNumber, chatId, callback);
  }

  public void getBlockedAccounts(String phoneNumber, Callback callback) {
    ChatHandler.getBlockedAccounts(appApi, phoneNumber, callback);
  }

  public void createGroup(String userId, String name, String description,
                          List<String> memberIds, Callback callback) {
    GroupHandler.create(appApi, userId, name, description, memberIds, callback);
  }

  public void getGroup(String userId, String groupId, Callback callback) {
    GroupHandler.get(appApi, userId, groupId, callback);
  }

  public void getGroupDetails(String userId, String groupId, Callback callback) {
    GroupHandler.details(appApi, userId, groupId, callback);
  }

  public void getChatMedia(String userId, String chatId, int pageSize, Long before,
                           Callback callback) {
    ChatHandler.getMedia(appApi, chatId, userId, pageSize, before, callback);
  }

  public void getGroupMessages(String userId, String groupId, int pageSize, String cursor,
                               Callback callback) {
    GroupHandler.messages(appApi, userId, groupId, pageSize, cursor, callback);
  }

  public void updateGroup(String userId, String groupId, String name, String description,
                          Callback callback) {
    GroupHandler.update(appApi, userId, groupId, name, description, callback);
  }

  public void updateGroupAdminOnly(String userId, String groupId, boolean enabled,
                                   Callback callback) {
    GroupHandler.updateAdminOnly(appApi, userId, groupId, enabled, callback);
  }

  public void updateGroupMembers(String userId, String groupId, List<String> memberIds,
                                 boolean add, Callback callback) {
    GroupHandler.members(appApi, userId, groupId, memberIds, add, callback);
  }

  public void updateGroupMemberRole(String userId, String groupId, String memberId,
                                    String role, Callback callback) {
    GroupHandler.role(appApi, userId, groupId, memberId, role, callback);
  }

  public void leaveGroup(String userId, String groupId, Callback callback) {
    GroupHandler.leave(appApi, userId, groupId, callback);
  }

  public void leaveGroup(String userId, String groupId, String successorAdminId,
                         Callback callback) {
    GroupHandler.leave(appApi, userId, groupId, successorAdminId, callback);
  }

  public void reportGroup(String userId, String groupId, String reason, Callback callback) {
    GroupHandler.report(appApi, userId, groupId, reason, callback);
  }

  public void syncPresence(List<String> userIds, Callback callback) {
    ChatHandler.syncPresence(appApi, userIds, callback);
  }

  public void updateFcmToken(Context context, String token, Callback callback) {
    ChatHandler.updateFcmToken(context, appApi, token, callback);
  }

  public interface Callback {
    void onSuccess(Object object);

    void onError(String error);
  }
}
