package com.w3n.pinggo.data.repository;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String PERF_TAG = "ChatsRepoPerf";
    private static final String TESTING_TAG = "PARVEZ_TESTING";
    private static final long ATTACHMENT_CHUNK_SIZE = 3L * 1024L * 1024L;
    private static final int CHAT_LIST_PAGE_SIZE = 20;
    public static final int MESSAGE_PAGE_SIZE = 50;
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

    public interface MessagePageCallback {
        void onLoaded(String nextCursor, boolean hasMore, int loadedCount);
        void onError(String message);
    }

    /** Pagination state retained only while this application process is alive. */
    public static final class MessageSessionState {
        private final int messageLimit;
        private final boolean firstPageLoaded;
        private final String nextCursor;
        private final boolean networkHasMore;

        private MessageSessionState(int messageLimit, boolean firstPageLoaded,
                                    String nextCursor, boolean networkHasMore) {
            this.messageLimit = Math.max(MESSAGE_PAGE_SIZE, messageLimit);
            this.firstPageLoaded = firstPageLoaded;
            this.nextCursor = nextCursor;
            this.networkHasMore = networkHasMore;
        }

        public int getMessageLimit() { return messageLimit; }
        public boolean isFirstPageLoaded() { return firstPageLoaded; }
        public String getNextCursor() { return nextCursor; }
        public boolean hasMoreOnNetwork() { return networkHasMore; }
    }

    private static volatile ChatRepository instance;

    private final Context appContext;
    private final MessageDao messageDao;
    private final ChatDao chatDao;
    private final PresenceDao presenceDao;
    private final TransferDao transferDao;
    private final LiveData<List<ChatEntity>> chatListLiveData;
    private final AppFunctionManager appFunctionManager;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService profilePhotoExecutor = Executors.newFixedThreadPool(4);
    private final Set<String> profilePhotoDownloads =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<String> locallyReadChats =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<String> pendingDeliveredAcks =
            Collections.synchronizedSet(new HashSet<>());
    private final Map<String, MessageSessionState> messageSessionStates = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ChatWebSocketClient socketClient;
    private EventListener eventListener;
    private String chatListPhoneNumber = "";
    private String nextChatListCursor;
    private boolean chatListHasMore;
    private boolean chatListPageLoading;
    private boolean chatListFirstPageLoaded;
    private boolean chatCachePreloadStarted;
    private boolean chatCacheLoaded;
    private int cachedChatCount;
    private boolean chatListRefreshing;
    private boolean chatListPaginating;
    private String chatListError = "";
    private final MutableLiveData<ChatListState> chatListState =
            new MutableLiveData<>(ChatListState.initial());
    private final Observer<List<ChatEntity>> chatCacheObserver = chats -> {
        synchronized (ChatRepository.this) {
            chatCacheLoaded = true;
            cachedChatCount = chats == null ? 0 : chats.size();
        }
        Log.d(TESTING_TAG, "chat_list source=room_cache phase=loaded count="
                + (chats == null ? 0 : chats.size()));
        publishChatListState();
    };
    private int chatListGeneration;
    private volatile int latestTotalUnread = -1;
    private CallEventListener callEventListener;
    private IncomingCallListener incomingCallListener;
    private String currentUserId;
    private volatile String activeChatId = "";

    private ChatRepository(Context context) {
        appContext = context.getApplicationContext();
        PingGoDatabase database = PingGoDatabase.getInstance(appContext);
        messageDao = database.messageDao();
        chatDao = database.chatDao();
        chatListLiveData = chatDao.observeChats();
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
                instance.stopChatCacheObservation();
                instance.disconnect();
            }
            instance = null;
        }
    }

    public synchronized MessageSessionState getMessageSessionState(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) return null;
        return messageSessionStates.get(chatId);
    }

    public synchronized void saveMessageSessionState(
            String chatId, int messageLimit, boolean firstPageLoaded,
            String nextCursor, boolean networkHasMore) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        MessageSessionState previous = messageSessionStates.get(chatId);
        MessageSessionState updated = new MessageSessionState(
                messageLimit, firstPageLoaded, nextCursor, networkHasMore);
        messageSessionStates.put(chatId, updated);
        if (previous == null
                || previous.messageLimit != updated.messageLimit
                || previous.firstPageLoaded != updated.firstPageLoaded
                || previous.networkHasMore != updated.networkHasMore
                || !sameValue(previous.nextCursor, updated.nextCursor)) {
            Log.d(TESTING_TAG, "message_cache source=session phase=saved chatId=" + chatId
                    + " limit=" + updated.messageLimit
                    + " firstPageLoaded=" + updated.firstPageLoaded
                    + " hasMore=" + updated.networkHasMore
                    + " hasNextCursor=" + (updated.nextCursor != null
                    && !updated.nextCursor.isEmpty()));
        }
    }

    private static boolean sameValue(String first, String second) {
        return first == null ? second == null : first.equals(second);
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

    public LiveData<List<MessageEntity>> observeMessages(String chatId, int limit) {
        return messageDao.observeLatestMessages(chatId, Math.max(1, limit));
    }

    public LiveData<List<ChatEntity>> observeChats() {
        return chatListLiveData;
    }

    public void preloadChatCache() {
        synchronized (this) {
            if (chatCachePreloadStarted) return;
            chatCachePreloadStarted = true;
        }
        Log.d(TESTING_TAG, "chat_list source=room_cache phase=observe_start");
        Runnable startPreload = () -> chatListLiveData.observeForever(chatCacheObserver);
        if (Looper.myLooper() == Looper.getMainLooper()) startPreload.run();
        else mainHandler.post(startPreload);
    }

    private void stopChatCacheObservation() {
        Runnable stopPreload = () -> chatListLiveData.removeObserver(chatCacheObserver);
        if (Looper.myLooper() == Looper.getMainLooper()) stopPreload.run();
        else mainHandler.post(stopPreload);
    }

    public void updateChatSetting(String chatId, String setting, long value,
                                  AppFunctionManager.Callback callback) {
        String phoneNumber = normalizeAccountId(
                LoginStateManager.getInstance().getUID(appContext));
        appFunctionManager.updateChatSettings(
                phoneNumber, chatId, setting, value, callback);
    }

    public void updateChatSettings(List<String> chatIds, String setting, long value,
                                   AppFunctionManager.Callback callback) {
        if (chatIds == null || chatIds.isEmpty()) {
            if (callback != null) callback.onError("Select at least one chat.");
            return;
        }
        String phoneNumber = normalizeAccountId(
                LoginStateManager.getInstance().getUID(appContext));
        appFunctionManager.updateChatSettingsBulk(
                phoneNumber, chatIds, setting, value, callback);
    }

    public void deleteLocalChat(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        ioExecutor.execute(() -> chatDao.deleteByChatId(chatId));
    }

    public void setActiveChat(String chatId) {
        activeChatId = chatId == null ? "" : chatId.trim();
        if (!activeChatId.isEmpty()) {
            String openedChatId = activeChatId;
            ioExecutor.execute(() -> {
                ChatEntity chat = chatDao.findByChatId(openedChatId);
                boolean wasUnread = chat != null && chat.unreadCount > 0;
                chatDao.clearUnreadCount(openedChatId);
                locallyReadChats.add(openedChatId);
                if (wasUnread) {
                    int currentTotal = latestTotalUnread;
                    int updatedTotal = currentTotal >= 0
                            ? Math.max(0, currentTotal - 1)
                            : chatDao.countUnreadChats();
                    notifyTotalUnread(updatedTotal);
                }
            });
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

    /**
     * Acknowledges incoming messages while the chat list is visible. This deliberately
     * does not mark them seen; seen remains tied to opening the conversation.
     */
    public void acknowledgePendingIncomingDeliveries() {
        String receiverId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (receiverId.isEmpty()) return;
        ioExecutor.execute(() -> acknowledgeIncomingDeliveries(
                messageDao.findUndeliveredIncoming(receiverId)));
    }

    private void acknowledgeIncomingDeliveries(List<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) return;
        String receiverId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (receiverId.isEmpty()) return;

        Map<String, List<String>> idsByChat = new HashMap<>();
        for (MessageEntity message : messages) {
            if (message == null || message.invisible
                    || message.messageId == null || message.messageId.isEmpty()
                    || message.chatId == null || message.chatId.isEmpty()
                    || message.deliveredTime != null || message.readTime != null
                    || !receiverId.equals(normalizeAccountId(message.receiverId))) {
                continue;
            }
            if (!pendingDeliveredAcks.add(message.messageId)) continue;
            idsByChat.computeIfAbsent(message.chatId, ignored -> new ArrayList<>())
                    .add(message.messageId);
        }

        for (Map.Entry<String, List<String>> entry : idsByChat.entrySet()) {
            Log.d(TESTING_TAG, "delivery_ack phase=request chatId=" + entry.getKey()
                    + " messages=" + entry.getValue().size());
            markDelivered(entry.getKey(), entry.getValue());
        }
    }

    public void hideLocalMessage(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.markInvisible(messageId));
    }

    public void updateLocalMessageText(String messageId, String text) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.updateText(messageId, text));
    }

    public void markLocalMessageDeleted(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) return;
        ioExecutor.execute(() -> messageDao.markDeleted(messageId, "This Message was deleted"));
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
        deleteOwnMessages(chatId, Collections.singletonList(messageId));
    }

    public void deleteOwnMessages(String chatId, List<String> messageIds) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        List<String> ids = validMessageIds(messageIds);
        if (chatId == null || chatId.trim().isEmpty() || ids.isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        ioExecutor.execute(() -> {
            Map<String, MessageEntity> stored = messagesById(messageDao.findByMessageIds(ids));
            List<String> pending = new ArrayList<>();
            for (String messageId : ids) {
                MessageEntity message = stored.get(messageId);
                pending.add(messageId);
                if (message != null && isDeleted(message)) {
                    messageDao.markInvisible(messageId);
                } else {
                    messageDao.markDeleted(messageId, "This Message was deleted");
                }
            }
            if (pending.isEmpty()) return;
            JsonObject event = new JsonObject();
            event.addProperty("type", pending.size() == 1 ? "delete_message" : "delete_messages");
            event.addProperty("chatId", chatId);
            if (pending.size() == 1) event.addProperty("messageId", pending.get(0));
            else event.add("messageIds", jsonIds(pending));
            event.addProperty("senderId", senderId);
            if (!socketClient.send(event)) {
                notifySocketError("Unable to delete message while socket is disconnected.");
            }
        });
    }

    public void deleteOpponentMessage(String chatId, String messageId) {
        deleteOpponentMessages(chatId, Collections.singletonList(messageId));
    }

    public void deleteOpponentMessages(String chatId, List<String> messageIds) {
        List<String> ids = validMessageIds(messageIds);
        if (chatId == null || chatId.trim().isEmpty() || ids.isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        ioExecutor.execute(() -> messageDao.deleteByMessageIds(ids));

        JsonObject event = new JsonObject();
        event.addProperty("type",
                ids.size() == 1 ? "delete_opponent_message" : "delete_opponent_messages");
        event.addProperty("chatId", chatId);
        if (ids.size() == 1) event.addProperty("messageId", ids.get(0));
        else event.add("messageIds", jsonIds(ids));

        if (!socketClient.send(event)) {
            notifySocketError("Unable to delete message while socket is disconnected.");
        }
    }

    public void pinMessages(String chatId, List<String> messageIds) {
        List<String> ids = validMessageIds(messageIds);
        if (chatId == null || chatId.trim().isEmpty() || ids.isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }
        ioExecutor.execute(() -> {
            Map<String, MessageEntity> stored = messagesById(messageDao.findByMessageIds(ids));
            List<String> pending = new ArrayList<>();
            for (String messageId : ids) {
                MessageEntity message = stored.get(messageId);
                if (message != null && message.pinned) continue;
                pending.add(messageId);
            }
            if (pending.isEmpty()) return;
            long pinnedAt = System.currentTimeMillis();
            messageDao.updatePinned(pending, true, pinnedAt);
            JsonObject event = new JsonObject();
            event.addProperty("type", "pin_messages");
            event.addProperty("chatId", chatId);
            event.add("messageIds", jsonIds(pending));
            event.addProperty("pinned", true);
            event.addProperty("pinned_at", pinnedAt);
            if (!socketClient.send(event)) {
                notifySocketError("Unable to pin messages while socket is disconnected.");
            }
        });
    }

    public void unpinMessages(String chatId, List<String> messageIds) {
        List<String> ids = validMessageIds(messageIds);
        if (chatId == null || chatId.trim().isEmpty() || ids.isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }
        ioExecutor.execute(() -> {
            Map<String, MessageEntity> stored = messagesById(messageDao.findByMessageIds(ids));
            List<String> pending = new ArrayList<>();
            for (String messageId : ids) {
                MessageEntity message = stored.get(messageId);
                if (message != null && !message.pinned) continue;
                pending.add(messageId);
            }
            if (pending.isEmpty()) return;
            messageDao.updatePinned(pending, false, null);
            JsonObject event = new JsonObject();
            event.addProperty("type", "unpin_messages");
            event.addProperty("chatId", chatId);
            event.add("messageIds", jsonIds(pending));
            event.addProperty("pinned", false);
            if (!socketClient.send(event)) {
                notifySocketError("Unable to unpin messages while socket is disconnected.");
            }
        });
    }

    public void forwardMessages(String sourceChatId, List<String> messageIds,
                                String destinationChatId, String receiverId) {
        List<String> ids = validMessageIds(messageIds);
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        String normalizedReceiver = normalizeAccountId(receiverId);
        if (sourceChatId == null || sourceChatId.trim().isEmpty()
                || destinationChatId == null || destinationChatId.trim().isEmpty()
                || normalizedReceiver.isEmpty() || ids.isEmpty()) {
            notifySocketError("Forward destination or messages are missing.");
            return;
        }
        JsonObject event = new JsonObject();
        event.addProperty("type", "forward_messages");
        event.addProperty("sourceChatId", sourceChatId);
        event.addProperty("destinationChatId", destinationChatId);
        event.addProperty("senderId", senderId);
        event.addProperty("receiverId", normalizedReceiver);
        event.addProperty("operationId", UUID.randomUUID().toString());
        event.add("messageIds", jsonIds(ids));
        if (!socketClient.send(event)) {
            notifySocketError("Unable to forward messages while socket is disconnected.");
        }
    }

    private static List<String> validMessageIds(List<String> messageIds) {
        List<String> ids = new ArrayList<>();
        if (messageIds == null) return ids;
        for (String id : messageIds) {
            if (id != null && !id.trim().isEmpty() && !ids.contains(id.trim())) ids.add(id.trim());
        }
        return ids;
    }

    private static JsonArray jsonIds(List<String> messageIds) {
        JsonArray ids = new JsonArray();
        for (String messageId : messageIds) ids.add(messageId);
        return ids;
    }

    private static Map<String, MessageEntity> messagesById(List<MessageEntity> messages) {
        Map<String, MessageEntity> values = new HashMap<>();
        if (messages == null) return values;
        for (MessageEntity message : messages) {
            if (message != null && message.messageId != null) values.put(message.messageId, message);
        }
        return values;
    }

    private static boolean isDeleted(MessageEntity message) {
        return message.deletedText != null
                || "This Message was deleted".equals(message.text);
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
            // An empty database has no incremental checkpoint. Chat history is hydrated
            // page-by-page when a conversation opens instead of downloading every message.
            if (lastSync == null) return;
            appFunctionManager.syncChatMessages(
                    phoneNumber,
                    lastSync,
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
                        acknowledgeIncomingDeliveries(entities);
                        Log.d(TESTING_TAG, "message_cache source=sync_routes phase=room_upsert"
                                + " messages=" + entities.size() + " since=" + lastSync);
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
            chatListFirstPageLoaded = false;
            chatListRefreshing = false;
            chatListPaginating = false;
            chatListError = "";
            generation = ++chatListGeneration;
        }
        publishChatListState();
        requestChatListPage(normalizedPhoneNumber, null, generation);
    }

    public void ensureChatListLoaded(String phoneNumber) {
        preloadChatCache();
        String normalizedPhoneNumber = normalizeAccountId(phoneNumber);
        synchronized (this) {
            if (normalizedPhoneNumber.equals(chatListPhoneNumber)
                    && (chatListPageLoading || chatListFirstPageLoaded)) {
                return;
            }
        }
        refreshChatList(normalizedPhoneNumber);
    }

    public LiveData<ChatListState> observeChatListState() {
        return chatListState;
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
        final long requestStarted = SystemClock.elapsedRealtime();
        final boolean pagination = cursor != null && !cursor.isEmpty();
        synchronized (this) {
            if (chatListPageLoading || generation != chatListGeneration) return;
            chatListPageLoading = true;
            chatListPaginating = pagination;
            chatListRefreshing = !pagination;
            chatListError = "";
        }
        Log.d(TESTING_TAG, "chat_list source=routes phase=request page="
                + (pagination ? "pagination" : "initial")
                + " pageSize=" + CHAT_LIST_PAGE_SIZE
                + " hasCursor=" + (cursor != null && !cursor.isEmpty())
                + " generation=" + generation);
        publishChatListState();
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
                    failChatListPage(generation,
                            "Invalid chat list response.");
                    return;
                }
                JsonObject response = (JsonObject) object;
                if (isDebugBuild()) {
                    Log.d(PERF_TAG, "pageNetwork="
                            + (SystemClock.elapsedRealtime() - requestStarted)
                            + "ms pagination=" + pagination);
                }
                if ((cursor == null || cursor.isEmpty())
                        && response.has("total_unread")
                        && !response.get("total_unread").isJsonNull()) {
                    notifyTotalUnread(Math.max(0,
                            (int) JsonParserUtil.getLong(response, "total_unread")));
                }
                JsonArray userProfiles = response.getAsJsonArray("userProfiles");
                if (userProfiles == null) {
                    failChatListPage(generation,
                            "Chat list response is incomplete.");
                    return;
                }
                String returnedCursor = JsonParserUtil.getString(response, "nextCursor");
                boolean hasMore = JsonParserUtil.getBoolean(response, "hasMore")
                        && !returnedCursor.isEmpty();
                List<ChatEntity> chats = new ArrayList<>();
                List<MessageEntity> cachedMessages = parseMessageCache(response);
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
                Log.d(TESTING_TAG, "chat_list source=routes phase=response page="
                        + (pagination ? "pagination" : "initial")
                        + " chats=" + chats.size()
                        + " cachedMessages=" + cachedMessages.size()
                        + " hasMore=" + hasMore
                        + " hasNextCursor=" + !returnedCursor.isEmpty());
                ioExecutor.execute(() -> {
                    long databaseStarted = SystemClock.elapsedRealtime();
                    preserveLocalAttachmentUris(cachedMessages);
                    if (!cachedMessages.isEmpty()) messageDao.upsertAll(cachedMessages);
                    acknowledgeIncomingDeliveries(cachedMessages);
                    chatDao.mergeServerPage(
                            chats, chatsWithServerUnreadCount, activeChatId);
                    Log.d(TESTING_TAG, "chat_list source=routes phase=room_write_complete page="
                            + (pagination ? "pagination" : "initial")
                            + " chats=" + chats.size()
                            + " cachedMessages=" + cachedMessages.size());
                    if (isDebugBuild()) {
                        Log.d(PERF_TAG, "pageRoomWrite="
                                + (SystemClock.elapsedRealtime() - databaseStarted)
                                + "ms chats=" + chats.size()
                                + " cachedMessages=" + cachedMessages.size()
                                + " pagination=" + pagination);
                    }
                    prefetchChatProfilePhotos(chats);
                    recordServerMerge(generation, chats.size());
                    markChatListFirstPageLoaded(generation, pagination);
                    finishChatListPage(generation, returnedCursor, hasMore);
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TESTING_TAG, "chat_list source=routes phase=error page="
                        + (pagination ? "pagination" : "initial")
                        + " generation=" + generation + " error=" + error);
                failChatListPage(generation, error);
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
        chatListPaginating = false;
        chatListRefreshing = false;
        chatListError = "";
        publishChatListState();
    }

    private synchronized void markChatListFirstPageLoaded(
            int generation, boolean pagination) {
        if (generation == chatListGeneration && !pagination) {
            chatListFirstPageLoaded = true;
        }
    }

    private synchronized void recordServerMerge(int generation, int mergedChatCount) {
        if (generation == chatListGeneration && mergedChatCount > 0) {
            chatCacheLoaded = true;
            cachedChatCount = Math.max(cachedChatCount, mergedChatCount);
        }
    }

    private synchronized void failChatListPage(int generation, String error) {
        if (generation == chatListGeneration) {
            chatListPageLoading = false;
            chatListPaginating = false;
            chatListRefreshing = false;
            chatListError = error == null || error.trim().isEmpty()
                    ? "Unable to refresh chats." : error.trim();
            publishChatListState();
        }
    }

    private void publishChatListState() {
        ChatListState state;
        synchronized (this) {
            state = ChatListState.create(
                    chatCacheLoaded,
                    cachedChatCount,
                    chatListRefreshing,
                    chatListPaginating,
                    chatListError);
        }
        chatListState.postValue(state);
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
        List<ChatEntity> pending = new ArrayList<>();
        for (ChatEntity chat : chats) {
            if (chat == null || chat.chatId == null || chat.chatId.isEmpty()
                    || chat.otherUserId == null || chat.otherUserId.isEmpty()
                    || chat.profilePhotoUrl == null || chat.profilePhotoUrl.trim().isEmpty()
                    || !profilePhotoDownloads.add(chat.chatId)) {
                continue;
            }
            pending.add(chat);
        }
        if (pending.isEmpty()) return;
        List<ChatEntity> completed = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(pending.size());
        long batchStarted = SystemClock.elapsedRealtime();
        for (ChatEntity chat : pending) {
            profilePhotoExecutor.execute(() -> {
                try {
                    String localPath = ChatProfilePhotoStore.downloadAndStore(
                            appContext,
                            chat.otherUserId,
                            chat.profilePhotoUrl
                    );
                    if (localPath != null) {
                        chat.localProfilePhotoPath = localPath;
                        completed.add(chat);
                    }
                } finally {
                    profilePhotoDownloads.remove(chat.chatId);
                    if (remaining.decrementAndGet() == 0 && !completed.isEmpty()) {
                        ioExecutor.execute(() -> {
                            long roomStarted = SystemClock.elapsedRealtime();
                            chatDao.updateLocalProfilePhotoPaths(
                                    new ArrayList<>(completed), System.currentTimeMillis());
                            if (isDebugBuild()) {
                                Log.d(PERF_TAG, "photoBatch total="
                                        + (SystemClock.elapsedRealtime() - batchStarted)
                                        + "ms room=" + (SystemClock.elapsedRealtime() - roomStarted)
                                        + "ms requested=" + pending.size()
                                        + " updated=" + completed.size());
                            }
                        });
                    }
                }
            });
        }
    }

    public void hydrateChat(String chatId, String phoneNumber) {
        hydrateChatPage(chatId, phoneNumber, null, null);
    }

    public void hydrateChatPage(String chatId, String phoneNumber, String cursor,
                                MessagePageCallback callback) {
        if (chatId == null || chatId.trim().isEmpty()) {
            if (callback != null) callback.onError("Chat id missing.");
            return;
        }
        Log.d(TESTING_TAG, "message_list source=routes phase=request chatId=" + chatId
                + " page=" + (cursor == null || cursor.isEmpty() ? "initial" : "pagination")
                + " pageSize=" + MESSAGE_PAGE_SIZE
                + " hasCursor=" + (cursor != null && !cursor.isEmpty()));
        appFunctionManager.getChat(
                chatId, phoneNumber, MESSAGE_PAGE_SIZE, cursor, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                if (!(object instanceof JsonObject)) {
                    if (callback != null) callback.onError("Invalid chat response.");
                    return;
                }
                JsonObject response = (JsonObject) object;
                List<MessageEntity> messages = new ArrayList<>();
                JsonElement pageElement = response.get("messages");
                if (pageElement != null && pageElement.isJsonArray()) {
                    for (JsonElement element : pageElement.getAsJsonArray()) {
                        if (element == null || !element.isJsonObject()) continue;
                        MessageEntity entity = toMessageEntity(element.getAsJsonObject());
                        if (entity != null) messages.add(entity);
                    }
                } else {
                    // Compatibility with servers that still return the complete chat document.
                    JsonElement chatElement = response.get("chat");
                    if (chatElement != null && chatElement.isJsonObject()) {
                        messages = parseChatDocument(chatElement.getAsJsonObject(), chatId);
                    }
                }
                final List<MessageEntity> page = messages;
                final String nextCursor = response.has("nextCursor")
                        && !response.get("nextCursor").isJsonNull()
                        ? response.get("nextCursor").getAsString() : null;
                final boolean hasMore = response.has("hasMore")
                        && !response.get("hasMore").isJsonNull()
                        && response.get("hasMore").getAsBoolean();
                Log.d(TESTING_TAG, "message_list source=routes phase=response chatId=" + chatId
                        + " page=" + (cursor == null || cursor.isEmpty()
                                ? "initial" : "pagination")
                        + " messages=" + page.size()
                        + " hasMore=" + hasMore
                        + " hasNextCursor=" + (nextCursor != null && !nextCursor.isEmpty()));
                ioExecutor.execute(() -> {
                    preserveLocalAttachmentUris(page);
                    if (!page.isEmpty()) messageDao.upsertAll(page);
                    Log.d(TESTING_TAG, "message_list source=routes phase=room_write_complete chatId="
                            + chatId + " messages=" + page.size());
                    if (callback != null) mainHandler.post(
                            () -> callback.onLoaded(nextCursor, hasMore, page.size()));
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TESTING_TAG, "message_list source=routes phase=error chatId=" + chatId
                        + " page=" + (cursor == null || cursor.isEmpty()
                                ? "initial" : "pagination")
                        + " error=" + error);
                notifySocketError(error);
                if (callback != null) callback.onError(error);
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
        retryPersistedPendingMessages();
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

    private void retryPersistedPendingMessages() {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (senderId.isEmpty()) return;
        ioExecutor.execute(() -> {
            List<MessageEntity> pending = messageDao.findPendingOutgoing(senderId);
            for (MessageEntity message : pending) {
                String clientId = message.clientMessageId == null
                        ? message.messageId : message.clientMessageId;
                if (clientId == null || clientId.isEmpty()
                        || socketClient.isAwaitingMessageAck(clientId)) {
                    continue;
                }
                if (("image".equals(message.messageType)
                        || "video".equals(message.messageType)
                        || "file".equals(message.messageType))
                        && message.attachmentId == null) {
                    // WorkManager owns uploads and retries them when connectivity returns.
                    continue;
                }
                JsonObject attachment = null;
                if (message.attachmentId != null && !message.attachmentId.isEmpty()) {
                    attachment = new JsonObject();
                    attachment.addProperty("id", message.attachmentId);
                }
                JsonObject location = null;
                if (message.latitude != null && message.longitude != null) {
                    location = new JsonObject();
                    location.addProperty("latitude", message.latitude);
                    location.addProperty("longitude", message.longitude);
                    if (message.locationAccuracy != null) {
                        location.addProperty("accuracy", message.locationAccuracy);
                    }
                }
                Log.d(TESTING_TAG, "message_retry phase=reconnect clientMessageId=" + clientId);
                sendExistingMessage(clientId, message.chatId, message.senderId,
                        message.receiverId, message.text, message.repliedMessageId,
                        message.messageType, attachment, location);
            }
        });
    }

    @Override
    public void onEvent(JsonObject event) {
        String type = JsonParserUtil.getString(event, "type");
        int totalUnreadBeforeEvent = latestTotalUnread;
        int serverTotalUnread = -1;
        if (event.has("total_unread") && !event.get("total_unread").isJsonNull()) {
            serverTotalUnread = Math.max(0,
                    (int) JsonParserUtil.getLong(event, "total_unread"));
            notifyTotalUnread(serverTotalUnread);
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
            handleNewMessage(event, totalUnreadBeforeEvent, serverTotalUnread);
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
        } else if ("delete_messages_ack".equals(type)) {
            handleMessagesDeleted(event);
        } else if ("delete_messages_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("delete_opponent_messages_ack".equals(type)) {
            handleOpponentMessagesDeleted(event);
        } else if ("delete_opponent_messages_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("pin_messages_ack".equals(type) || "messages_pinned".equals(type)) {
            handleMessagesPinned(event);
        } else if ("pin_messages_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("unpin_messages_ack".equals(type) || "messages_unpinned".equals(type)) {
            handleMessagesPinned(event);
        } else if ("unpin_messages_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("forward_messages_ack".equals(type)) {
            handleForwardedMessages(event);
        } else if ("forward_messages_failed".equals(type)) {
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
        String chatId = JsonParserUtil.getString(event, "chatId");
        long sentTime = JsonParserUtil.getLong(event, "sentTime");
        ioExecutor.execute(() -> {
            messageDao.applyAck(clientMessageId, messageId, MessageStatus.SENT, sentTime);
            if (!chatId.isEmpty()) chatDao.applyLastMessageAck(chatId, clientMessageId,
                    messageId, sentTime, MessageStatus.SENT, System.currentTimeMillis());
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

    private void handleNewMessage(
            JsonObject event, int totalUnreadBeforeEvent, int serverTotalUnread) {
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
            ChatEntity chatBeforeMessage = chatDao.findByChatId(message.chatId);
            boolean chatWasUnread = chatBeforeMessage != null
                    && chatBeforeMessage.unreadCount > 0
                    && !locallyReadChats.contains(message.chatId);
            preserveLocalAttachmentUri(message);
            messageDao.upsert(message);
            Log.d(TESTING_TAG, "message_cache source=socket phase=room_upsert chatId="
                    + message.chatId + " messageId=" + message.messageId
                    + " sentTime=" + message.sentTime);
            updateChatSummary(message);
            boolean incoming = !normalizeAccountId(message.senderId).equals(currentUserId);
            if (incoming && isNewMessage) {
                if (message.chatId.equals(activeChatId)) {
                    chatDao.clearUnreadCount(message.chatId);
                    locallyReadChats.add(message.chatId);
                } else {
                    chatDao.incrementUnreadCount(message.chatId);
                    locallyReadChats.remove(message.chatId);
                    if (!chatWasUnread) {
                        int updatedTotal;
                        if (serverTotalUnread >= 0 && totalUnreadBeforeEvent >= 0) {
                            int localExpectedTotal = totalUnreadBeforeEvent + 1;
                            updatedTotal = Math.max(serverTotalUnread, localExpectedTotal);
                        } else if (serverTotalUnread < 0 && latestTotalUnread >= 0) {
                            updatedTotal = latestTotalUnread + 1;
                        } else {
                            updatedTotal = serverTotalUnread >= 0
                                    ? serverTotalUnread
                                    : chatDao.countUnreadChats();
                        }
                        notifyTotalUnread(updatedTotal);
                    }
                }
            }
            acknowledgeIncomingDeliveries(Collections.singletonList(message));
        });
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
        pendingDeliveredAcks.removeAll(messageIds);
        long deliveredTime = JsonParserUtil.getLong(event, "deliveredTime");
        String eventChatId = JsonParserUtil.getString(event, "chatId");
        ioExecutor.execute(() -> {
            messageDao.markDelivered(messageIds, MessageStatus.DELIVERED, deliveredTime);
            for (String messageId : messageIds) {
                MessageEntity message = messageDao.findByMessageId(messageId);
                String chatId = message == null ? eventChatId : message.chatId;
                long sentTime = message == null ? 0L : message.sentTime;
                Long readTime = message == null ? null : message.readTime;
                if (!chatId.isEmpty()) chatDao.updateLastMessageReceipt(chatId, messageId,
                        sentTime, deliveredTime, readTime,
                        readTime == null ? MessageStatus.DELIVERED : MessageStatus.SEEN);
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
        String eventChatId = JsonParserUtil.getString(event, "chatId");
        ioExecutor.execute(() -> {
            messageDao.markSeen(messageIds, MessageStatus.SEEN, readTime);
            for (String messageId : messageIds) {
                MessageEntity message = messageDao.findByMessageId(messageId);
                String chatId = message == null ? eventChatId : message.chatId;
                long sentTime = message == null ? 0L : message.sentTime;
                Long deliveredTime = message == null ? null : message.deliveredTime;
                if (!chatId.isEmpty()) chatDao.updateLastMessageReceipt(chatId, messageId,
                        sentTime, deliveredTime, readTime, MessageStatus.SEEN);
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
        if (JsonParserUtil.getBoolean(event, "hidden")) hideLocalMessage(messageId);
        else markLocalMessageDeleted(messageId);
    }

    private void handleOpponentMessageDeleted(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        hideLocalMessage(messageId);
    }

    private void handleMessagesDeleted(JsonObject event) {
        List<String> messageIds = messageIds(event);
        Set<String> hidden = new HashSet<>(stringList(event, "hiddenMessageIds"));
        for (String messageId : messageIds) {
            if (hidden.contains(messageId)) hideLocalMessage(messageId);
            else markLocalMessageDeleted(messageId);
        }
    }

    private void handleOpponentMessagesDeleted(JsonObject event) {
        List<String> messageIds = messageIds(event);
        if (!messageIds.isEmpty()) ioExecutor.execute(() -> messageDao.deleteByMessageIds(messageIds));
    }

    private void handleMessagesPinned(JsonObject event) {
        List<String> messageIds = messageIds(event);
        if (messageIds.isEmpty()) return;
        boolean pinned = !event.has("pinned") || event.get("pinned").getAsBoolean();
        Long pinnedAt = getNullableLong(event, "pinned_at");
        ioExecutor.execute(() -> messageDao.updatePinned(messageIds, pinned, pinnedAt));
    }

    private void handleForwardedMessages(JsonObject event) {
        JsonArray messages = event.getAsJsonArray("messages");
        if (messages == null) return;
        List<MessageEntity> forwarded = new ArrayList<>();
        for (JsonElement element : messages) {
            if (element == null || !element.isJsonObject()) continue;
            MessageEntity message = toMessageEntity(element.getAsJsonObject());
            if (message != null) forwarded.add(message);
        }
        if (forwarded.isEmpty()) return;
        ioExecutor.execute(() -> {
            messageDao.upsertAll(forwarded);
            for (MessageEntity message : forwarded) updateChatSummary(message);
        });
    }

    private static List<String> messageIds(JsonObject event) {
        return stringList(event, "messageIds");
    }

    private static List<String> stringList(JsonObject event, String key) {
        List<String> ids = new ArrayList<>();
        JsonArray values = event.getAsJsonArray(key);
        if (values == null) return ids;
        for (JsonElement value : values) {
            if (value != null && !value.isJsonNull()) ids.add(value.getAsString());
        }
        return ids;
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
        boolean invisible = "gone".equals(JsonParserUtil.getString(message, "visible"))
                || isInvisibleToCurrentUser(message);
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
        entity.pinned = JsonParserUtil.getBoolean(message, "pinned");
        entity.pinnedAt = getNullableLong(message, "pinned_at");
        entity.forwardedFrom = JsonParserUtil.getString(message, "forwarded_from");
        entity.deletedText = message.has("deletedText") && !message.get("deletedText").isJsonNull()
                ? JsonParserUtil.getString(message, "deletedText") : null;
        entity.invisible = invisible;
        return entity;
    }

    private boolean isInvisibleToCurrentUser(JsonObject message) {
        JsonArray invisible = message.has("invisible") && message.get("invisible").isJsonArray()
                ? message.getAsJsonArray("invisible") : null;
        if (invisible == null || invisible.size() == 0) return false;
        String viewer = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        for (JsonElement value : invisible) {
            if (value != null && !value.isJsonNull()
                    && viewer.equals(normalizeAccountId(value.getAsString()))) return true;
        }
        return false;
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

    private List<MessageEntity> parseMessageCache(JsonObject response) {
        List<MessageEntity> messages = new ArrayList<>();
        JsonElement cacheElement = response.get("messageCache");
        if (cacheElement == null || !cacheElement.isJsonObject()) return messages;
        for (java.util.Map.Entry<String, JsonElement> chatEntry
                : cacheElement.getAsJsonObject().entrySet()) {
            if (chatEntry.getValue() == null || !chatEntry.getValue().isJsonArray()) continue;
            int chatMessageCount = 0;
            for (JsonElement element : chatEntry.getValue().getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject message = element.getAsJsonObject();
                if (!message.has("chatId") || message.get("chatId").isJsonNull()
                        || message.get("chatId").getAsString().isEmpty()) {
                    message.addProperty("chatId", chatEntry.getKey());
                }
                MessageEntity entity = toMessageEntity(message);
                if (entity != null) {
                    messages.add(entity);
                    chatMessageCount++;
                }
            }
            Log.d(TESTING_TAG, "message_cache source=list_routes phase=parsed chatId="
                    + chatEntry.getKey() + " messages=" + chatMessageCount);
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
        String lastMessageId = "";
        long lastMessageTime = 0;
        String lastMessageSenderId = "";
        Long lastMessageDeliveredTime = null;
        Long lastMessageReadTime = null;
        String lastMessageStatus = "";
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
            lastMessageId = JsonParserUtil.getString(lastMessageObject, "id");
            if (lastMessageId.isEmpty()) {
                lastMessageId = JsonParserUtil.getString(lastMessageObject, "messageId");
            }
            lastMessageTime = JsonParserUtil.getLong(lastMessageObject, "sentTime");
            lastMessageSenderId = normalizeAccountId(
                    JsonParserUtil.getString(lastMessageObject, "senderId"));
            lastMessageDeliveredTime = getNullableLong(lastMessageObject, "deliveredTime");
            lastMessageReadTime = getNullableLong(lastMessageObject, "readTime");
            lastMessageStatus = JsonParserUtil.getString(lastMessageObject, "status");
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
                lastMessageId,
                lastMessageTime,
                lastMessageSenderId,
                lastMessageDeliveredTime,
                lastMessageReadTime,
                lastMessageStatus,
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
        int updated = chatDao.updateLastMessage(message.chatId, message.messageId, preview, sentTime,
                normalizeAccountId(message.senderId), message.deliveredTime, message.readTime,
                message.status,
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
                message.messageId,
                sentTime,
                senderId,
                message.deliveredTime,
                message.readTime,
                message.status,
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

    private boolean isDebugBuild() {
        return (appContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

}
