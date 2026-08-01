package com.w3n.wavestream;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;


public class AppContextProvider extends Application {
    private static Context appContext;
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

    private void addDevOverlay(Activity activity) {
        FrameLayout decorView = (FrameLayout) activity.getWindow().getDecorView();

        // Check if already added
        final String tag = "DEV_OVERLAY_TAG";
        View existingView = decorView.findViewWithTag(tag);
        if (existingView != null) {
            return; // already added
        }

        TextView devLabel = new TextView(activity);
        devLabel.setText("DEV");
        devLabel.setTag(tag);
        devLabel.setTextColor(Color.WHITE);
        devLabel.setBackgroundColor(Color.RED);
        devLabel.setPadding(20, 10, 20, 10);
        devLabel.setTextSize(18);
        devLabel.setAlpha(0.8f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(16, 50, 16, 16); // adjust top margin if needed

        // Post to ensure it's added after layout is ready
        decorView.post(() -> decorView.addView(devLabel, params));
    }

}
