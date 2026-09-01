package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import java.util.Locale;

/** Rounded quoted-message card shown inside a reply bubble. */
class ReplyPreviewComponent implements Component {
  interface ClickListener { void onClick(String messageId); }
  private static final int FILL_COLOR = 0xFFF0FCFE;
  private static final int BORDER_COLOR = 0xFFCDEBF2;
  private static final int ACCENT_COLOR = 0xFF019CC4;
  private static final int MESSAGE_COLOR = 0xFF5C6B85;
  private static final float BORDER_WIDTH_PX = 3f;
  private static final float SIDE_STROKE_WIDTH_PX = 8f;
  private static final float CORNER_RADIUS_PX = 20f;
  private static final float TEXT_LEFT_PX = 27f;
  private static final float TEXT_RIGHT_PX = 18f;
  private static final float TEXT_TOP_PX = 13f;
  private static final float SENDER_MESSAGE_GAP_PX = 5f;
  private static final float CONTENT_CORNER_RADIUS_PX = 12f;
  private static final int PLACEHOLDER_COLOR = 0xFFDDE7EC;
  private static final int WAVEFORM_COLOR = 0xFFA8B3C0;

  private final Context context;
  private final String id;
  private final Bitmap documentIcon;
  private final Bitmap audioAvatar;
  private final ClickListener clickListener;
  private final RectF bounds = new RectF();
  private final RectF borderBounds = new RectF();
  private final RectF accentBounds = new RectF();
  private final Path cardClip = new Path();
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint senderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private String sender = "";
  private ReplyContent content = ReplyContent.text("");
  private Bitmap mediaThumbnail;
  private String mediaRequestKey = "";
  private String targetMessageId = "";
  private boolean pressed;
  private boolean visible;
  private boolean released;

  ReplyPreviewComponent(
      Context context, String id, Bitmap documentIcon, Bitmap audioAvatar,
      Typeface typeface, float senderTextSize, float messageTextSize,
      ClickListener clickListener) {
    this.context = context.getApplicationContext();
    this.id = id;
    this.documentIcon = documentIcon;
    this.audioAvatar = audioAvatar;
    this.clickListener = clickListener;
    fill.setColor(FILL_COLOR);
    border.setColor(BORDER_COLOR);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(BORDER_WIDTH_PX);
    accent.setColor(ACCENT_COLOR);
    accent.setStyle(Paint.Style.FILL);
    senderPaint.setColor(ACCENT_COLOR);
    senderPaint.setTextSize(senderTextSize);
    senderPaint.setTypeface(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        ? Typeface.create(typeface, 600, false) : Typeface.create(typeface, Typeface.BOLD));
    messagePaint.setColor(MESSAGE_COLOR);
    messagePaint.setTextSize(messageTextSize);
    messagePaint.setTypeface(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        ? Typeface.create(typeface, 100, false) : Typeface.create(typeface, Typeface.NORMAL));
  }

  ReplyPreviewComponent bind(
      RectF region, String senderValue, ReplyContent replyContent, String repliedMessageId) {
    bounds.set(region);
    sender = senderValue == null ? "" : senderValue;
    content = replyContent == null ? ReplyContent.text("Message unavailable") : replyContent;
    targetMessageId = repliedMessageId == null ? "" : repliedMessageId;
    bindMediaThumbnail();
    visible = true;
    invalidate();
    return this;
  }

  ReplyPreviewComponent hide() {
    visible = false;
    invalidate();
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return visible && !released; }
  @Override public boolean isEnabled() {
    return isVisible() && clickListener != null && !targetMessageId.isEmpty();
  }
  @Override public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      pressed = bounds.contains(event.getX(), event.getY());
      return pressed;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      pressed &= bounds.contains(event.getX(), event.getY());
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      boolean activate = pressed && bounds.contains(event.getX(), event.getY());
      pressed = false;
      if (activate) clickListener.onClick(targetMessageId);
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      pressed = false;
      return true;
    }
    return false;
  }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() {
    released = true;
    visible = false;
    host = null;
    mediaThumbnail = null;
    mediaRequestKey = "";
    targetMessageId = "";
    pressed = false;
  }

  @Override
  public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    canvas.drawRoundRect(bounds, CORNER_RADIUS_PX, CORNER_RADIUS_PX, fill);
    accentBounds.set(bounds);
    accentBounds.inset(BORDER_WIDTH_PX, BORDER_WIDTH_PX);
    float accentRadius = Math.max(1f, CORNER_RADIUS_PX - BORDER_WIDTH_PX);
    cardClip.reset();
    cardClip.addRoundRect(accentBounds, accentRadius, accentRadius, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(cardClip);
    canvas.drawRect(accentBounds.left, accentBounds.top,
        accentBounds.left + SIDE_STROKE_WIDTH_PX, accentBounds.bottom, accent);
    canvas.restore();
    borderBounds.set(bounds);
    borderBounds.inset(BORDER_WIDTH_PX / 2f, BORDER_WIDTH_PX / 2f);
    canvas.drawRoundRect(borderBounds, CORNER_RADIUS_PX, CORNER_RADIUS_PX, border);

    float textLeft = bounds.left + TEXT_LEFT_PX;
    int textWidth = Math.max(1,
        Math.round(bounds.right - TEXT_RIGHT_PX - textLeft));
    StaticLayout senderLayout = layout(sender, senderPaint, textWidth);
    canvas.save();
    canvas.translate(textLeft, bounds.top + TEXT_TOP_PX);
    senderLayout.draw(canvas);
    canvas.restore();

    float contentTop = bounds.top + TEXT_TOP_PX + senderLayout.getHeight()
        + SENDER_MESSAGE_GAP_PX;
    RectF contentBounds = new RectF(
        textLeft, contentTop, bounds.right - TEXT_RIGHT_PX,
        Math.max(contentTop + 1f, bounds.bottom - TEXT_TOP_PX));
    if (content.isMedia()) drawMedia(canvas, contentBounds);
    else if (ReplyContent.AUDIO.equals(content.type)) drawAudio(canvas, contentBounds);
    else if (ReplyContent.FILE.equals(content.type)) drawFile(canvas, contentBounds);
    else if (ReplyContent.LOCATION.equals(content.type)) drawLocation(canvas, contentBounds);
    else if (content.isCall()) drawCall(canvas, contentBounds);
    else drawText(canvas, contentBounds);
  }

  private void bindMediaThumbnail() {
    if (!content.isMedia() || content.source.isEmpty()) {
      mediaThumbnail = null;
      mediaRequestKey = "";
      return;
    }
    boolean video = ReplyContent.VIDEO.equals(content.type);
    String requestKey = content.source + '|' + video;
    mediaRequestKey = requestKey;
    MediaPreviewCache.Thumbnail cached =
        MediaPreviewCache.anyMemoryThumbnail(content.source, video);
    mediaThumbnail = cached == null ? null : cached.bitmap;
    if (mediaThumbnail != null) return;
    MediaPreviewCache.loadThumbnail(context, content.source, video, 480, 160,
        new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
          @Override public void onSuccess(MediaPreviewCache.Thumbnail value) {
            if (!requestKey.equals(mediaRequestKey) || released) return;
            mediaThumbnail = value == null ? null : value.bitmap;
            invalidate();
          }

          @Override public void onError() {
            if (!requestKey.equals(mediaRequestKey) || released) return;
            mediaThumbnail = null;
            invalidate();
          }
        });
  }

  private void drawMedia(Canvas canvas, RectF region) {
    Path clip = new Path();
    clip.addRoundRect(region, CONTENT_CORNER_RADIUS_PX, CONTENT_CORNER_RADIUS_PX,
        Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    if (mediaThumbnail == null || mediaThumbnail.isRecycled()) {
      fill.setColor(PLACEHOLDER_COLOR);
      canvas.drawRect(region, fill);
      drawCenteredText(canvas, "Loading...", region, messagePaint);
    } else {
      canvas.drawBitmap(mediaThumbnail, null, fitCenter(mediaThumbnail, region), fill);
    }
    canvas.restore();
    if (ReplyContent.VIDEO.equals(content.type)) {
      float size = Math.min(region.width(), region.height()) * .28f;
      float centerX = region.centerX();
      float centerY = region.centerY();
      Path play = new Path();
      play.moveTo(centerX - size * .35f, centerY - size * .5f);
      play.lineTo(centerX + size * .55f, centerY);
      play.lineTo(centerX - size * .35f, centerY + size * .5f);
      play.close();
      accent.setColor(0xFFFFFFFF);
      canvas.drawPath(play, accent);
      accent.setColor(ACCENT_COLOR);
    }
  }

  private void drawAudio(Canvas canvas, RectF region) {
    float centerY = region.centerY();
    float scale = Math.min(1f, region.height() / 146f);
    float avatarRadius = 47f * scale;
    float avatarCenterX = region.left + 56f * scale;
    if (audioAvatar != null && !audioAvatar.isRecycled()) {
      canvas.save();
      Path avatarClip = new Path();
      avatarClip.addCircle(avatarCenterX, centerY, avatarRadius, Path.Direction.CW);
      canvas.clipPath(avatarClip);
      canvas.drawBitmap(audioAvatar, null,
          new RectF(avatarCenterX - avatarRadius, centerY - avatarRadius,
              avatarCenterX + avatarRadius, centerY + avatarRadius), fill);
      canvas.restore();
    }
    float controlX = region.left + 146f * scale;
    Path play = new Path();
    play.moveTo(controlX - 6f, centerY - 12f);
    play.lineTo(controlX + 14f, centerY);
    play.lineTo(controlX - 6f, centerY + 12f);
    play.close();
    canvas.drawPath(play, accent);
    float durationWidth = messagePaint.measureText(formatDuration(content.durationMs));
    float waveLeft = region.left + 205f * scale;
    float waveRight = Math.max(waveLeft + 1f, region.right - durationWidth - 12f);
    float[] waveform = content.waveform;
    int bars = waveform == null || waveform.length == 0 ? 18 : waveform.length;
    float step = (waveRight - waveLeft) / Math.max(1, bars - 1);
    fill.setColor(WAVEFORM_COLOR);
    fill.setStrokeWidth(3f);
    fill.setStrokeCap(Paint.Cap.ROUND);
    for (int index = 0; index < bars; index++) {
      float level = waveform == null || waveform.length == 0
          ? .28f + ((index * 37) % 65) / 100f
          : waveform[index];
      float half = 5f + 13f * Math.max(.08f, Math.min(1f, level));
      float x = waveLeft + step * index;
      canvas.drawLine(x, centerY - half, x, centerY + half, fill);
    }
    drawFromBaseline(canvas, formatDuration(content.durationMs),
        region.right - durationWidth, centerY - (messagePaint.ascent() + messagePaint.descent()) / 2f,
        messagePaint);
  }

  private void drawFile(Canvas canvas, RectF region) {
    float scale = Math.min(1f, region.height() / 100f);
    float iconWidth = 57f * scale;
    float iconHeight = 70f * scale;
    RectF icon = new RectF(region.left, region.centerY() - iconHeight / 2f,
        region.left + iconWidth, region.centerY() + iconHeight / 2f);
    if (documentIcon != null && !documentIcon.isRecycled()) {
      canvas.drawBitmap(documentIcon, null, icon, fill);
    }
    float left = icon.right + 10f;
    int width = Math.max(1, Math.round(region.right - left));
    String title = content.title.isEmpty() ? "File" : content.title;
    StaticLayout titleLayout = StaticLayout.Builder.obtain(
            title, 0, title.length(), messagePaint, width)
        .setIncludePad(false)
        .setMaxLines(1)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build();
    canvas.save();
    canvas.translate(left, region.top);
    titleLayout.draw(canvas);
    canvas.restore();
    if (!content.subtitle.isEmpty()) {
      drawFromBaseline(canvas, content.subtitle, left,
          Math.min(region.bottom - messagePaint.descent(),
              region.top + titleLayout.getHeight() + 8f - messagePaint.ascent()), messagePaint);
    }
  }

  private void drawLocation(Canvas canvas, RectF region) {
    float scale = Math.min(region.width() / 685f, region.height() / 345f);
    Path clip = new Path();
    clip.addRoundRect(region, CONTENT_CORNER_RADIUS_PX, CONTENT_CORNER_RADIUS_PX,
        Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    fill.setColor(0xFFE6F2EC);
    canvas.drawRect(region, fill);
    fill.setColor(0xFFD4E8DB);
    canvas.drawRect(region.left + 30f * scale, region.top + 24f * scale,
        region.left + 190f * scale, region.top + 103f * scale, fill);
    canvas.drawRect(region.right - 215f * scale, region.top + 31f * scale,
        region.right - 37f * scale, region.top + 116f * scale, fill);
    fill.setColor(0xFFFFFFFF);
    fill.setStyle(Paint.Style.STROKE);
    fill.setStrokeWidth(18f * scale);
    canvas.drawLine(region.left, region.bottom - 83f * scale,
        region.right, region.top + 57f * scale, fill);
    fill.setStrokeWidth(12f * scale);
    canvas.drawLine(region.left + 132f * scale, region.top,
        region.right - 88f * scale, region.bottom - 57f * scale, fill);
    fill.setStyle(Paint.Style.FILL);
    float actionHeight = 72f * scale;
    fill.setColor(0xFFF8F9FA);
    canvas.drawRect(region.left, region.bottom - actionHeight, region.right, region.bottom, fill);
    float centerX = region.centerX();
    float centerY = region.centerY() - actionHeight * .45f;
    fill.setColor(ACCENT_COLOR);
    canvas.drawCircle(centerX, centerY, 21f * scale, fill);
    Path pin = new Path();
    pin.moveTo(centerX - 13f * scale, centerY + 13f * scale);
    pin.lineTo(centerX + 13f * scale, centerY + 13f * scale);
    pin.lineTo(centerX, centerY + 40f * scale);
    pin.close();
    canvas.drawPath(pin, fill);
    fill.setColor(0xFFFFFFFF);
    canvas.drawCircle(centerX, centerY, 7f * scale, fill);
    canvas.restore();

    String coordinates = content.latitude == null || content.longitude == null ? ""
        : String.format(Locale.US, "%.6f, %.6f", content.latitude, content.longitude);
    messagePaint.setColor(MESSAGE_COLOR);
    messagePaint.setTextSize(22f * scale);
    drawFromBaseline(canvas, coordinates, region.left + 15f * scale,
        region.bottom - actionHeight - 13f * scale, messagePaint);
    messagePaint.setColor(ACCENT_COLOR);
    messagePaint.setTextSize(27f * scale);
    drawFromBaseline(canvas, "Open in map", region.left + 20f * scale,
        region.bottom - actionHeight / 2f
            - (messagePaint.ascent() + messagePaint.descent()) / 2f, messagePaint);
    messagePaint.setColor(MESSAGE_COLOR);
  }

  private void drawCall(Canvas canvas, RectF region) {
    float scale = Math.min(1f, region.height() / 146f);
    fill.setColor(0xFFF8F9FA);
    canvas.drawRoundRect(region, CONTENT_CORNER_RADIUS_PX, CONTENT_CORNER_RADIUS_PX, fill);
    float iconSize = 46f * scale;
    float iconLeft = region.left + 34f * scale;
    RectF iconBounds = new RectF(iconLeft, region.centerY() - iconSize / 2f,
        iconLeft + iconSize, region.centerY() + iconSize / 2f);
    if (content.icon != null && !content.icon.isRecycled()) {
      canvas.drawBitmap(content.icon, null, iconBounds, fill);
    }
    if (!content.title.isEmpty()) {
      messagePaint.setTextSize(34f * scale);
      float baseline = region.centerY()
          - (messagePaint.ascent() + messagePaint.descent()) / 2f;
      drawFromBaseline(canvas, content.title, region.left + 116f * scale,
          baseline, messagePaint);
    }
  }

  private void drawText(Canvas canvas, RectF region) {
    String value = content.text.isEmpty() ? "Message unavailable" : content.text;
    StaticLayout messageLayout = layout(value, messagePaint, Math.max(1, Math.round(region.width())));
    canvas.save();
    canvas.translate(region.left, region.top);
    messageLayout.draw(canvas);
    canvas.restore();
  }

  private static RectF fitCenter(Bitmap bitmap, RectF destination) {
    float sourceRatio = bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
    float destinationRatio = destination.width() / Math.max(1f, destination.height());
    RectF fitted = new RectF(destination);
    if (sourceRatio > destinationRatio) {
      float height = destination.width() / sourceRatio;
      fitted.top = destination.centerY() - height / 2f;
      fitted.bottom = fitted.top + height;
    } else {
      float width = destination.height() * sourceRatio;
      fitted.left = destination.centerX() - width / 2f;
      fitted.right = fitted.left + width;
    }
    return fitted;
  }

  private static void drawCenteredText(
      Canvas canvas, String value, RectF bounds, Paint paint) {
    float x = bounds.centerX() - paint.measureText(value) / 2f;
    float y = bounds.centerY() - (paint.ascent() + paint.descent()) / 2f;
    canvas.drawText(value, x, y, paint);
  }

  private static void drawFromBaseline(
      Canvas canvas, String value, float x, float baseline, Paint paint) {
    canvas.drawText(value, x, baseline, paint);
  }

  private static String formatDuration(long milliseconds) {
    long seconds = Math.max(0L, milliseconds / 1000L);
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
  }

  private static StaticLayout layout(String value, TextPaint paint, int width) {
    return StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
        .setIncludePad(false)
        .build();
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
