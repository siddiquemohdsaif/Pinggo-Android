package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatHeaderComponent;
import com.w3n.pinggo.views.home.HomeMenuDialogView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full conversation attachment browser, split into Media, Docs and Links. */
public final class ChatMediaActivity extends AppCompatActivity {
  public static final String EXTRA_CHAT_ID = "pinggo.media.CHAT_ID";
  public static final String EXTRA_CHAT_NAME = "pinggo.media.CHAT_NAME";
  private static final Pattern LINK = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

  private final List<JsonObject> records = new ArrayList<>();
  private final Set<String> recordIds = new HashSet<>();
  private final Map<String, ImageView> images = new HashMap<>();
  private final Map<String, MessageEntity> messages = new HashMap<>();
  private final ExecutorService thumbnails = Executors.newFixedThreadPool(2);
  private String chatId;
  private String userId;
  private String selected = "media";
  private Long cursor;
  private boolean loading;
  private boolean hasMore = true;
  private boolean loadingStored = true;
  private LinearLayout content;
  private TextView mediaTab;
  private TextView docsTab;
  private TextView linksTab;
  private ChatRepository repository;
  private ProgressBar pageProgress;
  private HomeMenuDialogView mediaMenu;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(0xFFF9FBFE);
    getWindow().setNavigationBarColor(0xFFF9FBFE);
    chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
    userId = LoginStateManager.getInstance().getUID(this);
    repository = ChatRepository.getInstance(this);
    setContentView(build());
    mediaMenu = new HomeMenuDialogView(this,
        java.util.Arrays.asList("Media", "Docs", "Links"), index -> {
          selected = index == 0 ? "media" : index == 1 ? "docs" : "links";
          updateTabs();
          render();
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(mediaMenu,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    repository.observeTransfers(chatId).observe(this, this::completedTransfers);
    loadNext();
  }

  private View build() {
    LinearLayout root = column();
    root.setBackgroundColor(0xFFF9FBFE);
    String title = getIntent().getStringExtra(EXTRA_CHAT_NAME);
    root.addView(ChatHeaderComponent.detailsHeader(this,
        title == null || title.isEmpty() ? "Chat media" : title,
        this::finish, this::showMediaMenu), new LinearLayout.LayoutParams(-1, -2));

    LinearLayout tabs = row();
    tabs.setWeightSum(3);
    mediaTab = tab("Media", "media");
    docsTab = tab("Docs", "docs");
    linksTab = tab("Links", "links");
    tabs.addView(mediaTab, weighted());
    tabs.addView(docsTab, weighted());
    tabs.addView(linksTab, weighted());
    root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(52)));
    updateTabs();

    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xFFF7F9FB);
    content = column();
    content.setPadding(dp(12), dp(12), dp(12), dp(28));
    scroll.addView(content);
    scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
      if (!scroll.canScrollVertically(1))
        loadNext();
    });
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
      v.setPadding(0, bars.top, 0, Math.max(bars.bottom, ime.bottom));
      return insets;
    });
    return root;
  }

  private void showMediaMenu(View anchor) {
    if (mediaMenu != null) mediaMenu.show();
  }

  private TextView tab(String caption, String value) {
    TextView tab = label(caption, 15, true);
    tab.setGravity(Gravity.CENTER);
    tab.setOnClickListener(v -> {
      selected = value;
      updateTabs();
      render();
    });
    return tab;
  }

  private void updateTabs() {
    mediaTab.setTextColor("media".equals(selected) ? 0xFF009D85 : 0xFF687382);
    docsTab.setTextColor("docs".equals(selected) ? 0xFF009D85 : 0xFF687382);
    linksTab.setTextColor("links".equals(selected) ? 0xFF009D85 : 0xFF687382);
  }

  private void loadNext() {
    if (loading || !hasMore)
      return;
    loading = true;
    showPageProgress();
    if (loadingStored) {
      repository.loadStoredMedia(chatId, 30, cursor, (values, next, more) -> {
        loading = false;
        for (MessageEntity value : values)
          addRecord(record(value));
        if (!values.isEmpty())
          cursor = values.get(values.size() - 1).sentTime;
        if (!more)
          loadingStored = false;
        render();
      });
      return;
    }
    AppFunctionManager.getInstance().getChatMedia(userId, chatId, 30, cursor,
        new AppFunctionManager.Callback() {
          @Override
          public void onSuccess(Object result) {
            runOnUiThread(() -> {
              loading = false;
              JsonObject root = result instanceof JsonObject ? (JsonObject) result : new JsonObject();
              JsonArray page = root.has("media") && root.get("media").isJsonArray()
                  ? root.getAsJsonArray("media")
                  : new JsonArray();
              for (JsonElement item : page)
                if (item.isJsonObject())
                  addRecord(item.getAsJsonObject());
              hasMore = root.has("hasMore") && root.get("hasMore").getAsBoolean();
              cursor = root.has("nextCursor") && !root.get("nextCursor").isJsonNull()
                  ? root.get("nextCursor").getAsLong()
                  : null;
              render();
            });
          }

          @Override
          public void onError(String error) {
            runOnUiThread(() -> {
              loading = false;
              removePageProgress();
              Toast.makeText(ChatMediaActivity.this, error, Toast.LENGTH_LONG).show();
            });
          }
        });
  }

  private void addRecord(JsonObject record) {
    String id = string(record, "id");
    if (id.isEmpty() || recordIds.add(id))
      records.add(record);
  }

  private JsonObject record(MessageEntity message) {
    JsonObject value = new JsonObject();
    value.addProperty("id", message.messageId);
    value.addProperty("clientMessageId", message.clientMessageId);
    value.addProperty("chatId", message.chatId);
    value.addProperty("senderId", message.senderId);
    value.addProperty("receiverId", message.receiverId);
    value.addProperty("text", message.text);
    value.addProperty("messageType", message.messageType);
    value.addProperty("sentTime", message.sentTime);
    if (message.attachmentId != null) {
      JsonObject a = new JsonObject();
      a.addProperty("id", message.attachmentId);
      a.addProperty("kind", message.attachmentKind);
      a.addProperty("name", message.attachmentName);
      a.addProperty("mimeType", message.attachmentMimeType);
      a.addProperty("url", message.attachmentUrl);
      a.addProperty("sha256", message.attachmentSha256);
      if (message.attachmentSize != null)
        a.addProperty("size", message.attachmentSize);
      value.add("attachment", a);
    }
    return value;
  }

  private void render() {
    content.removeAllViews();
    images.clear();
    messages.clear();
    if ("links".equals(selected))
      renderLinks();
    else
      renderTiles();
    removePageProgress();
  }

  private void showPageProgress() {
    if (pageProgress != null)
      return;
    pageProgress = new ProgressBar(this);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
    params.gravity = Gravity.CENTER_HORIZONTAL;
    params.topMargin = dp(16);
    params.bottomMargin = dp(16);
    content.addView(pageProgress, params);
  }

  private void removePageProgress() {
    if (pageProgress == null)
      return;
    if (pageProgress.getParent() == content)
      content.removeView(pageProgress);
    pageProgress = null;
  }

  private void renderTiles() {
    GridLayout grid = new GridLayout(this);
    grid.setColumnCount("media".equals(selected) ? 3 : 2);
    int count = 0;
    for (JsonObject record : records) {
      String type = string(record, "messageType");
      boolean accepted = "media".equals(selected)
          ? "image".equalsIgnoreCase(type) || "video".equalsIgnoreCase(type)
          : "file".equalsIgnoreCase(type);
      if (!accepted)
        continue;
      count++;
      MessageEntity message = message(record);
      LinearLayout tile = column();
      tile.setGravity(Gravity.CENTER);
      tile.setPadding(dp(4), dp(4), dp(4), dp(8));
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setImageResource("file".equalsIgnoreCase(type) ? android.R.drawable.ic_menu_save
          : android.R.drawable.ic_menu_gallery);
      tile.addView(image, new LinearLayout.LayoutParams(-1, dp("media".equals(selected) ? 112 : 82)));
      tile.addView(label(message.attachmentName == null || message.attachmentName.isEmpty()
          ? type
          : message.attachmentName, 12, false));
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = 0;
      params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      params.setMargins(dp(3), dp(3), dp(3), dp(3));
      grid.addView(tile, params);
      if (message.attachmentId != null && !message.attachmentId.isEmpty()) {
        images.put(message.attachmentId, image);
        messages.put(message.attachmentId, message);
        repository.downloadAttachment(message, callback(image, message));
      }
    }
    content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    if (count == 0)
      content.addView(empty("No " + selected + " found"));
  }

  private void renderLinks() {
    int count = 0;
    for (JsonObject record : records) {
      Matcher matcher = LINK.matcher(string(record, "text"));
      while (matcher.find()) {
        count++;
        TextView link = label(matcher.group(), 15, false);
        link.setTextColor(0xFF087EA4);
        String url = matcher.group();
        link.setPadding(dp(8), dp(16), dp(8), dp(16));
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        content.addView(link, new LinearLayout.LayoutParams(-1, -2));
      }
    }
    if (count == 0)
      content.addView(empty("No links found"));
  }

  private ChatRepository.DownloadCallback callback(ImageView image, MessageEntity message) {
    return new ChatRepository.DownloadCallback() {
      @Override
      public void onAvailable(Uri uri) {
        thumbnail(image, uri, message);
      }

      @Override
      public void onQueued() {
        image.setImageResource(android.R.drawable.stat_sys_download);
      }

      @Override
      public void onError(String error) {
        image.setImageResource(android.R.drawable.ic_dialog_alert);
      }
    };
  }

  private void completedTransfers(List<TransferEntity> values) {
    if (values == null)
      return;
    for (TransferEntity transfer : values) {
      if (transfer == null || transfer.attachmentId == null || transfer.localUri == null
          || !"completed".equalsIgnoreCase(transfer.status))
        continue;
      ImageView image = images.get(transfer.attachmentId);
      MessageEntity message = messages.get(transfer.attachmentId);
      if (image != null && message != null)
        thumbnail(image, Uri.parse(transfer.localUri), message);
    }
  }

  private void thumbnail(ImageView image, Uri uri, MessageEntity message) {
    if ("file".equalsIgnoreCase(message.messageType)) {
      image.setImageResource(android.R.drawable.ic_menu_save);
      return;
    }
    thumbnails.execute(() -> {
      Bitmap bitmap = null;
      try {
        if ("video".equalsIgnoreCase(message.messageType)) {
          MediaMetadataRetriever retriever = new MediaMetadataRetriever();
          try {
            retriever.setDataSource(this, uri);
            bitmap = retriever.getFrameAtTime(0);
          } finally {
            retriever.release();
          }
        } else
          try (InputStream stream = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            bitmap = BitmapFactory.decodeStream(stream, null, options);
          }
      } catch (Exception ignored) {
      }
      Bitmap result = bitmap;
      runOnUiThread(() -> {
        if (result != null)
          image.setImageBitmap(result);
      });
    });
  }

  private MessageEntity message(JsonObject record) {
    MessageEntity value = new MessageEntity();
    value.messageId = string(record, "id");
    value.clientMessageId = string(record, "clientMessageId");
    value.chatId = chatId;
    value.senderId = string(record, "senderId");
    value.receiverId = string(record, "receiverId");
    value.setMessageType(string(record, "messageType"));
    JsonObject a = object(record, "attachment");
    if (a != null) {
      value.attachmentId = string(a, "id");
      value.attachmentKind = string(a, "kind");
      value.attachmentName = string(a, "name");
      value.attachmentMimeType = string(a, "mimeType");
      value.attachmentUrl = string(a, "url");
      value.attachmentSha256 = string(a, "sha256");
      if (a.has("size") && !a.get("size").isJsonNull())
        value.attachmentSize = a.get("size").getAsLong();
    }
    return value;
  }

  private TextView empty(String text) {
    TextView view = label(text, 15, false);
    view.setGravity(Gravity.CENTER);
    view.setPadding(0, dp(48), 0, 0);
    return view;
  }

  private static JsonObject object(JsonObject p, String key) {
    return p.has(key) && p.get(key).isJsonObject() ? p.getAsJsonObject(key) : null;
  }

  private static String string(JsonObject o, String key) {
    try {
      return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.HORIZONTAL);
    return v;
  }

  private TextView label(String text, int size, boolean bold) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(0xFF07131E);
    if (bold)
      v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return v;
  }

  private LinearLayout.LayoutParams weighted() {
    return new LinearLayout.LayoutParams(0, -1, 1f);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onDestroy() {
    if (mediaMenu != null) mediaMenu.release();
    mediaMenu = null;
    thumbnails.shutdownNow();
    super.onDestroy();
  }
}
