package com.w3n.pinggo.data.download;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Streams images, video, audio, or other HTTP files to disk without loading them into memory. */
public final class BackgroundFileDownloader {
    public static final long DEFAULT_MAX_BYTES = 100L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private BackgroundFileDownloader() {}

    public interface Callback {
        void onSuccess(File file);
        void onError(Exception error);
    }

    /** Starts a background download and returns a Future that can be cancelled. */
    public static Future<?> download(
            String sourceUrl, File destination, long maxBytes, Callback callback) {
        return DOWNLOAD_EXECUTOR.submit(() -> {
            try {
                File file = downloadToFile(sourceUrl, destination, maxBytes);
                if (callback != null) MAIN_HANDLER.post(() -> callback.onSuccess(file));
            } catch (Exception error) {
                if (callback != null) MAIN_HANDLER.post(() -> callback.onError(error));
            }
        });
    }

    public static Future<?> download(String sourceUrl, File destination, Callback callback) {
        return download(sourceUrl, destination, DEFAULT_MAX_BYTES, callback);
    }

    /** Blocking worker API. Call only from a background executor or Worker. */
    public static File downloadToFile(
            String sourceUrl, File destination, long maxBytes) throws IOException {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            throw new IOException("Download URL is empty.");
        }
        if (destination == null) throw new IOException("Download destination is missing.");
        if (maxBytes <= 0) throw new IOException("Maximum download size must be positive.");

        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Could not create the download directory.");
        }
        File temporaryFile = new File(
                parent,
                destination.getName() + ".part-" + UUID.randomUUID()
        );
        HttpURLConnection connection = null;
        try {
            URLConnection openedConnection = new URL(sourceUrl).openConnection();
            if (!(openedConnection instanceof HttpURLConnection)) {
                throw new IOException("Only HTTP and HTTPS downloads are supported.");
            }
            connection = (HttpURLConnection) openedConnection;
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept-Encoding", "identity");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Download failed with HTTP " + responseCode + ".");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxBytes) {
                throw new IOException("Download exceeds the maximum allowed size.");
            }

            long downloadedBytes = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(temporaryFile))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download was cancelled.");
                    }
                    downloadedBytes += read;
                    if (downloadedBytes > maxBytes) {
                        throw new IOException("Download exceeds the maximum allowed size.");
                    }
                    output.write(buffer, 0, read);
                }
            }

            if (destination.exists() && !destination.delete()) {
                throw new IOException("Could not replace the existing file.");
            }
            if (!temporaryFile.renameTo(destination)) {
                throw new IOException("Could not finalize the downloaded file.");
            }
            return destination;
        } finally {
            if (connection != null) connection.disconnect();
            if (temporaryFile.exists()) temporaryFile.delete();
        }
    }
}
