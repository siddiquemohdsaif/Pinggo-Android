package com.w3n.wavestream.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.R;

public class SignUpActivity extends AppCompatActivity {
    private EditText nameEditText;
    private EditText emailEditText;
    private EditText dobEditText;
    private Button nextButton;
    private Button confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        dobEditText = findViewById(R.id.dobEditText);
        nextButton = findViewById(R.id.nextButton);
        confirmButton = findViewById(R.id.signUpConfirmButton);

        nextButton.setOnClickListener(v -> showEmailStep());
        dobEditText.setOnClickListener(v -> showDatePicker());
        confirmButton.setOnClickListener(v -> confirmSignUp());
    }

    private void showEmailStep() {
        if (nameEditText.getText().toString().trim().isEmpty()) {
            nameEditText.setError(getString(R.string.name_required));
            return;
        }

        emailEditText.setVisibility(View.VISIBLE);
        dobEditText.setVisibility(View.VISIBLE);
        confirmButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.GONE);
        emailEditText.requestFocus();
    }

    private void confirmSignUp() {
        if (emailEditText.getText().toString().trim().isEmpty()) {
            emailEditText.setError(getString(R.string.email_required));
            return;
        }

        if (dobEditText.getText().toString().trim().isEmpty()) {
            dobEditText.setError(getString(R.string.dob_required));
            return;
        }

        Toast.makeText(this, R.string.sign_up_complete, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
                    dobEditText.setText(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }
}
