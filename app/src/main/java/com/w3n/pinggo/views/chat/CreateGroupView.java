package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.switchcomponent.Switch;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Native AAR form whose vertical scrolling is driven by ComponentList. */
public final class CreateGroupView extends FrameLayout {
  private final ZLayerGroup scrollLayers = new ZLayerGroup(this);
  private final ZLayer scrollLayer = scrollLayers.addLayer("group_scroll");
  private ComponentList<String> scrollList;
  private float lastScrollOffset;
  private boolean listDragging;
  private float downY;
  private final FormView form;
  private final List<String> members = new ArrayList<>();
  private final Runnable onBack, onPhoto, onCreate, onAddMember, onRemovePhoto;
  private final IntConsumer onRemoveMember;
  private String nameValue = "";
  private boolean adminValue, busy, nameError;
  private Bitmap photoBitmap;
  public CreateGroupView(Context context, List<String> members, Runnable onBack,
      Runnable onPhoto, Runnable onCreate, Runnable onAddMember, Runnable onRemovePhoto,
      IntConsumer onRemoveMember) {
    super(context);
    this.members.addAll(members);
    this.onBack = onBack; this.onPhoto = onPhoto; this.onCreate = onCreate;
    this.onAddMember = onAddMember; this.onRemovePhoto = onRemovePhoto;
    this.onRemoveMember = onRemoveMember;
    setBackgroundColor(0xFFF7F9FB);
    form = new FormView(context);
    addView(form, new FrameLayout.LayoutParams(-1, contentHeight()));
  }
  public String groupName() { return nameValue.trim(); }
  public boolean adminsOnly() { return adminValue; }
  public void showNameError() {
    nameError = true;
    if (form.error != null) form.error.setVisible(true);
    if (form.name != null) form.name.requestFocus();
    if (scrollList != null) scrollList.scrollBy(0f, dp(240));
    syncScroll(); form.invalidate();
  }
  public void setPhoto(Bitmap bitmap) { photoBitmap = bitmap; form.rebuild(); }
  public void clearPhoto() { photoBitmap = null; form.rebuild(); }
  public void setMembers(List<String> values) {
    members.clear(); members.addAll(values);
    ViewGroup.LayoutParams params = form.getLayoutParams();
    params.height = contentHeight();
    form.setLayoutParams(params);
    rebuildScroll();
    form.requestLayout(); form.rebuild();
  }
  public void setBusy(boolean value) {
    busy = value;
    for (Button button : form.buttons) button.setEnabled(!busy);
    if (form.name != null) form.name.setEnabled(!busy);
    if (form.admin != null) form.admin.setEnabled(!busy);
    if (form.create != null) form.create.setLabel(busy ? "Creating…" : "Create group");
    form.invalidate();
  }
  public void release() {
    scrollLayers.release();
    form.layers.release(); form.white.recycle(); form.accent.recycle(); form.transparent.recycle();
  }
  @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    rebuildScroll();
  }
  private void rebuildScroll() {
    if (getWidth() <= 0 || getHeight() <= 0) return;
    float offset = scrollList == null ? lastScrollOffset : scrollList.getScrollOffset();
    scrollLayer.clear();
    scrollList = scrollLayer.add(new ComponentList.Builder<String>(getContext(), "group_scroll_list",
        new RectF(0, 0, getWidth(), getHeight()))
        .setOrientation(ComponentList.Orientation.VERTICAL).setItemSize(contentHeight())
        .setAdapter(new ComponentList.Adapter<String>() {
          @Override public int getItemCount() { return 1; }
          @Override public String getItem(int position) { return "form"; }
          @Override public void onCreateItem(ComponentList.Item item, int type) { item.addLayer("scroll_item"); }
          @Override public void onBindItem(ComponentList.Item item, String value, int position) { }
        }).setScrollEnabled(true).setFlingEnabled(true).setOverscrollEnabled(false)
        .setClipToBounds(true));
    if (offset > 0f) scrollList.scrollBy(0f, offset);
    syncScroll();
  }
  private void syncScroll() {
    if (scrollList == null) return;
    lastScrollOffset = scrollList.getScrollOffset();
    form.setTranslationY(-lastScrollOffset);
    invalidate();
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    if (scrollList == null) return super.dispatchTouchEvent(event);
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      downY = event.getY(); listDragging = false; scrollLayers.onTouchEvent(event);
      return super.dispatchTouchEvent(event);
    }
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
      if (!listDragging && Math.abs(event.getY() - downY) > dp(6)) {
        listDragging = true;
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
      }
      scrollLayers.onTouchEvent(event); syncScroll();
      if (listDragging) return true;
    } else if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      scrollLayers.onTouchEvent(event); syncScroll();
      if (listDragging) { listDragging = false; return true; }
    }
    return super.dispatchTouchEvent(event);
  }
  @Override protected void dispatchDraw(Canvas canvas) {
    syncScroll();
    super.dispatchDraw(canvas);
  }
  private int contentHeight() { return dp(554 + members.size() * 64); }
  private final class FormView extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer content = layers.addLayer("group_form");
    private final Bitmap white = colorBitmap(Color.WHITE), accent = colorBitmap(0xFF019CC4),
        transparent = colorBitmap(Color.TRANSPARENT);
    private final List<Button> buttons = new ArrayList<>();
    private TextField name;
    private Switch admin;
    private Text error;
    private Button create;
    FormView(Context context) { super(context); setClickable(true); setFocusableInTouchMode(true); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(dp(554 + members.size() * 64), heightSpec));
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }
    void rebuild() {
      if (getWidth() <= 0) return;
      content.clear(); buttons.clear();
      float width = getWidth(), pad = dp(24), left = (width - dp(96)) / 2f;
      button("back", "‹  New group", new RectF(pad, dp(8), width - pad, dp(56)), false, onBack);
      if (photoBitmap != null) content.add(new Image.Builder(getContext(), "group_photo", photoBitmap,
          new RectF(left, dp(64), left + dp(96), dp(160))).setScaleType(Image.ScaleType.CENTER_CROP));
      else text("photo_placeholder", "Group photo", new RectF(left - dp(24), dp(64), left + dp(120), dp(160)), 16);
      Button photoTouch = content.add(new Button.Builder(getContext(), "photo_touch", transparent, "",
          new RectF(left, dp(64), left + dp(96), dp(160))).setRippleEnabled(false)
          .setOnClickListener(id -> { if (!busy) onPhoto.run(); }));
      photoTouch.setEnabled(!busy); buttons.add(photoTouch);
      button("choose_photo", photoBitmap == null ? "Add group photo" : "Change group photo",
          new RectF(pad, dp(166), width - pad, dp(210)), false, onPhoto);
      if (photoBitmap != null) button("remove_photo", "Remove photo",
          new RectF(pad, dp(214), width - pad, dp(254)), false, onRemovePhoto);
      text("name_label", "Group name", new RectF(pad, dp(258), width - pad, dp(282)), 14);
      name = content.add(new TextField.Builder(getContext(), "group_name", new RectF(pad, dp(286), width - pad, dp(340)))
          .setText(nameValue).setHint("Enter group name").setMaxLength(100)
          .setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
          .setImeOptions(EditorInfo.IME_ACTION_DONE).setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
          .setTextSizePx(sp(18)).setTextColor(0xFF000E1A).setHintColor(0xFF687382)
          .setBackgroundColor(Color.WHITE, Color.WHITE).setStrokeColor(0xFFD5DEE7, 0xFF019CC4)
          .setCornerRadiusPx(dp(12)).setPaddingPx(dp(12), dp(8)).setOnTextChangedListener((id, value) -> {
            nameValue = value;
            if (!value.trim().isEmpty()) { nameError = false; if (error != null) error.setVisible(false); }
          }));
      error = text("name_error", "Enter a group name", new RectF(pad, dp(340), width - pad, dp(360)), 12);
      error.setTextColor(0xFFD92D20).setVisible(nameError);
      text("admin_label", "Only admins can message", new RectF(pad, dp(366), width - dp(94), dp(414)), 16);
      admin = content.add(new Switch.Builder(getContext(), "admin_only", new RectF(width - dp(80), dp(376), width - pad, dp(406)))
          .setChecked(adminValue).setCheckedTrackColor(0xFF019CC4).setUncheckedTrackColor(0xFFBAC4CE)
          .setThumbColor(Color.WHITE).setOnCheckedChangeListener((id, checked, fromUser) -> adminValue = checked));
      text("member_count", "Selected members (" + members.size() + ")", new RectF(pad, dp(422), width - dp(146), dp(462)), 15);
      button("add_members", "Add members", new RectF(width - dp(142), dp(420), width - pad, dp(464)), false, onAddMember);
      for (int i = 0; i < members.size(); i++) {
        final int index = i; float top = dp(470 + i * 64);
        text("member_" + i, members.get(i), new RectF(pad, top, width - dp(124), top + dp(56)), 15);
        button("remove_" + i, "Remove", new RectF(width - dp(116), top + dp(4), width - pad, top + dp(52)), false,
            () -> onRemoveMember.accept(index));
      }
      float top = dp(478 + members.size() * 64);
      create = button("create", busy ? "Creating…" : "Create group", new RectF(pad, top, width - pad, top + dp(52)), true, onCreate);
      name.setEnabled(!busy); admin.setEnabled(!busy); invalidate();
    }
    private Button button(String id, String label, RectF rect, boolean primary, Runnable callback) {
      Button button = content.add(new Button.Builder(getContext(), id, primary ? accent : white, label, rect)
          .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(12)).setFont(NativeFonts.INTER)
          .setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(14)).setTextColor(primary ? Color.WHITE : 0xFF019CC4)
          .setRippleEnabled(true).setWaitForRippleBeforeClick(false).setOnClickListener(value -> { if (!busy) callback.run(); }));
      button.setEnabled(!busy); buttons.add(button); return button;
    }
    private Text text(String id, String value, RectF rect, int size) {
      return content.add(new Text.Builder(getContext(), id, value, rect).setFont(NativeFonts.INTER)
          .setFontVariations(FontVariation.REGULAR).setTextSizePx(sp(size)).setTextColor(0xFF000E1A)
          .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
    }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) { return layers.onTouchEvent(event) || super.onTouchEvent(event); }
    @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) { return layers.onCreateInputConnection(info); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) { return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event); }
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private float sp(int value) { return value * getResources().getDisplayMetrics().scaledDensity; }
  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return bitmap;
  }
}
