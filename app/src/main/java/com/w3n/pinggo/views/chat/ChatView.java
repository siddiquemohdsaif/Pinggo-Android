package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.MessageEntity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** AAR-native conversation screen and message composer. */
public final class ChatView extends View {
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer bg = layers.addLayer("background"),
      content = layers.addLayer("content"),
      overlay = layers.addLayer("overlay");
  private final Listener listener;
  private final MessageAdapter adapter = new MessageAdapter();
  private final Bitmap white = color(Color.WHITE),
      transparent = color(Color.TRANSPARENT),
      divider = color(0xFFE5EAF0),
      accent = color(ACCENT),
      attachmentOption = color(0xFFEAF7FA),
      incoming = color(0xFFE9EEF3),
      outgoing = color(ACCENT),
      conversationBackground = resourceBitmap(R.drawable.conversation_background),
      headerBackground = resourceBitmap(R.drawable.conversation_header_background),
      composerBackground = resourceBitmap(R.drawable.conversation_composer_background),
      microphoneIcon = resourceBitmap(R.drawable.conversation_microphone),
      emojiIcon = resourceBitmap(R.drawable.conversation_emoji),
      attachmentIcon = resourceBitmap(R.drawable.conversation_attachment),
      cameraIcon = resourceBitmap(R.drawable.conversation_camera),
      backIcon = drawableBitmap(R.drawable.ic_arrow_back),
      voiceCallIcon = resourceBitmap(R.drawable.bottom_nav_calls_inactive),
      videoCallIcon = resourceBitmap(R.drawable.bottom_nav_meet_inactive),
      moreIcon = resourceBitmap(R.drawable.home_overflow_dots);
  private final String chatName, currentUser;
  private final Bitmap profile;
  private ComponentList<MessageEntity> list;
  private Text presence, status, replyText;
  private TextField input;
  private Button send;
  private int topInset, bottomInset, imeInset;
  private int floatingCallInset;
  private boolean imeVisible, attachmentPanelVisible;
  private String draft = "", replyPreview = "";
  private String attachmentPreviewType = "", attachmentPreviewName = "";
  private String statusValue = "Loading messages...";
  private float headerBottom, baseListBottom;

  public ChatView(Context c, String name, String currentUser, String photoPath, Listener l) {
    super(c);
    chatName = name;
    this.currentUser = normalize(currentUser);
    listener = l;
    Bitmap b =
        photoPath == null || photoPath.trim().isEmpty()
            ? null
            : BitmapFactory.decodeFile(photoPath);
    if (b == null) {
      profile = avatar(name);
    } else {
      profile = circleCrop(b);
      b.recycle();
    }
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
    setFocusableInTouchMode(true);
  }

  public void setInsets(int top, int bottom, int ime, boolean visible) {
    int nextTop = Math.max(0, top);
    int nextBottom = Math.max(0, bottom);
    boolean structureChanged = topInset != nextTop || bottomInset != nextBottom;
    topInset = nextTop;
    bottomInset = nextBottom;
    imeInset = Math.max(0, ime);
    imeVisible = visible;
    if (getWidth() <= 0) return;
    if (structureChanged || input == null || list == null) build();
    else applyKeyboardInsets();
  }

  public void setFloatingCallInset(int inset) {
    int next = Math.max(0, inset);
    if (floatingCallInset == next) return;
    floatingCallInset = next;
    if (getWidth() > 0 && getHeight() > 0) build();
  }

  public void setPresence(String value) {
    if (presence != null)
      presence.setText(value == null ? "" : value).setVisible(value != null && !value.isEmpty());
    invalidate();
  }

  public void showStatus(String value) {
    statusValue = value == null ? "" : value;
    adapter.submit(new ArrayList<>());
    if (status != null) status.setText(statusValue).setVisible(!statusValue.isEmpty());
    if (list != null) list.setVisible(false);
    invalidate();
  }

  public void submitMessages(List<MessageEntity> values) {
    adapter.submit(values);
    boolean empty = adapter.getItemCount() == 0;
    statusValue = "";
    if (status != null) status.setText("").setVisible(false);
    if (list != null) {
      list.setVisible(!empty).setEnabled(!empty);
      if (!empty) list.scrollToPosition(adapter.getItemCount() - 1);
    }
    invalidate();
  }

  public String getDraft() {
    return input == null ? draft : input.getText().trim();
  }

  public void clearDraft() {
    draft = "";
    if (input != null) input.clear();
  }

  public void showReply(String value) {
    replyPreview = value == null ? "" : value;
    updateReply();
  }

  public void clearReply() {
    replyPreview = "";
    updateReply();
  }

  public void showAttachmentPreview(String type, String name) {
    attachmentPreviewType = type == null ? "File" : type;
    attachmentPreviewName = name == null || name.trim().isEmpty() ? "Attachment" : name;
    if (getWidth() > 0) build();
  }

  public void clearAttachmentPreview() {
    attachmentPreviewType = "";
    attachmentPreviewName = "";
    if (getWidth() > 0) build();
  }

  public void setDraft(String value) {
    draft = value == null ? "" : value;
    if (input != null) {
      input.setText(draft);
      input.setSelection(draft.length());
      input.requestFocus();
    }
  }

  private void updateReply() {
    if (getWidth() > 0 && getHeight() > 0) {
      build();
    } else {
      invalidate();
    }
  }

  private void toggleAttachmentPanel() {
    attachmentPanelVisible = !attachmentPanelVisible;
    build();
  }

  private void selectAttachment(String type) {
    attachmentPanelVisible = false;
    build();
    listener.onAttachmentSelected(type);
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    if (input != null) draft = input.getText();
    bg.clear();
    content.clear();
    overlay.clear();
    float attachmentPanelHeight = attachmentPanelVisible ? dp(112) : 0;
    float w = getWidth(),
        scale = w / 1080f,
        top = topInset + floatingCallInset,
        screenBottom = getHeight() - bottomInset,
        attachmentPanelTop = screenBottom - attachmentPanelHeight,
        composerBottom = attachmentPanelTop - 50f * scale,
        composerTop = composerBottom - 114f * scale,
        microphoneBottom = attachmentPanelTop - 50f * scale,
        microphoneTop = microphoneBottom - 114f * scale,
        nextHeaderBottom = top + 170f * scale;
    headerBottom = nextHeaderBottom;
    bg.add(
        new Image.Builder(
                getContext(), "conversation_background", conversationBackground,
                new RectF(0, headerBottom, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    bg.add(
        new Image.Builder(getContext(), "status_bar_background", white, new RectF(0, 0, w, top))
            .setScaleType(Image.ScaleType.FIT_XY));
    bg.add(
        new Image.Builder(
                getContext(), "header_background", headerBackground,
                new RectF(0, top, w, headerBottom))
            .setScaleType(Image.ScaleType.FIT_XY));
    iconButton(
        content,
        "back",
        backIcon,
        new RectF(51f * scale, top + 60f * scale,
            102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale,
            128f * scale, top + 137f * scale),
        id -> listener.onBack());
    content.add(
        new Image.Builder(
                getContext(),
                "profile",
                profile,
                new RectF(152f * scale, top + 34f * scale,
                    254f * scale, top + 136f * scale))
            .setScaleType(Image.ScaleType.CENTER_CROP));
    text(
        content,
        "name",
        chatName,
        new RectF(285f * scale, top + 42f * scale,
            742f * scale, top + 91f * scale),
        38f * scale,
        PRIMARY,
        FontVariation.MEDIUM,
        Text.Alignment.START);
    presence =
        text(
            content,
            "presence",
            "connecting...",
            new RectF(285f * scale, top + 95f * scale,
                742f * scale, top + 139f * scale),
            31f * scale,
            SECONDARY,
            FontVariation.REGULAR,
            Text.Alignment.START);
    iconButton(
        content,
        "video_call",
        videoCallIcon,
        new RectF(869f * scale, top + 55f * scale,
            926f * scale, top + 112f * scale),
        new RectF(844f * scale, top + 30f * scale,
            951f * scale, top + 137f * scale),
        id -> listener.onVideoCall());
    iconButton(
        content,
        "voice_call",
        voiceCallIcon,
        new RectF(750f * scale, top + 55f * scale,
            807f * scale, top + 112f * scale),
        new RectF(725f * scale, top + 30f * scale,
            832f * scale, top + 137f * scale),
        id -> listener.onVoiceCall());
    iconButton(
        content,
        "more",
        moreIcon,
        new RectF(1000f * scale, top + 55f * scale,
            1032f * scale, top + 112f * scale),
        new RectF(972f * scale, top + 27f * scale,
            1060f * scale, top + 140f * scale),
        id -> listener.onMore());
    float replyHeight = replyPreview.isEmpty() ? 0 : dp(42);
    float previewHeight = attachmentPreviewType.isEmpty() ? 0 : dp(72);
    float listBottom = composerTop - replyHeight - previewHeight;
    baseListBottom = listBottom;
    list =
        content.add(
            new ComponentList.Builder<MessageEntity>(
                    getContext(), "messages", new RectF(0, headerBottom, w, listBottom))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSizeProvider(
                    (message, position) ->
                        dp(isRichMessage(message) ? 112 : 72)
                            + (hasTransientStatus(message) ? dp(18) : 0)
                            + (message.repliedMessageId == null || message.repliedMessageId.isEmpty()
                                ? 0
                                : dp(22)))
                .setPaddingPx(dp(12), dp(8), dp(12), dp(12))
                .setAdapter(adapter)
                .setClipToBounds(true)
                .setOverscrollEnabled(false)
                .setOnItemLongClickListener(
                    (componentList, message, position) -> {
                      listener.onMessageLongPress(message);
                      return true;
                    })
                .setOnItemClickListener(
                    (componentList, message, position) -> listener.onMessageClick(message)));
    status =
        text(
            content,
            "status",
            statusValue,
            new RectF(dp(20), headerBottom + dp(20), w - dp(20), headerBottom + dp(120)),
            sp(16),
            SECONDARY,
            FontVariation.REGULAR,
            Text.Alignment.CENTER);
    float replyTop = replyPreview.isEmpty() ? composerTop - previewHeight : listBottom;
    replyText =
        text(
            overlay,
            "reply",
            replyPreview,
            new RectF(dp(16), replyTop, w - dp(16), replyTop + Math.max(dp(1), replyHeight)),
            sp(13),
            SECONDARY,
            FontVariation.REGULAR,
            Text.Alignment.START);
    replyText.setVisible(!replyPreview.isEmpty());
    if (!attachmentPreviewType.isEmpty()) {
      float previewTop = composerTop - previewHeight;
      overlay.add(
          new Image.Builder(
                  getContext(), "attachment_preview_bg", white,
                  new RectF(0, previewTop, w, composerTop))
              .setScaleType(Image.ScaleType.FIT_XY));
      text(
          overlay,
          "attachment_preview_text",
          attachmentPreviewType + "\n" + attachmentPreviewName,
          new RectF(dp(16), previewTop + dp(8), w - dp(132), composerTop - dp(8)),
          sp(13),
          PRIMARY,
          FontVariation.SEMI_BOLD,
          Text.Alignment.START);
      button(
          overlay,
          "attachment_preview_remove",
          white,
          "×",
          new RectF(w - dp(128), previewTop + dp(12), w - dp(82), composerTop - dp(12)),
          SECONDARY,
          id -> {
            clearAttachmentPreview();
            listener.onAttachmentPreviewRemoved();
          });
      button(
          overlay,
          "attachment_preview_send",
          accent,
          "Send",
          new RectF(w - dp(78), previewTop + dp(12), w - dp(8), composerTop - dp(12)),
          Color.WHITE,
          id -> listener.onSend());
    }
    overlay.add(
        new Image.Builder(
                getContext(), "composer_background", composerBackground,
                new RectF(33f * scale, composerTop,
                    901f * scale, composerBottom))
            .setScaleType(Image.ScaleType.FIT_XY));
    input =
        overlay.add(
            new TextField.Builder(
                    getContext(),
                    "composer",
                    new RectF(155f * scale, composerTop + 8f * scale,
                        696f * scale, composerBottom - 8f * scale))
                .setText(draft)
                .setHint("Message")
                .setMaxLength(4000)
                .setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .setImeOptions(EditorInfo.IME_ACTION_NONE)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16))
                .setTextColor(PRIMARY)
                .setHintColor(SECONDARY)
                .setCursorColor(ACCENT)
                .setBackgroundColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeWidthPx(0)
                .setCornerRadiusPx(0)
                .setPaddingPx(0, dp(8))
                .setOnTextChangedListener(
                    (id, value) -> {
                      draft = value;
                      listener.onTypingChanged(value);
                    }));
    iconButton(
        overlay,
        "emoji",
        emojiIcon,
        new RectF(71f * scale, composerTop + 30f * scale,
            126f * scale, composerTop + 85f * scale),
        new RectF(49f * scale, composerTop + 8f * scale,
            148f * scale, composerBottom - 8f * scale),
        id -> {
          if (input != null) input.requestFocus();
        });
    iconButton(
        overlay,
        "attachment",
        attachmentIcon,
        new RectF(708f * scale, composerTop + 30f * scale,
            763f * scale, composerTop + 85f * scale),
        new RectF(686f * scale, composerTop + 8f * scale,
            785f * scale, composerBottom - 8f * scale),
        id -> toggleAttachmentPanel());
    iconButton(
        overlay,
        "camera",
        cameraIcon,
        new RectF(808f * scale, composerTop + 30f * scale,
            863f * scale, composerTop + 85f * scale),
        new RectF(786f * scale, composerTop + 8f * scale,
            885f * scale, composerBottom - 8f * scale),
        id -> selectAttachment("Image"));
    send =
        iconButton(
            overlay,
            "send",
            microphoneIcon,
            new RectF(931f * scale, microphoneTop,
                1045f * scale, microphoneBottom),
            new RectF(909f * scale, microphoneTop - 22f * scale,
                1067f * scale, microphoneBottom + 22f * scale),
            id -> listener.onSend());
    if (attachmentPanelVisible) {
      overlay.add(
          new Image.Builder(
                  getContext(),
                  "attachment_panel_bg",
                  white,
                  new RectF(0, attachmentPanelTop, w, screenBottom))
              .setScaleType(Image.ScaleType.FIT_XY));
      overlay.add(
          new Image.Builder(
                  getContext(),
                  "attachment_panel_line",
                  divider,
                  new RectF(0, attachmentPanelTop, w, attachmentPanelTop + dp(1)))
              .setScaleType(Image.ScaleType.FIT_XY));
      String[] attachmentTypes = {"Image", "Video", "File", "Location"};
      float gap = dp(8), side = dp(10), optionWidth = (w - side * 2 - gap * 3) / 4f;
      for (int i = 0; i < attachmentTypes.length; i++) {
        String type = attachmentTypes[i];
        float left = side + i * (optionWidth + gap);
        button(
            overlay,
            "attachment_" + type.toLowerCase(Locale.US),
            attachmentOption,
            type,
            new RectF(left, attachmentPanelTop + dp(16), left + optionWidth, screenBottom - dp(16)),
            ACCENT,
            id -> selectAttachment(type));
      }
    }
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!empty);
    status.setVisible(empty && !statusValue.isEmpty());
    if (!empty) list.scrollToPosition(adapter.getItemCount() - 1);
    applyKeyboardInsets();
    invalidate();
  }

  private void applyKeyboardInsets() {
    if (list == null || getWidth() <= 0) return;
    float shift = imeVisible ? -Math.max(0, imeInset - bottomInset) : 0;
    overlay.setTranslationY(shift);
    float listBottom = Math.max(headerBottom + dp(1), baseListBottom + shift);
    list.setRegion(new RectF(0, headerBottom, getWidth(), listBottom));
    invalidate();
  }

  private final class MessageAdapter extends ComponentList.Adapter<MessageEntity> {
    private final List<MessageEntity> messages = new ArrayList<>();
    private final Map<String, String> texts = new HashMap<>();

    void submit(List<MessageEntity> values) {
      messages.clear();
      texts.clear();
      if (values != null) {
        messages.addAll(values);
        for (MessageEntity m : values) texts.put(m.messageId, displayMessage(m));
      }
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return messages.size();
    }

    @Override
    public MessageEntity getItem(int p) {
      return messages.get(p);
    }

    @Override
    public int getItemViewType(int p) {
      return currentUser.equals(normalize(messages.get(p).senderId)) ? 1 : 0;
    }

    @Override
    public long getItemId(int p) {
      String id = messages.get(p).messageId;
      return id == null ? p : id.hashCode();
    }

    @Override
    public void onCreateItem(ComponentList.Item item, int type) {
      ComponentList.ItemScope s = item.getScope();
      float w = s.width(),
          h = s.height(),
          left = type == 1 ? w - dp(292) : dp(4),
          right = type == 1 ? w - dp(4) : dp(292);
      ZLayer row = item.addLayer("row");
      row.add(
          new Image.Builder(
                  getContext(),
                  s.id("bubble"),
                  type == 1 ? outgoing : incoming,
                  new RectF(left, dp(8), right, h - dp(8)))
              .setScaleType(Image.ScaleType.FIT_XY));
      row.add(
          textBuilder(
              s.id("reply"),
              "",
              new RectF(left + dp(12), dp(7), right - dp(12), dp(30)),
              sp(11),
              type == 1 ? 0xDDFFFFFF : SECONDARY,
              FontVariation.REGULAR));
      row.add(
          textBuilder(
              s.id("message"),
              "",
              new RectF(left + dp(12), dp(24), right - dp(12), h - dp(9)),
              sp(15),
              type == 1 ? Color.WHITE : PRIMARY,
              FontVariation.REGULAR));
    }

    @Override
    public void onBindItem(ComponentList.Item item, MessageEntity m, int p) {
      String replied = m.repliedMessageId == null ? "" : texts.get(m.repliedMessageId);
      Text r = item.find("reply", Text.class);
      r.setText(replied == null ? "Reply" : replied)
          .setVisible(m.repliedMessageId != null && !m.repliedMessageId.isEmpty());
      item.find("message", Text.class).setText(displayMessage(m));
    }
  }

  private String displayMessage(MessageEntity message) {
    String type = message.messageType == null ? "text" : message.messageType;
    String displayed;
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      displayed = "[LOCATION]\n" + formatCoordinate(message.latitude) + ", "
          + formatCoordinate(message.longitude) + "\nTap to open map";
    } else if ("image".equals(type) || "video".equals(type) || "file".equals(type)) {
      String fallback = "file".equals(type) ? "File" : capitalize(type);
      String name = message.attachmentName == null || message.attachmentName.isEmpty()
          ? fallback : message.attachmentName;
      String size = message.attachmentSize == null ? "" : " • " + formatSize(message.attachmentSize);
      String availability = "";
      if (!"sending".equals(message.status) && !"failed".equals(message.status)) {
        int state = listener.attachmentState(message);
        if (state == 1) availability = "\n↓ Download";
        else if (state == 2) availability = "\n◷ Downloading";
      }
      displayed = "[" + type.toUpperCase(Locale.US) + "]\n" + name + size
          + captionSuffix(message.text) + availability;
    } else {
      displayed = message.text == null ? "" : message.text;
    }
    return displayed + statusSuffix(message);
  }

  private boolean hasTransientStatus(MessageEntity message) {
    return "sending".equals(message.status) || "failed".equals(message.status);
  }

  private String statusSuffix(MessageEntity message) {
    if (!currentUser.equals(normalize(message.senderId))) return "";
    if ("sending".equals(message.status)) return "\n◷ Sending";
    if ("failed".equals(message.status)) return "\nFailed • Tap to resend";
    return "";
  }

  private boolean isRichMessage(MessageEntity message) {
    String type = message.messageType == null ? "text" : message.messageType;
    return !"text".equals(type);
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
    return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
  }

  private String formatCoordinate(double value) {
    return String.format(Locale.US, "%.6f", value);
  }

  private String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
  }

  private String captionSuffix(String caption) {
    return caption == null || caption.trim().isEmpty() ? "" : " — " + caption.trim();
  }

  private Text.Builder textBuilder(String id, String v, RectF r, float sz, int c, FontVariation f) {
    return new Text.Builder(getContext(), id, v, r)
        .setFont(NativeFonts.INTER)
        .setFontVariations(f)
        .setTextSizePx(sz)
        .setTextColor(c)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(4);
  }

  private Text text(
      ZLayer l, String id, String v, RectF r, float sz, int c, FontVariation f, Text.Alignment a) {
    return l.add(textBuilder(id, v, r, sz, c, f).setAlignment(a));
  }

  private Button button(
      ZLayer l, String id, Bitmap b, String label, RectF r, int c, Button.OnClickListener click) {
    return l.add(
        new Button.Builder(getContext(), id, b, label, r)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(dp(16))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(18))
            .setTextColor(c)
            .setRippleEnabled(true)
            .setRippleColor(0x22019CC4)
            .setOnClickListener(click));
  }

  private Button iconButton(
      ZLayer layer, String id, Bitmap icon, RectF iconBounds, RectF touchBounds,
      Button.OnClickListener click) {
    layer.add(
        new Image.Builder(getContext(), id + "_icon", icon, iconBounds)
            .setScaleType(Image.ScaleType.FIT_CENTER));
    return layer.add(
        new Button.Builder(getContext(), id + "_touch", transparent, "", touchBounds)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(0)
            .setRippleEnabled(true)
            .setRippleColor(0x16019CC4)
            .setOnClickListener(click));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    layers.draw(c);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return layers.onTouchEvent(e) || super.onTouchEvent(e);
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return layers.onCheckIsTextEditor();
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo a) {
    InputConnection x = layers.onCreateInputConnection(a);
    return x != null ? x : super.onCreateInputConnection(a);
  }

  @Override
  public boolean onKeyDown(int k, KeyEvent e) {
    return layers.onKeyDown(k, e) || super.onKeyDown(k, e);
  }

  public void release() {
    layers.release();
    recycle(
        white, transparent, divider, accent, attachmentOption, incoming, outgoing, profile,
        conversationBackground, headerBackground, composerBackground, microphoneIcon, emojiIcon,
        attachmentIcon, cameraIcon, backIcon, voiceCallIcon, videoCallIcon, moreIcon);
  }

  private Bitmap avatar(String value) {
    int s = Math.round(dp(48));
    Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(1);
    p.setColor(0xFFD9F1F7);
    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
    String x =
        value == null || value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(Locale.US);
    p.setColor(ACCENT);
    p.setTextSize(s * .42f);
    p.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics m = p.getFontMetrics();
    c.drawText(x, s / 2f, s / 2f - (m.ascent + m.descent) / 2, p);
    return b;
  }

  private Bitmap circleCrop(Bitmap source) {
    int size = Math.min(source.getWidth(), source.getHeight());
    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setScale(scale, scale);
    matrix.postTranslate(
        (size - source.getWidth() * scale) / 2f,
        (size - source.getHeight() * scale) / 2f);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    return result;
  }

  private static String normalize(String v) {
    if (v == null) return "";
    String n = v.trim();
    if (n.startsWith("<plus>")) n = n.substring(6);
    return n.startsWith("+") ? n.substring(1) : n;
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap color(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private Bitmap resourceBitmap(int resource) {
    return BitmapFactory.decodeResource(getResources(), resource);
  }

  private Bitmap drawableBitmap(int resource) {
    android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(getContext(), resource);
    if (drawable == null) return transparent;
    int width = Math.max(1, drawable.getIntrinsicWidth());
    int height = Math.max(1, drawable.getIntrinsicHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, width, height);
    drawable.draw(canvas);
    return bitmap;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

  public interface Listener {
    void onBack();

    void onVideoCall();

    void onVoiceCall();

    void onMore();

    void onSend();

    void onAttachmentSelected(String type);

    void onAttachmentPreviewRemoved();

    void onTypingChanged(String value);

    void onMessageLongPress(MessageEntity message);

    void onMessageClick(MessageEntity message);

    /** 0 = locally available, 1 = needs download, 2 = downloading. */
    int attachmentState(MessageEntity message);
  }
}
