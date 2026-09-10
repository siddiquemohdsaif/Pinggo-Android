package com.w3n.pinggo.Database.CloudFunction.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.w3n.pinggo.modals.UserData;

//import com.google.firebase.messaging.FirebaseMessaging;
//import com.ogfa.carromclash.Util.Notification.NotificationTokenManager;

public class LoginStateManager {

    private static final String PREFS_NAME = "LoginState";
    private static final String PREF_UID = "UID";
    private static final String PREF_ENC = "ENC";
    private static final String PREF_USER_DATA = "USER_DATA";
    private static final String PREF_DEVICE_ROLE = "DEVICE_ROLE";
    private static final String PREF_LOGIN_AT = "LOGIN_AT";
    private static final String ROLE_PRIMARY = "primary";
    private static final String ROLE_COMPANION = "companion";
    private static String UID = null;
    private static String ENC = null;
    private static String USER_DATA = null;
    private static UserData userDataModal = null;
    private static LoginStateManager instance;
    private static final Object lock = new Object();

    private LoginStateManager() {
    }

    public static LoginStateManager getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (lock) {
            if (instance == null) {
                instance = new LoginStateManager();
            }
        }
        return instance;
    }


    public boolean isLoggedIn(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String UID = sharedPreferences.getString(PREF_UID, null);
        String ENC = sharedPreferences.getString(PREF_ENC, null);
        return UID != null && ENC != null;
    }


    public void setLogin(Context context, String UID, String ENC) {
        setLogin(context, UID, ENC, (UserData) null);
    }

    public void setLogin(Context context, String UID, String ENC, String userData) {
        setLogin(context, UID, ENC, UserData.fromJson(userData));
    }

    public void setLogin(Context context, String UID, String ENC, UserData userData) {
        String userDataJson = userData == null ? null : userData.toJson();
        setLoginData(context, UID, ENC, userDataJson, userData, ROLE_PRIMARY, true);

//        FirebaseMessaging.getInstance().getToken()
//                .addOnCompleteListener(task -> {
//                    if (!task.isSuccessful()) {
//                        NotificationTokenManager.saveToken(context, "pending");
//                        //Log.w("LoginStateManager", "Fetching FCM token failed", task.getException());
//                        return;
//                    }
//
//                    // Safe to call getResult() now
//                    String token = task.getResult();
//                    if (token != null) {
//                        NotificationTokenManager.saveToken(context, token);
//                    }
//                });
    }

    public void setCompanionLogin(Context context, String UID, String ENC) {
        setCompanionLogin(context, UID, ENC, null);
    }

    public void setCompanionLogin(Context context, String UID, String ENC, UserData userData) {
        UserData accountData = userData == null ? new UserData() : userData;
        accountData.setId(UID);
        accountData.setEncryptedCredential(ENC);
        accountData.setPhoneNumber(UID);
        UserData.ProfileData profileData = accountData.getProfileData();
        if (profileData == null) {
            profileData = new UserData.ProfileData();
            accountData.setProfileData(profileData);
        }
        if (profileData.getPhoneNumber() == null || profileData.getPhoneNumber().trim().isEmpty()) {
            profileData.setPhoneNumber(UID);
        }
        setLoginData(context, UID, ENC, accountData.toJson(), accountData,
                ROLE_COMPANION, true);
    }

    public void setUserData(Context context, UserData userData) {
        String uid = userData == null || userData.getId() == null ? getUID(context) : userData.getId();
        String enc = userData == null || userData.getEncryptedCredential() == null ? getENC(context) : userData.getEncryptedCredential();
        if (userData != null) {
            userData.setId(uid);
            userData.setEncryptedCredential(enc);
            if (userData.getPhoneNumber() == null) {
                userData.setPhoneNumber(uid);
            }
        }
        String userDataJson = userData == null ? null : userData.toJson();
        setLoginData(context, uid, enc, userDataJson, userData, getDeviceRole(context), false);
    }

    private void setLoginData(Context context, String UID, String ENC, String userDataJson,
                              UserData userData, String deviceRole, boolean refreshLoginTime) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit()
                .putString(PREF_UID, UID)
                .putString(PREF_ENC, ENC)
                .putString(PREF_USER_DATA, userDataJson)
                .putString(PREF_DEVICE_ROLE, ROLE_COMPANION.equals(deviceRole)
                        ? ROLE_COMPANION : ROLE_PRIMARY);
        if (refreshLoginTime) editor.putLong(PREF_LOGIN_AT, System.currentTimeMillis());
        editor.commit();
        LoginStateManager.UID = UID;
        LoginStateManager.ENC = ENC;
        LoginStateManager.USER_DATA = userDataJson;
        LoginStateManager.userDataModal = userData;
    }


    public void logOut(Context context) {
//        NotificationTokenManager.unSubscribeFromUidAndClan(context);
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putString(PREF_UID, null)
                .putString(PREF_ENC, null)
                .putString(PREF_USER_DATA, null)
                .putString(PREF_DEVICE_ROLE, null)
                .putLong(PREF_LOGIN_AT, 0L)
                .apply();
        UID = null;
        ENC = null;
        USER_DATA = null;
        userDataModal = null;
    }

    public String getUID(Context context) {
        if (UID == null){
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            UID = sharedPreferences.getString(PREF_UID, null);
        }
        return UID;
    }


    public String getENC(Context context) {
        if (ENC == null){
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            ENC = sharedPreferences.getString(PREF_ENC, null);
        }
        return ENC;
    }

    public String getUserData(Context context) {
        if (USER_DATA == null){
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            USER_DATA = sharedPreferences.getString(PREF_USER_DATA, null);
        }
        return USER_DATA;
    }

    public String getDeviceRole(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_DEVICE_ROLE, ROLE_PRIMARY);
    }

    public boolean isCompanionDevice(Context context) {
        return ROLE_COMPANION.equals(getDeviceRole(context));
    }

    public long getLoginAt(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_LOGIN_AT, 0L);
    }

    public UserData getUserDataModal(Context context) {
        if (userDataModal == null) {
            userDataModal = UserData.fromJson(getUserData(context));
        }
        return userDataModal;
    }

}
