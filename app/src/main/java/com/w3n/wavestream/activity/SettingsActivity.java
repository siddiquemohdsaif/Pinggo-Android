package com.w3n.wavestream.activity;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.R;
import com.w3n.wavestream.modals.UserData;

import java.util.regex.Pattern;

public class SettingsActivity extends AppCompatActivity {
    private static final Pattern DOB_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private TextView nameValueTextView;
    private TextView dobValueTextView;
    private TextView emailValueTextView;
    private TextView phoneValueTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        nameValueTextView = findViewById(R.id.nameValueTextView);
        dobValueTextView = findViewById(R.id.dobValueTextView);
        emailValueTextView = findViewById(R.id.emailValueTextView);
        phoneValueTextView = findViewById(R.id.phoneValueTextView);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.nameRow).setOnClickListener(v -> showEditDialog(
                getString(R.string.name),
                getCurrentProfileValue("name"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                this::updateName
        ));
        findViewById(R.id.dobRow).setOnClickListener(v -> showEditDialog(
                getString(R.string.dob),
                getCurrentProfileValue("dob"),
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE,
                this::updateDob
        ));
        findViewById(R.id.emailRow).setOnClickListener(v -> showEditDialog(
                getString(R.string.email),
                getCurrentProfileValue("email"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                this::updateEmail
        ));
        findViewById(R.id.phoneRow).setOnClickListener(v -> showPhoneDialog());

        refreshUserData();
    }

    private void refreshUserData() {
        UserData userData = LoginStateManager.getInstance().getUserDataModal(this);
        UserData.ProfileData profileData = userData == null ? null : userData.getProfileData();

        nameValueTextView.setText(getValueOrDash(profileData == null ? null : profileData.getName()));
        dobValueTextView.setText(getValueOrDash(profileData == null ? null : profileData.getDob()));
        emailValueTextView.setText(getValueOrDash(profileData == null ? null : profileData.getEmail()));

        String phoneNumber = null;
        if (profileData != null && profileData.getPhoneNumber() != null) {
            phoneNumber = profileData.getPhoneNumber();
        } else if (userData != null) {
            phoneNumber = userData.getPhoneNumber();
        }
        phoneValueTextView.setText(getValueOrDash(phoneNumber));
    }

    private String getCurrentProfileValue(String field) {
        UserData userData = LoginStateManager.getInstance().getUserDataModal(this);
        UserData.ProfileData profileData = userData == null ? null : userData.getProfileData();
        if (profileData == null) {
            return "";
        }

        if ("name".equals(field)) {
            return getNonNull(profileData.getName());
        } else if ("dob".equals(field)) {
            return getNonNull(profileData.getDob());
        } else if ("email".equals(field)) {
            return getNonNull(profileData.getEmail());
        }
        return "";
    }

    private void showEditDialog(String title, String currentValue, int inputType, OnConfirmListener listener) {
        EditText editText = new EditText(this);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        editText.setText(currentValue);
        editText.setSelection(editText.getText().length());
        editText.setPadding(dp(20), dp(12), dp(20), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = editText.getText().toString().trim();
            if (value.isEmpty()) {
                editText.setError(getString(R.string.field_required));
                return;
            }
            listener.onConfirm(value, dialog);
        }));
        dialog.show();
    }

    private void showPhoneDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.phone_number)
                .setMessage(getString(R.string.phone_cannot_be_changed, phoneValueTextView.getText().toString()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateName(String name, AlertDialog dialog) {
        AppFunctionManager.getInstance().updateUserName(name, createUpdateCallback(dialog));
    }

    private void updateDob(String dob, AlertDialog dialog) {
        if (!DOB_PATTERN.matcher(dob).matches()) {
            Toast.makeText(this, R.string.invalid_dob, Toast.LENGTH_SHORT).show();
            return;
        }
        AppFunctionManager.getInstance().updateUserDob(dob, createUpdateCallback(dialog));
    }

    private void updateEmail(String email, AlertDialog dialog) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }
        AppFunctionManager.getInstance().updateUserEmail(email, createUpdateCallback(dialog));
    }

    private AppFunctionManager.Callback createUpdateCallback(AlertDialog dialog) {
        return new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                dialog.dismiss();
                refreshUserData();
                Toast.makeText(SettingsActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private String getValueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String getNonNull(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface OnConfirmListener {
        void onConfirm(String value, AlertDialog dialog);
    }
}
