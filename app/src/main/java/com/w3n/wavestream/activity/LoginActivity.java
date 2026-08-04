package com.w3n.wavestream.activity;

import static android.widget.Toast.LENGTH_SHORT;

import android.graphics.RectF;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.TextViewAnimator;
import com.w3n.wavestream.views.animator.WaveAnimatorView;
import com.w3n.wavestream.views.login.LoginPhoneCardView;

public class LoginActivity extends AppCompatActivity {
    private static final String FIXED_OTP = "123456";

    private CountryCodePicker countryCodePicker;
    private EditText phoneNumberEditText;
    private EditText otpEditText;
    private TextView otpHintTextView;
    private TextView phoneCodeTextView;
    private TextView countryFlagTextView;
    private View confirmButton;
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
        countryCodePicker = findViewById(R.id.countryCodePicker);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        otpEditText = findViewById(R.id.otpEditText);
        otpHintTextView = findViewById(R.id.otpHintTextView);
        phoneCodeTextView = findViewById(R.id.phoneCodeTextView);
        countryFlagTextView = findViewById(R.id.countryFlagTextView);
        confirmButton = findViewById(R.id.confirmButton);
        loginPhoneCardView = findViewById(R.id.loginPhoneCardView);
        termsAnimatorView = findViewById(R.id.termsAnimatorView);
        countryCodePicker.registerCarrierNumberEditText(phoneNumberEditText);
        updateSelectedCountryViews();
        countryCodePicker.setOnCountryChangeListener(this::updateSelectedCountryViews);
        ViewCompat.requestApplyInsets(mainView);
        findViewById(R.id.main).post(this::applyResponsiveLoginLayout);
        phoneNumberEditText.setOnFocusChangeListener((v, hasFocus) -> mainView.post(this::applyKeyboardLift));
        otpEditText.setOnFocusChangeListener((v, hasFocus) -> mainView.post(this::applyKeyboardLift));

        loginPhoneCardView.setOnOtpAnimatorClickListener(this::showOtpFields);
        loginPhoneCardView.setOnConfirmAnimatorClickListener(this::confirmOtp);
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
        View focusedInput = otpEditText != null && otpEditText.hasFocus()
                ? otpEditText
                : phoneNumberEditText;
        if (focusedInput == null || focusedInput.getHeight() == 0 || mainView.getHeight() == 0) {
            return;
        }

        int[] mainLocation = new int[2];
        int[] inputLocation = new int[2];
        mainView.getLocationOnScreen(mainLocation);
        focusedInput.getLocationOnScreen(inputLocation);

        int width = mainView.getWidth() > 0
                ? mainView.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int inputBottom = inputLocation[1] - mainLocation[1] + focusedInput.getHeight();
        int keyboardTop = mainView.getHeight() - imeInsets.bottom;
        int visibleMargin = (int) (0.040741f * width);
        int neededLift = Math.max(0, inputBottom + visibleMargin - keyboardTop);
        setLoginContentTranslation(-neededLift);
    }

    private void setLoginContentTranslation(float translationY) {
        if (loginContentLayout.getTranslationY() == translationY) {
            return;
        }
        loginContentLayout.animate()
                .translationY(translationY)
                .setDuration(180L)
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

    private void updateSelectedCountryViews() {
        try {
            phoneCodeTextView.setText(countryCodePicker.getSelectedCountryCodeWithPlus());
            countryFlagTextView.setText(countryCodeToFlagEmoji(countryCodePicker.getSelectedCountryNameCode()));
        } catch (NullPointerException ignored) {
            countryCodePicker.setDefaultCountryUsingNameCode("IN");
            countryCodePicker.resetToDefaultCountry();
            phoneCodeTextView.setText("+91");
            countryFlagTextView.setText(countryCodeToFlagEmoji("IN"));
        }
    }

    private String countryCodeToFlagEmoji(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return "";
        }

        String upperCountryCode = countryCode.toUpperCase();
        int firstLetter = Character.codePointAt(upperCountryCode, 0) - 'A' + 0x1F1E6;
        int secondLetter = Character.codePointAt(upperCountryCode, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
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
        if (phoneNumberEditText.getText().toString().trim().isEmpty()) {
            phoneNumberEditText.setError(getString(R.string.phone_required));
            showAnimatorDialog(getString(R.string.phone_required));
            return;
        }

        if (!countryCodePicker.isValidFullNumber()) {
            phoneNumberEditText.setError(getString(R.string.invalid_phone));
            showAnimatorDialog(getString(R.string.invalid_phone));
            return;
        }

        fullPhoneNumber = countryCodePicker.getFullNumberWithPlus();
        otpHintTextView.setVisibility(View.VISIBLE);
        otpEditText.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
        otpEditText.requestFocus();
        Toast.makeText(this, getString(R.string.fixed_otp_message, FIXED_OTP, fullPhoneNumber), LENGTH_SHORT).show();
        loginContentLayout.post(() -> {
            applyBottomAnchoredSpacing();
            loginPhoneCardView.refreshAnimators();
        });
    }

    private void confirmOtp() {
        String otp = otpEditText.getText().toString().trim();
        if (!FIXED_OTP.equals(otp)) {
            otpEditText.setError(getString(R.string.invalid_otp));
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
