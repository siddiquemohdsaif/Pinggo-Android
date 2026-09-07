package com.w3n.pinggo.views.call;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.R;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** AAR-native call detail screen. */
public final class CallDetailView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background");
  private final ZLayer content = layers.addLayer("content");
  private final Listener listener;
  private final String name;
  private final String phoneNumber;
  private final String dateTime;
  private final String duration;
  private final boolean video;
  private final Bitmap white = colorBitmap(Color.WHITE);
  private final Bitmap accent = colorBitmap(0xFF019CC4);
  private final Bitmap divider = colorBitmap(0xFFE5EAF0);
  private final Bitmap phoneIncomingIcon = drawableBitmap(R.drawable.chat_phone_incoming);
  private final Bitmap phoneOutgoingIcon = drawableBitmap(R.drawable.chat_phone_outgoing);
  private final Bitmap phoneMissedIcon = drawableBitmap(R.drawable.chat_phone_missed);
  private final Bitmap videoIncomingIcon = drawableBitmap(R.drawable.chat_video_incoming);
  private final Bitmap videoOutgoingIcon = drawableBitmap(R.drawable.chat_video_outgoing);
  private final Bitmap videoMissedIcon = drawableBitmap(R.drawable.chat_video_missed);
  private final HistoryAdapter adapter = new HistoryAdapter();
  private ComponentList<CallLog> list;
  private Text status;
  private String statusText = "Loading call history...";
  private int topInset;
  private int bottomInset;

  public CallDetailView(
      Context context,
      String name,
      String phoneNumber,
      String dateTime,
      String duration,
      boolean video,
      Listener listener) {
    super(context);
    this.name = name;
    this.phoneNumber = phoneNumber;
    this.dateTime = dateTime;
    this.duration = duration;
    this.video = video;
    this.listener = listener;
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top);
    bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  public void showLoading() {
    statusText = "Loading call history...";
    updateVisibility();
  }

  public void showError(String message) {
    statusText = message == null ? "Unable to load call history." : message;
    adapter.submit(new ArrayList<>());
    updateVisibility();
  }

  public void submitCalls(List<CallLog> calls) {
    adapter.submit(calls);
    statusText = adapter.getItemCount() == 0 ? "No calls found." : "";
    updateVisibility();
  }

  public void appendCalls(List<CallLog> calls) {
    adapter.append(calls);
    statusText = adapter.getItemCount() == 0 ? "No calls found." : "";
    updateVisibility();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    background.clear();
    content.clear();
    float w = getWidth();
    float top = topInset + px(27.5f);
    background.add(
        new Image.Builder(getContext(), "bg", white, new RectF(0, 0, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    addButton(
        "back",
        white,
        "‹",
        new RectF(px(22f), top, px(154f), top + px(132f)),
        0xFF000E1A,
        id -> listener.onBack());
    addText(
        "title",
        "Call details",
        new RectF(px(176f), top, w - px(55f), top + px(132f)),
        sp(23),
        0xFF000E1A,
        FontVariation.BOLD,
        Text.Alignment.START);
    float avatarTop = top + px(154f);
    float avatarSize = px(220f);
    content.add(new Image.Builder(getContext(), "profile", avatar(name),
        new RectF(w / 2f - avatarSize / 2f, avatarTop,
            w / 2f + avatarSize / 2f, avatarTop + avatarSize))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    addText("phone", name,
        new RectF(px(55f), avatarTop + px(226f), w - px(55f), avatarTop + px(303f)),
        sp(18), 0xFF000E1A, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    float actionTop = avatarTop + px(330f);
    float actionSize = px(154f);
    float gap = px(35f);
    float groupWidth = actionSize * 3f + gap * 2f;
    float actionLeft = (w - groupWidth) / 2f;
    addButton("voice", accent, "Voice", new RectF(actionLeft, actionTop,
        actionLeft + actionSize, actionTop + actionSize), Color.WHITE,
        id -> listener.onVoiceCall());
    addButton("video", accent, "Video", new RectF(actionLeft + actionSize + gap, actionTop,
        actionLeft + actionSize * 2f + gap, actionTop + actionSize), Color.WHITE,
        id -> listener.onVideoCall());
    addButton("message", accent, "Message",
        new RectF(actionLeft + (actionSize + gap) * 2f, actionTop,
            actionLeft + actionSize * 3f + gap * 2f, actionTop + actionSize),
        Color.WHITE, id -> listener.onMessage());
    float listTop = actionTop + actionSize + px(44f);
    list = content.add(new ComponentList.Builder<CallLog>(getContext(), "call_history",
        new RectF(0f, listTop, w, getHeight() - bottomInset))
        .setOrientation(ComponentList.Orientation.VERTICAL)
        .setItemSize(px(185f)).setPaddingPx(0, 0, 0, px(44f))
        .setAdapter(adapter).setClipToBounds(true).setScrollEnabled(true)
        .setOverscrollEnabled(false));
    status = content.add(new Text.Builder(getContext(), "history_status", statusText,
        new RectF(px(55f), listTop, w - px(55f), listTop + px(264f)))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
        .setTextSizePx(sp(16)).setTextColor(0xFF687382)
        .setAlignment(Text.Alignment.CENTER)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER));
    updateVisibility();
    invalidate();
  }

  private void updateVisibility() {
    if (list == null || status == null) return;
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!empty).setEnabled(!empty);
    status.setText(statusText).setVisible(empty);
    invalidate();
  }

  private final class HistoryAdapter extends ComponentList.Adapter<CallLog> {
    private final List<CallLog> calls = new ArrayList<>();
    void submit(List<CallLog> values) {
      calls.clear();
      if (values != null) calls.addAll(values);
      notifyDataSetChanged();
    }
    void append(List<CallLog> values) {
      if (values == null || values.isEmpty()) return;
      calls.addAll(values);
      notifyDataSetChanged();
    }
    @Override public int getItemCount() { return calls.size(); }
    @Override public CallLog getItem(int position) { return calls.get(position); }
    @Override public long getItemId(int position) {
      CallLog call = calls.get(position);
      return (call.getFullCalledDateTime() + '|' + position).hashCode();
    }
    @Override public void onCreateItem(ComponentList.Item item, int type) {
      ComponentList.ItemScope scope = item.getScope();
      float width = scope.width(), height = scope.height();
      ZLayer row = item.addLayer("row");
      row.add(new Image.Builder(getContext(), scope.id("type"), phoneOutgoingIcon,
          new RectF(px(68f), px(52f), px(148f), px(132f)))
          .setScaleType(Image.ScaleType.FIT_CENTER));
      row.add(rowText(scope.id("date"), new RectF(px(220f), px(38f),
          width - px(44f), px(92f)), sp(16), 0xFF000E1A, FontVariation.MEDIUM));
      row.add(rowText(scope.id("duration"), new RectF(px(220f), px(103f),
          width - px(44f), px(157f)), sp(14), 0xFF687382, FontVariation.REGULAR));
      row.add(new Image.Builder(getContext(), scope.id("divider"), divider,
          new RectF(px(220f), height - Math.max(1f, px(1f)), width, height))
          .setScaleType(Image.ScaleType.FIT_XY));
    }
    @Override public void onBindItem(ComponentList.Item item, CallLog call, int position) {
      item.find("type", Image.class)
          .setBitmap(callIcon(call));
      item.find("date", Text.class).setText(call.getFullCalledDateTime());
      item.find("duration", Text.class).setText(call.getDuration());
      item.find("divider", Image.class).setVisible(position < calls.size() - 1);
    }
  }

  private Text.Builder rowText(String id, RectF bounds, float size, int color,
                               FontVariation variation) {
    return new Text.Builder(getContext(), id, "", bounds).setFont(NativeFonts.INTER)
        .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false);
  }

  private void addText(
      String id,
      String value,
      RectF rect,
      float size,
      int color,
      FontVariation weight,
      Text.Alignment alignment) {
    content.add(
        new Text.Builder(getContext(), id, value, rect)
            .setFont(NativeFonts.INTER)
            .setFontVariations(weight)
            .setTextSizePx(size)
            .setTextColor(color)
            .setAlignment(alignment)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER)
            .setMaxLines(2));
  }

  private void addButton(
      String id, Bitmap bitmap, String label, RectF rect, int color, Button.OnClickListener click) {
    content.add(
        new Button.Builder(getContext(), id, bitmap, label, rect)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(px(44f))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(16))
            .setTextColor(color)
            .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
            .setRippleColor(0x22019CC4)
            .setOnClickListener(click));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    boolean handled = layers.onTouchEvent(event);
    if (handled) post(this::loadNextPageIfNeeded);
    return handled || super.onTouchEvent(event);
  }

  private void loadNextPageIfNeeded() {
    if (list != null && adapter.getItemCount() > 0
        && list.getLastVisiblePosition() >= adapter.getItemCount() - 3) {
      listener.onLoadMoreCalls();
    }
  }

  public void release() {
    layers.release();
    recycle(white, accent, divider, phoneIncomingIcon, phoneOutgoingIcon, phoneMissedIcon,
        videoIncomingIcon, videoOutgoingIcon, videoMissedIcon);
  }

  private Bitmap avatar(String value) {
    int size = Math.round(px(319f));
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    String label =
        value == null || value.trim().isEmpty()
            ? "?"
            : value.trim().substring(0, 1).toUpperCase(Locale.US);
    paint.setColor(0xFF019CC4);
    paint.setTextSize(size * .42f);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText(label, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }

  private float px(float v) {
    return figmaConfig.toRuntime(v, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap colorBitmap(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private Bitmap drawableBitmap(int resource) {
    Drawable drawable = ContextCompat.getDrawable(getContext(), resource);
    if (drawable == null) return colorBitmap(Color.TRANSPARENT);
    int width = Math.max(1, drawable.getIntrinsicWidth());
    int height = Math.max(1, drawable.getIntrinsicHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, width, height);
    drawable.draw(canvas);
    return bitmap;
  }

  private Bitmap callIcon(CallLog call) {
    int direction = call.getIconDirection();
    if (call.isVideoCall()) {
      return direction == CallLog.ICON_MISSED ? videoMissedIcon
          : direction == CallLog.ICON_OUTGOING ? videoOutgoingIcon : videoIncomingIcon;
    }
    return direction == CallLog.ICON_MISSED ? phoneMissedIcon
        : direction == CallLog.ICON_OUTGOING ? phoneOutgoingIcon : phoneIncomingIcon;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

  public interface Listener {
    void onBack();

    void onCallAgain(boolean video);

    void onVoiceCall();

    void onVideoCall();

    void onMessage();

    void onLoadMoreCalls();
  }
}
