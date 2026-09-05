package com.w3n.pinggo.data.worker;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.w3n.pinggo.data.local.MessageDao;
import com.w3n.pinggo.data.local.PingGoDatabase;
import com.w3n.pinggo.data.local.TransferDao;
import com.w3n.pinggo.data.local.TransferEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AttachmentDownloadWorker extends Worker {
    public static final String KEY_TRANSFER_ID = "transferId";
    private static final long MAX_SIZE = 25L * 1024L * 1024L;
    private final Context context;
    private final TransferDao transfers;
    private final MessageDao messages;

    public AttachmentDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context.getApplicationContext();
        PingGoDatabase db = PingGoDatabase.getInstance(context);
        transfers = db.transferDao();
        messages = db.messageDao();
    }

    @NonNull @Override public Result doWork() {
        String id = getInputData().getString(KEY_TRANSFER_ID);
        TransferEntity transfer = id == null ? null : transfers.find(id);
        if (transfer == null || transfer.remoteUrl == null) return Result.failure();
        Log.d("PingGoAttachmentTransfer", "stage=worker_started transferId=" + id
                + " attachmentId=" + transfer.attachmentId + " totalSize=" + transfer.totalSize);
        Uri destination = null;
        try {
            Uri existing = findExisting(transfer);
            if (existing != null) {
                transfers.completed(id, transfer.attachmentId, transfer.remoteUrl,
                        existing.toString(), System.currentTimeMillis());
                messages.updateAttachmentLocalUri(transfer.attachmentId, existing.toString());
                return Result.success();
            }
            destination = createDestination(transfer);
            try (Response response = new OkHttpClient().newCall(
                    new Request.Builder().url(transfer.remoteUrl).build()).execute()) {
                if (!response.isSuccessful()) throw new IOException("Download failed (HTTP " + response.code() + ").");
                ResponseBody body = response.body();
                if (body == null || body.contentLength() > MAX_SIZE) throw new IOException("Invalid attachment response.");
                try (java.io.InputStream input = body.byteStream();
                     OutputStream output = openOutput(destination)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_SIZE) throw new IOException("Attachment exceeds 25 MB.");
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        transfers.progress(id, total, "downloading", System.currentTimeMillis());
                        Log.d("PingGoAttachmentTransfer", "stage=worker_progress transferId=" + id
                                + " attachmentId=" + transfer.attachmentId
                                + " transferredBytes=" + total + " totalSize=" + transfer.totalSize);
                        setProgressAsync(new Data.Builder().putLong("bytes", total)
                                .putLong("total", transfer.totalSize).build());
                    }
                    if (transfer.totalSize > 0 && total != transfer.totalSize)
                        throw new IOException("Downloaded file size does not match.");
                    if (transfer.fileHash != null && !transfer.fileHash.isEmpty()
                            && !transfer.fileHash.equals(hex(digest.digest())))
                        throw new IOException("Downloaded file checksum does not match.");
                }
            }
            publish(destination);
            transfers.completed(id, transfer.attachmentId, transfer.remoteUrl,
                    destination.toString(), System.currentTimeMillis());
            messages.updateAttachmentLocalUri(transfer.attachmentId, destination.toString());
            Log.d("PingGoAttachmentTransfer", "stage=worker_completed transferId=" + id
                    + " attachmentId=" + transfer.attachmentId);
            return Result.success();
        } catch (Exception error) {
            Log.e("PingGoAttachmentTransfer", "stage=worker_error transferId=" + id
                    + " attachmentId=" + transfer.attachmentId, error);
            if (destination != null) deleteDestination(destination);
            if (getRunAttemptCount() < 3) {
                transfers.failed(id, "retrying", error.getMessage(), System.currentTimeMillis());
                return Result.retry();
            }
            transfers.failed(id, "failed", error.getMessage(), System.currentTimeMillis());
            return Result.failure();
        }
    }

    private Uri createDestination(TransferEntity transfer) throws IOException {
        String folder = destinationFolder(transfer);
        String safeName = (transfer.attachmentId + "-" + transfer.fileName)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        File directory = new File(Environment.getExternalStorageDirectory(),
                "PingGo" + File.separator + folder);
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to create download folder.");
        return Uri.fromFile(new File(directory, safeName));
    }

    private Uri findExisting(TransferEntity transfer) {
        String folder = destinationFolder(transfer);
        String safeName = (transfer.attachmentId + "-" + transfer.fileName)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        File file = new File(Environment.getExternalStorageDirectory(),
                "PingGo" + File.separator + folder + File.separator + safeName);
        Uri uri = Uri.fromFile(file);
        return file.isFile() && (transfer.totalSize <= 0 || file.length() == transfer.totalSize)
                && (transfer.fileHash == null || transfer.fileHash.isEmpty()
                    || transfer.fileHash.equals(hashUri(uri))) ? uri : null;
    }

    private static String destinationFolder(TransferEntity transfer) {
        String mime = transfer.mimeType == null
                ? "" : transfer.mimeType.trim().toLowerCase(java.util.Locale.US);
        String name = transfer.fileName == null
                ? "" : transfer.fileName.trim().toLowerCase(java.util.Locale.US);
        if (mime.startsWith("video/") || hasExtension(name,
                ".mp4", ".m4v", ".mov", ".webm", ".mkv", ".avi", ".3gp")) {
            return "Videos";
        }
        if (mime.startsWith("image/") || hasExtension(name,
                ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif")) {
            return "Images";
        }
        if (mime.startsWith("audio/") || hasExtension(name,
                ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac")) {
            return "Audio";
        }
        if ("video".equals(transfer.kind)) return "Videos";
        if ("image".equals(transfer.kind)) return "Images";
        if ("audio".equals(transfer.kind)) return "Audio";
        return "Files";
    }

    private static boolean hasExtension(String name, String... extensions) {
        for (String extension : extensions) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    private OutputStream openOutput(Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) return new FileOutputStream(new File(uri.getPath()));
        OutputStream output = context.getContentResolver().openOutputStream(uri, "w");
        if (output == null) throw new IOException("Unable to open download destination.");
        return output;
    }

    private void publish(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "content".equals(uri.getScheme())) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        }
    }

    private void deleteDestination(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            if (file.isFile()) file.delete();
        } else {
            context.getContentResolver().delete(uri, null, null);
        }
    }

    private String hashUri(Uri uri) {
        try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return hex(digest.digest());
        } catch (Exception error) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }
}
