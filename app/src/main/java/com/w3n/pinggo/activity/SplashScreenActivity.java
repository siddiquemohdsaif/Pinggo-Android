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

import org.json.JSONObject;

public class SplashScreenActivity extends AppCompatActivity {
    private static final long SPLASH_DURATION_MS = 2000L;
    private boolean splashDurationElapsed;
    private boolean navigationStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadAppConfig();
        MainRunnerThread.runDelayed(this::onSplashDurationElapsed, SPLASH_DURATION_MS);
    }

    private void loadAppConfig() {
        BackgroundRunnerThread.run(() -> fetchAppConfig(
                appConfig -> MainRunnerThread.run(() -> {
                    boolean configLoaded = AppContextProvider.setAppConfig(appConfig);
                    if (configLoaded && splashDurationElapsed) {
                        continueToApp();
                    }
                }),
                error -> {
                    // The two-second checkpoint displays the load error to the user.
                }
        ));
    }

    private void onSplashDurationElapsed() {
        splashDurationElapsed = true;
        if (AppContextProvider.getParsedAppConfig() == null) {
            Toast.makeText(this, R.string.unable_to_load_app_config, Toast.LENGTH_LONG).show();
            return;
        }
        continueToApp();
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
