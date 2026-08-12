package com.w3n.wavestream.activity;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.R;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.wavestream.views.login.PingGoLoginView;

public class LoginActivity2 extends AppCompatActivity {
    private PingGoLoginView loginView;
    private String phoneNumberForOtp;
    private String otpRequestId;
    private String otpProvider;
    private boolean otpRequestInProgress;
    private boolean otpVerificationInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        loginView = new PingGoLoginView(this);
        loginView.setId(R.id.main);
        setContentView(loginView);
        ViewCompat.setOnApplyWindowInsetsListener(loginView, (view, windowInsets) -> {
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets keyboardInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            boolean isKeyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            loginView.setInsets(
                    systemBarInsets.top,
                    Math.max(systemBarInsets.bottom, navigationBarInsets.bottom),
                    keyboardInsets.bottom,
                    isKeyboardVisible
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(loginView);
        loginView.setOnRequestOtpListener(this::requestOtp);
        loginView.setOnConfirmOtpListener(this::verifyOtp);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && loginView != null
                && loginView.handleCountryOutsideTap(event.getRawX(), event.getRawY())) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void requestOtp(String fullPhoneNumber) {
        if (otpRequestInProgress) return;
        phoneNumberForOtp = fullPhoneNumber;
        setOtpRequestInProgress(true);
        AppFunctionManager.getInstance().sendOtp(fullPhoneNumber, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object result) {
                runOnUiThread(() -> {
                    OtpHandler.OtpResult otpResult = result instanceof OtpHandler.OtpResult
                            ? (OtpHandler.OtpResult) result : null;
                    otpRequestId = otpResult == null ? null : otpResult.getReqId();
                    otpProvider = otpResult == null ? null : otpResult.getProvider();
                    setOtpRequestInProgress(false);
                    if (otpRequestId == null || otpRequestId.trim().isEmpty()) {
                        loginView.showPhoneError(getString(R.string.otp_send_failed));
                        return;
                    }
                    loginView.showOtpFields();
                    Toast.makeText(LoginActivity2.this, R.string.otp_sent, LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setOtpRequestInProgress(false);
                    loginView.showPhoneError(error == null || error.trim().isEmpty()
                            ? getString(R.string.otp_send_failed) : error);
                });
            }
        });
    }

    private void verifyOtp(String otp) {
        if (otpVerificationInProgress) return;
        if (otpRequestId == null || otpRequestId.trim().isEmpty()) {
            loginView.showOtpError(getString(R.string.otp_request_first));
            return;
        }
        if (!phoneNumberForOtp.equals(loginView.getFullPhoneNumber())) {
            loginView.showOtpError(getString(R.string.otp_phone_changed));
            return;
        }
        setOtpVerificationInProgress(true);
        AppFunctionManager.getInstance().verifyOtp(
                otpRequestId,
                otpProvider,
                otp,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object result) {
                        runOnUiThread(() -> {
                            setOtpVerificationInProgress(false);
                            Toast.makeText(LoginActivity2.this,
                                    R.string.otp_verified, LENGTH_SHORT).show();
                            loginUser();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            setOtpVerificationInProgress(false);
                            loginView.showOtpError(error == null || error.trim().isEmpty()
                                    ? getString(R.string.invalid_otp) : error);
                        });
                    }
                }
        );
    }

    private void setOtpRequestInProgress(boolean inProgress) {
        otpRequestInProgress = inProgress;
        loginView.setOtpRequestInProgress(inProgress);
    }

    private void setOtpVerificationInProgress(boolean inProgress) {
        otpVerificationInProgress = inProgress;
        loginView.setOtpVerifyInProgress(inProgress);
    }

    private void loginUser() {
        AppFunctionManager.getInstance().userLogin(phoneNumberForOtp,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object result) {
                        runOnUiThread(() -> {
                            startActivity(new Intent(LoginActivity2.this, HomeActivity.class));
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if ("No user found.".equals(error)) {
                                startActivity(new Intent(LoginActivity2.this, SignUpActivity.class)
                                        .putExtra(SignUpActivity.EXTRA_PHONE_NUMBER, phoneNumberForOtp));
                                finish();
                            } else {
                                Toast.makeText(getApplicationContext(), error, LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }
}
