package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.w3n.pinggo.data.download.BackgroundFileDownloader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatProfilePhotoStore {
  private static final String PREFS_NAME = "ChatProfilePhotos";
  private static final String URL_KEY_PREFIX = "url_";
  private static final String FILE_PREFIX = "chat_profile_";
  private static final String FILE_EXTENSION = ".jpg";
  private static final int JPEG_QUALITY = 92;
  private static final long MAX_PROFILE_PHOTO_BYTES = 25L * 1024L * 1024L;
  private static final String NO_LOCAL_PATH = "\u0000";
  private static final ConcurrentHashMap<String, String> LOCAL_PATH_CACHE =
      new ConcurrentHashMap<>();

  private ChatProfilePhotoStore() {}

  public static String getLocalPath(Context context, String phoneNumber) {
    if (context == null || phoneNumber == null) {
      return null;
    }

    String normalized = normalizePhoneNumber(phoneNumber);
    String cached = LOCAL_PATH_CACHE.get(normalized);
    if (cached != null) return NO_LOCAL_PATH.equals(cached) ? null : cached;
    String localPath = getPrefs(context).getString(normalized, null);
    if (localPath == null || !new File(localPath).exists()) {
      LOCAL_PATH_CACHE.put(normalized, NO_LOCAL_PATH);
      return null;
    }
    LOCAL_PATH_CACHE.put(normalized, localPath);
    return localPath;
  }

  public static void remove(Context context, String phoneNumber) {
    if (context == null || phoneNumber == null) return;
    String normalized = normalizePhoneNumber(phoneNumber);
    LOCAL_PATH_CACHE.remove(normalized);
    String path = getPrefs(context).getString(normalized, null);
    if (path != null) {
      File file = new File(path);
      if (file.exists()) file.delete();
    }
    getPrefs(context).edit().remove(normalized).remove(URL_KEY_PREFIX + normalized).apply();
  }

  public static String downloadAndStore(
      Context context, String phoneNumber, String profilePhotoUrl) {
    if (context == null || isEmpty(phoneNumber) || isEmpty(profilePhotoUrl)) {
      return null;
    }
    String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    String normalizedUrl = normalizeProfilePhotoUrl(profilePhotoUrl);
    String cachedPath = getLocalPath(context, normalizedPhoneNumber);
    String cachedUrl = getPrefs(context).getString(
        URL_KEY_PREFIX + normalizedPhoneNumber, null);
    if (cachedPath != null && normalizedUrl.equals(cachedUrl)) return cachedPath;

    File temporaryFile = new File(
        context.getCacheDir(),
        "profile_" + normalizedPhoneNumber + "_" + UUID.randomUUID() + ".download");
    try {
      File downloadedFile = BackgroundFileDownloader.downloadToFile(
          normalizedUrl, temporaryFile, MAX_PROFILE_PHOTO_BYTES);
      Bitmap bitmap = BitmapFactory.decodeFile(downloadedFile.getAbsolutePath());
      if (bitmap == null) return null;
      String localPath = save(context, normalizedPhoneNumber, bitmap);
      bitmap.recycle();
      if (localPath != null) {
        LOCAL_PATH_CACHE.put(normalizedPhoneNumber, localPath);
        getPrefs(context).edit()
            .putString(normalizedPhoneNumber, localPath)
            .putString(URL_KEY_PREFIX + normalizedPhoneNumber, normalizedUrl)
            .apply();
      }
      return localPath;
    } catch (IOException ignored) {
      return null;
    } finally {
      if (temporaryFile.exists()) temporaryFile.delete();
    }
  }

  private static String save(Context context, String phoneNumber, Bitmap bitmap) {
    File file = new File(context.getFilesDir(), FILE_PREFIX + phoneNumber + FILE_EXTENSION);
    try (FileOutputStream outputStream = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
      return file.getAbsolutePath();
    } catch (IOException e) {
      return null;
    }
  }

  private static SharedPreferences getPrefs(Context context) {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  private static String normalizePhoneNumber(String phoneNumber) {
    if (phoneNumber == null) {
      return "";
    }
    if (phoneNumber.startsWith("<plus>")) {
      return phoneNumber.substring("<plus>".length());
    }
    if (phoneNumber.startsWith("+")) {
      return phoneNumber.substring(1);
    }
    return phoneNumber;
  }

  private static String normalizeProfilePhotoUrl(String profilePhotoUrl) {
    if (profilePhotoUrl != null && profilePhotoUrl.startsWith("http://function.cloudsw3.com/")) {
      return profilePhotoUrl.replace("http://", "https://");
    }
    return profilePhotoUrl;
  }

  private static boolean isEmpty(String value) {
    return value == null || value.trim().isEmpty();
  }
}
