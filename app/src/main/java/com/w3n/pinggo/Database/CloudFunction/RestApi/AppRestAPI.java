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

  @POST("chats/getChat")
  Call<JsonObject> getChat(@Body RequestBody body);

  @POST("chats/discover")
  Call<JsonObject> discoverContacts(@Body RequestBody body);

  @POST("chats/sync")
  Call<JsonObject> syncChatMessages(@Body RequestBody body);

  @POST("chats/settings")
  Call<JsonObject> updateChatSettings(@Body RequestBody body);

  @POST("profile/presence")
  Call<JsonObject> syncPresence(@Body RequestBody body);

  @POST("profile/updateFcmToken")
  Call<JsonObject> updateFcmToken(@Body RequestBody body);

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
