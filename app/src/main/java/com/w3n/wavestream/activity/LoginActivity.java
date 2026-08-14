package com.w3n.wavestream.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.w3n.wavestream.R;
import com.w3n.wavestream.fragment.login.PhoneNumberFragment;

/** Hosts the fragments that make up the login flow. */
public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        setContentView(R.layout.activity_login);
        installStatusBarScrim();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int systemBarColor = ContextCompat.getColor(
                this, R.color.login_system_bar_background);
        window.setStatusBarColor(systemBarColor);
        window.setNavigationBarColor(systemBarColor);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Status-bar colors are forced transparent for apps targeting Android 16.
     * Draw an opaque view in the status-bar inset so the login artwork cannot
     * show through it. Login fragments still receive the unconsumed insets and
     * position their content below the status bar as before.
     */
    private void installStatusBarScrim() {
        View root = findViewById(R.id.login_root);
        View scrim = findViewById(R.id.login_status_bar_scrim);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars());
            ViewGroup.LayoutParams layoutParams = scrim.getLayoutParams();
            if (layoutParams.height != statusBars.top) {
                layoutParams.height = statusBars.top;
                scrim.setLayoutParams(layoutParams);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.login_fragment_container);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && fragment instanceof PhoneNumberFragment
                && ((PhoneNumberFragment) fragment).handleOutsideTap(
                        event.getRawX(), event.getRawY())) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
}
