package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.modals.UserData;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.common.ExitAppController;
import com.w3n.pinggo.views.common.NativeCropDialogView;
import com.w3n.pinggo.views.signup.ProfileSetupView;
import java.io.IOException;
import java.io.InputStream;

/** Owns and displays the single AAR-native profile setup view. */
public class SignUpActivity extends AppCompatActivity {
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.extra.PHONE_NUMBER";
  public static final String EXTRA_EMAIL = "com.w3n.pinggo.extra.EMAIL";
  private static final int MIN_CROP_BOX_SIZE_DP = 180;
  private static final int MAX_CROP_BOX_SIZE_DP = 420;

  private final ActivityResultLauncher<String> photoPicker =
      registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoSelected);
  private ProfileSetupView profileSetupView;
  private BlockingProgressView progressView;
  private Bitmap selectedPhoto;
  private NativeCropDialogView cropDialog;
  private String phoneNumber;
  private String email;
  private boolean requestInProgress;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    configureSystemBars();
    phoneNumber = getIntent().getStringExtra(EXTRA_PHONE_NUMBER);
    email = getIntent().getStringExtra(EXTRA_EMAIL);
    email = email == null ? "" : email.trim();
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      Toast.makeText(this, R.string.phone_required, Toast.LENGTH_SHORT).show();
      startActivity(new Intent(this, LoginActivity.class));
      finish();
      return;
    }

    profileSetupView = new ProfileSetupView(this);
    profileSetupView.setOnBackListener(() -> getOnBackPressedDispatcher().onBackPressed());
    profileSetupView.setOnPhotoListener(() -> photoPicker.launch("image/*"));
    profileSetupView.setOnNextListener(this::confirmSignUp);
    setContentView(profileSetupView);

    progressView = new BlockingProgressView(this);
    ViewGroup content = findViewById(android.R.id.content);
    content.addView(
        progressView,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewCompat.setOnApplyWindowInsetsListener(
        profileSetupView,
        (view, insets) -> {
          Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
          Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
          profileSetupView.setInsets(
              system.top,
              Math.max(system.bottom, navigation.bottom),
              keyboard.bottom,
              insets.isVisible(WindowInsetsCompat.Type.ime()));
          return insets;
        });
    ViewCompat.requestApplyInsets(profileSetupView);
    ExitAppController.install(this, null);
  }

  private void configureSystemBars() {
    Window window = getWindow();
    int color = ContextCompat.getColor(this, R.color.login_system_bar_background);
    window.setStatusBarColor(color);
    window.setNavigationBarColor(color);
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(window, window.getDecorView());
    controller.setAppearanceLightStatusBars(true);
    controller.setAppearanceLightNavigationBars(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.setNavigationBarContrastEnforced(false);
    }
  }

  private void onPhotoSelected(Uri uri) {
    if (uri == null || inactive()) return;
    Bitmap bitmap = decodeBitmap(uri);
    if (bitmap == null) {
      Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
    } else {
      showCropDialog(bitmap);
    }
  }

  private Bitmap decodeBitmap(Uri uri) {
    try (InputStream stream = getContentResolver().openInputStream(uri)) {
      return BitmapFactory.decodeStream(stream);
    } catch (IOException | SecurityException exception) {
      return null;
    }
  }

  private void showCropDialog(Bitmap bitmap) {
    cropDialog = new NativeCropDialogView(this, bitmap,
        MIN_CROP_BOX_SIZE_DP, MAX_CROP_BOX_SIZE_DP,
        new NativeCropDialogView.Listener() {
          @Override public void onRetry() { photoPicker.launch("image/*"); }
          @Override public void onConfirm(Bitmap cropped) {
            selectedPhoto = cropped;
            profileSetupView.setProfilePhoto(cropped);
          }
          @Override public void onInvalidCrop() {
            Toast.makeText(SignUpActivity.this, R.string.image_load_failed,
                Toast.LENGTH_SHORT).show();
          }
          @Override public void onDismiss() { removeCropDialog(); }
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(cropDialog,
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removeCropDialog() {
    NativeCropDialogView current = cropDialog;
    cropDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    }
    current.release();
  }

  private void confirmSignUp(String name) {
    if (requestInProgress) return;
    setRequestInProgress(true);
    AppFunctionManager.getInstance()
        .userSignUp(
            name,
            phoneNumber,
            email,
            new AppFunctionManager.Callback() {
              @Override
              public void onSuccess(Object value) {
                if (!inactive()) uploadPhotoIfNeeded();
              }

              @Override
              public void onError(String error) {
                showError(error);
              }
            });
  }

  private void uploadPhotoIfNeeded() {
    if (selectedPhoto == null) {
      completeSignUp();
      return;
    }
    AppFunctionManager.getInstance()
        .uploadProfilePhoto(
            selectedPhoto,
            new AppFunctionManager.Callback() {
              @Override
              public void onSuccess(Object value) {
                if (inactive()) return;
                UserData user =
                    value instanceof UserData
                        ? (UserData) value
                        : LoginStateManager.getInstance().getUserDataModal(SignUpActivity.this);
                saveLocalPhoto(user);
                completeSignUp();
              }

              @Override
              public void onError(String error) {
                showError(error);
              }
            });
  }

  private void saveLocalPhoto(UserData user) {
    if (user == null || selectedPhoto == null) return;
    UserData.ProfileData profile = user.getProfileData();
    if (profile == null) {
      profile = new UserData.ProfileData();
      profile.setPhoneNumber(user.getPhoneNumber());
      user.setProfileData(profile);
    }
    String path = ProfilePhotoLocalStore.save(this, selectedPhoto);
    if (path != null) profile.setLocalProfilePhotoPath(path);
    LoginStateManager.getInstance().setUserData(this, user);
  }

  private void showError(String error) {
    setRequestInProgress(false);
    if (!inactive()) Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
  }

  private void completeSignUp() {
    setRequestInProgress(false);
    if (inactive()) return;
    Toast.makeText(this, R.string.sign_up_complete, Toast.LENGTH_SHORT).show();
    Intent intent = new Intent(this, HomeActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
    finish();
  }

  private void setRequestInProgress(boolean active) {
    requestInProgress = active;
    if (progressView != null) progressView.setLoading(active);
  }

  private boolean inactive() {
    return isFinishing() || isDestroyed();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onDestroy() {
    if (profileSetupView != null) {
      profileSetupView.clearListeners();
      ViewCompat.setOnApplyWindowInsetsListener(profileSetupView, null);
    }
    if (progressView != null) progressView.setLoading(false);
    removeCropDialog();
    requestInProgress = false;
    super.onDestroy();
  }
}
