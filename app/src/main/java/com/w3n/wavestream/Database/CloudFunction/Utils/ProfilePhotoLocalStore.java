package com.w3n.wavestream.Database.CloudFunction.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.w3n.wavestream.modals.UserData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ProfilePhotoLocalStore {
    private static final String TAG = "ProfilePhotoLocalStore";
    private static final String FILE_NAME = "profile_photo.jpg";
    private static final int JPEG_QUALITY = 92;

    public static String save(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            return file.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    public static void downloadAndStore(Context context, UserData userData) {
        if (context == null || userData == null || userData.getProfileData() == null) {
            Log.d(TAG, "downloadAndStore skipped: missing context/userData/profileData");
            return;
        }

        String profilePhotoUrl = normalizeProfilePhotoUrl(userData.getProfileData().getProfilePhotoUrl());
        if (profilePhotoUrl == null || profilePhotoUrl.trim().isEmpty()) {
            Log.d(TAG, "downloadAndStore skipped: profilePhotoUrl is empty");
            return;
        }

        Log.d(TAG, "downloadAndStore started: " + profilePhotoUrl);
        new Thread(() -> {
            try (InputStream inputStream = new URL(profilePhotoUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) {
                    Log.d(TAG, "downloadAndStore failed: decoded bitmap is null");
                    return;
                }
                String localPath = save(context, bitmap);
                if (localPath == null) {
                    Log.d(TAG, "downloadAndStore failed: could not save bitmap");
                    return;
                }

                UserData storedUserData = LoginStateManager.getInstance().getUserDataModal(context);
                if (storedUserData == null) {
                    storedUserData = userData;
                }

                UserData.ProfileData profileData = storedUserData.getProfileData();
                if (profileData == null) {
                    profileData = new UserData.ProfileData();
                    profileData.setPhoneNumber(storedUserData.getPhoneNumber());
                    storedUserData.setProfileData(profileData);
                }
                profileData.setLocalProfilePhotoPath(localPath);
                LoginStateManager.getInstance().setUserData(context, storedUserData);
                Log.d(TAG, "downloadAndStore success: " + localPath);
            } catch (IOException ignored) {
                Log.d(TAG, "downloadAndStore failed: " + ignored.getMessage());
            }
        }).start();
    }

    private static String normalizeProfilePhotoUrl(String profilePhotoUrl) {
        if (profilePhotoUrl == null) {
            return null;
        }

        if (profilePhotoUrl.startsWith("http://function.cloudsw3.com/")) {
            return profilePhotoUrl.replace("http://", "https://");
        }
        return profilePhotoUrl;
    }
}
