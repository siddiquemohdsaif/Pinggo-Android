package com.w3n.wavestream.Database.CloudFunction.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.w3n.wavestream.modals.UserData;

//import com.google.firebase.messaging.FirebaseMessaging;
//import com.ogfa.carromclash.Util.Notification.NotificationTokenManager;

public class LoginStateManager {

    private static final String PREFS_NAME = "LoginState";
    private static final String PREF_UID = "UID";
    private static final String PREF_ENC = "ENC";
    private static final String PREF_USER_DATA = "USER_DATA";
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
        setLoginData(context, UID, ENC, userDataJson, userData);

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
        setLoginData(context, uid, enc, userDataJson, userData);
    }

    private void setLoginData(Context context, String UID, String ENC, String userDataJson, UserData userData) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putString(PREF_UID, UID)
                .putString(PREF_ENC, ENC)
                .putString(PREF_USER_DATA, userDataJson)
                .commit();
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

    public UserData getUserDataModal(Context context) {
        if (userDataModal == null) {
            userDataModal = UserData.fromJson(getUserData(context));
        }
        return userDataModal;
    }

}
