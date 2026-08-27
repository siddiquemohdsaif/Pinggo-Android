package com.w3n.pinggo.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.WebSocket.ChatWebSocketClient;
import com.w3n.pinggo.data.local.MessageDao;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.MessageStatus;
import com.w3n.pinggo.data.local.ChatDao;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.local.PresenceDao;
import com.w3n.pinggo.data.local.PresenceEntity;
import com.w3n.pinggo.data.local.PingGoDatabase;
import com.w3n.pinggo.data.local.TransferDao;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.worker.AttachmentUploadWorker;
import com.w3n.pinggo.data.worker.AttachmentDownloadWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.io.InputStream;

import android.provider.OpenableColumns;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatRepository implements ChatWebSocketClient.Listener {
    private static final long ATTACHMENT_CHUNK_SIZE = 3L * 1024L * 1024L;
    private static final int CHAT_LIST_PAGE_SIZE = 20;
    public interface EventListener {
        void onTyping(String chatId, String userId, boolean typing);

        void onSocketError(String error);

        default void onTotalUnread(int totalUnread) { }
    }

    public interface CallEventListener {
        void onCallEvent(JsonObject event);
    }

    public interface IncomingCallListener {
        void onIncomingCall(JsonObject event);
    }

    public interface AttachmentCallback {
        void onSent();

        void onError(String message);
    }

    public interface DownloadCallback {
        void onAvailable(Uri uri);
        void onQueued();
        void onError(String message);
    }

    private static volatile ChatRepository instance;

    private final Context appContext;
    private final MessageDao messageDao;
    private final ChatDao chatDao;
    private final PresenceDao presenceDao;
    private final TransferDao transferDao;
    private final AppFunctionManager appFunctionManager;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService profilePhotoExecutor = Executors.newFixedThreadPool(4);
    private final Set<String> profilePhotoDownloads =
            Collections.synchronizedSet(new HashSet<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ChatWebSocketClient socketClient;
    private EventListener eventListener;
    private String chatListPhoneNumber = "";
    private String nextChatListCursor;
    private boolean chatListHasMore;
    private boolean chatListPageLoading;
    private final MutableLiveData<Boolean> chatListPaginationLoading =
            new MutableLiveData<>(false);
    private int chatListGeneration;
    private int latestTotalUnread = -1;
    private CallEventListener callEventListener;
    private IncomingCallListener incomingCallListener;
    private String currentUserId;
    private volatile String activeChatId = "";

    private ChatRepository(Context context) {
        appContext = context.getApplicationContext();
        PingGoDatabase database = PingGoDatabase.getInstance(appContext);
        messageDao = database.messageDao();
        chatDao = database.chatDao();
        presenceDao = database.presenceDao();
        transferDao = database.transferDao();
        appFunctionManager = AppFunctionManager.getInstance();
        socketClient = new ChatWebSocketClient(this);
    }

    public static ChatRepository getInstance(Context context) {
        if (instance != null) {
            return instance;
        }
        synchronized (ChatRepository.class) {
            if (instance == null) {
                instance = new ChatRepository(context);
            }
        }
        return instance;
    }

    public static void resetInstance() {
        synchronized (ChatRepository.class) {
            if (instance != null) {
                instance.disconnect();
            }
            instance = null;
        }
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
        if (eventListener != null && latestTotalUnread >= 0) {
            int totalUnread = latestTotalUnread;
            mainHandler.post(() -> {
                if (this.eventListener == eventListener) {
                    eventListener.onTotalUnread(totalUnread);
                }
            });
        }
    }

    public void setCallEventListener(CallEventListener listener) {
        callEventListener = listener;
    }

    public void setIncomingCallListener(IncomingCallListener listener) {
        incomingCallListener = listener;
    }

    public boolean sendCallEvent(JsonObject event) {
        connect();
        return socketClient.send(event);
    }

    public LiveData<List<MessageEntity>> observeMessages(String chatId) {
        return messageDao.observeMessages(chatId);
    }

    public LiveData<List<ChatEntity>> observeChats() {
        return chatDao.observeChats();
    }

    public void updateChatSetting(String chatId, String setting, long value,
                                  AppFunctionManager.Callback callback) {
        String phoneNumber = normalizeAccountId(
                LoginStateManager.getInstance().getUID(appContext));
        appFunctionManager.updateChatSettings(
                phoneNumber, chatId, setting, value, callback);
    }

    public void deleteLocalChat(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        ioExecutor.execute(() -> chatDao.deleteByChatId(chatId));
    }

    public void setActiveChat(String chatId) {
        activeChatId = chatId == null ? "" : chatId.trim();
        if (!activeChatId.isEmpty()) {
            String openedChatId = activeChatId;
            ioExecutor.execute(() -> chatDao.clearUnreadCount(openedChatId));
        }
    }

    public void clearActiveChat(String chatId) {
        String closingChatId = chatId == null ? "" : chatId.trim();
        if (activeChatId.equals(closingChatId)) activeChatId = "";
    }

    public LiveData<List<TransferEntity>> observeTransfers(String chatId) {
        return transferDao.observeChat(chatId);
    }

    public void downloadAttachment(MessageEntity message, DownloadCallback callback) {
        ioExecutor.execute(() -> {
            if (message == null || message.attachmentId == null || message.attachmentUrl == null) {
                mainHandler.post(() -> callback.onError("Download URL is unavailable."));
                return;
            }
            TransferEntity transfer = transferDao.findByAttachmentId(message.attachmentId);
            if (transfer != null && transfer.localUri != null
                    && canReadAttachment(Uri.parse(transfer.localUri))) {
                Uri uri = Uri.parse(transfer.localUri);
                mainHandler.post(() -> callback.onAvailable(uri));
                return;
            }
            if (transfer == null) transfer = new TransferEntity(UUID.randomUUID().toString());
            transfer.attachmentId = message.attachmentId;
            transfer.clientMessageId = message.clientMessageId;
            transfer.direction = "download";
            transfer.chatId = message.chatId;
            transfer.senderId = message.senderId;
            transfer.receiverId = message.receiverId;
            transfer.kind = message.messageType;
            transfer.fileName = message.attachmentName == null ? "attachment" : message.attachmentName;
            transfer.mimeType = message.attachmentMimeType == null ? "application/octet-stream" : message.attachmentMimeType;
            transfer.remoteUrl = message.attachmentUrl;
            transfer.totalSize = message.attachmentSize == null ? 0 : message.attachmentSize;
            transfer.fileHash = message.attachmentSha256;
            transfer.status = "queued";
            transfer.error = null;
            transfer.updatedTime = System.currentTimeMillis();
            transferDao.upsert(transfer);
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AttachmentDownloadWorker.class)
                    .setConstraints(constraints)
                    .setInputData(new androidx.work.Data.Builder()
                            .putString(AttachmentDownloadWorker.KEY_TRANSFER_ID, transfer.transferId).build())
                    .build();
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "pinggo-download-" + transfer.attachmentId, ExistingWorkPolicy.KEEP, request);
            mainHandler.post(callback::onQueued);
        });
    }

    public LiveData<PresenceEntity> observePresence(String userId) {
        return presenceDao.observePresence(normalizeAccountId(userId));
    }

    public void connect() {
        currentUserId = normalizeAccountId(LoginStateManager.getInstance().getUID(appContext));
        String encryptedCredential = LoginStateManager.getInstance().getENC(appContext);
        if (currentUserId.isEmpty() || encryptedCredential == null || encryptedCredential.trim().isEmpty()) {
            return;
        }
        socketClient.connect(currentUserId, encryptedCredential);
    }

    public void disconnect() {
        socketClient.disconnect();
    }

    public void sendMessage(String chatId, String receiverId, String text, String repliedMessageId) {
        sendTypedMessage(chatId, receiverId, text, repliedMessageId, "text", null, null);
    }

    public void sendLocation(String chatId, String receiverId, double latitude, double longitude,
                             float accuracy, String repliedMessageId) {
        JsonObject location = new JsonObject();
        location.addProperty("latitude", latitude);
        location.addProperty("longitude", longitude);
        location.addProperty("accuracy", accuracy);
        sendTypedMessage(chatId, receiverId, "", repliedMessageId, "location", null, location);
    }

    public void uploadAndSendAttachment(String chatId, String receiverId, String caption,
                                        String repliedMessageId, Uri uri, String kind,
                                        AttachmentCallback callback) {
        AttachmentCallback mainCallback = onMainThread(callback);
        ioExecutor.execute(() -> enqueueAttachmentTransfer(chatId, receiverId, caption,
                repliedMessageId, uri, kind, null, mainCallback));
    }

    private void enqueueAttachmentTransfer(String chatId, String receiverId, String caption,
                                           String repliedMessageId, Uri uri, String kind,
                                           String existingClientMessageId,
                                           AttachmentCallback callback) {
        if (uri == null || kind == null || !canReadAttachment(uri)) {
            callback.onError("File no longer available.");
            return;
        }
        String name = attachmentName(uri);
        long size = attachmentSize(uri);
        if (size > 25L * 1024L * 1024L) {
            callback.onError("Attachment must be 25 MB or smaller.");
            return;
        }
        String mime = appContext.getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        String clientMessageId = existingClientMessageId == null
                ? "local_" + UUID.randomUUID() : existingClientMessageId;
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext)) : currentUserId;
        String normalizedReceiverId = normalizeAccountId(receiverId);
        if (existingClientMessageId == null) {
            MessageEntity localMessage = new MessageEntity(clientMessageId, clientMessageId,
                    chatId, senderId,
                    normalizedReceiverId, caption, repliedMessageId, System.currentTimeMillis(),
                    null, null, MessageStatus.SENDING, kind, null, kind, name, mime, null,
                    uri.toString(), size < 0 ? null : size, null, null, null);
            messageDao.upsert(localMessage);
            updateChatSummary(localMessage);
        } else {
            messageDao.updateStatusByClientMessageId(clientMessageId, MessageStatus.SENDING);
        }
        TransferEntity transfer = transferDao.findByClientMessageId(clientMessageId);
        if (transfer == null) transfer = new TransferEntity(UUID.randomUUID().toString());
        transfer.clientMessageId = clientMessageId;
        transfer.direction = "upload";
        transfer.chatId = chatId;
        transfer.senderId = senderId;
        transfer.receiverId = normalizedReceiverId;
        transfer.kind = kind;
        transfer.caption = caption;
        transfer.repliedMessageId = repliedMessageId;
        transfer.fileName = name;
        transfer.mimeType = mime;
        transfer.sourceUri = uri.toString();
        transfer.totalSize = Math.max(0, size);
        transfer.status = "queued";
        transfer.error = null;
        transfer.updatedTime = System.currentTimeMillis();
        transferDao.upsert(transfer);
        enqueueUploadWork(transfer.transferId);
        callback.onSent();
    }

    private void enqueueUploadWork(String transferId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AttachmentUploadWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putString(AttachmentUploadWorker.KEY_TRANSFER_ID, transferId).build())
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                "pinggo-upload-" + transferId, ExistingWorkPolicy.REPLACE, request);
    }

    public void sendCompletedBackgroundAttachment(TransferEntity transfer, JsonObject attachment) {
        connect();
        sendExistingMessage(transfer.clientMessageId, transfer.chatId, transfer.senderId,
                transfer.receiverId, transfer.caption, transfer.repliedMessageId,
                transfer.kind, attachment, null);
    }

    private void uploadAndSendAttachmentInternal(String chatId, String receiverId, String caption,
                                                  String repliedMessageId, Uri uri, String kind,
                                                  String existingClientMessageId,
                                                  AttachmentCallback callback) {
        if (uri == null || kind == null) {
            callback.onError("Attachment is missing.");
            return;
        }
        if (!canReadAttachment(uri)) {
            callback.onError("Attachment access expired. Select the file again.");
            return;
        }
        String name = attachmentName(uri);
        long size = attachmentSize(uri);
        if (size > 25L * 1024L * 1024L) {
            callback.onError("Attachment must be 25 MB or smaller.");
            return;
        }
        String mime = appContext.getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        String clientMessageId = existingClientMessageId == null
                ? "local_" + UUID.randomUUID() : existingClientMessageId;
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext)) : currentUserId;
        String normalizedReceiverId = normalizeAccountId(receiverId);
        if (existingClientMessageId == null) {
            MessageEntity localMessage = new MessageEntity(
                    clientMessageId, clientMessageId, chatId, senderId, normalizedReceiverId,
                    caption, repliedMessageId, System.currentTimeMillis(), null, null,
                    MessageStatus.SENDING, kind, null, kind, name, mime, null, uri.toString(),
                    size < 0 ? null : size, null, null, null);
            ioExecutor.execute(() -> {
                messageDao.upsert(localMessage);
                updateChatSummary(localMessage);
            });
        } else {
            ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(
                    clientMessageId, MessageStatus.SENDING));
        }
        RequestBody streamBody = contentUriBody(uri, mime, size);
        MultipartBody.Part file = MultipartBody.Part.createFormData("file", name, streamBody);
        String token = LoginStateManager.getInstance().getUID(appContext) + "_"
                + LoginStateManager.getInstance().getENC(appContext);
        AppRestAPI api = new APIAuth(token).getRetrofit().create(AppRestAPI.class);
        if (size > ATTACHMENT_CHUNK_SIZE) {
            uploadAttachmentInChunks(api, uri, name, mime, size, chatId, kind,
                    clientMessageId, senderId, normalizedReceiverId, caption,
                    repliedMessageId, callback);
            return;
        }
        RequestBody chatPart = RequestBody.create(chatId, MediaType.get("text/plain"));
        RequestBody kindPart = RequestBody.create(kind, MediaType.get("text/plain"));
        api.uploadChatAttachment(file, chatPart, kindPart).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonObject body = response.body();
                JsonObject attachment = body == null ? null : body.getAsJsonObject("attachment");
                if (!response.isSuccessful() || attachment == null) {
                    ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(
                            clientMessageId, MessageStatus.FAILED));
                    callback.onError("Attachment upload failed.");
                    return;
                }
                ioExecutor.execute(() -> messageDao.applyAttachmentUpload(
                        clientMessageId,
                        JsonParserUtil.getString(attachment, "id"),
                        JsonParserUtil.getString(attachment, "kind"),
                        JsonParserUtil.getString(attachment, "name"),
                        JsonParserUtil.getString(attachment, "mimeType"),
                        JsonParserUtil.getString(attachment, "url"),
                        JsonParserUtil.getLong(attachment, "size")));
                sendExistingMessage(clientMessageId, chatId, senderId, normalizedReceiverId,
                        caption, repliedMessageId, kind, attachment, null);
                callback.onSent();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable throwable) {
                ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(
                        clientMessageId, MessageStatus.FAILED));
                callback.onError(throwable.getMessage() == null ? "Attachment upload failed." : throwable.getMessage());
            }
        });
    }

    private void uploadAttachmentInChunks(AppRestAPI api, Uri uri, String name, String mime,
                                          long size, String chatId, String kind,
                                          String clientMessageId, String senderId,
                                          String receiverId, String caption,
                                          String repliedMessageId, AttachmentCallback callback) {
        int totalChunks = (int) Math.ceil((double) size / ATTACHMENT_CHUNK_SIZE);
        JsonObject request = new JsonObject();
        request.addProperty("chatId", chatId);
        request.addProperty("kind", kind);
        request.addProperty("fileName", name);
        request.addProperty("mimeType", mime);
        request.addProperty("totalSize", size);
        request.addProperty("totalChunks", totalChunks);
        RequestBody body = RequestBody.create(request.toString(), MediaType.get("application/json"));
        api.initChatAttachment(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonObject responseBody = response.body();
                JsonObject upload = responseBody == null ? null : responseBody.getAsJsonObject("upload");
                String uploadId = upload == null ? null : JsonParserUtil.getString(upload, "uploadId");
                if (!response.isSuccessful() || uploadId == null || uploadId.isEmpty()) {
                    failAttachment(clientMessageId, callback,
                            "Chunk upload initialization failed (HTTP " + response.code() + ").", null);
                    return;
                }
                uploadNextChunk(api, uri, mime, size, uploadId, 0, totalChunks, name,
                        chatId, kind, clientMessageId, senderId, receiverId, caption,
                        repliedMessageId, callback);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable throwable) {
                failAttachment(clientMessageId, callback, "Chunk upload initialization failed.", throwable);
            }
        });
    }

    private void uploadNextChunk(AppRestAPI api, Uri uri, String mime, long totalSize,
                                 String uploadId, int index, int totalChunks, String name,
                                 String chatId, String kind, String clientMessageId,
                                 String senderId, String receiverId, String caption,
                                 String repliedMessageId, AttachmentCallback callback) {
        if (index >= totalChunks) {
            RequestBody emptyJson = RequestBody.create("{}", MediaType.get("application/json"));
            api.completeChatAttachment(uploadId, emptyJson).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    JsonObject responseBody = response.body();
                    JsonObject attachment = responseBody == null ? null : responseBody.getAsJsonObject("attachment");
                    if (!response.isSuccessful() || attachment == null) {
                        failAttachment(clientMessageId, callback,
                                "Attachment assembly failed (HTTP " + response.code() + ").", null);
                        return;
                    }
                    finishAttachmentUpload(clientMessageId, chatId, senderId, receiverId,
                            caption, repliedMessageId, kind, attachment, callback);
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable throwable) {
                    failAttachment(clientMessageId, callback, "Attachment assembly failed.", throwable);
                }
            });
            return;
        }
        long offset = index * ATTACHMENT_CHUNK_SIZE;
        long length = Math.min(ATTACHMENT_CHUNK_SIZE, totalSize - offset);
        RequestBody chunkBody = contentUriChunkBody(uri, mime, offset, length);
        MultipartBody.Part chunk = MultipartBody.Part.createFormData(
                "chunk", name + ".part" + index, chunkBody);
        RequestBody emptyHash = RequestBody.create("", MediaType.get("text/plain"));
        api.uploadChatAttachmentChunk(uploadId, index, chunk, emptyHash).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    String serverError = readErrorBody(response);
                    failAttachment(clientMessageId, callback,
                            "Chunk " + (index + 1) + " upload failed (HTTP " + response.code()
                                    + ")" + (serverError.isEmpty() ? "." : ": " + serverError), null);
                    return;
                }
                uploadNextChunk(api, uri, mime, totalSize, uploadId, index + 1, totalChunks,
                        name, chatId, kind, clientMessageId, senderId, receiverId, caption,
                        repliedMessageId, callback);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable throwable) {
                failAttachment(clientMessageId, callback,
                        "Chunk " + (index + 1) + " upload failed.", throwable);
            }
        });
    }

    private void finishAttachmentUpload(String clientMessageId, String chatId, String senderId,
                                        String receiverId, String caption, String repliedMessageId,
                                        String kind, JsonObject attachment, AttachmentCallback callback) {
        ioExecutor.execute(() -> messageDao.applyAttachmentUpload(
                clientMessageId,
                JsonParserUtil.getString(attachment, "id"),
                JsonParserUtil.getString(attachment, "kind"),
                JsonParserUtil.getString(attachment, "name"),
                JsonParserUtil.getString(attachment, "mimeType"),
                JsonParserUtil.getString(attachment, "url"),
                JsonParserUtil.getLong(attachment, "size")));
        sendExistingMessage(clientMessageId, chatId, senderId, receiverId, caption,
                repliedMessageId, kind, attachment, null);
        callback.onSent();
    }

    private void failAttachment(String clientMessageId, AttachmentCallback callback,
                                String message, Throwable throwable) {
        ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(
                clientMessageId, MessageStatus.FAILED));
        callback.onError(throwable != null && throwable.getMessage() != null
                ? throwable.getMessage() : message);
    }

    private String readErrorBody(Response<?> response) {
        if (response.errorBody() == null) return "";
        try {
            return response.errorBody().string();
        } catch (IOException error) {
            return "";
        }
    }

    private void sendTypedMessage(String chatId, String receiverId, String text,
                                  String repliedMessageId, String messageType,
                                  JsonObject attachment, JsonObject location) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        String normalizedReceiverId = normalizeAccountId(receiverId);
        String clientMessageId = "local_" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        MessageEntity localMessage = new MessageEntity(
                clientMessageId,
                clientMessageId,
                chatId,
                senderId,
                normalizedReceiverId,
                text,
                repliedMessageId,
                now,
                null,
                null,
                MessageStatus.SENDING,
                messageType,
                attachment == null ? null : JsonParserUtil.getString(attachment, "id"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "kind"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "name"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "mimeType"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "url"),
                null,
                attachment == null ? null : JsonParserUtil.getLong(attachment, "size"),
                location == null ? null : location.get("latitude").getAsDouble(),
                location == null ? null : location.get("longitude").getAsDouble(),
                location == null ? null : location.get("accuracy").getAsFloat()
        );

        ioExecutor.execute(() -> {
            messageDao.upsert(localMessage);
            updateChatSummary(localMessage);
        });

        sendExistingMessage(clientMessageId, chatId, senderId, normalizedReceiverId, text,
                repliedMessageId, messageType, attachment, location);
    }

    private void sendExistingMessage(String clientMessageId, String chatId, String senderId,
                                     String receiverId, String text, String repliedMessageId,
                                     String messageType, JsonObject attachment, JsonObject location) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "send_message");
        event.addProperty("clientMessageId", clientMessageId);
        event.addProperty("chatId", chatId);
        event.addProperty("senderId", senderId);
        event.addProperty("receiverId", receiverId);
        event.addProperty("text", text);
        event.addProperty("messageType", messageType);
        if (attachment != null) event.addProperty("attachmentId", JsonParserUtil.getString(attachment, "id"));
        if (location != null) event.add("location", location);
        if (repliedMessageId != null && !repliedMessageId.trim().isEmpty()) {
            event.addProperty("repliedMessageId", repliedMessageId);
        }

        boolean sentToSocket = socketClient.send(event);
        if (!sentToSocket) {
            ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(clientMessageId, MessageStatus.FAILED));
        }
    }

    public void resendMessage(MessageEntity message) {
        if (message == null || !MessageStatus.FAILED.equals(message.status)) return;
        String clientId = message.clientMessageId == null ? message.messageId : message.clientMessageId;
        if (("image".equals(message.messageType) || "video".equals(message.messageType)
                || "file".equals(message.messageType)) && message.attachmentId == null
                && message.attachmentLocalUri != null) {
            AttachmentCallback mainCallback = onMainThread(new AttachmentCallback() {
                        @Override public void onSent() {}
                        @Override public void onError(String error) { notifySocketError(error); }
                    });
            ioExecutor.execute(() -> enqueueAttachmentTransfer(
                    message.chatId, message.receiverId, message.text,
                    message.repliedMessageId, Uri.parse(message.attachmentLocalUri),
                    message.messageType, clientId, mainCallback));
            return;
        }
        JsonObject attachment = null;
        if (message.attachmentId != null) {
            attachment = new JsonObject();
            attachment.addProperty("id", message.attachmentId);
        }
        JsonObject location = null;
        if (message.latitude != null && message.longitude != null) {
            location = new JsonObject();
            location.addProperty("latitude", message.latitude);
            location.addProperty("longitude", message.longitude);
            if (message.locationAccuracy != null) location.addProperty("accuracy", message.locationAccuracy);
        }
        ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(clientId, MessageStatus.SENDING));
        sendExistingMessage(clientId, message.chatId, message.senderId, message.receiverId,
                message.text, message.repliedMessageId, message.messageType, attachment, location);
    }

    private AttachmentCallback onMainThread(AttachmentCallback callback) {
        return new AttachmentCallback() {
            @Override
            public void onSent() {
                mainHandler.post(callback::onSent);
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> callback.onError(message));
            }
        };
    }

    private RequestBody contentUriBody(Uri uri, String mime, long size) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse(mime);
            }

            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Unable to open attachment.");
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 25L * 1024L * 1024L) throw new IOException("Attachment exceeds 25 MB.");
                        sink.write(buffer, 0, read);
                    }
                }
            }
        };
    }

    private RequestBody contentUriChunkBody(Uri uri, String mime, long offset, long length) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse(mime);
            }

            @Override
            public long contentLength() {
                return length;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Unable to open attachment.");
                    long remainingSkip = offset;
                    while (remainingSkip > 0) {
                        long skipped = input.skip(remainingSkip);
                        if (skipped > 0) {
                            remainingSkip -= skipped;
                        } else if (input.read() == -1) {
                            throw new IOException("Attachment ended before chunk offset.");
                        } else {
                            remainingSkip--;
                        }
                    }
                    byte[] buffer = new byte[8192];
                    long remaining = length;
                    while (remaining > 0) {
                        int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read == -1) throw new IOException("Attachment ended before chunk was complete.");
                        sink.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
            }
        };
    }

    private String attachmentName(Uri uri) {
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (RuntimeException error) {
        }
        return "attachment";
    }

    private long attachmentSize(Uri uri) {
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column);
            }
        } catch (RuntimeException error) {
        }
        return -1;
    }

    private boolean canReadAttachment(Uri uri) {
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            return input != null;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    public void markSeen(String chatId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        JsonObject event = new JsonObject();
        JsonArray ids = new JsonArray();
        for (String messageId : messageIds) {
            ids.add(messageId);
        }
        event.addProperty("type", "message_seen");
        event.addProperty("chatId", chatId);
        event.add("messageIds", ids);
        socketClient.send(event);
    }

    public void markDelivered(String chatId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        JsonObject event = new JsonObject();
        JsonArray ids = new JsonArray();
        for (String messageId : messageIds) {
            ids.add(messageId);
        }
        event.addProperty("type", "message_delivered");
        event.addProperty("chatId", chatId);
        event.add("messageIds", ids);
        socketClient.send(event);
    }

    public void hideLocalMessage(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.deleteByMessageId(messageId));
    }

    public void updateLocalMessageText(String messageId, String text) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.updateText(messageId, text));
    }

    public void markLocalMessageDeleted(String messageId) {
        updateLocalMessageText(messageId, "This Message was deleted");
    }

    public void editMessage(String chatId, String messageId, String text) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            notifySocketError("Message text missing.");
            return;
        }

        updateLocalMessageText(messageId, text);

        JsonObject event = new JsonObject();
        event.addProperty("type", "edit_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);
        event.addProperty("senderId", senderId);
        event.addProperty("text", text);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to edit message while socket is disconnected.");
        }
    }

    public void deleteOwnMessage(String chatId, String messageId) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        markLocalMessageDeleted(messageId);

        JsonObject event = new JsonObject();
        event.addProperty("type", "delete_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);
        event.addProperty("senderId", senderId);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to delete message while socket is disconnected.");
        }
    }

    public void deleteOpponentMessage(String chatId, String messageId) {
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        hideLocalMessage(messageId);

        JsonObject event = new JsonObject();
        event.addProperty("type", "delete_opponent_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to delete message while socket is disconnected.");
        }
    }

    public void sendTyping(String chatId, String receiverId, boolean typing) {
        JsonObject event = new JsonObject();
        event.addProperty("type", typing ? "typing_start" : "typing_stop");
        event.addProperty("chatId", chatId);
        event.addProperty("receiverId", normalizeAccountId(receiverId));
        socketClient.send(event);
    }

    public void syncAfterReconnect(String phoneNumber) {
        ioExecutor.execute(() -> {
            Long lastSync = messageDao.getLastSyncTime();
            appFunctionManager.syncChatMessages(
                    phoneNumber,
                    lastSync == null ? 0L : lastSync,
                    new AppFunctionManager.Callback() {
                @Override
                public void onSuccess(Object object) {
                    if (!(object instanceof JsonObject)) {
                        return;
                    }
                    JsonObject response = (JsonObject) object;
                    JsonArray messages = response.getAsJsonArray("messages");
                    JsonObject chatListSettings = response.has("chatList")
                            && response.get("chatList").isJsonObject()
                            ? response.getAsJsonObject("chatList") : null;
                    if (messages == null) {
                        return;
                    }
                    List<MessageEntity> entities = new ArrayList<>();
                    for (JsonElement element : messages) {
                        if (element != null && element.isJsonObject()) {
                            MessageEntity message = toMessageEntity(element.getAsJsonObject());
                            if (message != null) {
                                entities.add(message);
                            }
                        }
                    }
                    ioExecutor.execute(() -> {
                        preserveLocalAttachmentUris(entities);
                        messageDao.upsertAll(entities);
                        for (MessageEntity message : entities) updateChatSummary(message);
                        applySyncedUnreadCounts(chatListSettings);
                    });
                }

                @Override
                public void onError(String error) {
                    notifySocketError(error);
                }
            });
        });
    }

    public void refreshChatList(String phoneNumber) {
        String normalizedPhoneNumber = normalizeAccountId(phoneNumber);
        final int generation;
        synchronized (this) {
            chatListPhoneNumber = normalizedPhoneNumber;
            nextChatListCursor = null;
            chatListHasMore = true;
            chatListPageLoading = false;
            generation = ++chatListGeneration;
        }
        chatListPaginationLoading.postValue(false);
        requestChatListPage(normalizedPhoneNumber, null, generation);
    }

    public LiveData<Boolean> observeChatListPaginationLoading() {
        return chatListPaginationLoading;
    }

    public void loadNextChatListPage() {
        final String phoneNumber;
        final String cursor;
        final int generation;
        synchronized (this) {
            if (chatListPageLoading || !chatListHasMore
                    || nextChatListCursor == null || nextChatListCursor.isEmpty()
                    || chatListPhoneNumber.isEmpty()) {
                return;
            }
            phoneNumber = chatListPhoneNumber;
            cursor = nextChatListCursor;
            generation = chatListGeneration;
        }
        requestChatListPage(phoneNumber, cursor, generation);
    }

    private void requestChatListPage(String phoneNumber, String cursor, int generation) {
        final boolean pagination = cursor != null && !cursor.isEmpty();
        synchronized (this) {
            if (chatListPageLoading || generation != chatListGeneration) return;
            chatListPageLoading = true;
        }
        if (pagination) chatListPaginationLoading.postValue(true);
        appFunctionManager.getChatList(
                phoneNumber,
                CHAT_LIST_PAGE_SIZE,
                cursor,
                new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                synchronized (ChatRepository.this) {
                    if (generation != chatListGeneration) return;
                }
                if (!(object instanceof JsonObject)) {
                    finishChatListPage(generation, null, false);
                    return;
                }
                JsonObject response = (JsonObject) object;
                if ((cursor == null || cursor.isEmpty())
                        && response.has("total_unread")
                        && !response.get("total_unread").isJsonNull()) {
                    notifyTotalUnread(Math.max(0,
                            (int) JsonParserUtil.getLong(response, "total_unread")));
                }
                JsonArray userProfiles = response.getAsJsonArray("userProfiles");
                if (userProfiles == null) {
                    finishChatListPage(generation, null, false);
                    return;
                }
                String returnedCursor = JsonParserUtil.getString(response, "nextCursor");
                boolean hasMore = JsonParserUtil.getBoolean(response, "hasMore")
                        && !returnedCursor.isEmpty();
                List<ChatEntity> chats = new ArrayList<>();
                Set<String> chatsWithServerUnreadCount = new HashSet<>();
                long now = System.currentTimeMillis();
                for (JsonElement element : userProfiles) {
                    if (element != null && element.isJsonObject()) {
                        JsonObject profile = element.getAsJsonObject();
                        ChatEntity chat = toChatEntity(profile, now);
                        if (chat != null) {
                            chats.add(chat);
                            if (profile.has("unread_count")
                                    && !profile.get("unread_count").isJsonNull()) {
                                chatsWithServerUnreadCount.add(chat.chatId);
                            }
                        }
                    }
                }
                ioExecutor.execute(() -> {
                    for (ChatEntity chat : chats) {
                        ChatEntity existing = chatDao.findByChatId(chat.chatId);
                        if (chat.chatId.equals(activeChatId)) {
                            chat.unreadCount = 0;
                        } else if (existing != null
                                && !chatsWithServerUnreadCount.contains(chat.chatId)) {
                            chat.unreadCount = existing.unreadCount;
                        }
                    }
                    chatDao.upsertAll(chats);
                    prefetchChatProfilePhotos(chats);
                    finishChatListPage(generation, returnedCursor, hasMore);
                });
            }

            @Override
            public void onError(String error) {
                releaseChatListPage(generation);
                notifySocketError(error);
            }
        });
    }

    private synchronized void finishChatListPage(
            int generation, String cursor, boolean hasMore) {
        if (generation != chatListGeneration) return;
        chatListPageLoading = false;
        nextChatListCursor = cursor;
        chatListHasMore = hasMore;
        chatListPaginationLoading.postValue(false);
    }

    private synchronized void releaseChatListPage(int generation) {
        if (generation == chatListGeneration) {
            chatListPageLoading = false;
            chatListPaginationLoading.postValue(false);
        }
    }

    private void notifyTotalUnread(int totalUnread) {
        latestTotalUnread = totalUnread;
        EventListener listener = eventListener;
        if (listener == null) return;
        mainHandler.post(() -> {
            if (eventListener == listener) listener.onTotalUnread(totalUnread);
        });
    }

    private void prefetchChatProfilePhotos(List<ChatEntity> chats) {
        if (chats == null || chats.isEmpty()) return;
        for (ChatEntity chat : chats) {
            if (chat == null || chat.chatId == null || chat.chatId.isEmpty()
                    || chat.otherUserId == null || chat.otherUserId.isEmpty()
                    || chat.profilePhotoUrl == null || chat.profilePhotoUrl.trim().isEmpty()
                    || !profilePhotoDownloads.add(chat.chatId)) {
                continue;
            }
            profilePhotoExecutor.execute(() -> {
                try {
                    String localPath = ChatProfilePhotoStore.downloadAndStore(
                            appContext,
                            chat.otherUserId,
                            chat.profilePhotoUrl
                    );
                    if (localPath != null) {
                        chatDao.updateLocalProfilePhotoPath(
                                chat.chatId,
                                chat.profilePhotoUrl,
                                localPath,
                                System.currentTimeMillis()
                        );
                    }
                } finally {
                    profilePhotoDownloads.remove(chat.chatId);
                }
            });
        }
    }

    public void hydrateChat(String chatId, String phoneNumber) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return;
        }
        appFunctionManager.getChat(chatId, phoneNumber, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                if (!(object instanceof JsonObject)) {
                    return;
                }
                JsonElement chatElement = ((JsonObject) object).get("chat");
                if (chatElement == null || !chatElement.isJsonObject()) {
                    return;
                }
                List<MessageEntity> messages = parseChatDocument(chatElement.getAsJsonObject(), chatId);
                ioExecutor.execute(() -> {
                    preserveLocalAttachmentUris(messages);
                    messageDao.upsertAll(messages);
                });
            }

            @Override
            public void onError(String error) {
                notifySocketError(error);
            }
        });
    }

    public void syncPresence(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        appFunctionManager.syncPresence(userIds, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                if (!(object instanceof JsonObject)) {
                    return;
                }
                JsonArray presence = ((JsonObject) object).getAsJsonArray("presence");
                if (presence == null) {
                    return;
                }
                List<PresenceEntity> entities = new ArrayList<>();
                for (JsonElement element : presence) {
                    if (element != null && element.isJsonObject()) {
                        entities.add(toPresenceEntity(element.getAsJsonObject()));
                    }
                }
                ioExecutor.execute(() -> presenceDao.upsertAll(entities));
            }

            @Override
            public void onError(String error) {
                notifySocketError(error);
            }
        });
    }

    public void uploadFcmToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        appFunctionManager.updateFcmToken(token, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
            }

            @Override
            public void onError(String error) {
                notifySocketError(error);
            }
        });
    }

    public void syncFromFcmTap(String phoneNumber) {
        connect();
        syncAfterReconnect(phoneNumber);
    }

    @Override
    public void onConnected() {
        resendCompletedUploadsAwaitingAck();
        String phoneNumber = LoginStateManager.getInstance().getUID(appContext);
        syncAfterReconnect(phoneNumber);
        if (callEventListener != null) {
            JsonObject event = new JsonObject();
            event.addProperty("type", "call_socket_reconnected");
            mainHandler.post(() -> {
                if (callEventListener != null) callEventListener.onCallEvent(event);
            });
        }
    }

    @Override
    public void onEvent(JsonObject event) {
        String type = JsonParserUtil.getString(event, "type");
        if (event.has("total_unread") && !event.get("total_unread").isJsonNull()) {
            notifyTotalUnread(Math.max(0,
                    (int) JsonParserUtil.getLong(event, "total_unread")));
        }
        if ("call_invite".equals(type) && incomingCallListener != null) {
            mainHandler.post(() -> incomingCallListener.onIncomingCall(event));
        }
        if ((type.startsWith("call_") || "ice_candidate".equals(type))
                && callEventListener != null) {
            CallEventListener listener = callEventListener;
            mainHandler.post(() -> {
                if (callEventListener == listener) listener.onCallEvent(event);
            });
        }
        if ("message_ack".equals(type)) {
            handleMessageAck(event);
        } else if ("message_failed".equals(type)) {
            handleMessageFailed(event);
        } else if ("new_message".equals(type)) {
            handleNewMessage(event);
        } else if ("message_seen".equals(type)) {
            handleMessageSeen(event);
        } else if ("message_seen_ack".equals(type)) {
            handleMessageSeen(event);
        } else if ("message_delivered".equals(type)) {
            handleMessageDelivered(event);
        } else if ("message_delivered_ack".equals(type)) {
            handleMessageDelivered(event);
        } else if ("edit_message_ack".equals(type)) {
            handleMessageEdited(event);
        } else if ("edit_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("delete_message_ack".equals(type)) {
            handleMessageDeleted(event);
        } else if ("delete_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("delete_opponent_message_ack".equals(type)) {
            handleOpponentMessageDeleted(event);
        } else if ("delete_opponent_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("message_edited".equals(type)) {
            handleMessageEdited(event);
        } else if ("message_deleted".equals(type)) {
            handleMessageDeleted(event);
        } else if ("typing_start".equals(type)) {
            notifyTyping(event, true);
        } else if ("typing_stop".equals(type)) {
            notifyTyping(event, false);
        } else if ("online_status".equals(type)) {
            handleOnlineStatus(event);
        }
    }

    @Override
    public void onClosed() {
        notifyCallSocketDisconnected();
    }

    @Override
    public void onFailure(String error) {
        notifyCallSocketDisconnected();
        notifySocketError(error);
    }

    private void notifyCallSocketDisconnected() {
        if (callEventListener == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "call_socket_disconnected");
        mainHandler.post(() -> {
            if (callEventListener != null) callEventListener.onCallEvent(event);
        });
    }

    private void handleMessageAck(JsonObject event) {
        String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
        String messageId = JsonParserUtil.getString(event, "messageId");
        long sentTime = JsonParserUtil.getLong(event, "sentTime");
        ioExecutor.execute(() -> {
            messageDao.applyAck(clientMessageId, messageId, MessageStatus.SENT, sentTime);
            MessageEntity acknowledged = messageDao.findByClientMessageId(clientMessageId);
            if (acknowledged != null) updateChatSummary(acknowledged);
            transferDao.messageSent(clientMessageId, System.currentTimeMillis());
        });
    }

    private void resendCompletedUploadsAwaitingAck() {
        ioExecutor.execute(() -> {
            for (TransferEntity transfer : transferDao.completedUploadsAwaitingAck()) {
                MessageEntity message = messageDao.findByClientMessageId(transfer.clientMessageId);
                if (message == null || !MessageStatus.SENDING.equals(message.status)
                        || transfer.attachmentId == null) continue;
                JsonObject attachment = new JsonObject();
                attachment.addProperty("id", transfer.attachmentId);
                attachment.addProperty("kind", transfer.kind);
                attachment.addProperty("name", transfer.fileName);
                attachment.addProperty("mimeType", transfer.mimeType);
                attachment.addProperty("url", transfer.remoteUrl);
                attachment.addProperty("size", transfer.totalSize);
                sendExistingMessage(transfer.clientMessageId, transfer.chatId, transfer.senderId,
                        transfer.receiverId, transfer.caption, transfer.repliedMessageId,
                        transfer.kind, attachment, null);
            }
        });
    }

    private void handleMessageFailed(JsonObject event) {
        String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
        ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(clientMessageId, MessageStatus.FAILED));
    }

    private void handleNewMessage(JsonObject event) {
        JsonElement messageElement = event.get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            return;
        }
        MessageEntity message = toMessageEntity(messageElement.getAsJsonObject());
        if (message == null) {
            return;
        }
        ioExecutor.execute(() -> {
            boolean isNewMessage = !messageDao.existsByMessageId(message.messageId);
            preserveLocalAttachmentUri(message);
            messageDao.upsert(message);
            updateChatSummary(message);
            boolean incoming = !normalizeAccountId(message.senderId).equals(currentUserId);
            if (incoming && isNewMessage) {
                if (message.chatId.equals(activeChatId)) {
                    chatDao.clearUnreadCount(message.chatId);
                } else {
                    chatDao.incrementUnreadCount(message.chatId);
                }
            }
            messageDao.markDelivered(
                    Collections.singletonList(message.messageId),
                    MessageStatus.DELIVERED,
                    System.currentTimeMillis()
            );
        });
        markDelivered(message.chatId, Collections.singletonList(message.messageId));
    }

    private void preserveLocalAttachmentUris(List<MessageEntity> messages) {
        if (messages == null) return;
        for (MessageEntity message : messages) preserveLocalAttachmentUri(message);
    }

    private void preserveLocalAttachmentUri(MessageEntity message) {
        if (message == null || message.attachmentLocalUri != null
                || message.clientMessageId == null || message.clientMessageId.isEmpty()) return;
        MessageEntity existing = messageDao.findByClientMessageId(message.clientMessageId);
        if (existing != null) message.attachmentLocalUri = existing.attachmentLocalUri;
    }

    private void handleMessageDelivered(JsonObject event) {
        JsonArray ids = event.getAsJsonArray("messageIds");
        if (ids == null) {
            return;
        }
        List<String> messageIds = new ArrayList<>();
        for (JsonElement id : ids) {
            messageIds.add(id.getAsString());
        }
        long deliveredTime = JsonParserUtil.getLong(event, "deliveredTime");
        ioExecutor.execute(() -> {
            messageDao.markDelivered(messageIds, MessageStatus.DELIVERED, deliveredTime);
            for (String messageId : messageIds) {
                MessageEntity message = messageDao.findByMessageId(messageId);
                if (message != null) chatDao.updateLastMessageReceipt(message.chatId,
                        message.sentTime, deliveredTime, message.readTime);
            }
        });
    }

    private void handleMessageSeen(JsonObject event) {
        JsonArray ids = event.getAsJsonArray("messageIds");
        if (ids == null) {
            return;
        }
        List<String> messageIds = new ArrayList<>();
        for (JsonElement id : ids) {
            messageIds.add(id.getAsString());
        }
        long readTime = JsonParserUtil.getLong(event, "readTime");
        ioExecutor.execute(() -> {
            messageDao.markSeen(messageIds, MessageStatus.SEEN, readTime);
            for (String messageId : messageIds) {
                MessageEntity message = messageDao.findByMessageId(messageId);
                if (message != null) chatDao.updateLastMessageReceipt(message.chatId,
                        message.sentTime, message.deliveredTime, readTime);
            }
        });
    }

    private void handleMessageEdited(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        String text = JsonParserUtil.getString(event, "text");
        if (messageId.isEmpty() || text.isEmpty()) {
            return;
        }
        updateLocalMessageText(messageId, text);
    }

    private void handleMessageDeleted(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        if (messageId.isEmpty()) {
            JsonElement messageElement = event.get("message");
            if (messageElement != null && messageElement.isJsonObject()) {
                messageId = JsonParserUtil.getString(messageElement.getAsJsonObject(), "id");
            }
        }
        markLocalMessageDeleted(messageId);
    }

    private void handleOpponentMessageDeleted(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        hideLocalMessage(messageId);
    }

    private void handleOnlineStatus(JsonObject event) {
        PresenceEntity presence = toPresenceEntity(event);
        ioExecutor.execute(() -> presenceDao.upsert(presence));
    }

    private void notifyTyping(JsonObject event, boolean typing) {
        if (eventListener == null) {
            return;
        }
        String chatId = JsonParserUtil.getString(event, "chatId");
        String userId = JsonParserUtil.getString(event, "userId");
        mainHandler.post(() -> eventListener.onTyping(chatId, userId, typing));
    }

    private void notifySocketError(Throwable throwable) {
        notifySocketError(throwable == null || throwable.getMessage() == null ? "Network error." : throwable.getMessage());
    }

    private void notifySocketError(String error) {
        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onSocketError(error));
        }
    }

    private MessageEntity toMessageEntity(JsonObject message) {
        if ("gone".equals(JsonParserUtil.getString(message, "visible"))) {
            return null;
        }
        String id = JsonParserUtil.getString(message, "id");
        if (id.isEmpty()) {
            id = JsonParserUtil.getString(message, "messageId");
        }
        String status = JsonParserUtil.getString(message, "status");
        if (status.isEmpty()) {
            status = MessageStatus.SENT;
        }
        JsonObject attachment = message.has("attachment") && message.get("attachment").isJsonObject()
                ? message.getAsJsonObject("attachment") : null;
        JsonObject location = message.has("location") && message.get("location").isJsonObject()
                ? message.getAsJsonObject("location") : null;
        String messageType = JsonParserUtil.getString(message, "messageType");
        if (messageType.isEmpty()) messageType = "text";
        MessageEntity entity = new MessageEntity(
                id,
                JsonParserUtil.getString(message, "clientMessageId"),
                JsonParserUtil.getString(message, "chatId"),
                normalizeAccountId(JsonParserUtil.getString(message, "senderId")),
                normalizeAccountId(JsonParserUtil.getString(message, "receiverId")),
                JsonParserUtil.getString(message, "text"),
                JsonParserUtil.getString(message, "repliedMessageId"),
                JsonParserUtil.getLong(message, "sentTime"),
                getNullableLong(message, "deliveredTime"),
                getNullableLong(message, "readTime"),
                status,
                messageType,
                attachment == null ? null : JsonParserUtil.getString(attachment, "id"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "kind"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "name"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "mimeType"),
                attachment == null ? null : JsonParserUtil.getString(attachment, "url"),
                null,
                attachment == null ? null : JsonParserUtil.getLong(attachment, "size"),
                location == null ? null : location.get("latitude").getAsDouble(),
                location == null ? null : location.get("longitude").getAsDouble(),
                location == null || !location.has("accuracy") || location.get("accuracy").isJsonNull()
                        ? null : location.get("accuracy").getAsFloat()
        );
        entity.attachmentSha256 = attachment == null ? null
                : JsonParserUtil.getString(attachment, "sha256");
        return entity;
    }

    private List<MessageEntity> parseChatDocument(JsonObject chat, String chatId) {
        List<MessageEntity> messages = new ArrayList<>();
        for (java.util.Map.Entry<String, JsonElement> entry : chat.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject message = element.getAsJsonObject();
            if (!message.has("text")) {
                continue;
            }
            if (!message.has("id") || message.get("id").isJsonNull() || message.get("id").getAsString().isEmpty()) {
                message.addProperty("id", entry.getKey());
            }
            if (!message.has("chatId") || message.get("chatId").isJsonNull() || message.get("chatId").getAsString().isEmpty()) {
                message.addProperty("chatId", chatId);
            }
            MessageEntity entity = toMessageEntity(message);
            if (entity != null) {
                messages.add(entity);
            }
        }
        return messages;
    }

    private PresenceEntity toPresenceEntity(JsonObject presence) {
        String userId = JsonParserUtil.getString(presence, "userId");
        boolean isOnline = presence.has("isOnline") && !presence.get("isOnline").isJsonNull() && presence.get("isOnline").getAsBoolean();
        return new PresenceEntity(
                userId,
                isOnline,
                getNullableLong(presence, "lastSeen"),
                System.currentTimeMillis()
        );
    }

    private ChatEntity toChatEntity(JsonObject profile, long updatedAt) {
        String chatId = JsonParserUtil.getString(profile, "chatId");
        String phoneNumber = JsonParserUtil.getString(profile, "phoneNumber");
        boolean isOnline = JsonParserUtil.getBoolean(profile,"isOnline");
        long lastSeen = JsonParserUtil.getLong(profile,"lastSeen");
        String lastMessage = "";
        long lastMessageTime = 0;
        String lastMessageSenderId = "";
        Long lastMessageDeliveredTime = null;
        Long lastMessageReadTime = null;
        String lastMessageType = "text";
        String lastMessageAttachmentName = "";
        JsonElement lastMessageElement = profile.get("last_message");
        if (lastMessageElement == null || lastMessageElement.isJsonNull()) {
            // Compatibility with the previous /chats/list response.
            lastMessageElement = profile.get("lastMessage");
        }
        if (lastMessageElement != null && lastMessageElement.isJsonObject()) {
            JsonObject lastMessageObject = lastMessageElement.getAsJsonObject();
            lastMessage = getMessagePreview(lastMessageObject);
            lastMessageTime = JsonParserUtil.getLong(lastMessageObject, "sentTime");
            lastMessageSenderId = normalizeAccountId(
                    JsonParserUtil.getString(lastMessageObject, "senderId"));
            lastMessageDeliveredTime = getNullableLong(lastMessageObject, "deliveredTime");
            lastMessageReadTime = getNullableLong(lastMessageObject, "readTime");
            lastMessageType = JsonParserUtil.getString(lastMessageObject, "messageType");
            if (lastMessageType.isEmpty()) lastMessageType = "text";
            if (lastMessageObject.has("attachment")
                    && lastMessageObject.get("attachment").isJsonObject()) {
                lastMessageAttachmentName = JsonParserUtil.getString(
                        lastMessageObject.getAsJsonObject("attachment"), "name");
            }
        }
        int unreadCount = Math.max(
                0,
                (int) JsonParserUtil.getLong(profile, "unread_count")
        );
        boolean pinned = JsonParserUtil.getBoolean(profile, "pinned");
        long notificationMuted = JsonParserUtil.getLong(profile, "notification_muted");
        boolean archived = JsonParserUtil.getBoolean(profile, "archieved");

        if (chatId.isEmpty()) {
            return null;
        }
        String profilePhotoUrl = JsonParserUtil.getString(profile, "profilePhotoUrl");
        return new ChatEntity(
                chatId,
                phoneNumber,
                normalizeAccountId(phoneNumber),
                profilePhotoUrl,
                "",
                lastMessage,
                lastMessageTime,
                lastMessageSenderId,
                lastMessageDeliveredTime,
                lastMessageReadTime,
                lastMessageType,
                lastMessageAttachmentName,
                unreadCount,
                pinned,
                notificationMuted,
                archived,
                isOnline,
                lastSeen,
                updatedAt
        );
    }

    private String getMessagePreview(JsonObject message) {
        String text = JsonParserUtil.getString(message, "text").trim();
        if (!text.isEmpty()) {
            return text;
        }

        String messageType = JsonParserUtil.getString(message, "messageType");
        switch (messageType) {
            case "image":
                return "Photo";
            case "video":
                return "Video";
            case "audio":
            case "voice":
                return "Voice message";
            case "voice_call":
                return "Voice call";
            case "video_call":
                return "Video call";
            case "location":
                return "Location";
            default:
                return "Message";
        }
    }

    private void updateChatSummary(MessageEntity message) {
        if (message == null || message.chatId == null || message.chatId.trim().isEmpty()) return;
        long sentTime = message.sentTime > 0 ? message.sentTime : System.currentTimeMillis();
        String preview = getMessagePreview(message);
        int updated = chatDao.updateLastMessage(message.chatId, preview, sentTime,
                normalizeAccountId(message.senderId), message.deliveredTime, message.readTime,
                message.messageType, message.attachmentName,
                System.currentTimeMillis());
        if (updated > 0) return;
        // A newer message may already own the summary when socket events arrive out of order.
        if (chatDao.findByChatId(message.chatId) != null) return;

        String ownId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        String senderId = normalizeAccountId(message.senderId);
        String receiverId = normalizeAccountId(message.receiverId);
        String otherUserId = ownId.equals(senderId) ? receiverId : senderId;
        chatDao.upsert(new ChatEntity(
                message.chatId,
                otherUserId,
                otherUserId,
                "",
                "",
                preview,
                sentTime,
                senderId,
                message.deliveredTime,
                message.readTime,
                message.messageType,
                message.attachmentName,
                0,
                false,
                0,
                false,
                false,
                0,
                System.currentTimeMillis()
        ));
    }

    private void applySyncedUnreadCounts(JsonObject chatListSettings) {
        if (chatListSettings == null) return;
        for (java.util.Map.Entry<String, JsonElement> entry : chatListSettings.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
            int unreadCount = Math.max(0, (int) JsonParserUtil.getLong(
                    entry.getValue().getAsJsonObject(), "unread_count"));
            if (entry.getKey().equals(activeChatId)) unreadCount = 0;
            chatDao.setUnreadCount(entry.getKey(), unreadCount);
        }
    }

    private String getMessagePreview(MessageEntity message) {
        String text = message.text == null ? "" : message.text.trim();
        if (!text.isEmpty()) return text;
        String messageType = message.messageType == null ? "" : message.messageType;
        switch (messageType) {
            case "image": return "Photo";
            case "video": return "Video";
            case "audio":
            case "voice": return "Voice message";
            case "voice_call": return "Voice call";
            case "video_call": return "Video call";
            case "location": return "Location";
            default: return "Message";
        }
    }

    private Long getNullableLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeAccountId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        if (normalized.startsWith("+")) {
            return normalized.substring(1);
        }
        return normalized;
    }

}
