package com.w3n.pinggo.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
import com.w3n.pinggo.views.CropImageView;
import com.w3n.pinggo.views.settings.SettingsView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class SettingsActivity extends AppCompatActivity implements SettingsView.Listener {
  private SettingsView settingsView;
  private ActivityResultLauncher<String> picker;
  private Bitmap selectedPhoto;
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
    Dialog dialog = new Dialog(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(20), dp(20), dp(20));
    root.setBackgroundColor(Color.WHITE);
    TextView title = new TextView(this);
    title.setText(R.string.select_crop_region);
    title.setTextSize(22);
    title.setGravity(Gravity.CENTER);
    root.addView(title, new LinearLayout.LayoutParams(-1, -2));
    CropImageView crop = new CropImageView(this);
    crop.setCropBoxSizeRangeDp(180, 420);
    crop.setBitmap(bitmap);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1);
    cp.topMargin = dp(12);
    root.addView(crop, cp);
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    Button retry = new Button(this);
    retry.setText(R.string.retry);
    Button ok = new Button(this);
    ok.setText(android.R.string.ok);
    actions.addView(retry, new LinearLayout.LayoutParams(0, dp(56), 1));
    actions.addView(ok, new LinearLayout.LayoutParams(0, dp(56), 1));
    root.addView(actions);
    retry.setOnClickListener(
        v -> {
          dialog.dismiss();
          picker.launch("image/*");
        });
    ok.setOnClickListener(
        v -> {
          Bitmap value = crop.getCroppedBitmap();
          if (value == null) {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            return;
          }
          dialog.dismiss();
          upload(value);
        });
    dialog.setContentView(root);
    Window w = dialog.getWindow();
    if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
    dialog.show();
    if (w != null)
      w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
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
    new AlertDialog.Builder(this)
        .setTitle(R.string.phone_number)
        .setMessage(getString(R.string.phone_cannot_be_changed, phone))
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  private void edit(String title, String current, int type, ValueHandler handler) {
    EditText field = new EditText(this);
    field.setInputType(type);
    field.setSingleLine(true);
    field.setText(current);
    field.setSelection(current.length());
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(field)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    dialog.setOnShowListener(
        x ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String value = field.getText().toString().trim();
                      if (value.isEmpty()) {
                        field.setError(getString(R.string.field_required));
                        return;
                      }
                      dialog.dismiss();
                      handler.accept(value);
                    }));
    dialog.show();
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

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onDestroy() {
    if (settingsView != null) settingsView.release();
    settingsView = null;
    super.onDestroy();
  }

  private interface ValueHandler {
    void accept(String value);
  }
}
