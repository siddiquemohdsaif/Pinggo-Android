package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import java.util.List;

/** Group details step, displayed after choosing members. */
public final class CreateGroupView extends LinearLayout {
  private final EditText name;
  private final ImageView photo;
  private final Button choosePhoto, create, back;
  private final SwitchCompat adminOnly;
  private final LinearLayout memberList;
  private final TextView memberCount;
  private final Button addMember, removePhoto;
  private boolean busy;
  private java.util.function.IntConsumer removeMember;

  public CreateGroupView(Context context, List<String> members, Runnable onBack,
      Runnable onPhoto, Runnable onCreate, Runnable onAddMember, Runnable onRemovePhoto,
      java.util.function.IntConsumer onRemoveMember) {
    super(context);
    setOrientation(VERTICAL);
    setBackgroundColor(0xFFF7F9FB);
    back = new Button(context);
    back.setText("‹  New group");
    back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    back.setOnClickListener(v -> onBack.run());
    addView(back, new LayoutParams(-1, dp(56)));
    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    addView(scroll, new LayoutParams(-1, 0, 1));
    LinearLayout body = new LinearLayout(context);
    body.setOrientation(VERTICAL);
    body.setPadding(dp(24), dp(16), dp(24), dp(24));
    scroll.addView(body);
    photo = new ImageView(context);
    photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
    photo.setImageResource(android.R.drawable.ic_menu_camera);
    photo.setContentDescription("Group profile photo");
    GradientDrawable circle = new GradientDrawable();
    circle.setColor(0xFFD9F1F7);
    circle.setCornerRadius(dp(64));
    photo.setBackground(circle);
    photo.setClipToOutline(true);
    LayoutParams avatar = new LayoutParams(dp(112), dp(112));
    avatar.gravity = Gravity.CENTER_HORIZONTAL;
    body.addView(photo, avatar);
    photo.setOnClickListener(v -> onPhoto.run());
    choosePhoto = new Button(context);
    choosePhoto.setText("Add group photo");
    choosePhoto.setOnClickListener(v -> onPhoto.run());
    body.addView(choosePhoto, new LayoutParams(-1, dp(52)));
    removePhoto = new Button(context);
    removePhoto.setText("Remove group photo");
    removePhoto.setVisibility(GONE);
    removePhoto.setOnClickListener(v -> onRemovePhoto.run());
    body.addView(removePhoto, new LayoutParams(-1, dp(48)));
    body.addView(label("Group name", 14));
    name = new EditText(context);
    name.setSingleLine(true);
    name.setHint("Enter group name");
    name.setTextSize(18);
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
    name.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    body.addView(name, new LayoutParams(-1, dp(56)));
    adminOnly = new SwitchCompat(context);
    adminOnly.setText("Only admins can message");
    adminOnly.setTextSize(16);
    body.addView(adminOnly, new LayoutParams(-1, dp(72)));
    memberCount = label("", 16);
    body.addView(memberCount);
    addMember = new Button(context);
    addMember.setText("Add members");
    addMember.setOnClickListener(v -> onAddMember.run());
    body.addView(addMember, new LayoutParams(-1, dp(48)));
    memberList = new LinearLayout(context);
    memberList.setOrientation(VERTICAL);
    body.addView(memberList, new LayoutParams(-1, -2));
    removeMember = onRemoveMember;
    setMembers(members);
    create = new Button(context);
    create.setText("Create group");
    create.setTextColor(Color.WHITE);
    create.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF019CC4));
    create.setOnClickListener(v -> onCreate.run());
    LayoutParams action = new LayoutParams(-1, dp(56));
    action.setMargins(dp(24), dp(8), dp(24), dp(16));
    addView(create, action);
  }

  public String groupName() { return name.getText().toString().trim(); }
  public boolean adminsOnly() { return adminOnly.isChecked(); }
  public void showNameError() { name.setError("Enter a group name"); name.requestFocus(); }
  public void setPhoto(Bitmap bitmap) {
    photo.setImageBitmap(bitmap);
    choosePhoto.setText("Change group photo");
    removePhoto.setVisibility(VISIBLE);
  }
  public void clearPhoto() {
    photo.setImageResource(android.R.drawable.ic_menu_camera);
    choosePhoto.setText("Add group photo");
    removePhoto.setVisibility(GONE);
  }
  public void setMembers(List<String> members) {
    memberCount.setText("Selected members (" + members.size() + ")");
    memberList.removeAllViews();
    for (int i = 0; i < members.size(); i++) {
      final int index = i;
      LinearLayout row = new LinearLayout(getContext());
      row.setGravity(Gravity.CENTER_VERTICAL);
      TextView title = label(members.get(i), 16);
      title.setPadding(0, dp(12), 0, dp(12));
      row.addView(title, new LayoutParams(0, -2, 1));
      Button remove = new Button(getContext());
      remove.setText("Remove");
      remove.setContentDescription("Remove " + members.get(i));
      remove.setEnabled(!busy);
      remove.setOnClickListener(v -> { if (!busy) removeMember.accept(index); });
      row.addView(remove, new LayoutParams(-2, dp(48)));
      memberList.addView(row, new LayoutParams(-1, -2));
    }
  }
  public void setBusy(boolean busy) {
    this.busy = busy;
    addMember.setEnabled(!busy); removePhoto.setEnabled(!busy);
    for (int i = 0; i < memberList.getChildCount(); i++)
      ((LinearLayout) memberList.getChildAt(i)).getChildAt(1).setEnabled(!busy);
    create.setEnabled(!busy); back.setEnabled(!busy); choosePhoto.setEnabled(!busy);
    photo.setEnabled(!busy); name.setEnabled(!busy); adminOnly.setEnabled(!busy);
    create.setText(busy ? "Creating…" : "Create group");
  }
  private TextView label(String value, int size) {
    TextView text = new TextView(getContext());
    text.setText(value); text.setTextSize(size); text.setTextColor(0xFF000E1A);
    return text;
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
