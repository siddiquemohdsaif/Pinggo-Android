package com.w3n.wavestream.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.wavestream.R;
import com.w3n.wavestream.modals.UserData;
import com.w3n.wavestream.views.CropImageView;

import java.io.IOException;
import java.io.InputStream;

public class SignUpActivity extends AppCompatActivity {
    public static final String EXTRA_PHONE_NUMBER = "com.w3n.wavestream.extra.PHONE_NUMBER";
    private static final int PROFILE_PHOTO_PREVIEW_SIZE_DP = 180;
    private static final int PROFILE_PHOTO_EMPTY_PADDING_DP = 38;
    private static final int PROFILE_PHOTO_CROP_BOX_SIZE_DP = 280;

    private EditText nameEditText;
    private EditText descriptionEditText;
    private ImageView profilePhotoImageView;
    private CropImageView cropImageView;
    private View cropContainer;
    private Button confirmButton;
    private Button selectProfilePhotoButton;
    private Button okCropButton;
    private Button retryCropButton;
    private String phoneNumber;
    private Uri selectedProfilePhotoUri;
    private Uri pendingProfilePhotoUri;
    private Bitmap pendingProfilePhotoBitmap;
    private Bitmap selectedProfilePhotoBitmap;
    private ActivityResultLauncher<String> profilePhotoPicker;

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
        nameEditText = findViewById(R.id.nameEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        selectProfilePhotoButton = findViewById(R.id.selectProfilePhotoButton);
        okCropButton = findViewById(R.id.okCropButton);
        retryCropButton = findViewById(R.id.retryCropButton);
        confirmButton = findViewById(R.id.signUpConfirmButton);
        phoneNumber = getIntent().getStringExtra(EXTRA_PHONE_NUMBER);
        applyProfilePhotoSizing();
        cropImageView.setCropBoxSizeDp(PROFILE_PHOTO_CROP_BOX_SIZE_DP);

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Toast.makeText(this, R.string.phone_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        profilePhotoImageView.setOnClickListener(v -> openProfilePhotoPicker());
        selectProfilePhotoButton.setOnClickListener(v -> openProfilePhotoPicker());
        okCropButton.setOnClickListener(v -> confirmCrop());
        retryCropButton.setOnClickListener(v -> {
            hideCropView();
            openProfilePhotoPicker();
        });
        confirmButton.setOnClickListener(v -> confirmSignUp());
    }

    private void applyProfilePhotoSizing() {
        int previewSize = dp(PROFILE_PHOTO_PREVIEW_SIZE_DP);
        profilePhotoImageView.getLayoutParams().width = previewSize;
        profilePhotoImageView.getLayoutParams().height = previewSize;
        profilePhotoImageView.setPadding(
                dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                dp(PROFILE_PHOTO_EMPTY_PADDING_DP),
                dp(PROFILE_PHOTO_EMPTY_PADDING_DP)
        );
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

    private void confirmCrop() {
        Bitmap croppedBitmap = cropImageView.getCroppedBitmap();
        if (croppedBitmap == null) {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedProfilePhotoUri = pendingProfilePhotoUri;
        selectedProfilePhotoBitmap = croppedBitmap;
        profilePhotoImageView.setPadding(0, 0, 0, 0);
        profilePhotoImageView.setImageBitmap(selectedProfilePhotoBitmap);
        hideCropView();
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

    private void confirmSignUp() {
        if (nameEditText.getText().toString().trim().isEmpty()) {
            nameEditText.setError(getString(R.string.name_required));
            return;
        }

        String name = nameEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();
        if (description.isEmpty()) {
            descriptionEditText.setError(getString(R.string.description_required));
            return;
        }

        AppFunctionManager.getInstance().userSignUp(name, phoneNumber, description, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                uploadProfilePhotoIfNeeded();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SignUpActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadProfilePhotoIfNeeded() {
        if (selectedProfilePhotoBitmap == null) {
            completeSignUp();
            return;
        }

        AppFunctionManager.getInstance().uploadProfilePhoto(selectedProfilePhotoBitmap, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                UserData userData = object instanceof UserData
                        ? (UserData) object
                        : LoginStateManager.getInstance().getUserDataModal(SignUpActivity.this);
                saveLocalProfilePhoto(userData);
                completeSignUp();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SignUpActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
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

    private void completeSignUp() {
        Toast.makeText(SignUpActivity.this, R.string.sign_up_complete, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(SignUpActivity.this, HomeActivity.class));
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
