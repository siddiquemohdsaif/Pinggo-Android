package com.w3n.wavestream.activity;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

public class LoginActivity extends AppCompatActivity {
    private static final String FIXED_OTP = "123456";

    private CountryCodePicker countryCodePicker;
    private EditText phoneNumberEditText;
    private EditText otpEditText;
    private TextView otpHintTextView;
    private TextView phoneCodeTextView;
    private TextView countryFlagTextView;
    private Button confirmButton;
    private String fullPhoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        View loginScrollView = findViewById(R.id.loginScrollView);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int horizontalPadding = Math.round(getResources().getDisplayMetrics().density * 26);
            loginScrollView.setPadding(horizontalPadding, systemBars.top, horizontalPadding, systemBars.bottom);
            return insets;
        });

        countryCodePicker = findViewById(R.id.countryCodePicker);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        otpEditText = findViewById(R.id.otpEditText);
        otpHintTextView = findViewById(R.id.otpHintTextView);
        phoneCodeTextView = findViewById(R.id.phoneCodeTextView);
        countryFlagTextView = findViewById(R.id.countryFlagTextView);
        confirmButton = findViewById(R.id.confirmButton);
        countryCodePicker.registerCarrierNumberEditText(phoneNumberEditText);
        updateSelectedCountryViews();
        countryCodePicker.setOnCountryChangeListener(this::updateSelectedCountryViews);
        tintTermsLinks();

        findViewById(R.id.getOtpButton).setOnClickListener(v -> showOtpFields());
        confirmButton.setOnClickListener(v -> confirmOtp());
    }

    private void updateSelectedCountryViews() {
        phoneCodeTextView.setText(countryCodePicker.getSelectedCountryCodeWithPlus());
        countryFlagTextView.setText(countryCodeToFlagEmoji(countryCodePicker.getSelectedCountryNameCode()));
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

    private void tintTermsLinks() {
        TextView termsTextView = findViewById(R.id.termsTextView);
        SpannableString termsText = new SpannableString(getString(R.string.terms_notice));
        int accentColor = ContextCompat.getColor(this, R.color.pinggo_action);
        setTextColorSpan(termsText, "Terms of Service", accentColor);
        setTextColorSpan(termsText, "Privacy Policy", accentColor);
        termsTextView.setText(termsText);
    }

    private void setTextColorSpan(SpannableString text, String target, int color) {
        int start = text.toString().indexOf(target);
        if (start >= 0) {
            text.setSpan(
                    new ForegroundColorSpan(color),
                    start,
                    start + target.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private void showOtpFields() {
        if (phoneNumberEditText.getText().toString().trim().isEmpty()) {
            phoneNumberEditText.setError(getString(R.string.phone_required));
            return;
        }

        if (!countryCodePicker.isValidFullNumber()) {
            phoneNumberEditText.setError(getString(R.string.invalid_phone));
            return;
        }

        fullPhoneNumber = countryCodePicker.getFullNumberWithPlus();
        otpHintTextView.setVisibility(View.VISIBLE);
        otpEditText.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
        otpEditText.requestFocus();
        Toast.makeText(this, getString(R.string.fixed_otp_message, FIXED_OTP, fullPhoneNumber), LENGTH_SHORT).show();
    }

    private void confirmOtp() {
        String otp = otpEditText.getText().toString().trim();
        if (!FIXED_OTP.equals(otp)) {
            otpEditText.setError(getString(R.string.invalid_otp));
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
}
