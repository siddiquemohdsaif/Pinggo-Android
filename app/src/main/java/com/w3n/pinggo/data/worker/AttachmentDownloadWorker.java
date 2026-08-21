package com.w3n.pinggo.data.worker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;

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
            return Result.success();
        } catch (Exception error) {
            if (destination != null) context.getContentResolver().delete(destination, null, null);
            if (getRunAttemptCount() < 3) {
                transfers.failed(id, "retrying", error.getMessage(), System.currentTimeMillis());
                return Result.retry();
            }
            transfers.failed(id, "failed", error.getMessage(), System.currentTimeMillis());
            return Result.failure();
        }
    }

    private Uri createDestination(TransferEntity transfer) throws IOException {
        String folder = "image".equals(transfer.kind) ? "Image"
                : "video".equals(transfer.kind) ? "Video" : "File";
        String safeName = (transfer.attachmentId + "-" + transfer.fileName)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, transfer.mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PingGo/" + folder);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Unable to create download destination.");
            return uri;
        }
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PingGo" + File.separator + folder);
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to create download folder.");
        return Uri.fromFile(new File(directory, safeName));
    }

    private Uri findExisting(TransferEntity transfer) {
        String folder = "image".equals(transfer.kind) ? "Image"
                : "video".equals(transfer.kind) ? "Video" : "File";
        String safeName = (transfer.attachmentId + "-" + transfer.fileName)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relative = Environment.DIRECTORY_DOWNLOADS + "/PingGo/" + folder + "/";
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE},
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                            + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[]{safeName, relative}, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    long size = cursor.getLong(1);
                    if (transfer.totalSize <= 0 || size == transfer.totalSize) {
                        Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                String.valueOf(cursor.getLong(0)));
                        if (transfer.fileHash == null || transfer.fileHash.isEmpty()
                                || transfer.fileHash.equals(hashUri(uri))) return uri;
                    }
                }
            } catch (RuntimeException ignored) {}
            return null;
        }
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PingGo" + File.separator + folder + File.separator + safeName);
        Uri uri = Uri.fromFile(file);
        return file.isFile() && (transfer.totalSize <= 0 || file.length() == transfer.totalSize)
                && (transfer.fileHash == null || transfer.fileHash.isEmpty()
                    || transfer.fileHash.equals(hashUri(uri))) ? uri : null;
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
