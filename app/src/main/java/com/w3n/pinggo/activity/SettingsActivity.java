package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.LogoutDataCleaner;
import com.w3n.pinggo.modals.UserData;
import com.w3n.pinggo.views.common.NativeCropDialogView;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import com.w3n.pinggo.views.settings.SettingsView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class SettingsActivity extends AppCompatActivity implements SettingsView.Listener {
  private SettingsView settingsView;
  private ActivityResultLauncher<String> picker;
  private Bitmap selectedPhoto;
  private NativeCropDialogView cropDialog;
  private NativePromptDialogView promptDialog;
  private String phone = "";

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    settingsView = new SettingsView(this, this);
    setContentView(settingsView);
    ViewCompat.setOnApplyWindowInsetsListener(
        settingsView,
        (v, i) -> {
          Insets b = i.getInsets(WindowInsetsCompat.Type.systemBars());
          settingsView.setInsets(b.top, b.bottom);
          return i;
        });
    ViewCompat.requestApplyInsets(settingsView);
    picker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::photoSelected);
    refresh();
  }

  private void refresh() {
    UserData user = LoginStateManager.getInstance().getUserDataModal(this);
    UserData.ProfileData p = user == null ? null : user.getProfileData();
    phone =
        p != null && p.getPhoneNumber() != null
            ? p.getPhoneNumber()
            : user == null ? "" : user.getPhoneNumber();
    settingsView.setValues(p == null ? null : p.getName(), phone);
    loadPhoto(p);
  }

  private void loadPhoto(UserData.ProfileData p) {
    String local = p == null ? null : p.getLocalProfilePhotoPath();
    if (local != null && new File(local).exists()) {
      Bitmap b = BitmapFactory.decodeFile(local);
      if (b != null) {
        settingsView.setProfilePhoto(b);
        return;
      }
    }
    String url = p == null ? null : p.getProfilePhotoUrl();
    if (url == null || url.trim().isEmpty()) {
      settingsView.setProfilePhoto(null);
      return;
    }
    new Thread(
            () -> {
              try (InputStream in = new URL(url).openStream()) {
                Bitmap b = BitmapFactory.decodeStream(in);
                runOnUiThread(() -> settingsView.setProfilePhoto(b));
              } catch (IOException e) {
                runOnUiThread(
                    () ->
                        Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT)
                            .show());
              }
            })
        .start();
  }

  @Override
  public void onBack() {
    finish();
  }

  @Override
  public void onPhoto() {
    picker.launch("image/*");
  }

  private void photoSelected(Uri uri) {
    if (uri == null) return;
    Bitmap bitmap = decode(uri);
    if (bitmap == null) {
      Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
      return;
    }
    showCrop(bitmap);
  }

  private Bitmap decode(Uri uri) {
    try (InputStream in = getContentResolver().openInputStream(uri)) {
      return BitmapFactory.decodeStream(in);
    } catch (IOException | SecurityException e) {
      return null;
    }
  }

  private void showCrop(Bitmap bitmap) {
    cropDialog = new NativeCropDialogView(this, bitmap, 180, 420,
        new NativeCropDialogView.Listener() {
          @Override public void onRetry() { picker.launch("image/*"); }
          @Override public void onConfirm(Bitmap cropped) { upload(cropped); }
          @Override public void onInvalidCrop() {
            Toast.makeText(SettingsActivity.this, R.string.image_load_failed,
                Toast.LENGTH_SHORT).show();
          }
          @Override public void onDismiss() { removeCropDialog(); }
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(cropDialog,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removeCropDialog() {
    NativeCropDialogView current = cropDialog;
    cropDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    current.release();
  }

  private void upload(Bitmap bitmap) {
    selectedPhoto = bitmap;
    settingsView.setProfilePhoto(bitmap);
    AppFunctionManager.getInstance()
        .uploadProfilePhoto(
            bitmap,
            new AppFunctionManager.Callback() {
              @Override
              public void onSuccess(Object o) {
                UserData user =
                    o instanceof UserData
                        ? (UserData) o
                        : LoginStateManager.getInstance().getUserDataModal(SettingsActivity.this);
                saveLocal(user);
                refresh();
                Toast.makeText(SettingsActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT)
                    .show();
              }

              @Override
              public void onError(String e) {
                Toast.makeText(SettingsActivity.this, e, Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void saveLocal(UserData user) {
    if (user == null || selectedPhoto == null) return;
    UserData.ProfileData p = user.getProfileData();
    if (p == null) {
      p = new UserData.ProfileData();
      p.setPhoneNumber(user.getPhoneNumber());
      user.setProfileData(p);
    }
    String path = ProfilePhotoLocalStore.save(this, selectedPhoto);
    if (path != null) p.setLocalProfilePhotoPath(path);
    LoginStateManager.getInstance().setUserData(this, user);
  }

  @Override
  public void onName() {
    edit(
        getString(R.string.name),
        current("name"),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
        value -> AppFunctionManager.getInstance().updateUserName(value, updateCallback()));
  }

  @Override
  public void onPhone() {
    showPrompt(NativePromptDialogView.message(this, getString(R.string.phone_number),
        getString(R.string.phone_cannot_be_changed, phone), this::removePrompt));
  }

  private void edit(String title, String current, int type, ValueHandler handler) {
    showPrompt(NativePromptDialogView.input(this, title, current, type, value -> {
      if (value.isEmpty()) {
        Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
        return false;
      }
      handler.accept(value);
      return true;
    }, this::removePrompt));
  }

  private void showPrompt(NativePromptDialogView prompt) {
    removePrompt();
    promptDialog = prompt;
    ((ViewGroup) findViewById(android.R.id.content)).addView(prompt,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removePrompt() {
    NativePromptDialogView current = promptDialog;
    promptDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    current.release();
  }

  private AppFunctionManager.Callback updateCallback() {
    return new AppFunctionManager.Callback() {
      @Override
      public void onSuccess(Object o) {
        refresh();
        Toast.makeText(SettingsActivity.this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
      }

      @Override
      public void onError(String e) {
        Toast.makeText(SettingsActivity.this, e, Toast.LENGTH_SHORT).show();
      }
    };
  }

  private String current(String field) {
    UserData u = LoginStateManager.getInstance().getUserDataModal(this);
    UserData.ProfileData p = u == null ? null : u.getProfileData();
    if (p == null) return "";
    String v = p.getName();
    return v == null ? "" : v;
  }

  @Override
  public void onLogout() {
    settingsView.setLoading(true);
    new Thread(
            () -> {
              LogoutDataCleaner.clear(this);
              LoginStateManager.getInstance().logOut(this);
              runOnUiThread(
                  () -> {
                    Intent i = new Intent(this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                  });
            })
        .start();
  }

  @Override
  protected void onDestroy() {
    removeCropDialog();
    removePrompt();
    if (settingsView != null) settingsView.release();
    settingsView = null;
    super.onDestroy();
  }

  private interface ValueHandler {
    void accept(String value);
  }
}
