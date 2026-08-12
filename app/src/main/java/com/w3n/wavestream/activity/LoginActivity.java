package com.w3n.wavestream.activity;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
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
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int systemBarColor = Color.parseColor("#EBF1F7");
        window.setStatusBarColor(systemBarColor);
        window.setNavigationBarColor(systemBarColor);

        int systemUiFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUiFlags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
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
