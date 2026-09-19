package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatHeaderComponent;
import com.w3n.pinggo.views.chat.MediaAttachmentOpener;
import com.w3n.pinggo.views.chat.MediaRecordTypes;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Full conversation attachment browser, split into Media, Docs and Links. */
public final class ChatMediaActivity extends AppCompatActivity {
  public static final String EXTRA_CHAT_ID = "pinggo.media.CHAT_ID";
  public static final String EXTRA_CHAT_NAME = "pinggo.media.CHAT_NAME";
  private static final int PAGE_SIZE = 10;
  private static final Pattern LINK = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

  private final List<JsonObject> records = new ArrayList<>();
  private final Set<String> recordIds = new HashSet<>();
  private final Map<String, NativeImageSlot> images = new HashMap<>();
  private final Map<String, MessageEntity> messages = new HashMap<>();
  private String chatId;
  private String userId;
  private String selected = "media";
  private Long cursor;
  private boolean loading;
  private boolean hasMore = true;
  private boolean pagingFailed;
  private GridLayout mediaGrid;
  private int renderedGeneration = -1;
  private LinearLayout content;
  private NativeTextSlot mediaTab;
  private NativeTextSlot docsTab;
  private NativeTextSlot linksTab;
  private ChatRepository repository;
  private NativeProgressSlot pageProgress;
  private HomeMenuDialogView mediaMenu;
  private MediaAttachmentOpener attachmentOpener;
  private int pageGeneration;
  private ComponentScrollHost mediaScroll;
  private FrameLayout mediaViewport;
  private int traceScrollY;
  private int tracePage;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(0xFFF9FBFE);
    getWindow().setNavigationBarColor(0xFFF9FBFE);
    chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
    userId = LoginStateManager.getInstance().getUID(this);
    repository = ChatRepository.getInstance(this);
    attachmentOpener = new MediaAttachmentOpener(this, repository, chatId);
    setContentView(build());
    mediaMenu = new HomeMenuDialogView(this,
        java.util.Arrays.asList("Media", "Docs", "Links"), index -> {
          selectCategory(index == 0 ? "media" : index == 1 ? "docs" : "links");
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(mediaMenu,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    repository.observeTransfers(chatId).observe(this, this::completedTransfers);
    repository.observeLocalAttachments(chatId).observe(this, values -> {
      if (values == null) return;
      for (MessageEntity stored : values) {
        NativeImageSlot image = images.get(stored.attachmentId);
        MessageEntity message = messages.get(stored.attachmentId);
        if (image != null && message != null && stored.attachmentLocalUri != null)
          thumbnail(image, Uri.parse(stored.attachmentLocalUri), message);
      }
    });
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

    ComponentScrollHost scroll = new ComponentScrollHost();
    mediaScroll = scroll;
    scroll.setBackgroundColor(0xFFF7F9FB);
    content = column();
    content.setPadding(dp(12), dp(12), dp(12), dp(28));
    scroll.setScrollContent(content);
    content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (b - t != ob - ot) traceMedia("layout", "oldHeight=" + (ob - ot));
    });
    scroll.setOnOffsetChanged(() -> {
      int y = scroll.currentScrollY();
      if (y != traceScrollY) {
        traceMedia("scroll", "deltaY=" + (y - traceScrollY));
        traceScrollY = y;
      }
      pagingFailed = false;
      maybeLoadNext();
    });
    mediaViewport = new FrameLayout(this);
    mediaViewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    root.addView(mediaViewport, new LinearLayout.LayoutParams(-1, 0, 1f));
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

  private NativeTextSlot tab(String caption, String value) {
    NativeTextSlot tab = label(caption, 15, true);
    tab.setGravity(Gravity.CENTER);
    tab.setOnClickListener(v -> {
      selectCategory(value);
    });
    return tab;
  }

  private void updateTabs() {
    mediaTab.setTextColor("media".equals(selected) ? 0xFF009D85 : 0xFF687382);
    docsTab.setTextColor("docs".equals(selected) ? 0xFF009D85 : 0xFF687382);
    linksTab.setTextColor("links".equals(selected) ? 0xFF009D85 : 0xFF687382);
  }

  private void selectCategory(String category) {
    if (selected.equals(category)) return;
    selected = category;
    pageGeneration++;
    records.clear();
    recordIds.clear();
    cursor = null;
    hasMore = true;
    pagingFailed = false;
    loading = false;
    removePageProgress();
    updateTabs();
    mediaScroll.scrollToStart();
    loadNext();
    render();
  }

  private void loadNext() {
    if (loading || !hasMore)
      return;
    loading = true;
    tracePage++;
    traceMedia("page_start", "");
    final int generation = pageGeneration;
    showPageProgress();
    repository.loadStoredMedia(chatId, selected, PAGE_SIZE, cursor,
        (stored, next, localHasMore) -> {
          if (generation != pageGeneration || isFinishing() || isDestroyed()) return;
          for (MessageEntity message : stored) addRecord(record(message));
          cursor = next;
          traceMedia("local_result", "received=" + stored.size() + " localHasMore=" + localHasMore);
          if (stored.size() >= PAGE_SIZE) {
            loading = false;
            hasMore = localHasMore;
            render();
            return;
          }
          render();
          loadRemotePage(generation, PAGE_SIZE - stored.size());
        });
  }

  private void loadRemotePage(int generation, int pageSize) {
    traceMedia("remote_start", "requested=" + pageSize);
    AppFunctionManager.getInstance().getChatMedia(
        userId, chatId, Math.max(1, pageSize), cursor, selected,
        new AppFunctionManager.Callback() {
          @Override
          public void onSuccess(Object result) {
            runOnUiThread(() -> {
              if (generation != pageGeneration || isFinishing() || isDestroyed()) return;
              loading = false;
              pagingFailed = false;
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
              traceMedia("remote_result", "received=" + page.size());
              render();
            });
          }

          @Override
          public void onError(String error) {
            runOnUiThread(() -> {
              if (generation != pageGeneration || isFinishing() || isDestroyed()) return;
              loading = false;
              pagingFailed = true;
              render();
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
      a.addProperty("localUri", message.attachmentLocalUri);
      if (message.attachmentSize != null)
        a.addProperty("size", message.attachmentSize);
      value.add("attachment", a);
    }
    return value;
  }

  private void render() {
    if (isFinishing() || isDestroyed()) return;
    traceMedia("render_before", "");
    removePageProgress();
    boolean appendTiles = mediaGrid != null && renderedGeneration == pageGeneration
        && !"links".equals(selected);
    if (appendTiles) {
      // Keep existing rows attached so paging preserves the current ComponentList offset.
      for (int i = content.getChildCount() - 1; i >= 0; i--)
        if (content.getChildAt(i) != mediaGrid) content.removeViewAt(i);
    } else {
      content.removeAllViews();
      images.clear();
      messages.clear();
      mediaGrid = null;
    }
    renderedGeneration = pageGeneration;
    if ("links".equals(selected))
      renderLinks();
    else
      renderTiles();
    removePageProgress();
    if (loading) showPageProgress();
    traceMedia("render_after", "");
    mediaScroll.post(() -> traceMedia("render_post", ""));
    mediaScroll.post(this::maybeLoadNext);
  }

  private void maybeLoadNext() {
    if (loading || !hasMore || pagingFailed || isFinishing() || isDestroyed()
        || mediaScroll.getHeight() <= 0) return;
    int viewportBottom = mediaScroll.currentScrollY() + mediaScroll.getHeight();
    boolean nearEnd = viewportBottom >= content.getHeight() - dp(160);
    boolean incompleteVisibleRow = false;
    if (mediaGrid != null && mediaGrid.getChildCount() > 0) {
      int count = mediaGrid.getChildCount();
      int columns = mediaGrid.getColumnCount();
      View last = mediaGrid.getChildAt(count - 1);
      incompleteVisibleRow = count % columns != 0
          && mediaGrid.getTop() + last.getTop() < viewportBottom;
    }
    if (nearEnd || incompleteVisibleRow) {
      traceMedia("auto_load", "nearEnd=" + nearEnd + " incompleteRow=" + incompleteVisibleRow);
      loadNext();
    }
  }

  /** Debug-only geometry diagnostics; deliberately excludes filenames, URLs and message text. */
  private void traceMedia(String event, String detail) {
    if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0
        || mediaScroll == null || content == null) return;
    int anchor = -1;
    int offset = 0;
    if (mediaGrid != null) {
      for (int i = 0; i < mediaGrid.getChildCount(); i++) {
        View tile = mediaGrid.getChildAt(i);
        if (mediaGrid.getTop() + tile.getBottom() > mediaScroll.currentScrollY()) {
          anchor = i;
          offset = mediaGrid.getTop() + tile.getTop() - mediaScroll.currentScrollY();
          break;
        }
      }
    }
    Log.d("PingGoMediaPaging", "event=" + event + " category=" + selected
        + " generation=" + pageGeneration + " page=" + tracePage + " records=" + records.size()
        + " tiles=" + (mediaGrid == null ? 0 : mediaGrid.getChildCount())
        + " scrollY=" + mediaScroll.currentScrollY() + " viewport=" + mediaScroll.getHeight()
        + " contentHeight=" + content.getHeight() + " anchor=" + anchor + " offset=" + offset
        + " cursor=" + cursor + " loading=" + loading + " hasMore=" + hasMore
        + " footer=" + (pageProgress != null) + " " + detail);
  }

  private void showPageProgress() {
    if (pageProgress != null)
      return;
    pageProgress = new NativeProgressSlot();
    // An overlay avoids growing/shrinking the scroll range just to show loading.
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(44));
    params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
    params.bottomMargin = dp(16);
    mediaViewport.addView(pageProgress, params);
  }

  private void removePageProgress() {
    if (pageProgress == null)
      return;
    if (pageProgress.getParent() == mediaViewport)
      mediaViewport.removeView(pageProgress);
    pageProgress.release();
    pageProgress = null;
  }

  private void renderTiles() {
    GridLayout grid = mediaGrid == null ? new GridLayout(this) : mediaGrid;
    mediaGrid = grid;
    grid.setColumnCount("media".equals(selected) ? 3 : 2);
    int existingTiles = grid.getChildCount();
    int count = 0;
    for (JsonObject record : records) {
      String type = MediaRecordTypes.type(record);
      boolean accepted = "media".equals(selected)
          ? "image".equalsIgnoreCase(type) || "video".equalsIgnoreCase(type)
          : "file".equalsIgnoreCase(type);
      if (!accepted)
        continue;
      count++;
      if (count <= existingTiles) continue;
      MessageEntity message = message(record);
      LinearLayout tile = column();
      tile.setOnClickListener(v -> attachmentOpener.open(message));
      tile.setGravity(Gravity.CENTER);
      tile.setPadding(dp(4), dp(4), dp(4), dp(8));
      NativeImageSlot image = new NativeImageSlot();
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
        if (!reuseLoadedThumbnail(image, message))
          repository.downloadAttachment(message, callback(image, message));
      }
    }
    if (grid.getParent() == null)
      content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    if (count == 0)
      content.addView(empty(loading || hasMore ? "Loading " + selected + "…" : "No " + selected + " found"));
  }

  private void renderLinks() {
    int count = 0;
    for (JsonObject record : records) {
      Matcher matcher = LINK.matcher(string(record, "text"));
      while (matcher.find()) {
        count++;
        NativeTextSlot link = label(matcher.group(), 15, false);
        link.setTextColor(0xFF087EA4);
        String url = matcher.group();
        link.setPadding(dp(8), dp(16), dp(8), dp(16));
        link.setOnClickListener(v -> {
          try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
          catch (android.content.ActivityNotFoundException unavailable) {
            Toast.makeText(this, "No browser available to open this link.", Toast.LENGTH_SHORT).show();
          }
        });
        content.addView(link, new LinearLayout.LayoutParams(-1, -2));
      }
    }
    if (count == 0)
      content.addView(empty(loading || hasMore ? "Loading links…" : "No links found"));
  }

  private ChatRepository.DownloadCallback callback(NativeImageSlot image, MessageEntity message) {
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
      NativeImageSlot image = images.get(transfer.attachmentId);
      MessageEntity message = messages.get(transfer.attachmentId);
      if (image != null && message != null) {
        thumbnail(image, Uri.parse(transfer.localUri), message);
      }
    }
  }

  private void thumbnail(NativeImageSlot image, Uri uri, MessageEntity message) {
    if (isFinishing() || isDestroyed()) return;
    String key = uri.toString();
    if (key.equals(image.getTag())) return;
    image.setTag(key);
    if ("file".equalsIgnoreCase(message.messageType)) {
      image.setImageResource(android.R.drawable.ic_menu_save);
      return;
    }
    boolean video = "video".equalsIgnoreCase(message.messageType);
    int size = dp(112);
    MediaPreviewCache.Thumbnail cached =
        MediaPreviewCache.memoryThumbnail(key, video, size, size);
    if (cached == null) cached = MediaPreviewCache.anyMemoryThumbnail(key, video);
    if (cached == null && message.attachmentUrl != null)
      cached = MediaPreviewCache.anyMemoryThumbnail(message.attachmentUrl, video);
    if (cached != null) {
      image.setImageBitmap(cached.bitmap);
      traceMedia("thumbnail_reused", "source=shared_memory_resolved");
      return;
    }
    MediaPreviewCache.loadThumbnail(this, key, video, size, size,
        new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
          @Override public void onSuccess(MediaPreviewCache.Thumbnail result) {
            if (!isFinishing() && !isDestroyed() && key.equals(image.getTag()))
              image.setImageBitmap(result.bitmap);
          }

          @Override public void onError() {
            if (!isFinishing() && !isDestroyed() && key.equals(image.getTag())) {
              image.setTag(null);
              image.setImageResource(android.R.drawable.ic_menu_gallery);
            }
          }
        });
  }

  private boolean reuseLoadedThumbnail(NativeImageSlot image, MessageEntity message) {
    if (!"image".equalsIgnoreCase(message.messageType)
        && !"video".equalsIgnoreCase(message.messageType)) return false;
    boolean video = "video".equalsIgnoreCase(message.messageType);
    String source = message.attachmentLocalUri;
    MediaPreviewCache.Thumbnail cached = MediaPreviewCache.anyMemoryThumbnail(source, video);
    if (cached == null) {
      source = message.attachmentUrl;
      cached = MediaPreviewCache.anyMemoryThumbnail(source, video);
    }
    if (cached == null) return false;
    image.setTag(source);
    image.setImageBitmap(cached.bitmap);
    traceMedia("thumbnail_reused", "source=shared_memory");
    return true;
  }

  private MessageEntity message(JsonObject record) {
    MessageEntity value = new MessageEntity();
    value.messageId = string(record, "id");
    value.clientMessageId = string(record, "clientMessageId");
    value.chatId = chatId;
    value.senderId = string(record, "senderId");
    value.receiverId = string(record, "receiverId");
    value.setMessageType(MediaRecordTypes.type(record));
    JsonObject a = object(record, "attachment");
    if (a != null) {
      value.attachmentId = string(a, "id");
      value.attachmentKind = string(a, "kind");
      value.attachmentName = string(a, "name");
      value.attachmentMimeType = string(a, "mimeType");
      value.attachmentUrl = string(a, "url");
      value.attachmentSha256 = string(a, "sha256");
      String localUri = string(a, "localUri");
      value.attachmentLocalUri = localUri.isEmpty() ? null : localUri;
      if (a.has("size") && !a.get("size").isJsonNull())
        value.attachmentSize = a.get("size").getAsLong();
    }
    return value;
  }

  private NativeTextSlot empty(String text) {
    NativeTextSlot view = label(text, 15, false);
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

  private NativeTextSlot label(String text, int size, boolean bold) {
    return new NativeTextSlot(text, size, bold);
  }

  private LinearLayout.LayoutParams weighted() {
    return new LinearLayout.LayoutParams(0, -1, 1f);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  /** Screen-owned host whose vertical scrolling is supplied by the AAR ComponentList. */
  private final class ComponentScrollHost extends FrameLayout {
    private final ZLayerGroup scrollLayers = new ZLayerGroup(this);
    private final ZLayer scrollLayer = scrollLayers.addLayer("chat_media_scroll");
    private ComponentList<String> scrollList;
    private View scrollContent;
    private Runnable offsetChanged;
    private float lastOffset;
    private float downY;
    private boolean dragging;
    ComponentScrollHost() { super(ChatMediaActivity.this); setClipChildren(true); }
    void setScrollContent(View child) {
      removeAllViews(); scrollContent=child;
      addView(child,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      child.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
        if (b-t != ob-ot || r-l != or-ol) rebuildScroll();
      });
    }
    void setOnOffsetChanged(Runnable listener) { offsetChanged=listener; }
    int currentScrollY() { return Math.round(lastOffset); }
    void scrollToStart() {
      lastOffset=0f; rebuildScroll(); syncScroll();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
      super.onSizeChanged(w,h,ow,oh); rebuildScroll();
    }
    private void rebuildScroll() {
      if(getWidth()<=0||getHeight()<=0||scrollContent==null)return;
      float offset=scrollList==null?lastOffset:scrollList.getScrollOffset();
      scrollLayer.clear();
      scrollList=scrollLayer.add(new ComponentList.Builder<String>(getContext(),"media_scroll_list",
          new RectF(0,0,getWidth(),getHeight())).setOrientation(ComponentList.Orientation.VERTICAL)
          .setItemSize(Math.max(getHeight()+1,scrollContent.getHeight()))
          .setAdapter(new ComponentList.Adapter<String>(){
            @Override public int getItemCount(){return 1;}
            @Override public String getItem(int position){return "content";}
            @Override public void onCreateItem(ComponentList.Item item,int type){item.addLayer("spacer");}
            @Override public void onBindItem(ComponentList.Item item,String value,int position){}
          }).setScrollEnabled(true).setFlingEnabled(true).setOverscrollEnabled(false)
          .setClipToBounds(true));
      if(offset>0f)scrollList.scrollBy(0f,offset);
      syncScroll();
    }
    private void syncScroll(){
      if(scrollList==null||scrollContent==null)return;
      float previous=lastOffset;
      lastOffset=scrollList.getScrollOffset();
      scrollContent.setTranslationY(-lastOffset);
      if(offsetChanged!=null&&Math.abs(previous-lastOffset)>=.5f)offsetChanged.run();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
      if(scrollList==null)return super.dispatchTouchEvent(event);
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
        downY=event.getY();dragging=false;scrollLayers.onTouchEvent(event);
        traceMedia("touch","action="+event.getActionMasked());
        return super.dispatchTouchEvent(event);
      }
      if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
        if(!dragging&&Math.abs(event.getY()-downY)>dp(6)){
          dragging=true;
          MotionEvent cancel=MotionEvent.obtain(event);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
        }
        scrollLayers.onTouchEvent(event);syncScroll();if(dragging)return true;
      }else if(event.getActionMasked()==MotionEvent.ACTION_UP
          ||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
        scrollLayers.onTouchEvent(event);syncScroll();
        traceMedia("touch","action="+event.getActionMasked());
        if(dragging){dragging=false;return true;}
      }
      return super.dispatchTouchEvent(event);
    }
    @Override protected void dispatchDraw(Canvas canvas){syncScroll();super.dispatchDraw(canvas);}
    void release(){scrollLayers.release();}
  }

  /** Activity-owned AAR text surface; no compatibility widget class is created. */
  private final class NativeTextSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_media_text");
    private String value;
    private final int sizeSp;
    private final boolean bold;
    private int color = 0xFF07131E;
    private int gravity = Gravity.START | Gravity.CENTER_VERTICAL;
    NativeTextSlot(String value, int sizeSp, boolean bold) {
      super(ChatMediaActivity.this);
      this.value = value == null ? "" : value; this.sizeSp = sizeSp; this.bold = bold;
      setClickable(false);
    }
    void setText(String text) { value = text == null ? "" : text; rebuild(); }
    void setTextColor(int color) { this.color = color; rebuild(); }
    void setGravity(int gravity) { this.gravity = gravity; rebuild(); }
    private void rebuild() {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      layer.clear();
      Text.Alignment alignment = (gravity & Gravity.CENTER_HORIZONTAL) != 0
          ? Text.Alignment.CENTER : (gravity & Gravity.END) != 0
              ? Text.Alignment.END : Text.Alignment.START;
      layer.add(new Text.Builder(getContext(), "value", value,
          new RectF(getPaddingLeft(), getPaddingTop(),
              getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()))
          .setFont(NativeFonts.INTER)
          .setFontVariations(bold ? FontVariation.BOLD : FontVariation.REGULAR)
          .setTextColor(color).setTextSizePx(sizeSp * getResources().getDisplayMetrics().scaledDensity)
          .setAlignment(alignment).setVerticalAlignment(Text.VerticalAlignment.CENTER)
          .setMaxLines(3));
      invalidate();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
      int desiredWidth = (int) Math.ceil(paint.measureText(value)) + getPaddingLeft() + getPaddingRight();
      int desiredHeight = (int) Math.ceil(paint.getFontMetrics().descent - paint.getFontMetrics().ascent)
          + getPaddingTop() + getPaddingBottom();
      setMeasuredDimension(resolveSize(desiredWidth, widthSpec), resolveSize(desiredHeight, heightSpec));
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }
    @Override protected void onDraw(Canvas canvas) { layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
      return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    void release() { layers.release(); }
  }

  /** Activity-owned AAR image surface used by attachment tiles. */
  private final class NativeImageSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_media_image");
    private Bitmap bitmap;
    private boolean ownsBitmap;
    NativeImageSlot() { super(ChatMediaActivity.this); }
    void setScaleType(Object ignored) { }
    void setImageResource(int resource) {
      Bitmap decoded = BitmapFactory.decodeResource(getResources(), resource);
      replace(decoded, true);
    }
    void setImageBitmap(Bitmap bitmap) { replace(bitmap, false); }
    private void replace(Bitmap next, boolean owned) {
      if (ownsBitmap && bitmap != null && bitmap != next && !bitmap.isRecycled()) bitmap.recycle();
      bitmap = next; ownsBitmap = owned; rebuild();
    }
    private void rebuild() {
      if (getWidth() <= 0 || getHeight() <= 0 || bitmap == null || bitmap.isRecycled()) return;
      layer.clear();
      layer.add(new Image.Builder(getContext(), "image", bitmap,
          new RectF(0, 0, getWidth(), getHeight())).setScaleType(Image.ScaleType.CENTER_CROP));
      invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }
    @Override protected void onDraw(Canvas canvas) { layers.draw(canvas); }
    void release() {
      layers.release();
      if (ownsBitmap && bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
  }

  /** Activity-owned indeterminate AAR progress surface. */
  private final class NativeProgressSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_media_progress");
    NativeProgressSlot() { super(ChatMediaActivity.this); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      layer.clear();
      if (w <= 0 || h <= 0) return;
      layer.add(new Progress.Builder(getContext(), "progress", new RectF(0, 0, w, h))
          .setStyle(Progress.Style.CIRCULAR).setMode(Progress.Mode.INDETERMINATE)
          .setTrackColor(0x22019CC4).setProgressColor(0xFF019CC4));
    }
    @Override protected void onDraw(Canvas canvas) { layers.draw(canvas); invalidate(); }
    void release() { layers.release(); }
  }

  @Override
  protected void onDestroy() {
    if (mediaScroll != null) mediaScroll.release();
    for (NativeImageSlot image : images.values()) image.release();
    if (mediaTab != null) mediaTab.release();
    if (docsTab != null) docsTab.release();
    if (linksTab != null) linksTab.release();
    if (pageProgress != null) pageProgress.release();
    if (mediaMenu != null) mediaMenu.release();
    mediaMenu = null;
    super.onDestroy();
  }
}
