package com.w3n.wavestream.activity;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.w3n.wavestream.R;
import com.w3n.wavestream.fragment.login.PhoneNumberFragment;

/** Hosts the fragments that make up the login flow. */
public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
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
