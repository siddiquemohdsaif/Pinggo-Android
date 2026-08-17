package com.w3n.pinggo.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.w3n.pinggo.R;

/** Hosts the fragment-based profile setup flow shown after verification. */
public class SignUpActivity extends AppCompatActivity {
    public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.extra.PHONE_NUMBER";
    public static final String EXTRA_EMAIL = "com.w3n.pinggo.extra.EMAIL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        configureSystemBars();
        setContentView(R.layout.activity_sign_up);
        installStatusBarScrim();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int color = ContextCompat.getColor(this, R.color.login_system_bar_background);
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void installStatusBarScrim() {
        View root = findViewById(R.id.sign_up_root);
        View scrim = findViewById(R.id.sign_up_status_bar_scrim);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.LayoutParams params = scrim.getLayoutParams();
            if (params.height != statusBars.top) {
                params.height = statusBars.top;
                scrim.setLayoutParams(params);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
