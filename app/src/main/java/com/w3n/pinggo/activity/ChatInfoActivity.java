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
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatHeaderComponent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WhatsApp-style details page shared by direct chats and groups. */
public final class ChatInfoActivity extends AppCompatActivity {
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
  private final Map<String, ImageView> mediaImages = new HashMap<>();
  private final Map<String, MessageEntity> mediaMessages = new HashMap<>();
  private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
  private ChatRepository repository;
  private String chatId;
  private String userId;
  private String name;
  private String phone;
  private String profilePath;
  private boolean group;
  private LinearLayout mediaRow;
  private LinearLayout members;
  private TextView membersTitle;
  private TextView subtitle;
  private TextView description;
  private TextView mediaHeading;
  private TextView emptyMedia;
  private TextView membershipNotice;
  private LinearLayout callActions;
  private TextView groupBlockAction;
  private boolean groupMemberActive = true;
  private boolean ownGroupAdmin;
  private View mediaProgressTile;
  private View mediaArrowTile;
  private int mediaTileCount;
  private Long mediaCursor;
  private boolean mediaLoading;
  private boolean mediaHasMore = true;
  private boolean mediaLoadingStored = true;

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
    setContentView(buildPage());
    repository.observeTransfers(chatId).observe(this, this::showCompletedTransfers);
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

    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xFFF7F9FB);
    LinearLayout body = column();
    body.setGravity(Gravity.CENTER_HORIZONTAL);
    body.setPadding(0, dp(18), 0, dp(34));
    ImageView avatar = new ImageView(this);
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    Bitmap bitmap = profilePath.isEmpty() ? null : BitmapFactory.decodeFile(profilePath);
    if (bitmap == null)
      avatar.setImageResource(R.drawable.pinggo_logo);
    else
      avatar.setImageBitmap(bitmap);
    body.addView(avatar, new LinearLayout.LayoutParams(dp(132), dp(132)));
    TextView nameView = text(name, 25, true);
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
    PagingMediaScroll mediaScroll = new PagingMediaScroll();
    mediaScroll.setOnClickListener(v -> openMediaLibrary());
    mediaRow = row();
    mediaRow.setPadding(0, dp(12), 0, dp(12));
    mediaScroll.addView(mediaRow);
    mediaSection.addView(mediaScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
    body.addView(mediaSection, full());

    if (group) {
      members = column();
      membersTitle = text("Members", 17, true);
      body.addView(membersTitle, margins(0, 18, 0, 6));
      body.addView(members, full());
    }
    body.addView(dangerAction("Clear chat", this::confirmClear), margins(0, 24, 0, 0));
    if (group) {
      groupBlockAction = dangerAction("Block group", this::confirmBlockGroup);
      body.addView(groupBlockAction, full());
    } else
      body.addView(dangerAction("Block " + name, this::confirmBlockContact), full());
    body.addView(dangerAction(group ? "Report group" : "Report " + name, this::confirmReport), full());
    scroll.addView(body);
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
    android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
    menu.getMenu().add("Clear chat");
    menu.getMenu().add(group ? "Report group" : "Report " + name);
    menu.setOnMenuItemClickListener(item -> {
      if (item.getTitle().toString().startsWith("Clear"))
        confirmClear();
      else
        confirmReport();
      return true;
    });
    menu.show();
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
    ImageView symbol = new ImageView(this);
    symbol.setImageResource(icon);
    box.addView(symbol, new LinearLayout.LayoutParams(dp(34), dp(34)));
    TextView label = text(caption, 13, false);
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
      membershipNotice.setVisibility(groupMemberActive ? View.GONE : View.VISIBLE);
      callActions.setAlpha(groupMemberActive ? 1f : 0.42f);
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
    for (JsonElement element : list) {
      if (!element.isJsonObject())
        continue;
      JsonObject member = element.getAsJsonObject();
      String id = string(member, "userId");
      String contact = DeviceContactResolver.cachedNameOrPhone(id);
      String serverName = string(member, "serverProfileName");
      String display = contact.equals(DeviceContactResolver.fallback(id)) && !serverName.isEmpty() ? serverName
          : contact;
      String role = string(member, "role");
      LinearLayout item = row();
      item.setGravity(Gravity.CENTER_VERTICAL);
      item.setPadding(dp(12), dp(10), dp(12), dp(10));
      item.setBackgroundColor(Color.WHITE);
      ImageView avatar = new ImageView(this);
      avatar.setImageResource(R.drawable.pinggo_logo);
      avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
      item.addView(avatar, new LinearLayout.LayoutParams(dp(54), dp(54)));
      LinearLayout labels = column();
      labels.setPadding(dp(14), 0, 0, 0);
      labels.addView(text(id.equals(userId) ? "You" : display, 16, true));
      String about = string(member, "about");
      TextView preview = text(about.isEmpty() ? id : about, 13, false);
      preview.setTextColor(0xFF687382);
      labels.addView(preview);
      item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
      if (!role.isEmpty() && !"member".equals(role)) {
        TextView roleLabel = text("Group " + role, 12, false);
        roleLabel.setTextColor(0xFF687382);
        item.addView(roleLabel);
      }
      if (ownGroupAdmin && groupMemberActive && !id.equals(userId)) {
        TextView remove = text("Remove", 13, false);
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
      ImageView icon = new ImageView(this);
      icon.setImageResource(android.R.drawable.ic_input_add);
      addMembers.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
      TextView label = text("Add members", 16, true);
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
    if (mediaLoadingStored) {
      repository.loadStoredMedia(chatId, 12, mediaCursor, (values, next, more) -> {
        mediaLoading = false;
        removeMediaProgressTile();
        for (MessageEntity value : values)
          addMediaTile(mediaRecord(value));
        if (!values.isEmpty())
          mediaCursor = values.get(values.size() - 1).sentTime;
        if (!more)
          mediaLoadingStored = false;
        updateMediaEmptyState(false);
        if (mediaRow.getChildCount() == 0 && mediaHasMore)
          loadMedia();
      });
      return;
    }
    api.getChatMedia(userId, chatId, 12, mediaCursor, new AppFunctionManager.Callback() {
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
      if (message.attachmentSize != null)
        a.addProperty("size", message.attachmentSize);
      value.add("attachment", a);
    }
    return value;
  }

  private void addMediaTile(JsonObject item) {
    String type = string(item, "messageType");
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
    tile.setOnClickListener(v -> {
      /* Reserved for the media preview action. */ });
    ImageView thumbnail = new ImageView(this);
    thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
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
    tile.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(42), dp(42)));
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
    TextView arrow = text("→", 32, false);
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
    message.setMessageType(string(item, "messageType"));
    JsonObject attachment = object(item, "attachment");
    if (attachment != null) {
      message.attachmentId = string(attachment, "id");
      message.attachmentKind = string(attachment, "kind");
      message.attachmentName = string(attachment, "name");
      message.attachmentMimeType = string(attachment, "mimeType");
      message.attachmentUrl = string(attachment, "url");
      message.attachmentSha256 = string(attachment, "sha256");
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
      ImageView image = mediaImages.get(transfer.attachmentId);
      MessageEntity message = mediaMessages.get(transfer.attachmentId);
      if (image != null && message != null)
        renderThumbnail(image, Uri.parse(transfer.localUri), message);
    }
  }

  private void renderThumbnail(ImageView target, Uri uri, MessageEntity message) {
    thumbnailExecutor.execute(() -> {
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
        } else if ("image".equalsIgnoreCase(message.messageType)) {
          try (InputStream stream = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            bitmap = BitmapFactory.decodeStream(stream, null, options);
          }
        }
      } catch (Exception ignored) {
      }
      Bitmap result = bitmap;
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed())
          return;
        if (result != null)
          target.setImageBitmap(result);
        else
          target.setImageResource("file".equalsIgnoreCase(message.messageType)
              ? android.R.drawable.ic_menu_save
              : android.R.drawable.ic_menu_gallery);
      });
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
    confirm("Block group?", "Blocking exits this group and removes future access.",
        () -> api.leaveGroup(userId, chatId, callback(result -> {
          groupMemberActive = false;
          membershipNotice.setVisibility(View.VISIBLE);
          callActions.setAlpha(0.42f);
          groupBlockAction.setVisibility(View.GONE);
          membersTitle.setVisibility(View.GONE);
          members.setVisibility(View.GONE);
          members.removeAllViews();
          loadGroupDetails();
          toast("Group blocked.");
        })));
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
    new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null)
        .setPositiveButton("Continue", (d, w) -> yes.run()).show();
  }

  private TextView dangerAction(String label, Runnable action) {
    TextView view = text(label, 16, false);
    view.setTextColor(0xFFD9304F);
    view.setPadding(dp(16), dp(18), dp(16), dp(18));
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private final class PagingMediaScroll extends HorizontalScrollView {
    PagingMediaScroll() {
      super(ChatInfoActivity.this);
      setHorizontalScrollBarEnabled(false);
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
      super.onScrollChanged(x, y, oldX, oldY);
      View child = getChildAt(0);
      if (child != null && x + getWidth() >= child.getWidth() - dp(80))
        loadMedia();
    }
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

  private TextView text(String value, int sp, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(0xFF07131E);
    if (bold)
      view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return view;
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

  @Override
  protected void onDestroy() {
    thumbnailExecutor.shutdownNow();
    super.onDestroy();
  }
}
