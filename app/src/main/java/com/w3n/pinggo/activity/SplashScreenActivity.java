package com.w3n.pinggo.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.Firestore.Core.FirestoreManager;
import com.w3n.pinggo.Database.Firestore.Util.DocumentSnapshot;
import com.w3n.pinggo.Database.Firestore.Util.ListenerCallback.OnFailureListener;
import com.w3n.pinggo.Database.Firestore.Util.ListenerCallback.OnSuccessListener;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Util.BackgroundRunnerThread;
import com.w3n.pinggo.Util.MainRunnerThread;
import com.w3n.pinggo.views.SplashAnimationView;

import org.json.JSONObject;

public class SplashScreenActivity extends AppCompatActivity {
    private static final long MINIMUM_SPLASH_DURATION_MS = 1000L;
    private static final long APP_CONFIG_TIMEOUT_MS = 5000L;
    private boolean minimumSplashDurationElapsed;
    private boolean navigationStarted;
    private SplashAnimationView splashAnimationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);
        splashAnimationView = findViewById(R.id.splashAnimationView);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadAppConfig();
        MainRunnerThread.runDelayed(
                this::onMinimumSplashDurationElapsed,
                MINIMUM_SPLASH_DURATION_MS
        );
        MainRunnerThread.runDelayed(this::onAppConfigTimeout, APP_CONFIG_TIMEOUT_MS);
    }

    @Override
    protected void onDestroy() {
        if (splashAnimationView != null) {
            splashAnimationView.release();
            splashAnimationView = null;
        }
        super.onDestroy();
    }

    private void loadAppConfig() {
        BackgroundRunnerThread.run(() -> fetchAppConfig(
                appConfig -> MainRunnerThread.run(() -> {
                    boolean configLoaded = AppContextProvider.setAppConfig(appConfig);
                    if (configLoaded && minimumSplashDurationElapsed) {
                        continueToApp();
                    }
                }),
                error -> {
                    // The two-second checkpoint displays the load error to the user.
                }
        ));
    }

    private void onMinimumSplashDurationElapsed() {
        minimumSplashDurationElapsed = true;
        if (AppContextProvider.getParsedAppConfig() != null) {
            continueToApp();
        }
    }

    private void onAppConfigTimeout() {
        if (navigationStarted || isFinishing() || isDestroyed()
                || AppContextProvider.getParsedAppConfig() != null) {
            return;
        }
        Toast.makeText(this, R.string.unable_to_load_app_config, Toast.LENGTH_LONG).show();
    }

    private void continueToApp() {
        if (navigationStarted || isFinishing() || isDestroyed()) {
            return;
        }
        navigationStarted = true;

        boolean isLoggedIn = LoginStateManager.getInstance().isLoggedIn(this);
        if (isLoggedIn) {
            AppFunctionManager.getInstance().applyAuth(this);
        }
        Class<?> destination = isLoggedIn
                ? HomeActivity.class
                : LoginActivity.class;
        startActivity(new Intent(this, destination));
        finish();
    }

    public void fetchAppConfig(OnSuccessListener<JSONObject> onSuccessListener, OnFailureListener onFailureListener) {
        FirestoreManager.getInstance().readDocument("AppConfiguration", "AppConfiguration" + "_v_" + getVersionName(), "/", new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                JSONObject appConfig = documentSnapshot.getDataJson();
                if (appConfig != null) {
                    onSuccessListener.onSuccess(appConfig);
                } else {
                    onFailureListener.onFailure(new Exception("App Config response is empty"));
                }
            }
        }, new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                onFailureListener.onFailure(e);
            }
        });
    }

    public String getVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            if (versionName == null || versionName.isEmpty()) {
                throw new IllegalStateException("App version name is unavailable");
            }
            return versionName.replace(".", "_");
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Unable to read app version name", e);
        }
    }
}
