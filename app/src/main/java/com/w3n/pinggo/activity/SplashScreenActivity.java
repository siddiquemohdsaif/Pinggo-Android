package com.w3n.pinggo.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

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
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.notification.FcmTokenManager;
import com.w3n.pinggo.views.SplashAnimationView;
import com.ogfa.nativeviews.font.NativeFonts;

import org.json.JSONObject;

public class SplashScreenActivity extends PingGoActivity {
    private static final String FONT_PREWARM_TAG = "FontPrewarm";
    private static final long MINIMUM_SPLASH_DURATION_MS = 1000L;
    private static final long APP_CONFIG_TIMEOUT_MS = 5000L;
    private static final long FONT_PREWARM_TIMEOUT_MS = 2500L;
    private boolean minimumSplashDurationElapsed;
    private boolean fontPrewarmFinished;
    private long fontPrewarmStartedAtMs;
    private boolean navigationStarted;
    private boolean listRoutesStarted;
    private SplashAnimationView splashAnimationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        splashAnimationView = findViewById(R.id.splashAnimationView);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prewarmNativeFont();
        loadAppConfig();
        MainRunnerThread.runDelayed(
                this::onMinimumSplashDurationElapsed,
                MINIMUM_SPLASH_DURATION_MS);
        MainRunnerThread.runDelayed(this::onAppConfigTimeout, APP_CONFIG_TIMEOUT_MS);
        MainRunnerThread.runDelayed(
                () -> onFontPrewarmFinished("timeout"), FONT_PREWARM_TIMEOUT_MS);
    }

    @Override
    protected SystemBarStyle defaultSystemBarStyle() {
        return SystemBarStyle.THEME_MANAGED;
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
                    if (configLoaded) {
                        startListRoutes();
                        if (minimumSplashDurationElapsed) {
                            continueToApp();
                        }
                    }
                }),
                error -> {
                    // The two-second checkpoint displays the load error to the user.
                }));
    }

    private void onMinimumSplashDurationElapsed() {
        minimumSplashDurationElapsed = true;
        if (fontPrewarmFinished && AppContextProvider.getParsedAppConfig() != null) {
            continueToApp();
        }
    }

    private void prewarmNativeFont() {
        android.content.Context appContext = getApplicationContext();
        fontPrewarmStartedAtMs = SystemClock.elapsedRealtime();
        Log.i(FONT_PREWARM_TAG, "stage=started");
        BackgroundRunnerThread.run(() -> {
            try {
                // Populate the process-wide login caches before HomeActivity asks
                // for them, so SharedPreferences never has to load on its UI thread.
                LoginStateManager loginState = LoginStateManager.getInstance();
                loginState.isLoggedIn(appContext);
                loginState.getDeviceRole(appContext);
                loginState.getLoginAt(appContext);
                NativeFonts.load(appContext, NativeFonts.INTER);
            } finally {
                MainRunnerThread.run(() -> onFontPrewarmFinished("loaded"));
            }
        });
    }

    private void onFontPrewarmFinished(String reason) {
        if (fontPrewarmFinished) return;
        fontPrewarmFinished = true;
        Log.i(FONT_PREWARM_TAG, "stage=finished reason=" + reason + " durationMs="
                + (SystemClock.elapsedRealtime() - fontPrewarmStartedAtMs));
        if (minimumSplashDurationElapsed
                && AppContextProvider.getParsedAppConfig() != null) {
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
        if (!fontPrewarmFinished) return;
        navigationStarted = true;

        boolean isLoggedIn = LoginStateManager.getInstance().isLoggedIn(this);
        if (isLoggedIn)
            startListRoutes();
        Class<?> destination = isLoggedIn
                ? HomeActivity.class
                : LoginActivity.class;
        startActivity(new Intent(this, destination));
        finish();
    }

    private void startListRoutes() {
        if (listRoutesStarted
                || !LoginStateManager.getInstance().isLoggedIn(getApplicationContext())) {
            return;
        }
        String uid = LoginStateManager.getInstance().getUID(getApplicationContext());
        if (uid == null || uid.trim().isEmpty())
            return;

        listRoutesStarted = true;
        AppFunctionManager.getInstance().applyAuth(getApplicationContext());
        FcmTokenManager.refreshAndUpload(getApplicationContext());
        ChatRepository repository = ChatRepository.getInstance(getApplicationContext());
        repository.connect();
        repository.preloadChatCache();
        repository.ensureChatListLoaded(normalizeAccountId(uid));
    }

    private static String normalizeAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    public void fetchAppConfig(OnSuccessListener<JSONObject> onSuccessListener, OnFailureListener onFailureListener) {
        FirestoreManager.getInstance().readDocument("AppConfiguration", "AppConfiguration" + "_v_" + getVersionName(),
                "/", new OnSuccessListener<DocumentSnapshot>() {
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
                    .getPackageInfo(getPackageName(), 0).versionName;
            if (versionName == null || versionName.isEmpty()) {
                throw new IllegalStateException("App version name is unavailable");
            }
            return versionName.replace(".", "_");
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Unable to read app version name", e);
        }
    }
}
