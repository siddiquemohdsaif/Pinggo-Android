package com.w3n.pinggo.activity;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.pinggo.R;
import com.w3n.pinggo.fragment.login.PhoneNumberFragment;
import com.w3n.pinggo.views.common.ExitAppController;
import com.w3n.pinggo.data.local.SessionLogoutManager;

/** Hosts the fragments that make up the login flow. */
public class LoginActivity extends PingGoActivity {
    public static final String EXTRA_LOGOUT_MESSAGE = "logoutMessage";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        installStatusBarScrim();
        ExitAppController.install(this, getSupportFragmentManager());
        String logoutMessage = getIntent().getStringExtra(EXTRA_LOGOUT_MESSAGE);
        String pendingMessage = SessionLogoutManager.consumeLogoutMessage(this);
        if (logoutMessage == null || logoutMessage.trim().isEmpty()) {
            logoutMessage = pendingMessage;
        }
        if (logoutMessage != null && !logoutMessage.trim().isEmpty()) {
            Toast.makeText(this, logoutMessage.trim(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected SystemBarStyle defaultSystemBarStyle() {
        return SystemBarStyle.AUTH;
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
