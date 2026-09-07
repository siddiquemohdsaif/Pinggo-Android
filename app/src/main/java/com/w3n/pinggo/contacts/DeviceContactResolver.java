package com.w3n.pinggo.contacts;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves normalized account phone numbers to user-owned device contact names. */
public final class DeviceContactResolver {
    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private DeviceContactResolver() { }

    public static String normalize(String value) {
        String source = value == null ? "" : value.trim().replace("<plus>", "+");
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }
        return digits.toString();
    }

    public static String fallback(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        return normalized.isEmpty() ? "Unknown" : "+" + normalized;
    }

    /** Fast UI-safe lookup. Call warmUp after READ_CONTACTS is granted. */
    public static String cachedNameOrPhone(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        String name = cachedContactName(normalized);
        return name == null || name.trim().isEmpty() ? fallback(normalized) : name;
    }

    /** Direct lookup for background notification creation, with phone fallback. */
    public static String nameOrPhone(Context context, String phoneNumber) {
        String normalized = normalize(phoneNumber);
        String cached = cachedContactName(normalized);
        if (cached != null && !cached.trim().isEmpty()) return cached;
        if (!hasPermission(context) || normalized.isEmpty()) return fallback(normalized);
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode("+" + normalized));
        String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection,
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    NAMES.put(normalized, name.trim());
                    return name.trim();
                }
            }
        } catch (RuntimeException ignored) { }
        return fallback(normalized);
    }

    private static String cachedContactName(String normalized) {
        String direct = NAMES.get(normalized);
        if (direct != null) return direct;
        if (normalized.length() < 7) return null;
        for (Map.Entry<String, String> entry : NAMES.entrySet()) {
            String candidate = entry.getKey();
            if (candidate.length() >= 7 && (candidate.endsWith(normalized)
                    || normalized.endsWith(candidate))) {
                NAMES.put(normalized, entry.getValue());
                return entry.getValue();
            }
        }
        return null;
    }

    public static void warmUp(Context context, Runnable onComplete) {
        Context app = context.getApplicationContext();
        if (!hasPermission(app)) {
            if (onComplete != null) onComplete.run();
            return;
        }
        EXECUTOR.execute(() -> {
            String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY};
            try (Cursor cursor = app.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection,
                    null, null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    String number = normalize(cursor.getString(0));
                    String name = cursor.getString(1);
                    if (!number.isEmpty() && name != null && !name.trim().isEmpty()) {
                        NAMES.put(number, name.trim());
                    }
                }
            } catch (RuntimeException ignored) { }
            if (onComplete != null) new android.os.Handler(
                    android.os.Looper.getMainLooper()).post(onComplete);
        });
    }

    private static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
