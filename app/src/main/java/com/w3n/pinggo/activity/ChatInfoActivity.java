package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import com.w3n.pinggo.R;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatHeaderComponent;
import com.w3n.pinggo.views.chat.MediaAttachmentOpener;
import com.w3n.pinggo.views.chat.MediaRecordTypes;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** WhatsApp-style details page shared by direct chats and groups. */
public final class ChatInfoActivity extends AppCompatActivity {
  private NativePromptDialogView promptDialog;
  private HomeMenuDialogView detailsMenu;
  public static final String EXTRA_CHAT_ID = "pinggo.details.CHAT_ID";
  public static final String EXTRA_IS_GROUP = "pinggo.details.IS_GROUP";
  public static final String EXTRA_NAME = "pinggo.details.NAME";
  public static final String EXTRA_PHONE = "pinggo.details.PHONE";
  public static final String EXTRA_PROFILE_PATH = "pinggo.details.PROFILE_PATH";
  public static final String EXTRA_DESCRIPTION = "pinggo.details.DESCRIPTION";
  public static final String EXTRA_MEMBER_COUNT = "pinggo.details.MEMBER_COUNT";
  public static final String EXTRA_ROLE = "pinggo.details.ROLE";
  public static final String RESULT_ACTION = "pinggo.details.RESULT_ACTION";
  public static final String ACTION_SEARCH = "search";

  private final AppFunctionManager api = AppFunctionManager.getInstance();
  private final Map<String, NativeImageSlot> mediaImages = new HashMap<>();
  private final Map<String, MessageEntity> mediaMessages = new HashMap<>();
  private ChatRepository repository;
  private String chatId;
  private String userId;
  private String name;
  private String phone;
  private String profilePath;
  private boolean group;
  private LinearLayout mediaRow;
  private LinearLayout members;
  private NativeTextSlot membersTitle;
  private NativeTextSlot subtitle;
  private NativeTextSlot description;
  private NativeTextSlot mediaHeading;
  private NativeTextSlot emptyMedia;
  private NativeTextSlot membershipNotice;
  private LinearLayout callActions;
  private ComponentScrollHost pageScroll;
  private PagingMediaScroll mediaScroll;
  private NativeTextSlot groupBlockAction;
  private NativeTextSlot adminOnlyAction;
  private boolean groupMemberActive = true;
  private boolean ownGroupAdmin;
  private boolean ownGroupOwner;
  private String groupOwnerId = "";
  private int activeGroupAdminCount;
  private final List<String> successorIds = new ArrayList<>();
  private final List<String> successorLabels = new ArrayList<>();
  private boolean adminOnlyMode;
  private View mediaProgressTile;
  private View mediaArrowTile;
  private int mediaTileCount;
  private Long mediaCursor;
  private boolean mediaLoading;
  private boolean mediaHasMore = true;
  private boolean localMediaChecked;
  private MediaAttachmentOpener attachmentOpener;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(0xFFF9FBFE);
    getWindow().setNavigationBarColor(0xFFF9FBFE);
    chatId = value(EXTRA_CHAT_ID, "");
    group = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
    name = value(EXTRA_NAME, group ? "Group" : "Chat");
    phone = value(EXTRA_PHONE, "");
    profilePath = value(EXTRA_PROFILE_PATH, "");
    ownGroupAdmin = group && "admin".equalsIgnoreCase(value(EXTRA_ROLE, ""));
    userId = LoginStateManager.getInstance().getUID(this);
    repository = ChatRepository.getInstance(this);
    attachmentOpener = new MediaAttachmentOpener(this, repository, chatId);
    setContentView(buildPage());
    detailsMenu = new HomeMenuDialogView(this,
        java.util.Arrays.asList("Clear chat", group ? "Report group" : "Report " + name),
        index -> { if (index == 0) confirmClear(); else confirmReport(); });
    ((ViewGroup) findViewById(android.R.id.content)).addView(detailsMenu,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    repository.observeTransfers(chatId).observe(this, this::showCompletedTransfers);
    repository.observeLocalAttachments(chatId).observe(this, values -> {
      if (values == null) return;
      for (MessageEntity stored : values) {
        NativeImageSlot image = mediaImages.get(stored.attachmentId);
        MessageEntity message = mediaMessages.get(stored.attachmentId);
        if (image != null && message != null && stored.attachmentLocalUri != null)
          renderThumbnail(image, Uri.parse(stored.attachmentLocalUri), message);
      }
    });
    loadMedia();
    if (!group)
      loadDirectDetails();
  }

  private View buildPage() {
    LinearLayout page = column();
    page.setBackgroundColor(0xFFF9FBFE);
    page.setPadding(dp(16), 0, dp(16), 0);
    page.addView(ChatHeaderComponent.detailsHeader(this,
        group ? "Group details" : "Chat details", this::finish, this::showDetailsMenu),
        new LinearLayout.LayoutParams(-1, -2));

    ComponentScrollHost scroll = pageScroll = new ComponentScrollHost(false, null);
    scroll.setBackgroundColor(0xFFF7F9FB);
    LinearLayout body = column();
    body.setGravity(Gravity.CENTER_HORIZONTAL);
    body.setPadding(0, dp(18), 0, dp(34));
    NativeImageSlot avatar = new NativeImageSlot();
    Bitmap bitmap = profilePath.isEmpty() ? null : BitmapFactory.decodeFile(profilePath);
    if (bitmap == null)
      avatar.setImageResource(R.drawable.pinggo_logo);
    else
      avatar.setImageBitmap(bitmap);
    body.addView(avatar, new LinearLayout.LayoutParams(dp(132), dp(132)));
    NativeTextSlot nameView = text(name, 25, true);
    nameView.setGravity(Gravity.CENTER);
    add(body, nameView, 16);
    subtitle = text(group ? memberCountText(getIntent().getIntExtra(EXTRA_MEMBER_COUNT, 0)) : phone, 15, false);
    subtitle.setTextColor(0xFF687382);
    subtitle.setGravity(Gravity.CENTER);
    add(body, subtitle, 5);
    description = text(group ? value(EXTRA_DESCRIPTION, "No group description") : "", 15, false);
    description.setTextColor(0xFF687382);
    description.setGravity(Gravity.CENTER);
    if (group)
      add(body, description, 8);
    membershipNotice = text("You are not an active member", 14, false);
    membershipNotice.setTextColor(0xFF687382);
    membershipNotice.setGravity(Gravity.CENTER);
    membershipNotice.setVisibility(View.GONE);
    if (group) body.addView(membershipNotice, margins(0, 12, 0, 0));
    callActions = actionRow();
    body.addView(callActions, margins(0, 24, 0, 22));

    LinearLayout mediaSection = column();
    mediaSection.setOnClickListener(v -> openMediaLibrary());
    mediaHeading = text("Media, links, and docs", 17, true);
    mediaHeading.setPadding(0, dp(8), 0, dp(4));
    mediaSection.addView(mediaHeading, full());
    PagingMediaScroll mediaScroll = this.mediaScroll = new PagingMediaScroll();
    mediaScroll.setOnClickListener(v -> openMediaLibrary());
    mediaRow = row();
    mediaRow.setPadding(0, dp(12), 0, dp(12));
    mediaScroll.setScrollContent(mediaRow);
    mediaSection.addView(mediaScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
    body.addView(mediaSection, full());

    if (group) {
      adminOnlyAction = text("Only admins can message and call: Off", 16, false);
      adminOnlyAction.setTextColor(0xFF019BC5);
      adminOnlyAction.setPadding(dp(16), dp(18), dp(16), dp(18));
      adminOnlyAction.setVisibility(View.GONE);
      adminOnlyAction.setOnClickListener(v -> toggleAdminOnlyMode());
      body.addView(adminOnlyAction, margins(0, 8, 0, 0));
      members = column();
      membersTitle = text("Members", 17, true);
      body.addView(membersTitle, margins(0, 18, 0, 6));
      body.addView(members, full());
    }
    body.addView(dangerAction("Clear chat", this::confirmClear), margins(0, 24, 0, 0));
    if (group) {
      groupBlockAction = dangerAction("Exit group", this::confirmBlockGroup);
      body.addView(groupBlockAction, full());
    } else
      body.addView(dangerAction("Block " + name, this::confirmBlockContact), full());
    body.addView(dangerAction(group ? "Report group" : "Report " + name, this::confirmReport), full());
    scroll.setScrollContent(body);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    ViewCompat.setOnApplyWindowInsetsListener(page, (v, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
      v.setPadding(dp(16), bars.top, dp(16), Math.max(bars.bottom, keyboard.bottom));
      return insets;
    });
    return page;
  }

  private void showDetailsMenu(View anchor) {
    if (detailsMenu != null) detailsMenu.show();
  }

  private void openMediaLibrary() {
    startActivity(new Intent(this, ChatMediaActivity.class)
        .putExtra(ChatMediaActivity.EXTRA_CHAT_ID, chatId)
        .putExtra(ChatMediaActivity.EXTRA_CHAT_NAME, name));
  }

  private LinearLayout actionRow() {
    LinearLayout actions = row();
    actions.setGravity(Gravity.CENTER);
    actions.setWeightSum(3);
    actions.addView(action(R.drawable.conversation_voice_call, "Voice", () -> openCall(false)), weighted());
    actions.addView(action(R.drawable.conversation_video_call, "Video", () -> openCall(true)), weighted());
    actions.addView(action(android.R.drawable.ic_menu_search, "Search", () -> {
      setResult(RESULT_OK, new Intent().putExtra(RESULT_ACTION, ACTION_SEARCH));
      finish();
    }), weighted());
    return actions;
  }

  private View action(int icon, String caption, Runnable click) {
    LinearLayout box = column();
    box.setGravity(Gravity.CENTER);
    box.setPadding(dp(4), dp(10), dp(4), dp(10));
    NativeImageSlot symbol = new NativeImageSlot();
    symbol.setImageResource(icon);
    box.addView(symbol, new LinearLayout.LayoutParams(dp(34), dp(34)));
    NativeTextSlot label = text(caption, 13, false);
    label.setGravity(Gravity.CENTER);
    box.addView(label);
    box.setOnClickListener(v -> click.run());
    return box;
  }

  private void openCall(boolean video) {
    if (group && !groupMemberActive) {
      toast("You are not an active member.");
      return;
    }
    if (group && adminOnlyMode && !ownGroupAdmin) {
      toast("Only group admins can message or call.");
      return;
    }
    if (group) {
      toast("Group call implementation pending.");
      return;
    }
    Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, chatId);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, phone);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER, name);
    intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, profilePath);
    startActivity(intent);
  }

  private void loadGroupDetails() {
    api.getGroupDetails(userId, chatId, callback(result -> {
      JsonObject root = asObject(result);
      JsonObject data = object(root, "group");
      if (data == null)
        return;
      String fetchedName = string(data, "name");
      if (!fetchedName.isEmpty())
        name = fetchedName;
      String fetchedDescription = string(data, "description");
      description.setText(fetchedDescription.isEmpty() ? "No group description" : fetchedDescription);
      JsonArray list = data.has("members") && data.get("members").isJsonArray()
          ? data.getAsJsonArray("members")
          : new JsonArray();
      JsonObject ownMembership = object(data, "ownMembership");
      groupMemberActive = ownMembership != null
          && "active".equalsIgnoreCase(string(ownMembership, "status"));
      ownGroupAdmin = groupMemberActive
          && "admin".equalsIgnoreCase(string(ownMembership, "role"));
      groupOwnerId = string(data, "ownerId");
      if (groupOwnerId.isEmpty()) groupOwnerId = string(data, "createdBy");
      ownGroupOwner = groupMemberActive && userId.equals(groupOwnerId);
      JsonObject permissions = object(data, "permissions");
      adminOnlyMode = permissions != null
          && "admins".equalsIgnoreCase(string(permissions, "sendMessages"));
      updateAdminOnlyAction();
      membershipNotice.setVisibility(groupMemberActive ? View.GONE : View.VISIBLE);
      callActions.setAlpha(groupMemberActive && (!adminOnlyMode || ownGroupAdmin) ? 1f : 0.42f);
      if (groupBlockAction != null)
        groupBlockAction.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      subtitle.setText(memberCountText(list.size()));
      membersTitle.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      members.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      if (groupMemberActive)
        renderMembers(list);
      else
        members.removeAllViews();
    }));
  }

  private void updateAdminOnlyAction() {
    if (adminOnlyAction == null) return;
    adminOnlyAction.setVisibility(ownGroupAdmin && groupMemberActive ? View.VISIBLE : View.GONE);
    adminOnlyAction.setText("Only admins can message and call: "
        + (adminOnlyMode ? "On" : "Off"));
  }

  private void toggleAdminOnlyMode() {
    if (!ownGroupAdmin || !groupMemberActive) return;
    boolean enabled = !adminOnlyMode;
    api.updateGroupAdminOnly(userId, chatId, enabled, callback(result -> {
      adminOnlyMode = enabled;
      updateAdminOnlyAction();
      toast(enabled ? "Only admins can now message and call."
          : "All members can now message and call.");
    }));
  }

  private void loadDirectDetails() {
    api.getChat(chatId, userId, callback(result -> {
      JsonObject profile = object(asObject(result), "userProfile");
      if (profile == null)
        return;
      String serverName = string(profile, "serverProfileName");
      boolean hasContactName = !name.isEmpty() && !name.equals(phone)
          && !name.equals(DeviceContactResolver.fallback(phone));
      subtitle.setText(hasContactName || serverName.isEmpty() ? phone : phone + "\n" + serverName);
    }));
  }

  private void renderMembers(JsonArray list) {
    members.removeAllViews();
    activeGroupAdminCount = 0;
    successorIds.clear();
    successorLabels.clear();
    for (JsonElement element : list) {
      if (!element.isJsonObject())
        continue;
      JsonObject member = element.getAsJsonObject();
      if (!"active".equalsIgnoreCase(string(member, "status")))
        continue;
      String id = string(member, "userId");
      String contact = DeviceContactResolver.cachedNameOrPhone(id);
      String serverName = string(member, "serverProfileName");
      String display = contact.equals(DeviceContactResolver.fallback(id)) && !serverName.isEmpty() ? serverName
          : contact;
      String role = string(member, "role");
      if ("admin".equalsIgnoreCase(role)) activeGroupAdminCount++;
      if (!id.equals(userId)) {
        successorIds.add(id);
        successorLabels.add(display);
      }
      LinearLayout item = row();
      item.setGravity(Gravity.CENTER_VERTICAL);
      item.setPadding(dp(12), dp(10), dp(12), dp(10));
      item.setBackgroundColor(Color.WHITE);
      NativeImageSlot avatar = new NativeImageSlot();
      avatar.setImageResource(R.drawable.pinggo_logo);
      item.addView(avatar, new LinearLayout.LayoutParams(dp(54), dp(54)));
      LinearLayout labels = column();
      labels.setPadding(dp(14), 0, 0, 0);
      labels.addView(text(id.equals(userId) ? "You" : display, 16, true));
      String about = string(member, "about");
      NativeTextSlot preview = text(about.isEmpty() ? id : about, 13, false);
      preview.setTextColor(0xFF687382);
      labels.addView(preview);
      item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
      if (!role.isEmpty() && !"member".equals(role)) {
        NativeTextSlot roleLabel = text(id.equals(groupOwnerId) ? "Group owner" : "Group " + role, 12, false);
        roleLabel.setTextColor(0xFF687382);
        item.addView(roleLabel);
      }
      if (ownGroupOwner && groupMemberActive && !id.equals(userId)
          && !"admin".equalsIgnoreCase(role)) {
        NativeTextSlot makeAdmin = text("Make admin", 13, false);
        makeAdmin.setTextColor(0xFF019BC5);
        makeAdmin.setGravity(Gravity.CENTER);
        makeAdmin.setPadding(dp(8), dp(10), dp(8), dp(10));
        makeAdmin.setOnClickListener(v -> confirmMakeAdmin(id, display));
        item.addView(makeAdmin);
      } else if (ownGroupOwner && groupMemberActive && !id.equals(userId)
          && "admin".equalsIgnoreCase(role) && !id.equals(groupOwnerId)) {
        NativeTextSlot makeMember = text("Make member", 13, false);
        makeMember.setTextColor(0xFF019BC5);
        makeMember.setGravity(Gravity.CENTER);
        makeMember.setPadding(dp(8), dp(10), dp(8), dp(10));
        makeMember.setOnClickListener(v -> confirmMakeMember(id, display));
        item.addView(makeMember);
      }
      if (ownGroupAdmin && groupMemberActive && !id.equals(userId)
          && !"admin".equalsIgnoreCase(role)) {
        NativeTextSlot remove = text("Remove", 13, false);
        remove.setTextColor(0xFFD9304F);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(dp(12), dp(10), dp(4), dp(10));
        remove.setOnClickListener(v -> confirmRemoveMember(id, display));
        item.addView(remove);
      }
      members.addView(item, full());
      View divider = new View(this);
      divider.setBackgroundColor(0xFFE5EAF0);
      LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
      dividerParams.leftMargin = dp(80);
      members.addView(divider, dividerParams);
    }
    if (ownGroupAdmin && groupMemberActive) {
      LinearLayout addMembers = row();
      addMembers.setGravity(Gravity.CENTER_VERTICAL);
      addMembers.setPadding(dp(12), dp(14), dp(12), dp(14));
      addMembers.setBackgroundColor(Color.WHITE);
      NativeImageSlot icon = new NativeImageSlot();
      icon.setImageResource(android.R.drawable.ic_input_add);
      addMembers.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
      NativeTextSlot label = text("Add members", 16, true);
      label.setTextColor(0xFF019BC5);
      label.setPadding(dp(14), 0, 0, 0);
      addMembers.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
      addMembers.setOnClickListener(v -> startActivity(new Intent(this, NewChatActivity.class)
          .putExtra(NewChatActivity.EXTRA_ADD_TO_GROUP_ID, chatId)));
      members.addView(addMembers, full());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (group && members != null) loadGroupDetails();
  }

  private void loadMedia() {
    if (mediaTileCount >= 10) {
      showMediaArrowTile();
      return;
    }
    if (mediaLoading || !mediaHasMore)
      return;
    mediaLoading = true;
    showMediaProgressTile();
    if (!localMediaChecked) {
      localMediaChecked = true;
      repository.loadStoredMedia(chatId, "media", 10, mediaCursor,
          (stored, next, localHasMore) -> {
            if (isFinishing() || isDestroyed()) return;
            for (MessageEntity message : stored) addMediaTile(mediaRecord(message));
            mediaCursor = next;
            if (mediaTileCount >= 10) {
              mediaLoading = false;
              mediaHasMore = localHasMore;
              removeMediaProgressTile();
              showMediaArrowTile();
            } else {
              loadRemoteMedia(10 - mediaTileCount);
            }
          });
      return;
    }
    loadRemoteMedia(10 - mediaTileCount);
  }

  private void loadRemoteMedia(int pageSize) {
    api.getChatMedia(userId, chatId, Math.max(1, pageSize), mediaCursor, "media",
        new AppFunctionManager.Callback() {
      @Override
      public void onSuccess(Object result) {
        runOnUiThread(() -> {
          mediaLoading = false;
          removeMediaProgressTile();
          JsonObject root = asObject(result);
          JsonArray list = root.has("media") && root.get("media").isJsonArray() ? root.getAsJsonArray("media")
              : new JsonArray();
          for (JsonElement value : list)
            if (value.isJsonObject())
              addMediaTile(value.getAsJsonObject());
          mediaHasMore = bool(root, "hasMore");
          mediaCursor = root.has("nextCursor") && !root.get("nextCursor").isJsonNull()
              ? root.get("nextCursor").getAsLong()
              : null;
          updateMediaEmptyState(!mediaHasMore);
          if (mediaRow.getChildCount() == 0 && mediaHasMore)
            loadMedia();
        });
      }

      @Override
      public void onError(String error) {
        runOnUiThread(() -> {
          mediaLoading = false;
          removeMediaProgressTile();
        });
      }
        });
  }

  private JsonObject mediaRecord(MessageEntity message) {
    JsonObject value = new JsonObject();
    value.addProperty("id", message.messageId);
    value.addProperty("clientMessageId", message.clientMessageId);
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

  private void addMediaTile(JsonObject item) {
    String type = MediaRecordTypes.type(item);
    if (!"image".equalsIgnoreCase(type) && !"video".equalsIgnoreCase(type))
      return;
    if (mediaTileCount >= 10) {
      showMediaArrowTile();
      return;
    }
    if (emptyMedia != null) {
      mediaRow.removeView(emptyMedia);
      emptyMedia = null;
    }
    LinearLayout tile = column();
    tile.setGravity(Gravity.CENTER);
    tile.setBackgroundColor(0xFFE4E9EE);
    tile.setOnClickListener(v -> attachmentOpener.open(mediaMessage(item)));
    NativeImageSlot thumbnail = new NativeImageSlot();
    thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
    tile.addView(thumbnail, new LinearLayout.LayoutParams(-1, 0, 1f));
    String caption = string(item, "text");
    tile.addView(text(caption.isEmpty() ? type : caption, 11, false));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(112), dp(108));
    p.rightMargin = dp(8);
    mediaRow.addView(tile, p);
    mediaTileCount++;
    if (mediaTileCount == 10)
      showMediaArrowTile();
    MessageEntity message = mediaMessage(item);
    if (message.attachmentId == null || message.attachmentId.isEmpty())
      return;
    mediaImages.put(message.attachmentId, thumbnail);
    mediaMessages.put(message.attachmentId, message);
    boolean cachedVideo = "video".equalsIgnoreCase(message.messageType);
    String cachedSource = message.attachmentLocalUri;
    MediaPreviewCache.Thumbnail loaded = MediaPreviewCache.anyMemoryThumbnail(cachedSource, cachedVideo);
    if (loaded == null) {
      cachedSource = message.attachmentUrl;
      loaded = MediaPreviewCache.anyMemoryThumbnail(cachedSource, cachedVideo);
    }
    if (loaded != null) {
      thumbnail.setTag(cachedSource);
      thumbnail.setImageBitmap(loaded.bitmap);
      return;
    }
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override
      public void onAvailable(Uri uri) {
        renderThumbnail(thumbnail, uri, message);
      }

      @Override
      public void onQueued() {
        thumbnail.setImageResource(android.R.drawable.stat_sys_download);
      }

      @Override
      public void onError(String error) {
        thumbnail.setImageResource(android.R.drawable.ic_dialog_alert);
      }
    });
  }

  private void showMediaProgressTile() {
    if (mediaProgressTile != null || mediaTileCount >= 10)
      return;
    LinearLayout tile = column();
    tile.setGravity(Gravity.CENTER);
    tile.addView(new NativeProgressSlot(), new LinearLayout.LayoutParams(dp(42), dp(42)));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(108));
    params.rightMargin = dp(8);
    mediaRow.addView(tile, params);
    mediaProgressTile = tile;
  }

  private void removeMediaProgressTile() {
    if (mediaProgressTile == null)
      return;
    mediaRow.removeView(mediaProgressTile);
    mediaProgressTile = null;
  }

  private void showMediaArrowTile() {
    if (mediaArrowTile != null)
      return;
    NativeTextSlot arrow = text("→", 32, false);
    arrow.setGravity(Gravity.CENTER);
    arrow.setContentDescription("View all media");
    arrow.setBackgroundColor(0xFFE4E9EE);
    arrow.setOnClickListener(v -> openMediaLibrary());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(108));
    params.rightMargin = dp(8);
    mediaRow.addView(arrow, params);
    mediaArrowTile = arrow;
  }

  private void updateMediaEmptyState(boolean finished) {
    if (!finished || mediaRow.getChildCount() > 0 || emptyMedia != null)
      return;
    emptyMedia = text("No media available", 15, false);
    emptyMedia.setTextColor(0xFF687382);
    emptyMedia.setGravity(Gravity.CENTER);
    int width = getResources().getDisplayMetrics().widthPixels - dp(32);
    mediaRow.addView(emptyMedia, new LinearLayout.LayoutParams(width, dp(108)));
  }

  private MessageEntity mediaMessage(JsonObject item) {
    MessageEntity message = new MessageEntity();
    message.messageId = string(item, "id");
    message.clientMessageId = string(item, "clientMessageId");
    message.chatId = chatId;
    message.senderId = string(item, "senderId");
    message.receiverId = string(item, "receiverId");
    message.text = string(item, "text");
    message.setMessageType(MediaRecordTypes.type(item));
    JsonObject attachment = object(item, "attachment");
    if (attachment != null) {
      message.attachmentId = string(attachment, "id");
      message.attachmentKind = string(attachment, "kind");
      message.attachmentName = string(attachment, "name");
      message.attachmentMimeType = string(attachment, "mimeType");
      message.attachmentUrl = string(attachment, "url");
      message.attachmentSha256 = string(attachment, "sha256");
      String localUri = string(attachment, "localUri");
      message.attachmentLocalUri = localUri.isEmpty() ? null : localUri;
      if (attachment.has("size") && !attachment.get("size").isJsonNull())
        message.attachmentSize = attachment.get("size").getAsLong();
    }
    return message;
  }

  private void showCompletedTransfers(List<TransferEntity> transfers) {
    if (transfers == null)
      return;
    for (TransferEntity transfer : transfers) {
      if (transfer == null || transfer.attachmentId == null || transfer.localUri == null
          || !"completed".equalsIgnoreCase(transfer.status))
        continue;
      NativeImageSlot image = mediaImages.get(transfer.attachmentId);
      MessageEntity message = mediaMessages.get(transfer.attachmentId);
      if (image != null && message != null)
        renderThumbnail(image, Uri.parse(transfer.localUri), message);
    }
  }

  private void renderThumbnail(NativeImageSlot target, Uri uri, MessageEntity message) {
    if (isFinishing() || isDestroyed()) return;
    String key = uri.toString();
    if (key.equals(target.getTag())) return;
    target.setTag(key);
    boolean video = "video".equalsIgnoreCase(message.messageType);
    int size = dp(112);
    MediaPreviewCache.Thumbnail cached =
        MediaPreviewCache.memoryThumbnail(key, video, size, size);
    if (cached == null) cached = MediaPreviewCache.anyMemoryThumbnail(key, video);
    if (cached == null && message.attachmentUrl != null)
      cached = MediaPreviewCache.anyMemoryThumbnail(message.attachmentUrl, video);
    if (cached != null) {
      target.setImageBitmap(cached.bitmap);
      return;
    }
    MediaPreviewCache.loadThumbnail(this, key, video, size, size,
        new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
          @Override public void onSuccess(MediaPreviewCache.Thumbnail result) {
            if (!isFinishing() && !isDestroyed() && key.equals(target.getTag()))
              target.setImageBitmap(result.bitmap);
          }

          @Override public void onError() {
            if (!isFinishing() && !isDestroyed() && key.equals(target.getTag())) {
              target.setTag(null);
              target.setImageResource(android.R.drawable.ic_menu_gallery);
            }
          }
        });
  }

  private void confirmClear() {
    confirm("Clear chat?", "Messages will be cleared for you.",
        () -> api.clearChat(userId, chatId, callback(result -> toast("Chat cleared."))));
  }

  private void confirmBlockContact() {
    confirm("Block " + name + "?", "They will no longer be able to contact you.",
        () -> api.updateBlock(userId, chatId, true, callback(result -> toast(name + " blocked."))));
  }

  private void confirmBlockGroup() {
    if (!groupMemberActive) return;
    if (ownGroupAdmin && activeGroupAdminCount <= 1) {
      showSuccessorDialog();
      return;
    }
    confirm("Exit group?", "You will stop receiving new messages and calls from this group.",
        () -> exitGroup(null));
  }

  private void showSuccessorDialog() {
    if (successorIds.isEmpty()) {
      toast("Add another member before exiting this group.");
      return;
    }
    List<String> actions = new ArrayList<>();
    for (String label : successorLabels) actions.add("Make " + label + " admin and exit");
    actions.add("Cancel");
    showPrompt(NativePromptDialogView.actions(this, actions, index -> {
      if (index < 0 || index >= successorIds.size()) return;
      exitGroup(successorIds.get(index));
    }, this::removePrompt));
  }

  private void exitGroup(String successorAdminId) {
    api.leaveGroup(userId, chatId, successorAdminId, callback(result -> {
          groupMemberActive = false;
          membershipNotice.setVisibility(View.VISIBLE);
          callActions.setAlpha(0.42f);
          groupBlockAction.setVisibility(View.GONE);
          membersTitle.setVisibility(View.GONE);
          members.setVisibility(View.GONE);
          members.removeAllViews();
          loadGroupDetails();
          toast("You left the group.");
        }));
  }

  private void confirmRemoveMember(String memberId, String memberName) {
    if (!ownGroupAdmin || !groupMemberActive || memberId == null || memberId.equals(userId)) return;
    String label = memberName == null || memberName.trim().isEmpty() ? memberId : memberName;
    confirm("Remove " + label + "?", "They will no longer be able to send messages in this group.",
        () -> api.updateGroupMembers(userId, chatId,
            java.util.Collections.singletonList(memberId), false,
            callback(result -> {
              toast(label + " removed.");
              loadGroupDetails();
            })));
  }

  private void confirmMakeAdmin(String memberId, String memberName) {
    if (!ownGroupOwner || !groupMemberActive || memberId == null || memberId.equals(userId)) return;
    String label = memberName == null || memberName.trim().isEmpty() ? memberId : memberName;
    confirm("Make " + label + " an admin?", "They will be able to manage members and group settings.",
        () -> api.updateGroupMemberRole(userId, chatId, memberId, "admin",
            callback(result -> {
              toast(label + " is now a group admin.");
              loadGroupDetails();
            })));
  }

  private void confirmMakeMember(String memberId, String memberName) {
    if (!ownGroupOwner || !groupMemberActive || memberId == null
        || memberId.equals(userId) || memberId.equals(groupOwnerId)) return;
    String label = memberName == null || memberName.trim().isEmpty() ? memberId : memberName;
    confirm("Make " + label + " a member?", "They will no longer have group admin permissions.",
        () -> api.updateGroupMemberRole(userId, chatId, memberId, "member",
            callback(result -> {
              toast(label + " is now a group member.");
              loadGroupDetails();
            })));
  }

  private void confirmReport() {
    confirm("Report " + (group ? "group" : name) + "?", "A report will be uploaded to the server.", () -> {
      AppFunctionManager.Callback done = callback(result -> toast("Report submitted."));
      if (group)
        api.reportGroup(userId, chatId, "Reported from group details", done);
      else
        api.reportChat(userId, chatId, "Reported from chat details", done);
    });
  }

  private AppFunctionManager.Callback callback(java.util.function.Consumer<Object> success) {
    return new AppFunctionManager.Callback() {
      @Override
      public void onSuccess(Object result) {
        runOnUiThread(() -> success.accept(result));
      }

      @Override
      public void onError(String error) {
        runOnUiThread(() -> toast(error == null ? "Request failed." : error));
      }
    };
  }

  private void confirm(String title, String message, Runnable yes) {
    showPrompt(NativePromptDialogView.confirm(this, title, message, "Continue", yes,
        this::removePrompt));
  }

  private void showPrompt(NativePromptDialogView prompt) {
    removePrompt();
    promptDialog = prompt;
    ((ViewGroup) findViewById(android.R.id.content)).addView(prompt,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removePrompt() {
    NativePromptDialogView current = promptDialog;
    promptDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup)
      ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  private NativeTextSlot dangerAction(String label, Runnable action) {
    NativeTextSlot view = text(label, 16, false);
    view.setTextColor(0xFFD9304F);
    view.setPadding(dp(16), dp(18), dp(16), dp(18));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private final class PagingMediaScroll extends ComponentScrollHost {
    PagingMediaScroll() {
      super(true, () -> loadMedia());
    }
  }

  /** Screen-owned host whose scrolling is supplied by the AAR ComponentList. */
  private class ComponentScrollHost extends FrameLayout {
    private final boolean horizontal;
    private final Runnable nearEnd;
    private final ZLayerGroup scrollLayers = new ZLayerGroup(this);
    private final ZLayer scrollLayer = scrollLayers.addLayer("chat_info_scroll");
    private ComponentList<String> scrollList;
    private View scrollContent;
    private float lastOffset;
    private float downAxis;
    private boolean dragging;
    ComponentScrollHost(boolean horizontal, Runnable nearEnd) {
      super(ChatInfoActivity.this);
      this.horizontal = horizontal;
      this.nearEnd = nearEnd;
      setClipChildren(true);
    }
    void setScrollContent(View child) {
      removeAllViews();
      scrollContent = child;
      FrameLayout.LayoutParams params = horizontal
          ? new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.MATCH_PARENT)
          : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT);
      addView(child, params);
      child.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
        if (r-l != or-ol || b-t != ob-ot) rebuildScroll();
      });
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      super.onSizeChanged(w,h,ow,oh); rebuildScroll();
    }
    private void rebuildScroll() {
      if (getWidth() <= 0 || getHeight() <= 0 || scrollContent == null) return;
      float offset = scrollList == null ? lastOffset : scrollList.getScrollOffset();
      float itemSize = horizontal ? Math.max(getWidth() + 1, scrollContent.getWidth())
          : Math.max(getHeight() + 1, scrollContent.getHeight());
      scrollLayer.clear();
      scrollList = scrollLayer.add(new ComponentList.Builder<String>(getContext(), "scroll_list",
          new RectF(0,0,getWidth(),getHeight()))
          .setOrientation(horizontal ? ComponentList.Orientation.HORIZONTAL
              : ComponentList.Orientation.VERTICAL)
          .setItemSize(itemSize).setAdapter(new ComponentList.Adapter<String>() {
            @Override public int getItemCount(){ return 1; }
            @Override public String getItem(int position){ return "content"; }
            @Override public void onCreateItem(ComponentList.Item item,int type){ item.addLayer("spacer"); }
            @Override public void onBindItem(ComponentList.Item item,String value,int position){}
          }).setScrollEnabled(true).setFlingEnabled(true).setOverscrollEnabled(false)
          .setClipToBounds(true));
      if (offset > 0f) scrollList.scrollBy(horizontal ? offset : 0f, horizontal ? 0f : offset);
      syncScroll();
    }
    private void syncScroll() {
      if (scrollList == null || scrollContent == null) return;
      lastOffset = scrollList.getScrollOffset();
      scrollContent.setTranslationX(horizontal ? -lastOffset : 0f);
      scrollContent.setTranslationY(horizontal ? 0f : -lastOffset);
      if (nearEnd != null) {
        float extent = horizontal ? scrollContent.getWidth() : scrollContent.getHeight();
        float viewport = horizontal ? getWidth() : getHeight();
        if (lastOffset + viewport >= extent - dp(80)) nearEnd.run();
      }
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
      if (scrollList == null) return super.dispatchTouchEvent(event);
      float axis = horizontal ? event.getX() : event.getY();
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
        downAxis=axis; dragging=false; scrollLayers.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
      }
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
        if (!dragging && Math.abs(axis-downAxis)>dp(6)) {
          dragging=true;
          android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);
          cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
        }
        scrollLayers.onTouchEvent(event); syncScroll();
        if (dragging) return true;
      } else if (event.getActionMasked()==android.view.MotionEvent.ACTION_UP
          || event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL) {
        scrollLayers.onTouchEvent(event); syncScroll();
        if (dragging) { dragging=false; return true; }
      }
      return super.dispatchTouchEvent(event);
    }
    @Override protected void dispatchDraw(Canvas canvas) { syncScroll(); super.dispatchDraw(canvas); }
    void release() { scrollLayers.release(); }
  }

  private static JsonObject asObject(Object value) {
    return value instanceof JsonObject ? (JsonObject) value : new JsonObject();
  }

  private static JsonObject object(JsonObject parent, String key) {
    return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
  }

  private static String string(JsonObject object, String key) {
    try {
      return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static boolean bool(JsonObject object, String key) {
    try {
      return object.has(key) && object.get(key).getAsBoolean();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private String value(String key, String fallback) {
    String result = getIntent().getStringExtra(key);
    return result == null || result.trim().isEmpty() ? fallback : result.trim();
  }

  private String memberCountText(int count) {
    return count + (count == 1 ? " member" : " members");
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
  }

  private LinearLayout column() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  private LinearLayout row() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.HORIZONTAL);
    return view;
  }

  private NativeTextSlot text(String value, int sp, boolean bold) {
    return new NativeTextSlot(value, sp, bold);
  }

  private void add(LinearLayout parent, View child, int top) {
    parent.addView(child, margins(0, top, 0, 0));
  }

  private LinearLayout.LayoutParams full() {
    return new LinearLayout.LayoutParams(-1, -2);
  }

  private LinearLayout.LayoutParams weighted() {
    return new LinearLayout.LayoutParams(0, -2, 1f);
  }

  private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
    LinearLayout.LayoutParams p = full();
    p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    return p;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class NativeTextSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_info_text");
    private String value;
    private final int sizeSp;
    private final boolean bold;
    private int color = 0xFF07131E;
    private int gravity = Gravity.START | Gravity.CENTER_VERTICAL;
    NativeTextSlot(String value, int sizeSp, boolean bold) {
      super(ChatInfoActivity.this);
      this.value = value == null ? "" : value; this.sizeSp = sizeSp; this.bold = bold;
    }
    void setText(String text) { value = text == null ? "" : text; requestLayout(); rebuild(); }
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
    void release() { layers.release(); }
  }

  private final class NativeImageSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_info_image");
    private Bitmap bitmap;
    private boolean ownsBitmap;
    NativeImageSlot() { super(ChatInfoActivity.this); }
    void setImageResource(int resource) { replace(BitmapFactory.decodeResource(getResources(), resource), true); }
    void setImageBitmap(Bitmap bitmap) { replace(bitmap, false); }
    private void replace(Bitmap next, boolean owned) {
      if (ownsBitmap && bitmap != null && bitmap != next && !bitmap.isRecycled()) bitmap.recycle();
      bitmap = next; ownsBitmap = owned; rebuild();
    }
    private void rebuild() {
      layer.clear();
      if (getWidth() <= 0 || getHeight() <= 0 || bitmap == null || bitmap.isRecycled()) return;
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

  private final class NativeProgressSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_info_progress");
    NativeProgressSlot() { super(ChatInfoActivity.this); }
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
    if (pageScroll != null) pageScroll.release();
    if (mediaScroll != null) mediaScroll.release();
    for (NativeImageSlot image : mediaImages.values()) image.release();
    removePrompt();
    if (detailsMenu != null) detailsMenu.release();
    detailsMenu = null;
    super.onDestroy();
  }
}
