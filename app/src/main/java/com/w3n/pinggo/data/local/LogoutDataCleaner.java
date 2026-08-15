package com.w3n.pinggo.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.w3n.pinggo.data.repository.ChatRepository;

import java.io.File;

public final class LogoutDataCleaner {
    private LogoutDataCleaner() {
    }

    public static void clear(Context context) {
        Context appContext = context.getApplicationContext();
        ChatRepository.getInstance(appContext).disconnect();
        PingGoDatabase.clearAllLocalData(appContext);
        clearSharedPreferences(appContext);
        clearCachedFiles(appContext);
        ChatRepository.resetInstance();
    }

    private static void clearSharedPreferences(Context context) {
        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] prefFiles = prefsDir.listFiles((dir, name) -> name.endsWith(".xml"));
        if (prefFiles == null) {
            return;
        }

        for (File prefFile : prefFiles) {
            String prefName = prefFile.getName().replaceFirst("\\.xml$", "");
            SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
            preferences.edit().clear().commit();
        }
    }

    private static void clearCachedFiles(Context context) {
        File[] files = context.getFilesDir().listFiles((dir, name) ->
                name.startsWith("chat_profile_") ||
                        "profile_photo.jpg".equals(name)
        );
        if (files == null) {
            return;
        }

        for (File file : files) {
            file.delete();
        }
    }
}
