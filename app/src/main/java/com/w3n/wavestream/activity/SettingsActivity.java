package com.w3n.wavestream.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.wavestream.R;
import com.w3n.wavestream.data.local.LogoutDataCleaner;
import com.w3n.wavestream.modals.UserData;
import com.w3n.wavestream.views.CropImageView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class SettingsActivity extends AppCompatActivity {
    private static final int PROFILE_PHOTO_PREVIEW_SIZE_DP = 150;
    private static final int PROFILE_PHOTO_EMPTY_PADDING_DP = 32;
    private static final int PROFILE_PHOTO_CROP_BOX_SIZE_DP = 280;

    private ImageView profilePhotoImageView;
    private CropImageView cropImageView;
    private View cropContainer;
    private Button editProfilePhotoButton;
    private Button okCropButton;
    private Button retryCropButton;
    private TextView nameValueTextView;
    private TextView descriptionValueTextView;
    private TextView phoneValueTextView;
    private Uri pendingProfilePhotoUri;
    private Bitmap pendingProfilePhotoBitmap;
    private Bitmap selectedProfilePhotoBitmap;
    private ActivityResultLauncher<String> profilePhotoPicker;

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

        profilePhotoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            pendingProfilePhotoUri = uri;
            showCropView(uri);
        });

        profilePhotoImageView = findViewById(R.id.profilePhotoImageView);
        cropImageView = findViewById(R.id.cropImageView);
        cropContainer = findViewById(R.id.cropContainer);
        editProfilePhotoButton = findViewById(R.id.editProfilePhotoButton);
        okCropButton = findViewById(R.id.okCropButton);
        retryCropButton = findViewById(R.id.retryCropButton);
        nameValueTextView = findViewById(R.id.nameValueTextView);
        descriptionValueTextView = findViewById(R.id.descriptionValueTextView);
        phoneValueTextView = findViewById(R.id.phoneValueTextView);
        applyProfilePhotoSizing();
        cropImageView.setCropBoxSizeDp(PROFILE_PHOTO_CROP_BOX_SIZE_DP);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        profilePhotoImageView.setOnClickListener(v -> showProfilePhotoOptions());
        editProfilePhotoButton.setOnClickListener(v -> openProfilePhotoPicker());
        okCropButton.setOnClickListener(v -> confirmCropAndUpload());
        retryCropButton.setOnClickListener(v -> {
            hideCropView();
            openProfilePhotoPicker();
        });
        findViewById(R.id.nameRow).setOnClickListener(v -> showEditDialog(
                getString(R.string.name),
                getCurrentProfileValue("name"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                this::updateName
        ));
        findViewById(R.id.descriptionRow).setOnClickListener(v -> showEditDialog(
                getString(R.string.description),
                getCurrentProfileValue("description"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                this::updateDescription
        ));
        findViewById(R.id.phoneRow).setOnClickListener(v -> showPhoneDialog());
        findViewById(R.id.logOutButton).setOnClickListener(v -> logOut());

        refreshUserData();
    }

    private void logOut() {
        findViewById(R.id.logOutButton).setEnabled(false);
        new Thread(() -> {
            LogoutDataCleaner.clear(SettingsActivity.this);
            LoginStateManager.getInstance().logOut(SettingsActivity.this);
            runOnUiThread(() -> {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }).start();
    }

    private void refreshUserData() {
        UserData userData = LoginStateManager.getInstance().getUserDataModal(this);
        UserData.ProfileData profileData = userData == null ? null : userData.getProfileData();

        nameValueTextView.setText(getValueOrDash(profileData == null ? null : profileData.getName()));
        descriptionValueTextView.setText(getValueOrDash(profileData == null ? null : profileData.getDescription()));
        loadProfilePhoto(profileData);

        String phoneNumber = null;
        if (profileData != null && profileData.getPhoneNumber() != null) {
            phoneNumber = profileData.getPhoneNumber();
        } else if (userData != null) {
            phoneNumber = userData.getPhoneNumber();
        }
        phoneValueTextView.setText(getValueOrDash(phoneNumber));
    }

    private void applyProfilePhotoSizing() {
        int previewSize = dp(PROFILE_PHOTO_PREVIEW_SIZE_DP);
        profilePhotoImageView.getLayoutParams().width = previewSize;
        profilePhotoImageView.getLayoutParams().height = previewSize;
        profilePhotoImageView.requestLayout();
    }

    private void openProfilePhotoPicker() {
        profilePhotoPicker.launch("image/*");
    }

    private void showCropView(Uri uri) {
        pendingProfilePhotoBitmap = decodeBitmap(uri);
        if (pendingProfilePhotoBitmap == null) {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        cropImageView.setBitmap(pendingProfilePhotoBitmap);
        cropContainer.setVisibility(View.VISIBLE);
    }

    private void confirmCropAndUpload() {
        Bitmap croppedBitmap = cropImageView.getCroppedBitmap();
        if (croppedBitmap == null) {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedProfilePhotoBitmap = croppedBitmap;
        profilePhotoImageView.setPadding(0, 0, 0, 0);
        profilePhotoImageView.setImageBitmap(selectedProfilePhotoBitmap);
        hideCropView();
        AppFunctionManager.getInstance().uploadProfilePhoto(selectedProfilePhotoBitmap, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                UserData userData = object instanceof UserData
                        ? (UserData) object
                        : LoginStateManager.getInstance().getUserDataModal(SettingsActivity.this);
                saveLocalProfilePhoto(userData);
                refreshUserData();
                Toast.makeText(SettingsActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showProfilePhotoOptions() {
        UserData userData = LoginStateManager.getInstance().getUserDataModal(this);
        UserData.ProfileData profileData = userData == null ? null : userData.getProfileData();
        if (profileData == null || (isEmpty(profileData.getLocalProfilePhotoPath()) && isEmpty(profileData.getProfilePhotoUrl()))) {
            openProfilePhotoPicker();
            return;
        }

        new AlertDialog.Builder(this)
                .setItems(new CharSequence[]{getString(R.string.edit_profile_photo)}, (dialog, which) -> openProfilePhotoPicker())
                .show();
    }

    private void hideCropView() {
        cropContainer.setVisibility(View.GONE);
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            return null;
        }
    }

    private void loadProfilePhoto(UserData.ProfileData profileData) {
        String localPath = profileData == null ? null : profileData.getLocalProfilePhotoPath();
        if (!isEmpty(localPath) && new File(localPath).exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(localPath);
            if (bitmap != null) {
                profilePhotoImageView.setPadding(0, 0, 0, 0);
                profilePhotoImageView.setImageBitmap(bitmap);
                editProfilePhotoButton.setVisibility(View.GONE);
                return;
            }
        }

        String profilePhotoUrl = profileData == null ? null : profileData.getProfilePhotoUrl();
        if (isEmpty(profilePhotoUrl)) {
            profilePhotoImageView.setPadding(
                    dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                    dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                    dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                    dp(PROFILE_PHOTO_EMPTY_PADDING_DP)
            );
            profilePhotoImageView.setImageResource(android.R.drawable.ic_menu_camera);
            editProfilePhotoButton.setVisibility(View.VISIBLE);
            return;
        }

        new Thread(() -> {
            try (InputStream inputStream = new URL(profilePhotoUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                runOnUiThread(() -> {
                    profilePhotoImageView.setPadding(0, 0, 0, 0);
                    profilePhotoImageView.setImageBitmap(bitmap);
                    editProfilePhotoButton.setVisibility(View.GONE);
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveLocalProfilePhoto(UserData userData) {
        if (userData == null || selectedProfilePhotoBitmap == null) {
            return;
        }

        UserData.ProfileData profileData = userData.getProfileData();
        if (profileData == null) {
            profileData = new UserData.ProfileData();
            profileData.setPhoneNumber(userData.getPhoneNumber());
            userData.setProfileData(profileData);
        }

        String localPath = ProfilePhotoLocalStore.save(this, selectedProfilePhotoBitmap);
        if (localPath != null) {
            profileData.setLocalProfilePhotoPath(localPath);
        }
        LoginStateManager.getInstance().setUserData(this, userData);
    }

    private String getCurrentProfileValue(String field) {
        UserData userData = LoginStateManager.getInstance().getUserDataModal(this);
        UserData.ProfileData profileData = userData == null ? null : userData.getProfileData();
        if (profileData == null) {
            return "";
        }

        if ("name".equals(field)) {
            return getNonNull(profileData.getName());
        } else if ("description".equals(field)) {
            return getNonNull(profileData.getDescription());
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

    private void updateDescription(String description, AlertDialog dialog) {
        AppFunctionManager.getInstance().updateUserDescription(description, createUpdateCallback(dialog));
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

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
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
