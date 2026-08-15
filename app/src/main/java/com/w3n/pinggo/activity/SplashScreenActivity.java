package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;

public class SplashScreenActivity extends AppCompatActivity {
    private static final long SPLASH_DURATION_MS = 2000L;

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

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean isLoggedIn = LoginStateManager.getInstance().isLoggedIn(this);
            if (isLoggedIn) {
                AppFunctionManager.getInstance().applyAuth(this);
            }
            Class<?> destination = isLoggedIn
                    ? HomeActivity.class
                    : LoginActivity.class;
            startActivity(new Intent(SplashScreenActivity.this, destination));
            finish();
        }, SPLASH_DURATION_MS);
    }
}
