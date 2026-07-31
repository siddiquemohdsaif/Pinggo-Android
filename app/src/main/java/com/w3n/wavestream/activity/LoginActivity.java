package com.w3n.wavestream.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.R;

public class LoginActivity extends AppCompatActivity {
    private static final String FIXED_OTP = "123456";

    private CountryCodePicker countryCodePicker;
    private EditText phoneNumberEditText;
    private EditText otpEditText;
    private TextView otpHintTextView;
    private Button confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        countryCodePicker = findViewById(R.id.countryCodePicker);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        otpEditText = findViewById(R.id.otpEditText);
        otpHintTextView = findViewById(R.id.otpHintTextView);
        confirmButton = findViewById(R.id.confirmButton);
        countryCodePicker.registerCarrierNumberEditText(phoneNumberEditText);

        findViewById(R.id.getOtpButton).setOnClickListener(v -> showOtpFields());
        findViewById(R.id.signUpButton).setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        confirmButton.setOnClickListener(v -> confirmOtp());
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

        String fullPhoneNumber = countryCodePicker.getFullNumberWithPlus();
        otpHintTextView.setVisibility(View.VISIBLE);
        otpEditText.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
        otpEditText.requestFocus();
        Toast.makeText(this, getString(R.string.fixed_otp_message, FIXED_OTP, fullPhoneNumber), Toast.LENGTH_SHORT).show();
    }

    private void confirmOtp() {
        String otp = otpEditText.getText().toString().trim();
        if (!FIXED_OTP.equals(otp)) {
            otpEditText.setError(getString(R.string.invalid_otp));
            return;
        }

        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
