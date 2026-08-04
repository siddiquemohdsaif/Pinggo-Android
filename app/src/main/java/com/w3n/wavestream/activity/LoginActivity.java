package com.w3n.wavestream.activity;

import static android.widget.Toast.LENGTH_SHORT;

import android.graphics.RectF;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.TextViewAnimator;
import com.w3n.wavestream.views.animator.WaveAnimatorView;
import com.w3n.wavestream.views.login.LoginPhoneCardView;

public class LoginActivity extends AppCompatActivity {
    private static final String FIXED_OTP = "123456";

    private String fullPhoneNumber;
    private View mainView;
    private LinearLayout loginContentLayout;
    private View loginHeaderAnimatorView;
    private LoginPhoneCardView loginPhoneCardView;
    private WaveAnimatorView termsAnimatorView;
    private Insets systemBarInsets = Insets.NONE;
    private Insets navigationBarInsets = Insets.NONE;
    private Insets imeInsets = Insets.NONE;
    private boolean imeVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int width = mainView.getWidth() > 0
                    ? mainView.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            int horizontalPadding = (int) (0.066204f * width);
            if (loginContentLayout != null) {
                loginContentLayout.setPadding(horizontalPadding, systemBarInsets.top,
                        horizontalPadding, loginContentLayout.getPaddingBottom());
                loginContentLayout.post(this::applyResponsiveLoginLayout);
            }
            mainView.post(this::applyKeyboardLift);
            return insets;
        });

        loginContentLayout = findViewById(R.id.loginContentLayout);
        loginHeaderAnimatorView = findViewById(R.id.loginHeaderAnimatorView);
        loginPhoneCardView = findViewById(R.id.loginPhoneCardView);
        termsAnimatorView = findViewById(R.id.termsAnimatorView);
        ViewCompat.requestApplyInsets(mainView);
        findViewById(R.id.main).post(this::applyResponsiveLoginLayout);

        loginPhoneCardView.setOnOtpAnimatorClickListener(this::showOtpFields);
        loginPhoneCardView.setOnConfirmAnimatorClickListener(this::confirmOtp);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && loginPhoneCardView != null
                && !loginPhoneCardView.isRawTouchInsideCountryField(event.getRawX(), event.getRawY())
                && loginPhoneCardView.handleCountryOutsideTap(imeVisible)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void applyResponsiveLoginLayout() {
        int width = mainView.getWidth() > 0
                ? mainView.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int horizontalPadding = (int) (0.066204f * width);
        int bottomSafePadding = Math.max(systemBarInsets.bottom, navigationBarInsets.bottom)
                + (int) (0.071296f * width);

        loginContentLayout.setPadding(horizontalPadding, 0, horizontalPadding, bottomSafePadding);
        setHorizontalMargins(loginHeaderAnimatorView, horizontalPadding);
        setTopMargin(loginHeaderAnimatorView, systemBarInsets.top + (int) (0.224074f * width));

        setLayoutHeight(loginHeaderAnimatorView, (int) (0.560185f * width));
        setLinearTopMargin(loginPhoneCardView, 0);
        setLayoutHeight(termsAnimatorView, (int) (0.112037f * width));
        setLinearTopMargin(termsAnimatorView, (int) (0.063657f * width));

        loginContentLayout.requestLayout();
        termsAnimatorView.post(this::setupTermsAnimator);
        mainView.post(this::applyKeyboardLift);
    }

    private void applyBottomAnchoredSpacing() {
        loginContentLayout.requestLayout();
    }

    private void applyKeyboardLift() {
        if (loginContentLayout == null || !imeVisible) {
            setLoginContentTranslation(0f);
            return;
        }
        if (loginPhoneCardView == null || loginPhoneCardView.getHeight() == 0 || mainView.getHeight() == 0) {
            return;
        }
        int[] mainLocation = new int[2];
        mainView.getLocationOnScreen(mainLocation);

        int width = mainView.getWidth() > 0
                ? mainView.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int inputBottom = loginPhoneCardView.getActiveInputBottomOnScreen() - mainLocation[1];
        int keyboardTop = mainView.getHeight() - imeInsets.bottom;
        int visibleMargin = (int) (0.040741f * width);
        int neededLift = Math.max(0, inputBottom + visibleMargin - keyboardTop);
        setLoginContentTranslation(-neededLift);
        loginPhoneCardView.post(loginPhoneCardView::updateCountryPopupPosition);
    }

    private void setLoginContentTranslation(float translationY) {
        if (loginContentLayout.getTranslationY() == translationY) {
            return;
        }
        loginContentLayout.animate()
                .translationY(translationY)
                .setDuration(180L)
                .withEndAction(() -> {
                    if (loginPhoneCardView != null) {
                        loginPhoneCardView.updateCountryPopupPosition();
                    }
                })
                .start();
    }

    private void setLayoutHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    private void setLinearTopMargin(View view, int topMargin) {
        ViewGroup.LayoutParams baseParams = view.getLayoutParams();
        if (baseParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) baseParams;
            params.topMargin = topMargin;
            view.setLayoutParams(params);
        }
    }

    private void setTopMargin(View view, int topMargin) {
        ViewGroup.LayoutParams baseParams = view.getLayoutParams();
        if (baseParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) baseParams;
            params.topMargin = topMargin;
            view.setLayoutParams(params);
        }
    }

    private void setHorizontalMargins(View view, int horizontalMargin) {
        ViewGroup.LayoutParams baseParams = view.getLayoutParams();
        if (baseParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) baseParams;
            params.leftMargin = horizontalMargin;
            params.rightMargin = horizontalMargin;
            view.setLayoutParams(params);
        }
    }

    private int getLinearTopMargin(View view) {
        ViewGroup.LayoutParams baseParams = view.getLayoutParams();
        if (baseParams instanceof LinearLayout.LayoutParams) {
            return ((LinearLayout.LayoutParams) baseParams).topMargin;
        }
        return 0;
    }

    private void setupTermsAnimator() {
        int width = termsAnimatorView.getWidth();
        termsAnimatorView.getTextAnimators().clear();
        termsAnimatorView.getTextAnimators().add(new TextViewAnimator(
                "terms_line_1",
                "By continuing, you agree to our",
                new RectF(0, 0, width, 0.050926f * width),
                0.025463f * width,
                ContextCompat.getColor(this, R.color.pinggo_muted_text),
                null
        ));
        termsAnimatorView.getTextAnimators().add(new TextViewAnimator(
                "terms_line_2",
                "Terms of Service and Privacy Policy.",
                new RectF(0, 0.045833f * width, width, 0.101852f * width),
                0.025463f * width,
                ContextCompat.getColor(this, R.color.pinggo_action),
                null
        ));
        termsAnimatorView.invalidate();
    }

    private void showOtpFields() {
        if (loginPhoneCardView.getPhoneNumber().trim().isEmpty()) {
            showAnimatorDialog(getString(R.string.phone_required));
            return;
        }

        if (!loginPhoneCardView.isValidFullNumber()) {
            showAnimatorDialog(getString(R.string.invalid_phone));
            return;
        }

        fullPhoneNumber = loginPhoneCardView.getFullNumberWithPlus();
        loginPhoneCardView.showOtpFields();
        Toast.makeText(this, getString(R.string.fixed_otp_message, FIXED_OTP, fullPhoneNumber), LENGTH_SHORT).show();
        loginContentLayout.post(() -> {
            applyBottomAnchoredSpacing();
            loginPhoneCardView.refreshAnimators();
        });
    }

    private void confirmOtp() {
        String otp = loginPhoneCardView.getOtp().trim();
        if (!FIXED_OTP.equals(otp)) {
            showAnimatorDialog(getString(R.string.invalid_otp));
            return;
        }

        userLogin();
    }

    private void userLogin() {
        AppFunctionManager.getInstance().userLogin(fullPhoneNumber, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onError(String error) {
                if(error.equals("No user found.")){
                    startActivity(new Intent(LoginActivity.this, SignUpActivity.class)
                            .putExtra(SignUpActivity.EXTRA_PHONE_NUMBER, fullPhoneNumber));
                    finish();
                }else {
                    Toast.makeText(getApplicationContext(),error,LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showAnimatorDialog(String message) {
        loginPhoneCardView.showAnimatorDialog(message);
    }
}
