package com.w3n.pinggo.fragment.signup;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.activity.HomeActivity;
import com.w3n.pinggo.activity.LoginActivity;
import com.w3n.pinggo.activity.SignUpActivity;
import com.w3n.pinggo.modals.UserData;
import com.w3n.pinggo.views.CropImageView;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.signup.ProfileSetupView;

import java.io.IOException;
import java.io.InputStream;

/** Owns profile setup behavior while {@link ProfileSetupView} owns presentation. */
public class ProfileSetupFragment extends Fragment {
    private static final int CROP_BOX_SIZE_DP = 280;

    private ProfileSetupView profileSetupView;
    private BlockingProgressView blockingProgressView;
    private ActivityResultLauncher<String> photoPicker;
    private Bitmap selectedPhoto;
    private Dialog cropDialog;
    private String phoneNumber;
    private String email;
    private boolean requestInProgress;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onPhotoSelected);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        profileSetupView = new ProfileSetupView(requireContext());
        profileSetupView.setOnBackListener(() -> requireActivity()
                .getOnBackPressedDispatcher().onBackPressed());
        profileSetupView.setOnPhotoListener(() -> photoPicker.launch("image/*"));
        profileSetupView.setOnNextListener(this::confirmSignUp);
        return profileSetupView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
        phoneNumber = requireActivity().getIntent()
                .getStringExtra(SignUpActivity.EXTRA_PHONE_NUMBER);
        email = requireActivity().getIntent().getStringExtra(SignUpActivity.EXTRA_EMAIL);
        if (email == null) email = "";
        email = email.trim();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.phone_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            if (profileSetupView != null) {
                profileSetupView.setInsets(systemBars.top,
                        Math.max(systemBars.bottom, navigationBars.bottom),
                        keyboard.bottom,
                        windowInsets.isVisible(WindowInsetsCompat.Type.ime()));
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    private void onPhotoSelected(Uri uri) {
        if (uri == null || !isAdded()) return;
        Bitmap bitmap = decodeBitmap(uri);
        if (bitmap == null) {
            Toast.makeText(requireContext(), R.string.image_load_failed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        showCropDialog(bitmap);
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream stream = requireContext().getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException | SecurityException exception) {
            return null;
        }
    }

    private void showCropDialog(Bitmap bitmap) {
        Dialog dialog = new Dialog(requireContext());
        cropDialog = dialog;
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(requireContext());
        title.setText(R.string.select_crop_region);
        title.setTextColor(0xFF000E1A);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CropImageView cropView = new CropImageView(requireContext());
        cropView.setBackgroundColor(Color.BLACK);
        cropView.setCropBoxSizeDp(CROP_BOX_SIZE_DP);
        cropView.setBitmap(bitmap);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cropParams.topMargin = dp(16);
        root.addView(cropView, cropParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        actionsParams.topMargin = dp(16);
        root.addView(actions, actionsParams);

        Button retry = new Button(requireContext());
        retry.setText(R.string.retry);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        retryParams.rightMargin = dp(8);
        actions.addView(retry, retryParams);

        Button confirm = new Button(requireContext());
        confirm.setText(android.R.string.ok);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        confirmParams.leftMargin = dp(8);
        actions.addView(confirm, confirmParams);

        retry.setOnClickListener(view -> {
            dialog.dismiss();
            photoPicker.launch("image/*");
        });
        confirm.setOnClickListener(view -> {
            Bitmap cropped = cropView.getCroppedBitmap();
            if (cropped == null) {
                Toast.makeText(requireContext(), R.string.image_load_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            selectedPhoto = cropped;
            if (profileSetupView != null) profileSetupView.setProfilePhoto(cropped);
            dialog.dismiss();
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        dialog.setOnDismissListener(ignored -> {
            if (cropDialog == dialog) cropDialog = null;
        });
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void confirmSignUp(String name) {
        if (requestInProgress) return;
        setRequestInProgress(true);
        AppFunctionManager.getInstance().userSignUp(
                name, phoneNumber, email, getString(R.string.default_description),
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (isAdded()) uploadPhotoIfNeeded();
                    }

                    @Override
                    public void onError(String error) {
                        setRequestInProgress(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void uploadPhotoIfNeeded() {
        if (selectedPhoto == null) {
            completeSignUp();
            return;
        }
        AppFunctionManager.getInstance().uploadProfilePhoto(
                selectedPhoto, new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (!isAdded()) return;
                        UserData userData = object instanceof UserData
                                ? (UserData) object
                                : LoginStateManager.getInstance()
                                        .getUserDataModal(requireContext());
                        saveLocalPhoto(userData);
                        completeSignUp();
                    }

                    @Override
                    public void onError(String error) {
                        setRequestInProgress(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void saveLocalPhoto(UserData userData) {
        if (userData == null || selectedPhoto == null) return;
        UserData.ProfileData profileData = userData.getProfileData();
        if (profileData == null) {
            profileData = new UserData.ProfileData();
            profileData.setPhoneNumber(userData.getPhoneNumber());
            userData.setProfileData(profileData);
        }
        String localPath = ProfilePhotoLocalStore.save(requireContext(), selectedPhoto);
        if (localPath != null) profileData.setLocalProfilePhotoPath(localPath);
        LoginStateManager.getInstance().setUserData(requireContext(), userData);
    }

    private void completeSignUp() {
        setRequestInProgress(false);
        if (!isAdded()) return;
        Toast.makeText(requireContext(), R.string.sign_up_complete, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        if (blockingProgressView != null) blockingProgressView.setLoading(inProgress);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        if (profileSetupView != null) profileSetupView.clearListeners();
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        profileSetupView = null;
        if (blockingProgressView != null) blockingProgressView.setLoading(false);
        blockingProgressView = null;
        requestInProgress = false;
        if (cropDialog != null) cropDialog.dismiss();
        cropDialog = null;
        super.onDestroyView();
    }
}
