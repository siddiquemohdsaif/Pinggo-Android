package com.w3n.pinggo.views.linkeddevice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.ArrayList;
import java.util.List;

/** Linked-device management rendered entirely by native-views-release.aar. */
public final class NativeLinkedDevicesView extends View {
  private static final int PRIMARY = 0xFF000E1A;
  private static final int SECONDARY = 0xFF687382;
  private static final int ACCENT = 0xFF019CC4;

  public interface Listener {
    void onBack();
    void onScanQr();
    void onUseAsCompanion();
    void onLogoutDevice(String deviceId);
  }

  public static final class DeviceItem {
    public final String deviceId;
    public final String name;
    public final String detail;
    public final boolean current;
    public DeviceItem(String deviceId, String name, String detail, boolean current) {
      this.deviceId = deviceId; this.name = name; this.detail = detail; this.current = current;
    }
  }

  private final FigmaConfig config = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("linked_background");
  private final ZLayer content = layers.addLayer("linked_content");
  private final DeviceAdapter adapter = new DeviceAdapter();
  private final Listener listener;
  private final Bitmap page = colorBitmap(0xFFF7F9FB);
  private final Bitmap white = colorBitmap(Color.WHITE);
  private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
  private final Bitmap accent = colorBitmap(ACCENT);
  private final Bitmap secondaryButton = colorBitmap(0xFFE3F4F8);
  private final Bitmap divider = colorBitmap(0xFFE5EAF0);
  private final Bitmap danger = colorBitmap(0xFFFFE9E7);
  private ComponentList<DeviceItem> list;
  private Text status;
  private Progress progress;
  private boolean loading = true;
  private int topInset;
  private int bottomInset;

  public NativeLinkedDevicesView(Context context, Listener listener) {
    super(context); this.listener = listener; setClickable(true); setBackgroundColor(0xFFF7F9FB);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top); bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  public void showLoading(boolean value) { loading = value; updateState(); }
  public void submitDevices(List<DeviceItem> values) {
    adapter.submit(values); loading = false; updateState();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width > 0 && height > 0) build();
  }

  private void build() {
    background.clear(); content.clear();
    float width = getWidth();
    background.add(new Image.Builder(getContext(), "page_background", page,
        new RectF(0, 0, width, getHeight())).setScaleType(Image.ScaleType.FIT_XY));
    float headerTop = topInset, headerHeight = px(170f);
    addButton("back", transparent, "‹", new RectF(px(20f), headerTop, px(155f), headerTop + headerHeight),
        PRIMARY, px(82f), id -> listener.onBack());
    addText("title", "Linked devices", new RectF(px(165f), headerTop, width - px(45f), headerTop + headerHeight),
        px(52f), PRIMARY, FontVariation.SEMI_BOLD, Text.Alignment.START, 1);
    float descriptionTop = headerTop + headerHeight;
    addText("description", "Use Pinggo on multiple devices at the same time. This phone stays signed in when a companion is linked.",
        new RectF(px(55f), descriptionTop, width - px(55f), descriptionTop + px(150f)),
        px(38f), SECONDARY, FontVariation.REGULAR, Text.Alignment.START, 3);
    float scanTop = descriptionTop + px(170f);
    addButton("scan", accent, "Link a device", new RectF(px(55f), scanTop, width - px(55f), scanTop + px(126f)),
        Color.WHITE, px(42f), id -> listener.onScanQr());
    float companionTop = scanTop + px(148f);
    addButton("companion", secondaryButton, "Link this phone as a companion",
        new RectF(px(55f), companionTop, width - px(55f), companionTop + px(126f)),
        ACCENT, px(39f), id -> listener.onUseAsCompanion());
    float listTop = companionTop + px(165f);
    list = content.add(new ComponentList.Builder<DeviceItem>(getContext(), "device_list",
        new RectF(0, listTop, width, Math.max(listTop, getHeight() - bottomInset)))
        .setOrientation(ComponentList.Orientation.VERTICAL).setItemSize(px(245f))
        .setPaddingPx(0, 0, 0, px(30f)).setAdapter(adapter).setClipToBounds(true)
        .setScrollEnabled(true).setOverscrollEnabled(false));
    status = content.add(new Text.Builder(getContext(), "device_status", "No linked devices found.",
        new RectF(px(55f), listTop, width - px(55f), listTop + px(260f)))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
        .setTextSizePx(px(39f)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(3));
    float indicator = px(82f);
    progress = content.add(new Progress.Builder(getContext(), "device_progress",
        new RectF((width - indicator) / 2f, listTop + px(60f), (width + indicator) / 2f, listTop + px(60f) + indicator))
        .setStyle(Progress.Style.CIRCULAR).setMode(Progress.Mode.INDETERMINATE)
        .setTrackColor(0x22019CC4).setProgressColor(ACCENT).setThicknessPx(px(9f))
        .setIndeterminateDuration(850L));
    updateState();
  }

  private void updateState() {
    if (list == null || status == null || progress == null) return;
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!loading && !empty).setEnabled(!loading && !empty);
    status.setText("No linked devices found.").setVisible(!loading && empty);
    progress.setVisible(loading); invalidate();
  }

  private final class DeviceAdapter extends ComponentList.Adapter<DeviceItem> {
    private final List<DeviceItem> devices = new ArrayList<>();
    void submit(List<DeviceItem> values) { devices.clear(); if (values != null) devices.addAll(values); notifyDataSetChanged(); }
    @Override public int getItemCount() { return devices.size(); }
    @Override public DeviceItem getItem(int position) { return devices.get(position); }
    @Override public long getItemId(int position) { return devices.get(position).deviceId.hashCode(); }
    @Override public void onCreateItem(ComponentList.Item item, int type) {
      ComponentList.ItemScope scope = item.getScope(); float width = scope.width(), height = scope.height();
      ZLayer row = item.addLayer("device_row");
      row.add(new Image.Builder(getContext(), scope.id("card"), white,
          new RectF(px(35f), px(10f), width - px(35f), height - px(12f)))
          .setScaleType(Image.ScaleType.FIT_XY));
      row.add(rowText(scope.id("name"), new RectF(px(70f), px(36f), width - px(320f), px(105f)),
          px(43f), PRIMARY, FontVariation.SEMI_BOLD, 1));
      row.add(rowText(scope.id("detail"), new RectF(px(70f), px(112f), width - px(320f), px(194f)),
          px(33f), SECONDARY, FontVariation.REGULAR, 2));
      row.add(new Button.Builder(getContext(), scope.id("logout"), danger, "Log out",
          new RectF(width - px(290f), px(64f), width - px(66f), px(178f)))
          .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(33f))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
          .setTextSizePx(px(34f)).setTextColor(0xFFB3261E).setRippleEnabled(true)
          .setWaitForRippleBeforeClick(true).setRippleColor(0x22B3261E).setOnClickListener(id -> {
            int position = item.getPosition();
            if (position >= 0 && position < devices.size() && !devices.get(position).current)
              listener.onLogoutDevice(devices.get(position).deviceId);
          }));
      row.add(new Image.Builder(getContext(), scope.id("divider"), divider,
          new RectF(px(70f), height - Math.max(1f, px(2f)), width - px(70f), height))
          .setScaleType(Image.ScaleType.FIT_XY));
    }
    @Override public void onBindItem(ComponentList.Item item, DeviceItem value, int position) {
      item.find("name", Text.class).setText(value.name + (value.current ? "  • This device" : ""));
      item.find("detail", Text.class).setText(value.detail);
      item.find("logout", Button.class).setVisible(!value.current).setEnabled(!value.current);
      item.find("divider", Image.class).setVisible(position < devices.size() - 1);
    }
  }

  private Text.Builder rowText(String id, RectF bounds, float size, int color, FontVariation variation, int maxLines) {
    return new Text.Builder(getContext(), id, "", bounds).setFont(NativeFonts.INTER)
        .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(maxLines);
  }
  private void addText(String id, String value, RectF bounds, float size, int color,
      FontVariation variation, Text.Alignment alignment, int maxLines) {
    content.add(new Text.Builder(getContext(), id, value, bounds).setFont(NativeFonts.INTER)
        .setFontVariations(variation).setTextSizePx(size).setTextColor(color).setAlignment(alignment)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(maxLines));
  }
  private void addButton(String id, Bitmap bitmap, String label, RectF bounds, int textColor,
      float textSize, Button.OnClickListener click) {
    content.add(new Button.Builder(getContext(), id, bitmap, label, bounds)
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(35f)).setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(textSize).setTextColor(textColor)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true).setRippleColor(0x22019CC4)
        .setOnClickListener(click));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) { return layers.onTouchEvent(event) || super.onTouchEvent(event); }
  public void release() { layers.release(); recycle(page, white, transparent, accent, secondaryButton, divider, danger); }
  private float px(float value) { return config.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)); }
  private static Bitmap colorBitmap(int color) { Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return bitmap; }
  private static void recycle(Bitmap... bitmaps) { for (Bitmap bitmap : bitmaps) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
}
