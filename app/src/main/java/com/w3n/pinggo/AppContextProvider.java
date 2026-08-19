package com.w3n.pinggo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.google.gson.Gson;
import com.w3n.pinggo.modals.AppConfiguration;
import com.w3n.pinggo.views.common.NativeMessageView;

import org.json.JSONObject;


public class AppContextProvider extends Application {
    private static Context appContext;
    private static volatile JSONObject appConfig;
    private static volatile AppConfiguration parsedAppConfig;
    public static boolean isDevelopment = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the app context when the application starts
        appContext = getApplicationContext();

        isDevelopment = false;
        devOverlay(isDevelopment);


    }

    private void devOverlay(boolean isDevelopment) {
        if (isDevelopment) {
            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    addDevOverlay(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityResumed(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        }
    }

    public static Context getAppContext() {
        return appContext;
    }

    public static JSONObject getAppConfig() {
        return appConfig;
    }

    public static AppConfiguration getParsedAppConfig() {
        return parsedAppConfig;
    }

    public static boolean setAppConfig(JSONObject appConfig) {
        try {
            AppConfiguration parsedConfig = new Gson().fromJson(
                    appConfig.toString(), AppConfiguration.class);
            if (parsedConfig == null || parsedConfig.getLoginOption() == null) return false;
            AppContextProvider.appConfig = appConfig;
            parsedAppConfig = parsedConfig;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void addDevOverlay(Activity activity) {
        FrameLayout decorView = (FrameLayout) activity.getWindow().getDecorView();

        // Check if already added
        final String tag = "DEV_OVERLAY_TAG";
        View existingView = decorView.findViewWithTag(tag);
        if (existingView != null) {
            return; // already added
        }

        NativeMessageView devLabel = new NativeMessageView(activity, "DEV", Color.WHITE, 18f);
        devLabel.setTag(tag);
        devLabel.setBackgroundColor(Color.RED);
        devLabel.setAlpha(0.8f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.round(72 * activity.getResources().getDisplayMetrics().density),
                Math.round(40 * activity.getResources().getDisplayMetrics().density)
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(16, 50, 16, 16); // adjust top margin if needed

        // Post to ensure it's added after layout is ready
        decorView.post(() -> decorView.addView(devLabel, params));
    }

}
