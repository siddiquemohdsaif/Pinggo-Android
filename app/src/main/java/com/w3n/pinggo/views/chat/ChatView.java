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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.MessageEntity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** AAR-native conversation screen and message composer. */
public final class ChatView extends View {
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private static final int MAX_VISIBLE_COMPOSER_LINES = 7;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer bg = layers.addLayer("background"),
      content = layers.addLayer("content"),
      overlay = layers.addLayer("overlay"),
      selectionOverlay = layers.addLayer("message_selection");
  private final Listener listener;
  private final ChatPerformanceProfiler profiler;
  private final ChatMessageAdapter adapter;
  private final TextPaint composerMeasurePaint =
      new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final Bitmap white = color(Color.WHITE),
      transparent = color(Color.TRANSPARENT),
      divider = color(0xFFE5EAF0),
      accent = color(ACCENT),
      attachmentOption = color(0xFFEAF7FA),
      conversationBackground = resourceBitmap(R.drawable.conversation_background),
      headerBackground = resourceBitmap(R.drawable.conversation_header_background),
      microphoneIcon = resourceBitmap(R.drawable.conversation_microphone),
      sendIcon = resourceBitmap(R.drawable.conversation_send),
      emojiIcon = resourceBitmap(R.drawable.conversation_emoji),
      attachmentIcon = resourceBitmap(R.drawable.conversation_attachment),
      cameraIcon = resourceBitmap(R.drawable.conversation_camera),
      backIcon = drawableBitmap(R.drawable.conversation_back),
      voiceCallIcon = resourceBitmap(R.drawable.conversation_voice_call),
      videoCallIcon = resourceBitmap(R.drawable.conversation_video_call),
      moreIcon = resourceBitmap(R.drawable.home_overflow_dots),
      selectionBackground = resourceBitmap(R.drawable.chat_selection_background),
      selectionStatusBarBackground = color(0xFFE9EDF0),
      messageSelectionBackground = color(0x40A9B3BB),
      selectionReplyIcon = namedDrawableBitmap("conversation_selection_reply"),
      selectionCopyIcon = namedDrawableBitmap("conversation_selection_copy"),
      selectionForwardIcon = namedDrawableBitmap("conversation_selection_forward"),
      selectionPinIcon = namedDrawableBitmap("conversation_selection_pin"),
      selectionUnpinIcon = namedDrawableBitmap("conversation_selection_unpin"),
      selectionDeleteIcon = namedDrawableBitmap("conversation_selection_delete"),
      messageSendingIcon = resourceBitmap(R.drawable.chat_status_sending_image),
      messageSentIcon = resourceBitmap(R.drawable.chat_status_sent),
      messageDeliveredIcon = resourceBitmap(R.drawable.chat_status_delivered),
      messageReadIcon = resourceBitmap(R.drawable.chat_status_read);
  private final String chatName, currentUser;
  private final Set<String> selectedMessageIds = new LinkedHashSet<>();
  private final Bitmap profile;
  private ComponentList<MessageEntity> list;
  private Text presence, status, olderStatus, replyText;
  private Image olderLoadingBackground, attachmentPreviewBackground;
  private Progress olderProgress;
  private TextField input;
  private Button send, attachmentPreviewRemove, attachmentPreviewSend;
  private Image composerActionIcon;
  private Text attachmentPreviewTextComponent;
  private ComposerBackgroundComponent composerBackground;
  private int topInset, bottomInset, imeInset;
  private int floatingCallInset;
  private boolean imeVisible, attachmentPanelVisible;
  private boolean loadingOlderMessages, canLoadOlderMessages = true;
  private String draft = "", replyPreview = "";
  private String attachmentPreviewType = "", attachmentPreviewName = "";
  private String statusValue = "Loading messages...";
  private float headerBottom, baseListBottom;
  private float renderedComposerHeight = -1f;
  private boolean composerResizePosted;
  private float loadingGestureStartY;
  private boolean loadingGestureBlocked;
  private boolean olderLoadRequestedForGesture;
  private boolean profileScrollCandidate;
  private boolean profileScrollStarted;
  private float profileGestureStartY;
  private final Runnable finishScrollProfile = this::finishScrollProfile;

  public ChatView(
      Context c,
      String name,
      String currentUser,
      String photoPath,
      Listener l,
      ChatPerformanceProfiler profiler) {
    super(c);
    chatName = name;
    this.currentUser = normalize(currentUser);
    listener = l;
    this.profiler = profiler;
    composerMeasurePaint.setTextSize(sp(16));
    composerMeasurePaint.setTypeface(ResourcesCompat.getFont(c, NativeFonts.INTER));
    adapter =
        new ChatMessageAdapter(
            c,
            this.currentUser,
            transparent,
            messageSelectionBackground,
            messageSendingIcon,
            messageSentIcon,
            messageDeliveredIcon,
            messageReadIcon,
            selectionPinIcon,
            listener::attachmentState,
            profiler,
            selectedMessageIds);
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

  public boolean submitMessages(List<MessageEntity> values) {
    int oldCount = adapter.getItemCount();
    int firstVisible = list == null ? -1 : list.getFirstVisiblePosition();
    int lastVisible = list == null ? -1 : list.getLastVisiblePosition();
    float availableWidth = getMessageLayoutWidth();
    float oldScrollOffset = list == null ? 0f : list.getScrollOffset();
    float oldAnchorStart = firstVisible < 0
        ? 0f : adapter.contentStartAt(firstVisible, availableWidth);
    float anchorPixelOffset = oldScrollOffset - oldAnchorStart;
    String anchorId = adapter.messageIdAt(firstVisible);
    String oldFirstId = adapter.messageIdAt(0);
    String oldLastId = adapter.messageIdAt(oldCount - 1);
    boolean wasNearBottom = oldCount == 0 || lastVisible >= oldCount - 2;
    boolean changed = adapter.submit(values);
    boolean selectionChanged = selectedMessageIds.removeIf(
        messageId -> adapter.indexOfMessage(messageId) < 0);
    if (selectionChanged) refreshMessageSelectionHeader();
    boolean empty = adapter.getItemCount() == 0;
    statusValue = "";
    if (status != null) status.setText("").setVisible(false);
    if (list != null) {
      list.setVisible(!empty).setEnabled(!empty);
      if (!empty && changed) {
        boolean prepended = oldFirstId != null && adapter.indexOfMessage(oldFirstId) > 0;
        String newLastId = adapter.messageIdAt(adapter.getItemCount() - 1);
        boolean appended = oldLastId != null && !oldLastId.equals(newLastId);
        int anchorPosition = adapter.indexOfMessage(anchorId);
        if (oldCount == 0 || (wasNearBottom && appended && !prepended)) {
          int newestPosition = adapter.getItemCount() - 1;
          // The data refresh already binds the visible range. Do not recycle and bind it again
          // when the entire incremental window already fits on screen.
          if (list.getLastVisiblePosition() < newestPosition) {
            list.scrollToPosition(newestPosition);
          }
        } else if (anchorPosition >= 0) {
          float desiredOffset = adapter.contentStartAt(anchorPosition, availableWidth)
              + anchorPixelOffset;
          float delta = desiredOffset - list.getScrollOffset();
          if (Math.abs(delta) >= .5f) list.scrollBy(0f, delta);
        }
      }
    }
    invalidate();
    return changed;
  }

  public float getMessageLayoutWidth() {
    return Math.max(1f, getWidth() - dp(24));
  }

  public void prepareMessageMetrics(List<MessageEntity> values, float availableWidth) {
    adapter.prepareMetrics(values, availableWidth);
  }

  public void setOlderMessagesState(boolean loading, boolean canLoad) {
    loadingOlderMessages = loading;
    canLoadOlderMessages = canLoad;
    updateOlderLoadingChrome();
  }

  public String getDraft() {
    return input == null ? draft : input.getText().trim();
  }

  public void clearDraft() {
    draft = "";
    if (input != null) input.clear();
    updateComposerActionIcon();
  }

  public void showReply(String value) {
    replyPreview = value == null ? "" : value;
    updateReply();
  }

  public void clearReply() {
    replyPreview = "";
    updateReply();
  }

  public boolean isSelectingMessages() {
    return !selectedMessageIds.isEmpty();
  }

  public boolean clearMessageSelection() {
    if (selectedMessageIds.isEmpty()) return false;
    List<String> previousIds = new ArrayList<>(selectedMessageIds);
    selectedMessageIds.clear();
    for (String id : previousIds) {
      int position = adapter.indexOfMessage(id);
      if (position >= 0) adapter.notifyItemChanged(position);
    }
    selectionOverlay.clear();
    listener.onMessageSelectionChanged(false);
    invalidate();
    return true;
  }

  private void toggleMessageSelection(MessageEntity message) {
    String id = selectionId(message);
    if (id.isEmpty()) return;
    if (!selectedMessageIds.add(id)) selectedMessageIds.remove(id);
    int position = adapter.indexOfMessage(id);
    if (position >= 0) adapter.notifyItemChanged(position);
    refreshMessageSelectionHeader();
  }

  private void refreshMessageSelectionHeader() {
    selectionOverlay.clear();
    if (isSelectingMessages() && getWidth() > 0) {
      buildMessageSelectionHeader(
          getWidth(), topInset + floatingCallInset, getWidth() / 1080f);
    }
    listener.onMessageSelectionChanged(isSelectingMessages());
    invalidate();
  }

  private List<MessageEntity> selectedMessages() {
    List<MessageEntity> selected = new ArrayList<>();
    for (int index = 0; index < adapter.getItemCount(); index++) {
      MessageEntity message = adapter.getItem(index);
      if (selectedMessageIds.contains(selectionId(message))) selected.add(message);
    }
    return selected;
  }

  private static String selectionId(MessageEntity message) {
    if (message == null) return "";
    if (message.messageId != null && !message.messageId.isEmpty()) return message.messageId;
    return message.clientMessageId == null ? "" : message.clientMessageId;
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
    updateComposerActionIcon();
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
    int previousCount = adapter.getItemCount();
    int previousFirstVisible = list == null ? -1 : list.getFirstVisiblePosition();
    int previousLastVisible = list == null ? -1 : list.getLastVisiblePosition();
    String previousAnchorId = adapter.messageIdAt(previousFirstVisible);
    boolean wasNearBottom = previousCount == 0 || previousLastVisible >= previousCount - 2;
    if (input != null) draft = input.getText();
    bg.clear();
    content.clear();
    overlay.clear();
    selectionOverlay.clear();
    composerActionIcon = null;
    attachmentPreviewBackground = null;
    attachmentPreviewTextComponent = null;
    attachmentPreviewRemove = null;
    attachmentPreviewSend = null;
    float attachmentPanelHeight = attachmentPanelVisible ? dp(112) : 0;
    float w = getWidth();
    float scale = w / 1080f;
    float top = topInset + floatingCallInset;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelTop = screenBottom - attachmentPanelHeight;
    float composerBottom = attachmentPanelTop - 40f * scale;
    float composerHeight = composerHeightForText(draft, scale);
    float composerTop = composerBottom - composerHeight;
    float microphoneBottom = attachmentPanelTop - 40f * scale;
    float microphoneTop = microphoneBottom - 134f * scale;
    float nextHeaderBottom = top + 170f * scale;
    renderedComposerHeight = composerHeight;
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
    float messageComposerGap = 20f;
    float listBottom = composerTop - replyHeight - previewHeight - messageComposerGap;
    baseListBottom = listBottom;
    float messageListTop = messageListTop();
    list =
        content.add(
            new ComponentList.Builder<MessageEntity>(
                    getContext(), "messages", new RectF(0, messageListTop, w, listBottom))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSizeProvider(
                    (message, position) -> adapter.rowHeight(message, position, w - dp(24)))
                .setPaddingPx(0, dp(8), 0, dp(12))
                .setAdapter(adapter)
                .setClipToBounds(true)
                .setScrollBarEnabled(true)
                .setOverscrollEnabled(false)
                .setOnItemLongClickListener(
                    (componentList, message, position) -> {
                      toggleMessageSelection(message);
                      return true;
                    })
                .setOnItemClickListener(
                    (componentList, message, position) -> {
                      if (isSelectingMessages()) toggleMessageSelection(message);
                      else listener.onMessageClick(message);
                    }));
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
    float olderLoadingHeight = olderLoadingHeight();
    olderLoadingBackground =
        overlay.add(
            new Image.Builder(
                    getContext(), "older_loading_background", white,
                    new RectF(0, headerBottom, w, headerBottom + olderLoadingHeight))
                .setScaleType(Image.ScaleType.FIT_XY));
    float olderProgressSize = dp(18);
    float olderGroupWidth = dp(218);
    float olderGroupLeft = (w - olderGroupWidth) / 2f;
    float olderProgressTop = headerBottom + (olderLoadingHeight - olderProgressSize) / 2f;
    olderProgress =
        overlay.add(
            new Progress.Builder(
                    getContext(), "older_message_progress",
                    new RectF(olderGroupLeft, olderProgressTop,
                        olderGroupLeft + olderProgressSize,
                        olderProgressTop + olderProgressSize))
                .setStyle(Progress.Style.CIRCULAR)
                .setMode(Progress.Mode.INDETERMINATE)
                .setProgressColor(ACCENT)
                .setTrackColor(0x22019CC4)
                .setThickness(dp(2))
                .setIndeterminateDuration(850L));
    olderStatus =
        text(
            overlay,
            "older_status",
            loadingOlderMessages ? "Loading older messages..." : "",
            new RectF(olderGroupLeft + olderProgressSize + dp(10), headerBottom,
                olderGroupLeft + olderGroupWidth, headerBottom + olderLoadingHeight),
            sp(13),
            SECONDARY,
            FontVariation.MEDIUM,
            Text.Alignment.START);
    updateOlderLoadingChrome();
    float replyTop = replyPreview.isEmpty()
        ? composerTop - previewHeight
        : listBottom + messageComposerGap;
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
      attachmentPreviewBackground = overlay.add(
          new Image.Builder(
                  getContext(), "attachment_preview_bg", white,
                  new RectF(0, previewTop, w, composerTop))
              .setScaleType(Image.ScaleType.FIT_XY));
      attachmentPreviewTextComponent = text(
          overlay,
          "attachment_preview_text",
          attachmentPreviewType + "\n" + attachmentPreviewName,
          new RectF(dp(16), previewTop + dp(8), w - dp(132), composerTop - dp(8)),
          sp(13),
          PRIMARY,
          FontVariation.SEMI_BOLD,
          Text.Alignment.START);
      attachmentPreviewRemove = button(
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
      attachmentPreviewSend = button(
          overlay,
          "attachment_preview_send",
          accent,
          "Send",
          new RectF(w - dp(78), previewTop + dp(12), w - dp(8), composerTop - dp(12)),
          Color.WHITE,
          id -> listener.onSend());
    }
    composerBackground = overlay.add(
        new ComposerBackgroundComponent(
            "composer_background",
            new RectF(30f * scale, composerTop, 898f * scale, composerBottom),
            64f * scale,
            3f * scale));
    input =
        overlay.add(
            new TextField.Builder(
                    getContext(),
                    "composer",
                    new RectF(155f * scale, composerTop + 8f * scale,
                        885f * scale, composerBottom - 8f * scale))
                .setText(draft)
                .setHint("Message")
                .setMaxLength(4000)
                .setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .setImeOptions(EditorInfo.IME_ACTION_NONE
                    | EditorInfo.IME_FLAG_NO_ENTER_ACTION)
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
                      updateComposerActionIcon();
                      scheduleComposerResize();
                    }));
    input.setMultilineBottomEndInsetPx(189f * scale);
    iconButton(
        overlay,
        "emoji",
        emojiIcon,
        new RectF(68f * scale, composerBottom - 94f * scale,
            123f * scale, composerBottom - 39f * scale),
        new RectF(46f * scale, composerBottom - 116f * scale,
            145f * scale, composerBottom - 17f * scale),
        id -> openEmojiKeyboard());
    iconButton(
        overlay,
        "attachment",
        attachmentIcon,
        new RectF(712f * scale, composerBottom - 94f * scale,
            767f * scale, composerBottom - 39f * scale),
        new RectF(690f * scale, composerBottom - 116f * scale,
            789f * scale, composerBottom - 17f * scale),
        id -> toggleAttachmentPanel());
    iconButton(
        overlay,
        "camera",
        cameraIcon,
        new RectF(805f * scale, composerBottom - 94f * scale,
            860f * scale, composerBottom - 39f * scale),
        new RectF(783f * scale, composerBottom - 116f * scale,
            882f * scale, composerBottom - 17f * scale),
        id -> selectAttachment("Image"));
    composerActionIcon =
        overlay.add(
            new Image.Builder(
                    getContext(), "send_icon",
                    hasComposerContent() ? sendIcon : microphoneIcon,
                    new RectF(916f * scale, microphoneTop,
                        1050f * scale, microphoneBottom))
                .setScaleType(Image.ScaleType.FIT_CENTER));
    send =
        overlay.add(
            new Button.Builder(
                    getContext(), "send_touch", transparent, "",
                    new RectF(894f * scale, microphoneTop - 12f * scale,
                        1072f * scale, microphoneBottom + 12f * scale))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadiusPx(0)
                .setRippleEnabled(true)
                .setRippleColor(0x16019CC4)
                .setOnClickListener(id -> listener.onSend()));
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
    if (isSelectingMessages()) buildMessageSelectionHeader(w, top, scale);
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!empty);
    status.setVisible(empty && !statusValue.isEmpty());
    if (!empty) {
      int anchorPosition = adapter.indexOfMessage(previousAnchorId);
      if (wasNearBottom || anchorPosition < 0) list.scrollToPosition(adapter.getItemCount() - 1);
      else list.scrollToPosition(anchorPosition);
    }
    applyKeyboardInsets();
    invalidate();
  }

  private void buildMessageSelectionHeader(float width, float top, float scale) {
    List<MessageEntity> selected = selectedMessages();
    selectionOverlay.add(
        new Image.Builder(
                getContext(), "message_selection_status_bar", selectionStatusBarBackground,
                new RectF(0f, 0f, width, top))
            .setScaleType(Image.ScaleType.FIT_XY));
    selectionOverlay.add(
        new Image.Builder(
                getContext(), "message_selection_header", selectionBackground,
                new RectF(0f, top, width, top + 170f * scale))
            .setScaleType(Image.ScaleType.FIT_XY));
    selectionOverlay.add(
        new Button.Builder(
                getContext(), "message_selection_header_touch", transparent, "",
                new RectF(0f, top, width, top + 170f * scale))
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setRippleEnabled(false)
            .setOnClickListener(id -> { }));
    iconButton(
        selectionOverlay,
        "message_selection_back",
        backIcon,
        new RectF(51f * scale, top + 60f * scale, 102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale, 128f * scale, top + 137f * scale),
        id -> clearMessageSelection());
    text(
        selectionOverlay,
        "message_selection_count",
        String.valueOf(selected.size()),
        new RectF(165f * scale, top + 57f * scale, 300f * scale, top + 117f * scale),
        50f * scale,
        SECONDARY,
        FontVariation.MEDIUM,
        Text.Alignment.START);
    if (selected.size() == 1) {
      selectionAction("reply", selectionReplyIcon, 535f, top, scale,
          () -> listener.onReplySelected(selected.get(0)));
    }
    selectionAction("copy", selectionCopyIcon, 645f, top, scale,
        () -> listener.onCopySelected(selected));
    selectionAction("forward", selectionForwardIcon, 755f, top, scale,
        () -> listener.onForwardSelected(selected));
    boolean allPinned = !selected.isEmpty();
    for (MessageEntity message : selected) allPinned &= message.pinned;
    final boolean unpin = allPinned;
    selectionAction(unpin ? "unpin" : "pin",
        unpin ? selectionUnpinIcon : selectionPinIcon, 865f, top, scale,
        () -> {
          if (unpin) listener.onUnpinSelected(selected);
          else listener.onPinSelected(selected);
        });
    selectionAction("delete", selectionDeleteIcon, 975f, top, scale,
        () -> listener.onDeleteSelected(selected));
  }

  private void selectionAction(
      String id, Bitmap icon, float centerX, float top, float scale, Runnable action) {
    float half = 25.5f;
    iconButton(
        selectionOverlay,
        "message_selection_" + id,
        icon,
        new RectF((centerX - half) * scale, top + 60f * scale,
            (centerX + half) * scale, top + 111f * scale),
        new RectF((centerX - 49f) * scale, top + 35f * scale,
            (centerX + 49f) * scale, top + 136f * scale),
        value -> action.run());
  }

  private void applyKeyboardInsets() {
    if (list == null || getWidth() <= 0) return;
    float shift = imeVisible ? -Math.max(0, imeInset - bottomInset) : 0;
    overlay.setTranslationY(shift);
    float listBottom = Math.max(headerBottom + dp(1), baseListBottom + shift);
    RectF nextBounds = new RectF(0, messageListTop(), getWidth(), listBottom);
    RectF currentBounds = list.getBounds();
    if (!sameBounds(currentBounds, nextBounds)) list.setRegion(nextBounds);
    invalidate();
  }

  private float composerHeightForText(String value, float scale) {
    float minimumHeight = 134f * scale;
    String measuredValue = value == null || value.isEmpty() ? "Message" : value;
    int textWidth = Math.max(1, (int) Math.floor(730f * scale));
    int bottomEndInset = Math.max(0, Math.round(189f * scale));
    StaticLayout layout = composerTextLayout(measuredValue, textWidth, bottomEndInset);
    float visibleTextHeight = layout.getHeight();
    if (layout.getLineCount() > MAX_VISIBLE_COMPOSER_LINES) {
      visibleTextHeight = layout.getLineBottom(MAX_VISIBLE_COMPOSER_LINES - 1);
    }
    float desiredHeight =
        (float) Math.ceil(visibleTextHeight + dp(16) + 16f * scale);

    float top = topInset + floatingCallInset;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelHeight = attachmentPanelVisible ? dp(112) : 0f;
    float composerBottom = screenBottom - attachmentPanelHeight - 40f * scale;
    float headerEdge = top + 170f * scale;
    float composerChromeAbove = dp(72) + 20f;
    if (!replyPreview.isEmpty()) composerChromeAbove += dp(42);
    if (!attachmentPreviewType.isEmpty()) composerChromeAbove += dp(72);
    float maximumHeight = Math.max(
        minimumHeight, composerBottom - headerEdge - composerChromeAbove);
    return Math.min(maximumHeight, Math.max(minimumHeight, desiredHeight));
  }

  private StaticLayout composerTextLayout(String value, int width, int bottomEndInset) {
    StaticLayout layout =
        StaticLayout.Builder.obtain(value, 0, value.length(), composerMeasurePaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build();
    if (bottomEndInset <= 0) return layout;
    int lineCount = Math.max(1, layout.getLineCount());
    int[] rightIndents = new int[lineCount];
    rightIndents[lineCount - 1] = Math.min(width - 1, bottomEndInset);
    return StaticLayout.Builder.obtain(value, 0, value.length(), composerMeasurePaint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setIndents(null, rightIndents)
        .build();
  }

  private void scheduleComposerResize() {
    if (getWidth() <= 0 || getHeight() <= 0) return;
    float desiredHeight = composerHeightForText(draft, getWidth() / 1080f);
    if (Math.abs(desiredHeight - renderedComposerHeight) < .5f) {
      invalidate();
      return;
    }
    if (input != null && composerBackground != null && list != null) {
      applyComposerHeight(desiredHeight);
      invalidate();
      return;
    }
    if (composerResizePosted) return;
    composerResizePosted = true;
    post(
        () -> {
          composerResizePosted = false;
          if (input == null || getWidth() <= 0 || getHeight() <= 0) return;
          draft = input.getText();
          float currentDesiredHeight = composerHeightForText(draft, getWidth() / 1080f);
          if (Math.abs(currentDesiredHeight - renderedComposerHeight) < .5f) return;
          applyComposerHeight(currentDesiredHeight);
        });
  }

  private boolean hasComposerContent() {
    return (draft != null && !draft.isEmpty())
        || (attachmentPreviewType != null && !attachmentPreviewType.isEmpty());
  }

  private void updateComposerActionIcon() {
    if (composerActionIcon != null) {
      composerActionIcon.setBitmap(hasComposerContent() ? sendIcon : microphoneIcon);
      invalidate();
    }
  }

  private void applyComposerHeight(float composerHeight) {
    if (input == null || composerBackground == null || list == null) return;
    float width = getWidth();
    float scale = width / 1080f;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelHeight = attachmentPanelVisible ? dp(112) : 0f;
    float attachmentPanelTop = screenBottom - attachmentPanelHeight;
    float composerBottom = attachmentPanelTop - 40f * scale;
    float composerTop = composerBottom - composerHeight;
    float replyHeight = replyPreview.isEmpty() ? 0f : dp(42);
    float previewHeight = attachmentPreviewType.isEmpty() ? 0f : dp(72);
    float listBottom = composerTop - replyHeight - previewHeight - 20f;

    renderedComposerHeight = composerHeight;
    composerBackground.setBounds(
        new RectF(30f * scale, composerTop, 898f * scale, composerBottom));
    input.setRegion(
            new RectF(155f * scale, composerTop + 8f * scale,
                885f * scale, composerBottom - 8f * scale))
        .setMultilineBottomEndInsetPx(189f * scale);

    baseListBottom = listBottom;
    if (replyText != null && !replyPreview.isEmpty()) {
      float replyTop = listBottom + 20f;
      replyText.setRegion(
          new RectF(dp(16), replyTop, width - dp(16), replyTop + replyHeight));
    }
    if (attachmentPreviewBackground != null) {
      float previewTop = composerTop - previewHeight;
      attachmentPreviewBackground.setRegion(new RectF(0, previewTop, width, composerTop));
      attachmentPreviewTextComponent.setRegion(
          new RectF(dp(16), previewTop + dp(8), width - dp(132), composerTop - dp(8)));
      attachmentPreviewRemove.setRegion(
          new RectF(width - dp(128), previewTop + dp(12),
              width - dp(82), composerTop - dp(12)));
      attachmentPreviewSend.setRegion(
          new RectF(width - dp(78), previewTop + dp(12),
              width - dp(8), composerTop - dp(12)));
    }
    applyKeyboardInsets();
  }

  private void openEmojiKeyboard() {
    if (attachmentPanelVisible) {
      if (input != null) draft = input.getText();
      attachmentPanelVisible = false;
      build();
    }
    if (input == null) return;
    requestFocus();
    input.requestFocus();
    post(() -> {
      if (input == null) return;
      input.requestFocus();
      InputMethodManager keyboard =
          (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (keyboard != null) {
        keyboard.restartInput(this);
        keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
      }
    });
  }

  private static boolean sameBounds(RectF first, RectF second) {
    return Math.abs(first.left - second.left) < .5f
        && Math.abs(first.top - second.top) < .5f
        && Math.abs(first.right - second.right) < .5f
        && Math.abs(first.bottom - second.bottom) < .5f;
  }

  private void updateOlderLoadingChrome() {
    boolean visible = shouldShowOlderLoading();
    if (visible && list != null) list.stopScroll();
    if (olderLoadingBackground != null) olderLoadingBackground.setVisible(visible);
    if (olderProgress != null) olderProgress.setVisible(visible);
    if (olderStatus != null) {
      olderStatus.setText(visible ? "Loading older messages..." : "").setVisible(visible);
    }
    if (list != null) applyKeyboardInsets();
    else invalidate();
  }

  private boolean shouldShowOlderLoading() {
    return loadingOlderMessages && adapter.getItemCount() > 0;
  }

  private float messageListTop() {
    return headerBottom + (shouldShowOlderLoading() ? olderLoadingHeight() : 0f);
  }

  private float olderLoadingHeight() {
    return dp(48);
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
    long drawStartedNanos = SystemClock.elapsedRealtimeNanos();
    layers.draw(c);
    if (profiler != null) {
      int first = list == null ? -1 : list.getFirstVisiblePosition();
      int last = list == null ? -1 : list.getLastVisiblePosition();
      int count = adapter.getItemCount();
      profiler.viewDraw(SystemClock.elapsedRealtimeNanos() - drawStartedNanos, count, first, last);
      profiler.scrollProgress(first, last, count);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      loadingGestureStartY = e.getY();
      loadingGestureBlocked = false;
      olderLoadRequestedForGesture = false;
      profileGestureStartY = e.getY();
      profileScrollCandidate = list != null && list.getBounds().contains(e.getX(), e.getY());
      profileScrollStarted = false;
      removeCallbacks(finishScrollProfile);
    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
        && !loadingGestureBlocked && shouldShowOlderLoading()
        && e.getY() - loadingGestureStartY
            > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
      MotionEvent cancel = MotionEvent.obtain(e);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      layers.onTouchEvent(cancel);
      cancel.recycle();
      loadingGestureBlocked = true;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_MOVE && profileScrollCandidate
        && Math.abs(e.getY() - profileGestureStartY)
            > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
      if (!profileScrollStarted && profiler != null) {
        profiler.scrollStart(
            list.getFirstVisiblePosition(), list.getLastVisiblePosition(), adapter.getItemCount());
      }
      profileScrollStarted = true;
      removeCallbacks(finishScrollProfile);
    } else if ((e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) && profileScrollStarted) {
      removeCallbacks(finishScrollProfile);
      postDelayed(finishScrollProfile, 1500L);
      profileScrollCandidate = false;
      profileScrollStarted = false;
    }
    if (loadingGestureBlocked) {
      if (e.getActionMasked() == MotionEvent.ACTION_UP
          || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
        loadingGestureBlocked = false;
      }
      return true;
    }
    boolean handled = layers.onTouchEvent(e);
    if (handled) loadOlderMessagesIfNeeded();
    return handled || super.onTouchEvent(e);
  }

  private void loadOlderMessagesIfNeeded() {
    if (list == null || adapter.getItemCount() == 0
        || olderLoadRequestedForGesture || loadingOlderMessages || !canLoadOlderMessages) return;
    if (list.getFirstVisiblePosition() <= 2) {
      olderLoadRequestedForGesture = true;
      listener.onLoadOlderMessages();
    }
  }

  private void finishScrollProfile() {
    if (profiler == null || list == null) return;
    profiler.scrollEnd(
        list.getFirstVisiblePosition(), list.getLastVisiblePosition(), adapter.getItemCount());
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
    removeCallbacks(finishScrollProfile);
    finishScrollProfile();
    layers.release();
    adapter.release();
    recycle(
        white, transparent, divider, accent, attachmentOption, profile,
        conversationBackground, headerBackground, microphoneIcon, sendIcon, emojiIcon,
        attachmentIcon, cameraIcon, backIcon, voiceCallIcon, videoCallIcon, moreIcon,
        selectionBackground, selectionStatusBarBackground, messageSelectionBackground,
        selectionReplyIcon,
        selectionCopyIcon, selectionForwardIcon, selectionPinIcon, selectionUnpinIcon,
        selectionDeleteIcon,
        messageSendingIcon, messageSentIcon, messageDeliveredIcon, messageReadIcon);
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

  private Bitmap namedDrawableBitmap(String name) {
    int resource = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
    return resource == 0 ? color(Color.TRANSPARENT) : resourceBitmap(resource);
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

    void onMessageClick(MessageEntity message);

    void onReplySelected(MessageEntity message);

    void onCopySelected(List<MessageEntity> messages);

    void onForwardSelected(List<MessageEntity> messages);

    void onPinSelected(List<MessageEntity> messages);

    void onUnpinSelected(List<MessageEntity> messages);

    void onDeleteSelected(List<MessageEntity> messages);

    void onMessageSelectionChanged(boolean selected);

    void onLoadOlderMessages();

    /** 0 = locally available, 1 = needs download, 2 = downloading. */
    int attachmentState(MessageEntity message);
  }
}
