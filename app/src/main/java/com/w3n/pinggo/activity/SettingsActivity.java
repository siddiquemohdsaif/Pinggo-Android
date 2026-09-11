package com.w3n.pinggo.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;
import com.w3n.pinggo.Database.CloudFunction.Utils.DeviceIdentityManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ProfilePhotoLocalStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.SessionLogoutManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.modals.UserData;
import com.w3n.pinggo.views.common.NativeCropDialogView;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import com.w3n.pinggo.views.settings.SettingsView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

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
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
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
    picker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::photoSelected);
    refresh();
  }

  private void refresh() {
    UserData user = LoginStateManager.getInstance().getUserDataModal(this);
    UserData.ProfileData p = user == null ? null : user.getProfileData();
    phone = p != null && p.getPhoneNumber() != null
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
                () -> Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT)
                    .show());
          }
        })
        .start();
  }

  @Override
  public void onBack() {
    if (settingsView.isProfileMode()) {
      settingsView.showSettings();
      refresh();
      return;
    }
    finish();
  }

  @Override
  public void onBackPressed() {
    onBack();
  }

  @Override
  public void onEditProfile() {
    settingsView.showProfile();
    refresh();
  }

  @Override
  public void onLinkedDevices() {
    startActivity(new Intent(this, LinkedDevicesActivity.class));
  }

  @Override
  public void onAccount() {
    List<String> actions = new ArrayList<>();
    actions.add("Log out");
    actions.add("Delete account");
    showPrompt(NativePromptDialogView.actions(this, actions, index -> {
      if (index == 0) {
        showPrompt(NativePromptDialogView.confirm(this, "Log out?",
            "You will need to sign in again to use this account.", "Log out",
            this::onLogout, this::removePrompt));
      } else {
        confirmAccountPhone();
      }
    }, this::removePrompt));
  }

  private void confirmAccountPhone() {
    showPrompt(NativePromptDialogView.input(this,
        "Enter your phone number to permanently delete your account", "",
        InputType.TYPE_CLASS_PHONE, value -> {
          if (!phoneDigits(value).equals(phoneDigits(phone))) {
            Toast.makeText(this, "Phone number does not match this account.", Toast.LENGTH_LONG).show();
            return false;
          }
          deleteAccount(value);
          return true;
        }, this::removePrompt));
  }

  private void deleteAccount(String confirmedPhone) {
    settingsView.setLoading(true);
    new Thread(() -> {
      try {
        LoginStateManager login = LoginStateManager.getInstance();
        String token = login.getUID(this) + "_" + login.getENC(this);
        AppRestAPI api = new APIAuth(token).getRetrofit().create(AppRestAPI.class);
        JsonObject body = new JsonObject();
        body.addProperty("phoneNumber", phoneDigits(confirmedPhone));
        retrofit2.Response<JsonObject> response = api.deleteAccount(body).execute();
        if (response.isSuccessful()) {
          SessionLogoutManager.forceLogout(this, "Your Pinggo account was deleted.");
        } else {
          runOnUiThread(() -> deletionFailed("Account deletion failed. Please try again."));
        }
      } catch (Exception error) {
        runOnUiThread(() -> deletionFailed("Account deletion failed. Check your connection and try again."));
      }
    }, "pinggo-account-delete").start();
  }

  private void deletionFailed(String message) {
    settingsView.setLoading(false);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private static String phoneDigits(String value) {
    return value == null ? "" : value.replaceAll("[^0-9]", "");
  }

  @Override
  public void onPrivacy() {
    showPrompt(NativePromptDialogView.message(this, "Blocked accounts", "Loading…",
        this::removePrompt));
    AppFunctionManager.getInstance().getBlockedAccounts(phone, new AppFunctionManager.Callback() {
      @Override public void onSuccess(Object value) {
        JsonObject root = value instanceof JsonObject ? (JsonObject) value : null;
        JsonArray accounts = root != null && root.has("blockedAccounts")
            && root.get("blockedAccounts").isJsonArray()
            ? root.getAsJsonArray("blockedAccounts") : new JsonArray();
        List<String> labels = new ArrayList<>();
        for (JsonElement element : accounts) {
          if (element == null || !element.isJsonObject()) continue;
          JsonObject account = element.getAsJsonObject();
          String id = account.has("userId") ? account.get("userId").getAsString() : "";
          if (!id.trim().isEmpty()) labels.add(DeviceContactResolver.cachedNameOrPhone(id));
        }
        String message = labels.isEmpty() ? "No blocked accounts."
            : android.text.TextUtils.join("\n", labels);
        runOnUiThread(() -> showPrompt(NativePromptDialogView.message(
            SettingsActivity.this, "Blocked accounts", message, SettingsActivity.this::removePrompt)));
      }

      @Override public void onError(String error) {
        runOnUiThread(() -> showPrompt(NativePromptDialogView.message(SettingsActivity.this,
            "Blocked accounts", error == null ? "Unable to load blocked accounts." : error,
            SettingsActivity.this::removePrompt)));
      }
    });
  }

  @Override
  public void onInvite() {
    Intent share = new Intent(Intent.ACTION_SEND);
    share.setType("text/plain");
    share.putExtra(Intent.EXTRA_SUBJECT, "Join me on PingGo");
    share.putExtra(Intent.EXTRA_TEXT, "Join me on PingGo for private messages and calls.");
    startActivity(Intent.createChooser(share, "Invite a friend"));
  }

  @Override
  public void onPhoto() {
    picker.launch("image/*");
  }

  private void photoSelected(Uri uri) {
    if (uri == null)
      return;
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
          @Override
          public void onRetry() {
            picker.launch("image/*");
          }

          @Override
          public void onConfirm(Bitmap cropped) {
            upload(cropped);
          }

          @Override
          public void onInvalidCrop() {
            Toast.makeText(SettingsActivity.this, R.string.image_load_failed,
                Toast.LENGTH_SHORT).show();
          }

          @Override
          public void onDismiss() {
            removeCropDialog();
          }
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(cropDialog,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removeCropDialog() {
    NativeCropDialogView current = cropDialog;
    cropDialog = null;
    if (current == null)
      return;
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
                UserData user = o instanceof UserData
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
    if (user == null || selectedPhoto == null)
      return;
    UserData.ProfileData p = user.getProfileData();
    if (p == null) {
      p = new UserData.ProfileData();
      p.setPhoneNumber(user.getPhoneNumber());
      user.setProfileData(p);
    }
    String path = ProfilePhotoLocalStore.save(this, selectedPhoto);
    if (path != null)
      p.setLocalProfilePhotoPath(path);
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
    if (current == null)
      return;
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
    if (p == null)
      return "";
    String v = p.getName();
    return v == null ? "" : v;
  }

  @Override
  public void onLogout() {
    settingsView.setLoading(true);
    new Thread(
        () -> {
          try {
            LoginStateManager login = LoginStateManager.getInstance();
            String token = login.getUID(this) + "_" + login.getENC(this);
            AppRestAPI api = new APIAuth(token).getRetrofit().create(AppRestAPI.class);
            String deviceId = DeviceIdentityManager.getDeviceId(this);
            retrofit2.Response<JsonObject> response;
            if (login.isCompanionDevice(this)) {
              response = api.unlinkDevice(deviceId).execute();
            } else {
              JsonObject body = new JsonObject();
              body.addProperty("deviceId", deviceId);
              response = api.logoutAccount(body).execute();
            }
            if (response.isSuccessful() || response.code() == 401 || response.code() == 404) {
              SessionLogoutManager.forceLogout(this);
            } else {
              runOnUiThread(() -> {
                settingsView.setLoading(false);
                Toast.makeText(this, R.string.logout_failed, Toast.LENGTH_LONG).show();
              });
            }
          } catch (Exception error) {
            runOnUiThread(() -> {
              settingsView.setLoading(false);
              Toast.makeText(this, R.string.logout_failed, Toast.LENGTH_LONG).show();
            });
          }
        })
        .start();
  }

  @Override
  protected void onDestroy() {
    removeCropDialog();
    removePrompt();
    if (settingsView != null)
      settingsView.release();
    settingsView = null;
    super.onDestroy();
  }

  private interface ValueHandler {
    void accept(String value);
  }
}
