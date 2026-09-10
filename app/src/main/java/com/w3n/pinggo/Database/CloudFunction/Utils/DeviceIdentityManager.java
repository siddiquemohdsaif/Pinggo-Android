package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.UUID;

/** Stable identity for one Pinggo installation. It is not an auth credential. */
public final class DeviceIdentityManager {
    private static final String PREFS = "PinggoDeviceIdentity";
    private static final String DEVICE_ID = "deviceId";

    private DeviceIdentityManager() {}

    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(DEVICE_ID, null);
        if (existing != null && !existing.trim().isEmpty()) return existing;
        String created = UUID.randomUUID().toString();
        prefs.edit().putString(DEVICE_ID, created).commit();
        return created;
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "device" : Build.MODEL.trim();
        return manufacturer.equalsIgnoreCase(model) ? model : manufacturer + " " + model;
    }
}
