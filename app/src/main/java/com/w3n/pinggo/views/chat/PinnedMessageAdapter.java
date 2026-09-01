package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.w3n.pinggo.data.local.MessageEntity;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns pinned-message row rendering and pinned-user presentation semantics. */
final class PinnedMessageAdapter extends ComponentList.Adapter<MessageEntity> {
  private static final int ACCENT = 0xFF019CC4;
  private static final int SECONDARY = 0xFF687382;
  private final Context context;
  private final String currentUser;
  private final Bitmap white;
  private final Bitmap divider;
  private final Bitmap pinIcon;
  private final FigmaConfig figmaConfig = new FigmaConfig(1080f);
  private final TextPaint previewMeasurePaint =
      new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final List<MessageEntity> items = new ArrayList<>();

  PinnedMessageAdapter(
      Context context, String currentUser, Bitmap white, Bitmap divider, Bitmap pinIcon) {
    this.context = context;
    this.currentUser = normalize(currentUser);
    this.white = white;
    this.divider = divider;
    this.pinIcon = pinIcon;
    previewMeasurePaint.setTextSize(sp(14));
    previewMeasurePaint.setTypeface(ResourcesCompat.getFont(context, NativeFonts.INTER));
  }

  boolean submit(List<MessageEntity> values) {
    List<MessageEntity> next = values == null ? new ArrayList<>() : new ArrayList<>(values);
    boolean changed = items.size() != next.size();
    if (!changed) {
      for (int index = 0; index < items.size(); index++) {
        if (!signature(items.get(index)).equals(signature(next.get(index)))) {
          changed = true;
          break;
        }
      }
    }
    items.clear();
    items.addAll(next);
    if (changed) notifyDataSetChanged();
    return changed;
  }

  boolean isPinnedByCurrentUser(MessageEntity message) {
    List<String> users = pinnedUsers(message);
    return users.isEmpty() ? message != null && message.pinned : users.contains(currentUser);
  }

  @Override public int getItemCount() { return items.size(); }
  @Override public MessageEntity getItem(int position) { return items.get(position); }

  @Override
  public long getItemId(int position) {
    MessageEntity message = items.get(position);
    return (message.messageId == null ? "pin|" + position : message.messageId).hashCode();
  }

  @Override
  public void onCreateItem(ComponentList.Item item, int type) {
    ComponentList.ItemScope scope = item.getScope();
    float width = scope.width();
    float height = scope.height();
    ZLayer row = item.addLayer("row");
    row.add(new Image.Builder(context, scope.id("background"), white,
        new RectF(0f, 0f, width, height)).setScaleType(Image.ScaleType.FIT_XY));
    row.add(new Image.Builder(context, scope.id("pin"), pinIcon,
        new RectF(px(44f), px(44f), px(110f), px(110f)))
        .setScaleType(Image.ScaleType.FIT_CENTER));
    row.add(textBuilder(scope.id("sender"), "",
        new RectF(px(143f), px(5.5f), width - px(49.5f), px(68.75f)),
        sp(13), ACCENT, FontVariation.SEMI_BOLD).setWrapEnabled(false).setMaxLines(1));
    row.add(textBuilder(scope.id("preview"), "",
        new RectF(px(143f), px(63.25f), width - px(49.5f), height - px(5.5f)),
        sp(14), SECONDARY, FontVariation.REGULAR).setWrapEnabled(false).setMaxLines(1));
    row.add(new Image.Builder(context, scope.id("divider"), divider,
        new RectF(px(143f), height - px(2.75f), width, height))
        .setScaleType(Image.ScaleType.FIT_XY));
  }

  @Override
  public void onBindItem(ComponentList.Item item, MessageEntity message, int position) {
    item.find("sender", Text.class).setText(pinnedByLabel(message));
    float availableWidth = Math.max(1f, item.getScope().width() - px(143f) - px(49.5f));
    item.find("preview", Text.class).setText(ellipsize(preview(message), availableWidth));
    item.find("divider", Image.class).setVisible(position < items.size() - 1);
  }

  private String signature(MessageEntity message) {
    if (message == null) return "null";
    return String.valueOf(message.messageId) + '\u0001' + String.valueOf(message.pinnedAt)
        + '\u0001' + String.valueOf(message.pinnedBy) + '\u0001' + preview(message);
  }

  private List<String> pinnedUsers(MessageEntity message) {
    List<String> users = new ArrayList<>();
    if (message == null || message.pinnedBy == null || message.pinnedBy.isEmpty()) return users;
    for (String value : message.pinnedBy.split("\u001F", -1)) {
      String accountId = normalize(value);
      if (!accountId.isEmpty() && !users.contains(accountId)) users.add(accountId);
    }
    return users;
  }

  private String pinnedByLabel(MessageEntity message) {
    List<String> users = pinnedUsers(message);
    if (users.isEmpty()) return "Pinned message";
    boolean you = users.contains(currentUser);
    List<String> others = new ArrayList<>(users);
    others.remove(currentUser);
    if (you && others.isEmpty()) return "You pinned";
    if (you) return "You and " + others.get(0) + " pinned";
    if (others.size() == 1) return others.get(0) + " pinned";
    return others.get(0) + " and " + (others.size() - 1) + " others pinned";
  }

  private String preview(MessageEntity message) {
    if (message == null) return "Message";
    String type = message.messageType == null ? ""
        : message.messageType.trim().toLowerCase(Locale.US);
    if (message.deletedText != null || "deleted".equals(type)) return "This message was deleted";
    if ("audio".equals(type) || "voice".equals(type)) return "Voice message";
    if (message.text != null && !message.text.trim().isEmpty()) return message.text.trim();
    if (message.latitude != null && message.longitude != null) return "Location";
    if (message.attachmentName != null && !message.attachmentName.trim().isEmpty()) {
      return message.attachmentName.trim();
    }
    if (message.attachmentKind != null && !message.attachmentKind.trim().isEmpty()) {
      String kind = message.attachmentKind.trim();
      return kind.substring(0, 1).toUpperCase(Locale.US) + kind.substring(1);
    }
    return type.isEmpty() || "text".equals(type) ? "Message"
        : type.substring(0, 1).toUpperCase(Locale.US) + type.substring(1);
  }

  private String ellipsize(String value, float maximumWidth) {
    String preview = value == null ? "" : value.replace('\r', ' ')
        .replace('\n', ' ').trim().replaceAll("\\s+", " ");
    if (previewMeasurePaint.measureText(preview) <= maximumWidth) return preview;
    String suffix = "...";
    float textWidth = Math.max(0f, maximumWidth - previewMeasurePaint.measureText(suffix));
    int low = 0;
    int high = preview.length();
    while (low < high) {
      int middle = (low + high + 1) >>> 1;
      if (previewMeasurePaint.measureText(preview, 0, middle) <= textWidth) low = middle;
      else high = middle - 1;
    }
    return preview.substring(0, low).trim() + suffix;
  }

  private Text.Builder textBuilder(
      String id, String value, RectF region, float size, int color, FontVariation variation) {
    return new Text.Builder(context, id, value, region)
        .setFont(NativeFonts.INTER)
        .setFontVariations(variation)
        .setTextSizePx(size)
        .setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(4);
  }

  private float px(float value) {
    return figmaConfig.toRuntime(
        value, Math.max(1, context.getResources().getDisplayMetrics().widthPixels));
  }

  private float sp(float value) {
    return value * context.getResources().getDisplayMetrics().scaledDensity;
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    return normalized.startsWith("+") ? normalized.substring(1) : normalized;
  }
}
