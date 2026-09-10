package com.w3n.pinggo.Database.CloudFunction.RestApi;

import androidx.annotation.Keep;
import com.google.gson.JsonObject;
import okhttp3.RequestBody;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.DELETE;
import retrofit2.http.GET;

@Keep
public interface AppRestAPI {

  @POST("checkUserExists")
  Call<JsonObject> checkUserExists(@Body RequestBody body);

  @POST("login")
  Call<JsonObject> login(@Body RequestBody body);

  @POST("signup")
  Call<JsonObject> signup(@Body RequestBody body);

  @POST("otp/smsSend")
  Call<JsonObject> smsSend(@Body RequestBody body);

  @POST("otp/smsVerify")
  Call<JsonObject> smsVerify(@Body RequestBody body);

  @POST("otp/smsResend")
  Call<JsonObject> smsResend(@Body RequestBody body);

  @POST("otp/emailSend")
  Call<JsonObject> emailSend(@Body RequestBody body);

  @POST("otp/emailVerify")
  Call<JsonObject> emailVerify(@Body RequestBody body);

  @POST("otp/emailResend")
  Call<JsonObject> emailResend(@Body RequestBody body);

  @POST("auth/google")
  Call<JsonObject> verifyGoogleIdToken(@Body RequestBody body);

  @POST("profile/updateName")
  Call<JsonObject> updateName(@Body RequestBody body);

  @POST("profile/updateEmail")
  Call<JsonObject> updateEmail(@Body RequestBody body);

  @POST("profile/uploadProfilePhoto")
  Call<JsonObject> uploadProfilePhoto(@Body RequestBody body);

  @POST("chats/list")
  Call<JsonObject> getChatList(@Body RequestBody body);

  @POST("calls/list")
  Call<JsonObject> getCallList(@Body RequestBody body);

  @POST("calls/logs")
  Call<JsonObject> getCallLogs(@Body RequestBody body);

  @POST("chats/getChat")
  Call<JsonObject> getChat(@Body RequestBody body);

  @POST("chats/media")
  Call<JsonObject> getChatMedia(@Body RequestBody body);

  @POST("chats/discover")
  Call<JsonObject> discoverContacts(@Body RequestBody body);

  @POST("chats/sync")
  Call<JsonObject> syncChatMessages(@Body RequestBody body);

  @POST("chats/settings")
  Call<JsonObject> updateChatSettings(@Body RequestBody body);

  @POST("chats/settings/bulk")
  Call<JsonObject> updateChatSettingsBulk(@Body RequestBody body);

  @POST("chats/clear")
  Call<JsonObject> clearChat(@Body RequestBody body);

  @POST("chats/report")
  Call<JsonObject> reportChat(@Body RequestBody body);

  @POST("chats/block")
  Call<JsonObject> blockChat(@Body RequestBody body);

  @POST("chats/unblock")
  Call<JsonObject> unblockChat(@Body RequestBody body);

  @POST("chats/block-status")
  Call<JsonObject> getBlockStatus(@Body RequestBody body);

  @POST("chats/blocked-accounts")
  Call<JsonObject> getBlockedAccounts(@Body RequestBody body);

  @POST("groups/create") Call<JsonObject> createGroup(@Body RequestBody body);
  @POST("groups/get") Call<JsonObject> getGroup(@Body RequestBody body);
  @POST("groups/details") Call<JsonObject> getGroupDetails(@Body RequestBody body);
  @POST("groups/report") Call<JsonObject> reportGroup(@Body RequestBody body);
  @POST("groups/messages") Call<JsonObject> getGroupMessages(@Body RequestBody body);
  @POST("groups/update") Call<JsonObject> updateGroup(@Body RequestBody body);
  @POST("groups/members/add") Call<JsonObject> addGroupMembers(@Body RequestBody body);
  @POST("groups/members/remove") Call<JsonObject> removeGroupMembers(@Body RequestBody body);
  @POST("groups/members/role") Call<JsonObject> updateGroupMemberRole(@Body RequestBody body);
  @POST("groups/leave") Call<JsonObject> leaveGroup(@Body RequestBody body);

  @POST("profile/presence")
  Call<JsonObject> syncPresence(@Body RequestBody body);

  @POST("profile/updateFcmToken")
  Call<JsonObject> updateFcmToken(@Body RequestBody body);

  @GET("devices")
  Call<JsonObject> getLinkedDevices();

  @POST("devices/register")
  Call<JsonObject> registerLinkedDevice(@Body JsonObject body);

  @POST("devices/logout-account")
  Call<JsonObject> logoutAccount(@Body JsonObject body);

  @DELETE("devices/{deviceId}")
  Call<JsonObject> unlinkDevice(@Path("deviceId") String deviceId);

  @POST("device-links")
  Call<JsonObject> createDeviceLink(@Body JsonObject body);

  @POST("device-links/{linkRequestId}/approve")
  Call<JsonObject> approveDeviceLink(@Path("linkRequestId") String linkRequestId,
      @Body JsonObject body);

  @POST("device-links/{linkRequestId}/status")
  Call<JsonObject> getDeviceLinkStatus(@Path("linkRequestId") String linkRequestId,
      @Body JsonObject body);

  @POST("device-links/{linkRequestId}/complete")
  Call<JsonObject> completeDeviceLink(@Path("linkRequestId") String linkRequestId,
      @Body JsonObject body);

  @Multipart
  @POST("chats/attachments")
  Call<JsonObject> uploadChatAttachment(
          @Part MultipartBody.Part file,
          @Part("chatId") RequestBody chatId,
          @Part("kind") RequestBody kind);

  @POST("chats/attachments/init")
  Call<JsonObject> initChatAttachment(@Body RequestBody body);

  @Multipart
  @POST("chats/attachments/{uploadId}/chunks/{index}")
  Call<JsonObject> uploadChatAttachmentChunk(
          @Path("uploadId") String uploadId,
          @Path("index") int index,
          @Part MultipartBody.Part chunk,
          @Part("chunkHash") RequestBody chunkHash);

  @GET("chats/attachments/{uploadId}/status")
  Call<JsonObject> getChatAttachmentStatus(@Path("uploadId") String uploadId);

  @POST("chats/attachments/{uploadId}/complete")
  Call<JsonObject> completeChatAttachment(
          @Path("uploadId") String uploadId,
          @Body RequestBody body);

  @DELETE("chats/attachments/{uploadId}")
  Call<JsonObject> cancelChatAttachment(@Path("uploadId") String uploadId);
}
