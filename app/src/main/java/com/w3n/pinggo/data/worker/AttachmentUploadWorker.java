package com.w3n.pinggo.data.worker;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.local.MessageDao;
import com.w3n.pinggo.data.local.MessageStatus;
import com.w3n.pinggo.data.local.PingGoDatabase;
import com.w3n.pinggo.data.local.TransferDao;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Response;

public class AttachmentUploadWorker extends Worker {
    public static final String KEY_TRANSFER_ID = "transferId";
    private static final long CHUNK_SIZE = 3L * 1024L * 1024L;
    private static final long MAX_SIZE = 25L * 1024L * 1024L;
    private final Context context;
    private final TransferDao transfers;
    private final MessageDao messages;

    public AttachmentUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
        PingGoDatabase db = PingGoDatabase.getInstance(context);
        transfers = db.transferDao();
        messages = db.messageDao();
    }

    @NonNull @Override public Result doWork() {
        String transferId = getInputData().getString(KEY_TRANSFER_ID);
        TransferEntity transfer = transferId == null ? null : transfers.find(transferId);
        if (transfer == null) return Result.failure();
        try {
            File staged = stage(transfer);
            String token = LoginStateManager.getInstance().getUID(context) + "_"
                    + LoginStateManager.getInstance().getENC(context);
            AppRestAPI api = new APIAuth(token).getRetrofit().create(AppRestAPI.class);
            JsonObject attachment = staged.length() > CHUNK_SIZE
                    ? uploadChunks(api, transfer, staged)
                    : uploadSingle(api, transfer, staged);
            if (attachment == null) throw new PermanentFailure("Server did not return the attachment.");
            String attachmentId = JsonParserUtil.getString(attachment, "id");
            String url = JsonParserUtil.getString(attachment, "url");
            transfers.completed(transfer.transferId, attachmentId, url, transfer.sourceUri, System.currentTimeMillis());
            messages.applyAttachmentUpload(transfer.clientMessageId, attachmentId,
                    JsonParserUtil.getString(attachment, "kind"),
                    JsonParserUtil.getString(attachment, "name"),
                    JsonParserUtil.getString(attachment, "mimeType"), url,
                    JsonParserUtil.getLong(attachment, "size"));
            ChatRepository.getInstance(context).sendCompletedBackgroundAttachment(transfer, attachment);
            if (staged.isFile()) staged.delete();
            return Result.success();
        } catch (PermanentFailure error) {
            fail(transfer, error.getMessage());
            cancelSession(transfer);
            return Result.failure(new Data.Builder().putString("error", error.getMessage()).build());
        } catch (Exception error) {
            if (getRunAttemptCount() < 3) {
                transfers.failed(transfer.transferId, "retrying", error.getMessage(), System.currentTimeMillis());
                return Result.retry();
            }
            fail(transfer, error.getMessage());
            return Result.failure();
        }
    }

    private File stage(TransferEntity transfer) throws Exception {
        File directory = new File(context.getCacheDir(), "pinggo_uploads");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to create upload cache.");
        File staged = transfer.stagedPath == null ? new File(directory, transfer.transferId + ".upload")
                : new File(transfer.stagedPath);
        if (staged.isFile() && transfer.fileHash != null && staged.length() == transfer.totalSize) return staged;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(transfer.sourceUri));
             FileOutputStream output = new FileOutputStream(staged, false)) {
            if (input == null) throw new PermanentFailure("File no longer available.");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SIZE) throw new PermanentFailure("Attachment must be 25 MB or smaller.");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total <= 0) throw new PermanentFailure("Attachment is empty.");
        transfer.totalSize = total;
        transfer.fileHash = hex(digest.digest());
        transfer.stagedPath = staged.getAbsolutePath();
        transfers.staged(transfer.transferId, transfer.uploadId, transfer.fileHash,
                transfer.stagedPath, total, "uploading", System.currentTimeMillis());
        return staged;
    }

    private JsonObject uploadSingle(AppRestAPI api, TransferEntity transfer, File file) throws Exception {
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", transfer.fileName,
                RequestBody.create(file, MediaType.get(transfer.mimeType)));
        Response<JsonObject> response = api.uploadChatAttachment(part,
                RequestBody.create(transfer.chatId, MediaType.get("text/plain")),
                RequestBody.create(transfer.kind, MediaType.get("text/plain"))).execute();
        return attachment(response);
    }

    private JsonObject uploadChunks(AppRestAPI api, TransferEntity transfer, File file) throws Exception {
        int totalChunks = (int) Math.ceil((double) file.length() / CHUNK_SIZE);
        if (transfer.uploadId == null || transfer.uploadId.isEmpty()) {
            JsonObject init = new JsonObject();
            init.addProperty("chatId", transfer.chatId);
            init.addProperty("kind", transfer.kind);
            init.addProperty("fileName", transfer.fileName);
            init.addProperty("mimeType", transfer.mimeType);
            init.addProperty("totalSize", file.length());
            init.addProperty("totalChunks", totalChunks);
            init.addProperty("fileHash", transfer.fileHash);
            Response<JsonObject> response = api.initChatAttachment(
                    RequestBody.create(init.toString(), MediaType.get("application/json"))).execute();
            JsonObject upload = successfulObject(response, "upload");
            transfer.uploadId = JsonParserUtil.getString(upload, "uploadId");
            transfers.staged(transfer.transferId, transfer.uploadId, transfer.fileHash,
                    transfer.stagedPath, file.length(), "uploading", System.currentTimeMillis());
        }
        UploadStatus uploadStatus;
        try {
            uploadStatus = uploadStatus(api, transfer.uploadId);
        } catch (SessionExpired error) {
            transfer.uploadId = null;
            transfers.staged(transfer.transferId, null, transfer.fileHash,
                    transfer.stagedPath, file.length(), "uploading", System.currentTimeMillis());
            return uploadChunks(api, transfer, file);
        }
        if (uploadStatus.completedAttachment != null) return uploadStatus.completedAttachment;
        Set<Integer> received = uploadStatus.receivedChunks;
        for (int index = 0; index < totalChunks; index++) {
            if (received.contains(index)) continue;
            long offset = index * CHUNK_SIZE;
            long length = Math.min(CHUNK_SIZE, file.length() - offset);
            String hash = hashRange(file, offset, length);
            MultipartBody.Part chunk = MultipartBody.Part.createFormData("chunk",
                    transfer.fileName + ".part" + index, rangeBody(file, transfer.mimeType, offset, length));
            Response<JsonObject> response = api.uploadChatAttachmentChunk(transfer.uploadId, index, chunk,
                    RequestBody.create(hash, MediaType.get("text/plain"))).execute();
            requireSuccess(response);
            long bytes = Math.min(file.length(), (index + 1L) * CHUNK_SIZE);
            transfers.progress(transfer.transferId, bytes, "uploading", System.currentTimeMillis());
            setProgressAsync(new Data.Builder().putLong("bytes", bytes).putLong("total", file.length()).build());
        }
        Response<JsonObject> complete = api.completeChatAttachment(transfer.uploadId,
                RequestBody.create("{}", MediaType.get("application/json"))).execute();
        return attachment(complete);
    }

    private UploadStatus uploadStatus(AppRestAPI api, String uploadId) throws Exception {
        Response<JsonObject> response = api.getChatAttachmentStatus(uploadId).execute();
        if (response.code() == 404) throw new SessionExpired();
        JsonObject upload = successfulObject(response, "upload");
        JsonArray values = upload.getAsJsonArray("receivedChunks");
        Set<Integer> result = new HashSet<>();
        if (values != null) for (JsonElement value : values) result.add(value.getAsInt());
        JsonObject completed = upload.has("completedAttachment")
                && upload.get("completedAttachment").isJsonObject()
                ? upload.getAsJsonObject("completedAttachment") : null;
        return new UploadStatus(result, completed);
    }

    private JsonObject attachment(Response<JsonObject> response) throws Exception {
        return successfulObject(response, "attachment");
    }

    private JsonObject successfulObject(Response<JsonObject> response, String field) throws Exception {
        requireSuccess(response);
        JsonObject body = response.body();
        JsonObject value = body == null ? null : body.getAsJsonObject(field);
        if (value == null) throw new IOException("Invalid server response.");
        return value;
    }

    private void requireSuccess(Response<?> response) throws Exception {
        if (response.isSuccessful()) return;
        String error = response.errorBody() == null ? "" : response.errorBody().string();
        if (response.code() >= 400 && response.code() < 500 && response.code() != 408 && response.code() != 429)
            throw new PermanentFailure(error.isEmpty() ? "Upload rejected (HTTP " + response.code() + ")." : error);
        throw new IOException("Upload failed (HTTP " + response.code() + ").");
    }

    private RequestBody rangeBody(File file, String mime, long offset, long length) {
        return new RequestBody() {
            @Override public MediaType contentType() { return MediaType.parse(mime); }
            @Override public long contentLength() { return length; }
            @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                    input.seek(offset);
                    byte[] buffer = new byte[64 * 1024];
                    long remaining = length;
                    while (remaining > 0) {
                        int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read < 0) throw new IOException("Unexpected end of staged attachment.");
                        sink.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
            }
        };
    }

    private String hashRange(File file, long offset, long length) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(offset);
            byte[] buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) throw new IOException("Unexpected end of staged attachment.");
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return hex(digest.digest());
    }

    private void cancelSession(TransferEntity transfer) {
        if (transfer.uploadId == null || transfer.uploadId.isEmpty()) return;
        try {
            String token = LoginStateManager.getInstance().getUID(context) + "_"
                    + LoginStateManager.getInstance().getENC(context);
            new APIAuth(token).getRetrofit().create(AppRestAPI.class)
                    .cancelChatAttachment(transfer.uploadId).execute();
        } catch (Exception ignored) {}
    }

    private void fail(TransferEntity transfer, String error) {
        String message = error == null ? "Attachment transfer failed." : error;
        transfers.failed(transfer.transferId, "failed", message, System.currentTimeMillis());
        messages.updateStatusByClientMessageId(transfer.clientMessageId, MessageStatus.FAILED);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }

    private static final class PermanentFailure extends Exception {
        PermanentFailure(String message) { super(message); }
    }
    private static final class SessionExpired extends Exception {}
    private static final class UploadStatus {
        final Set<Integer> receivedChunks;
        final JsonObject completedAttachment;
        UploadStatus(Set<Integer> receivedChunks, JsonObject completedAttachment) {
            this.receivedChunks = receivedChunks;
            this.completedAttachment = completedAttachment;
        }
    }
}
