package com.w3n.pinggo.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.cache.ProfileBitmapCache;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatHeaderComponent;
import com.w3n.pinggo.views.chat.MediaAttachmentOpener;
import com.w3n.pinggo.views.chat.MediaRecordTypes;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import com.w3n.pinggo.views.common.NativeCropView;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.w3n.pinggo.views.home.ProfilePhotoPreviewView;
import com.ogfa.nativeviews.image.Image;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WhatsApp-style details page shared by direct chats and groups. */
public final class ChatInfoActivity extends PingGoActivity {
  private NativePromptDialogView promptDialog;
  private HomeMenuDialogView detailsMenu;
  private ProfilePhotoPreviewView profilePhotoPreview;
  private NativeCropView groupPhotoCrop;
  private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
  private final androidx.activity.result.ActivityResultLauncher<String> groupPhotoPicker =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
          this::onGroupPhotoSelected);
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
  private LinearLayout membersSection;
  private NativeTextSlot membersTitle;
  private NativeTextSlot subtitle;
  private NativeTextSlot description;
  private NativeTextSlot mediaHeading;
  private NativeTextSlot emptyMedia;
  private NativeTextSlot membershipNotice;
  private LinearLayout callActions;
  private PagingMediaScroll mediaScroll;
  private NativeTextSlot groupBlockAction;
  private NativeTextSlot groupNameView;
  private NativeTextSlot editGroupNameAction;
  private PermissionRow profilePermissionAction;
  private PermissionRow namePermissionAction;
  private PermissionRow messagePermissionAction;
  private PermissionRow callPermissionAction;
  private NativeTextSlot editGroupPhotoAction;
  private NativeImageSlot groupAvatar;
  private LinearLayout permissionsSection;
  private Bitmap profileBitmap;
  private boolean groupMemberActive = true;
  private boolean ownGroupAdmin;
  private boolean ownGroupOwner;
  private String groupOwnerId = "";
  private int activeGroupAdminCount;
  private final List<String> successorIds = new ArrayList<>();
  private final List<String> successorLabels = new ArrayList<>();
  private boolean profileAdminOnly;
  private boolean nameAdminOnly;
  private boolean messagesAdminOnly;
  private boolean callsAdminOnly;
  private String groupProfilePhotoUrl = "";
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
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (groupPhotoCrop != null && groupPhotoCrop.dismissIfShowing()) return;
        if (profilePhotoPreview != null) { closeProfilePhotoPreview(); return; }
        if (detailsMenu != null && detailsMenu.dismissIfShowing()) return;
        if (promptDialog != null) { removePrompt(); return; }
        setEnabled(false);
        getOnBackPressedDispatcher().onBackPressed();
        setEnabled(true);
      }
    });
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

    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(0xFFF7F9FB);
    LinearLayout body = column();
    body.setGravity(Gravity.CENTER_HORIZONTAL);
    body.setPadding(0, dp(16), 0, dp(28));
    NativeImageSlot avatar = new NativeImageSlot();
    if (group) groupAvatar = avatar;
    Bitmap bitmap = profilePath.isEmpty() ? null : BitmapFactory.decodeFile(profilePath);
    profileBitmap = bitmap;
    if (bitmap == null)
      avatar.setImageResource(R.drawable.pinggo_logo);
    else
      avatar.setImageBitmap(bitmap);
    avatar.setOnClickListener(view -> showProfilePhoto(profileBitmap));
    body.addView(avatar, new LinearLayout.LayoutParams(dp(112), dp(112)));
    if (group) {
      editGroupPhotoAction = text("Edit group photo", 15, true);
      editGroupPhotoAction.setTextColor(0xFF019BC5);
      editGroupPhotoAction.setGravity(Gravity.CENTER);
      editGroupPhotoAction.setPadding(dp(12), dp(8), dp(12), dp(8));
      editGroupPhotoAction.setVisibility(View.GONE);
      editGroupPhotoAction.setOnClickListener(v -> editGroupPhoto());
      body.addView(editGroupPhotoAction, margins(0, 4, 0, 0));
    }
    NativeTextSlot nameView = text(name, 25, true);
    if (group) groupNameView = nameView;
    nameView.setGravity(Gravity.CENTER);
    add(body, nameView, 16);
    if (group) {
      editGroupNameAction = text("Edit group name", 15, true);
      editGroupNameAction.setTextColor(0xFF019BC5);
      editGroupNameAction.setGravity(Gravity.CENTER);
      editGroupNameAction.setPadding(dp(12), dp(6), dp(12), dp(6));
      editGroupNameAction.setVisibility(View.GONE);
      editGroupNameAction.setOnClickListener(v -> editGroupName());
      body.addView(editGroupNameAction, full());
    }
    boolean directTitleIsPhone = !group && (name.equals(phone)
        || name.equals(DeviceContactResolver.fallback(phone)));
    subtitle = text(group ? memberCountText(getIntent().getIntExtra(EXTRA_MEMBER_COUNT, 0))
        : directTitleIsPhone ? "" : phone, 15, false);
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
    callActions.setPadding(dp(6), dp(6), dp(6), dp(6));
    callActions.setBackground(cardBackground(0xFFFFFFFF));
    body.addView(callActions, margins(0, 20, 0, 16));

    LinearLayout mediaSection = column();
    mediaSection.setPadding(dp(14), dp(10), dp(14), dp(8));
    mediaSection.setBackground(cardBackground(0xFFFFFFFF));
    mediaSection.setOnClickListener(v -> openMediaLibrary());
    mediaHeading = text("Media, links, and docs", 17, true);
    mediaHeading.setPadding(0, dp(4), 0, dp(2));
    mediaSection.addView(mediaHeading, full());
    PagingMediaScroll mediaScroll = this.mediaScroll = new PagingMediaScroll();
    mediaScroll.setOnClickListener(v -> openMediaLibrary());
    mediaRow = row();
    mediaRow.setPadding(0, dp(12), 0, dp(12));
    mediaScroll.addView(mediaRow);
    mediaSection.addView(mediaScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
    body.addView(mediaSection, margins(0, 0, 0, 12));

    if (group) {
      LinearLayout permissions = permissionsSection = column();
      permissions.setPadding(dp(14), dp(10), dp(14), dp(8));
      permissions.setBackground(cardBackground(0xFFFFFFFF));
      permissions.setVisibility(View.GONE);
      NativeTextSlot permissionTitle = text("Group permissions", 17, true);
      permissionTitle.setPadding(0, dp(2), 0, dp(6));
      permissions.addView(permissionTitle, full());
      profilePermissionAction = permissionAction("Edit group photo");
      profilePermissionAction.setOnClickListener(v -> toggleGroupPermission("editProfilePhoto"));
      permissions.addView(profilePermissionAction, full());
      namePermissionAction = permissionAction("Edit group name");
      namePermissionAction.setOnClickListener(v -> toggleGroupPermission("editName"));
      permissions.addView(namePermissionAction, full());
      messagePermissionAction = permissionAction("Send messages");
      messagePermissionAction.setOnClickListener(v -> toggleGroupPermission("sendMessages"));
      permissions.addView(messagePermissionAction, full());
      callPermissionAction = permissionAction("Start calls");
      callPermissionAction.setOnClickListener(v -> toggleGroupPermission("startCalls"));
      permissions.addView(callPermissionAction, full());
      body.addView(permissions, margins(0, 0, 0, 12));
      LinearLayout memberSection = membersSection = column();
      memberSection.setBackground(cardBackground(0xFFFFFFFF));
      members = column();
      membersTitle = text("Members", 17, true);
      membersTitle.setPadding(dp(14), dp(10), dp(14), dp(8));
      membersTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      memberSection.addView(membersTitle, full());
      memberSection.addView(members, full());
      body.addView(memberSection, full());
    }
    LinearLayout dangerSection = column();
    dangerSection.setBackground(cardBackground(0xFFFFFFFF));
    dangerSection.addView(dangerAction("Clear chat", this::confirmClear), full());
    if (group) {
      groupBlockAction = dangerAction("Exit group", this::confirmBlockGroup);
      dangerSection.addView(groupBlockAction, full());
    } else
      dangerSection.addView(dangerAction("Block " + name, this::confirmBlockContact), full());
    dangerSection.addView(
        dangerAction(group ? "Report group" : "Report " + name, this::confirmReport), full());
    body.addView(dangerSection, margins(0, 18, 0, 0));
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

  private PermissionRow permissionAction(String label) {
    PermissionRow action = new PermissionRow(label);
    action.setVisibility(View.GONE);
    return action;
  }

  private void openCall(boolean video) {
    if (group && !groupMemberActive) {
      toast("You are not an active member.");
      return;
    }
    if (group && callsAdminOnly && !ownGroupAdmin) {
      toast("Only group admins can start calls.");
      return;
    }
    if (group) {
      toast("Group call implementation pending.");
      return;
    }
    com.w3n.pinggo.call.CallEngineChooser.show(
        this, video ? "video" : "audio", chatId,
        engine -> startOutgoingCall(video, engine));
  }

  private void startOutgoingCall(boolean video, String engine) {
    Intent intent = new Intent(this, CallActivity.class);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_CHAT_ID, chatId);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID, phone);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PHONE_NUMBER, name);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PROFILE_PATH, profilePath);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_VIDEO, video);
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_MEDIA_TYPE, video ? "video" : "audio");
    intent.putExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ENGINE,
        engine);
    startActivity(intent);
  }

  private void loadGroupDetails() {
    api.getGroupDetails(userId, chatId, callback(result -> {
      JsonObject root = asObject(result);
      JsonObject data = object(root, "group");
      if (data == null)
        return;
      String fetchedName = string(data, "name");
      if (!fetchedName.isEmpty()) {
        name = fetchedName;
        if (groupNameView != null) groupNameView.setText(name);
      }
      refreshGroupPhoto(string(data, "icon"));
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
      messagesAdminOnly = permissionAdminsOnly(permissions, "sendMessages", null);
      callsAdminOnly = permissionAdminsOnly(permissions, "startCalls",
          permissions == null ? "" : string(permissions, "sendMessages"));
      nameAdminOnly = permissionAdminsOnly(
          permissions, "editName", permissionFallback(permissions, "editInfo", "sendMessages"));
      profileAdminOnly = permissionAdminsOnly(
          permissions, "editProfilePhoto", permissionFallback(permissions, "editInfo", "sendMessages"));
      updatePermissionActions();
      updateGroupEditActions();
      membershipNotice.setVisibility(groupMemberActive ? View.GONE : View.VISIBLE);
      callActions.setAlpha(groupMemberActive && (!callsAdminOnly || ownGroupAdmin) ? 1f : 0.42f);
      if (groupBlockAction != null)
        groupBlockAction.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      subtitle.setText(memberCountText(list.size()));
      if (membersSection != null)
        membersSection.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      if (groupMemberActive)
        renderMembers(list);
      else
        members.removeAllViews();
    }));
  }

  private static String permissionFallback(
      JsonObject permissions, String first, String second) {
    if (permissions == null) return "";
    String value = string(permissions, first);
    return value.isEmpty() ? string(permissions, second) : value;
  }

  private static boolean permissionAdminsOnly(
      JsonObject permissions, String key, String fallbackMode) {
    String mode = permissions == null ? "" : string(permissions, key);
    if (mode.isEmpty()) mode = fallbackMode == null ? "" : fallbackMode;
    return "admins".equalsIgnoreCase(mode);
  }

  private void updatePermissionActions() {
    boolean visible = ownGroupAdmin && groupMemberActive;
    if (permissionsSection != null)
      permissionsSection.setVisibility(visible ? View.VISIBLE : View.GONE);
    updatePermissionAction(profilePermissionAction, profileAdminOnly, visible);
    updatePermissionAction(namePermissionAction, nameAdminOnly, visible);
    updatePermissionAction(messagePermissionAction, messagesAdminOnly, visible);
    updatePermissionAction(callPermissionAction, callsAdminOnly, visible);
  }

  private void updatePermissionAction(
      PermissionRow action, boolean adminsOnly, boolean visible) {
    if (action == null) return;
    action.setVisibility(visible ? View.VISIBLE : View.GONE);
    action.setMode(adminsOnly);
  }

  private void updateGroupEditActions() {
    if (editGroupPhotoAction != null) {
      boolean allowed = groupMemberActive && (!profileAdminOnly || ownGroupAdmin);
      editGroupPhotoAction.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      editGroupPhotoAction.setAlpha(allowed ? 1f : 0.42f);
    }
    if (editGroupNameAction != null) {
      boolean allowed = groupMemberActive && (!nameAdminOnly || ownGroupAdmin);
      editGroupNameAction.setVisibility(groupMemberActive ? View.VISIBLE : View.GONE);
      editGroupNameAction.setAlpha(allowed ? 1f : 0.42f);
    }
  }

  private void toggleGroupPermission(String permission) {
    if (!ownGroupAdmin || !groupMemberActive) return;
    boolean enabled;
    switch (permission) {
      case "editProfilePhoto": enabled = !profileAdminOnly; break;
      case "editName": enabled = !nameAdminOnly; break;
      case "startCalls": enabled = !callsAdminOnly; break;
      default: enabled = !messagesAdminOnly; break;
    }
    api.updateGroupPermission(userId, chatId, permission, enabled, callback(result -> {
      switch (permission) {
        case "editProfilePhoto": profileAdminOnly = enabled; break;
        case "editName": nameAdminOnly = enabled; break;
        case "startCalls": callsAdminOnly = enabled; break;
        default: messagesAdminOnly = enabled; break;
      }
      updatePermissionActions();
      updateGroupEditActions();
      callActions.setAlpha(groupMemberActive && (!callsAdminOnly || ownGroupAdmin) ? 1f : 0.42f);
      toast(enabled ? "Only admins can now use this group action."
          : "All members can now use this group action.");
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
      if (hasContactName) {
        subtitle.setText(phone);
      } else {
        subtitle.setText(serverName.isEmpty() || serverName.equalsIgnoreCase(name) ? "" : serverName);
      }
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
      LinearLayout item = column();
      item.setPadding(dp(12), dp(9), dp(12), dp(9));
      item.setBackgroundColor(Color.WHITE);
      LinearLayout identity = row();
      identity.setGravity(Gravity.CENTER_VERTICAL);
      NativeImageSlot avatar = new NativeImageSlot();
      avatar.setImageResource(R.drawable.pinggo_logo);
      identity.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
      LinearLayout labels = column();
      labels.setPadding(dp(12), 0, dp(8), 0);
      NativeTextSlot memberName = text(id.equals(userId) ? "You" : display, 16, true);
      memberName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      labels.addView(memberName);
      NativeTextSlot preview = text(id, 13, false);
      preview.setTextColor(0xFF687382);
      preview.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      labels.addView(preview);
      identity.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
      if (!role.isEmpty() && !"member".equals(role)) {
        NativeTextSlot roleLabel = text(id.equals(groupOwnerId) ? "Owner" : "Admin", 11, true);
        roleLabel.setTextColor(0xFF019BC5);
        roleLabel.setGravity(Gravity.CENTER);
        roleLabel.setMaxLines(1);
        roleLabel.setPadding(dp(6), dp(3), dp(6), dp(3));
        roleLabel.setBackground(cardBackground(0xFFE9F7FB));
        identity.addView(roleLabel, new LinearLayout.LayoutParams(dp(62), dp(30)));
      }
      item.addView(identity, full());
      LinearLayout memberActions = row();
      memberActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
      memberActions.setPadding(dp(60), 0, 0, 0);
      boolean hasMemberAction = false;
      if (ownGroupOwner && groupMemberActive && !id.equals(userId)
          && !"admin".equalsIgnoreCase(role)) {
        NativeTextSlot makeAdmin = text("Make admin", 13, false);
        makeAdmin.setTextColor(0xFF019BC5);
        makeAdmin.setGravity(Gravity.CENTER);
        makeAdmin.setMaxLines(1);
        makeAdmin.setOnClickListener(v -> confirmMakeAdmin(id, display));
        memberActions.addView(makeAdmin, new LinearLayout.LayoutParams(dp(104), dp(38)));
        hasMemberAction = true;
      } else if (ownGroupOwner && groupMemberActive && !id.equals(userId)
          && "admin".equalsIgnoreCase(role) && !id.equals(groupOwnerId)) {
        NativeTextSlot makeMember = text("Make member", 13, false);
        makeMember.setTextColor(0xFF019BC5);
        makeMember.setGravity(Gravity.CENTER);
        makeMember.setMaxLines(1);
        makeMember.setOnClickListener(v -> confirmMakeMember(id, display));
        memberActions.addView(makeMember, new LinearLayout.LayoutParams(dp(112), dp(38)));
        hasMemberAction = true;
      }
      if (ownGroupAdmin && groupMemberActive && !id.equals(userId)
          && !"admin".equalsIgnoreCase(role)) {
        NativeTextSlot remove = text("Remove", 13, false);
        remove.setTextColor(0xFFD9304F);
        remove.setGravity(Gravity.CENTER);
        remove.setMaxLines(1);
        remove.setOnClickListener(v -> confirmRemoveMember(id, display));
        memberActions.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(38)));
        hasMemberAction = true;
      }
      if (hasMemberAction) item.addView(memberActions, margins(0, 2, 0, 0));
      members.addView(item, full());
      View divider = new View(this);
      divider.setBackgroundColor(0xFFE5EAF0);
      LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
      dividerParams.leftMargin = dp(72);
      members.addView(divider, dividerParams);
    }
    if (ownGroupAdmin && groupMemberActive) {
      LinearLayout addMembers = row();
      addMembers.setGravity(Gravity.CENTER_VERTICAL);
      addMembers.setPadding(dp(12), dp(14), dp(12), dp(14));
      addMembers.setBackgroundColor(Color.WHITE);
      NativeImageSlot icon = new NativeImageSlot();
      icon.setImageResource(android.R.drawable.ic_input_add);
      addMembers.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        if (group && result instanceof JsonObject)
          repository.updateGroupMetadata((JsonObject) result);
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

  private void showProfilePhoto(Bitmap fallback) {
    closeProfilePhotoPreview();
    profilePhotoPreview = new ProfilePhotoPreviewView(this);
    ((ViewGroup) findViewById(android.R.id.content)).addView(profilePhotoPreview,
        new ViewGroup.LayoutParams(-1, -1));
    profilePhotoPreview.show(fallback, profilePath, group ? "" : phone,
        this::closeProfilePhotoPreview);
    ViewCompat.requestApplyInsets(profilePhotoPreview);
  }

  private void closeProfilePhotoPreview() {
    ProfilePhotoPreviewView current = profilePhotoPreview;
    profilePhotoPreview = null;
    if (current == null) return;
    current.dismiss();
    if (current.getParent() instanceof ViewGroup)
      ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  private void editGroupPhoto() {
    if (!group || !groupMemberActive) {
      toast("You are not an active member.");
      return;
    }
    if (profileAdminOnly && !ownGroupAdmin) {
      toast("Only group admins can edit the group photo.");
      return;
    }
    groupPhotoPicker.launch("image/*");
  }

  private void editGroupName() {
    if (!group || !groupMemberActive) {
      toast("You are not an active member.");
      return;
    }
    if (nameAdminOnly && !ownGroupAdmin) {
      toast("Only group admins can edit the group name.");
      return;
    }
    showPrompt(NativePromptDialogView.input(this, "Edit group name", name,
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        value -> {
          String nextName = value == null ? "" : value.trim();
          if (nextName.isEmpty()) {
            toast("Enter a group name.");
            return false;
          }
          if (nextName.length() > 100) {
            toast("Group name must be 100 characters or fewer.");
            return false;
          }
          api.updateGroup(userId, chatId, nextName, null, callback(result -> {
            name = nextName;
            if (groupNameView != null) groupNameView.setText(name);
            toast("Group name updated.");
          }));
          return true;
        }, this::removePrompt));
  }

  private void refreshGroupPhoto(String url) {
    if (url == null || url.trim().isEmpty()) return;
    String normalizedUrl = url.trim();
    if (normalizedUrl.equals(groupProfilePhotoUrl) && profileBitmap != null) return;
    groupProfilePhotoUrl = normalizedUrl;
    photoExecutor.execute(() -> {
      String localPath = ChatProfilePhotoStore.downloadAndStore(
          getApplicationContext(), chatId, normalizedUrl);
      Bitmap bitmap = localPath == null ? null : BitmapFactory.decodeFile(localPath);
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed() || bitmap == null) return;
        profilePath = localPath;
        profileBitmap = bitmap;
        ProfileBitmapCache.get().invalidatePath(localPath);
        if (groupAvatar != null) groupAvatar.setImageBitmap(bitmap);
      });
    });
  }

  private void onGroupPhotoSelected(Uri uri) {
    if (uri == null || !group || !groupMemberActive) return;
    photoExecutor.execute(() -> {
      try {
        Bitmap bitmap = decodeGroupPhoto(uri);
        runOnUiThread(() -> {
          if (isFinishing() || isDestroyed()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            return;
          }
          showGroupPhotoCrop(bitmap);
        });
      } catch (Exception error) {
        runOnUiThread(() -> toast("Unable to load group photo."));
      }
    });
  }

  private Bitmap decodeGroupPhoto(Uri uri) throws java.io.IOException {
    if (android.os.Build.VERSION.SDK_INT >= 28) {
      return android.graphics.ImageDecoder.decodeBitmap(
          android.graphics.ImageDecoder.createSource(getContentResolver(), uri),
          (decoder, info, source) -> {
            float scale = Math.min(1f,
                512f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
            decoder.setTargetSize(
                Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                Math.max(1, Math.round(info.getSize().getHeight() * scale)));
            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
          });
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(input, null, options);
    }
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
      Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
      if (bitmap == null) throw new java.io.IOException("Invalid image");
      return bitmap;
    }
  }

  private void showGroupPhotoCrop(Bitmap bitmap) {
    removeGroupPhotoCrop();
    setSystemBarsHidden(true);
    groupPhotoCrop = new NativeCropView(this, bitmap, 495, 1155,
        new NativeCropView.Listener() {
          @Override public void onRetry() { groupPhotoPicker.launch("image/*"); }
          @Override public void onConfirm(Bitmap cropped) { uploadGroupPhoto(cropped); }
          @Override public void onInvalidCrop() { toast("Unable to crop image."); }
          @Override public void onDismiss() { removeGroupPhotoCrop(); }
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(groupPhotoCrop,
        new ViewGroup.LayoutParams(-1, -1));
  }

  private void removeGroupPhotoCrop() {
    NativeCropView current = groupPhotoCrop;
    groupPhotoCrop = null;
    if (current == null) return;
    restoreSystemBars();
    if (current.getParent() instanceof ViewGroup)
      ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  private void uploadGroupPhoto(Bitmap photo) {
    if (editGroupPhotoAction != null) {
      editGroupPhotoAction.setText("Uploading group photo…");
      editGroupPhotoAction.setAlpha(0.42f);
    }
    api.uploadGroupProfilePhoto(userId, chatId, photo, new AppFunctionManager.Callback() {
      @Override public void onSuccess(Object result) {
        JsonObject response = asObject(result);
        repository.updateGroupMetadata(response);
        String uploadedUrl = string(response, "profilePhotoUrl");
        photoExecutor.execute(() -> {
          String localPath = ChatProfilePhotoStore.storeBitmap(
              getApplicationContext(), chatId, photo, uploadedUrl);
          runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            profilePath = localPath == null ? profilePath : localPath;
            groupProfilePhotoUrl = uploadedUrl;
            profileBitmap = photo;
            if (localPath != null) ProfileBitmapCache.get().invalidatePath(localPath);
            if (groupAvatar != null) groupAvatar.setImageBitmap(photo);
            if (editGroupPhotoAction != null) editGroupPhotoAction.setText("Edit group photo");
            updateGroupEditActions();
            toast("Group photo updated.");
          });
        });
      }

      @Override public void onError(String error) {
        runOnUiThread(() -> {
          if (editGroupPhotoAction != null) editGroupPhotoAction.setText("Edit group photo");
          updateGroupEditActions();
          toast(error == null ? "Group photo upload failed." : error);
        });
      }
    });
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

  private GradientDrawable cardBackground(int color) {
    GradientDrawable background = new GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(dp(14));
    background.setStroke(dp(1), 0xFFE5EAF0);
    return background;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class PermissionRow extends LinearLayout {
    private final NativeTextSlot mode;

    PermissionRow(String label) {
      super(ChatInfoActivity.this);
      setOrientation(HORIZONTAL);
      setGravity(Gravity.CENTER_VERTICAL);
      setPadding(0, dp(5), 0, dp(5));
      setMinimumHeight(dp(48));
      NativeTextSlot title = text(label, 15, false);
      title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
      mode = text("All members", 12, true);
      mode.setTextColor(0xFF019BC5);
      mode.setGravity(Gravity.CENTER);
      mode.setMaxLines(1);
      mode.setPadding(dp(8), dp(4), dp(8), dp(4));
      addView(mode, new LinearLayout.LayoutParams(dp(112), dp(36)));
      NativeTextSlot arrow = text("›", 22, false);
      arrow.setTextColor(0xFF8792A2);
      arrow.setGravity(Gravity.CENTER);
      addView(arrow, new LinearLayout.LayoutParams(dp(18), -2));
    }

    void setMode(boolean adminsOnly) {
      mode.setText(adminsOnly ? "Admins only" : "All members");
      mode.setTextColor(adminsOnly ? 0xFF8A5A00 : 0xFF007A61);
      mode.setBackground(cardBackground(adminsOnly ? 0xFFFFF4D6 : 0xFFE5F7F2));
    }
  }

  private final class NativeTextSlot extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("chat_info_text");
    private String value;
    private final int sizeSp;
    private final boolean bold;
    private int maxLines = 3;
    private int color = 0xFF07131E;
    private int gravity = Gravity.START | Gravity.CENTER_VERTICAL;
    NativeTextSlot(String value, int sizeSp, boolean bold) {
      super(ChatInfoActivity.this);
      this.value = value == null ? "" : value; this.sizeSp = sizeSp; this.bold = bold;
    }
    void setText(String text) { value = text == null ? "" : text; requestLayout(); rebuild(); }
    void setTextColor(int color) { this.color = color; rebuild(); }
    void setGravity(int gravity) { this.gravity = gravity; rebuild(); }
    void setMaxLines(int lines) {
      maxLines = Math.max(1, lines);
      requestLayout();
      rebuild();
    }
    private void rebuild() {
      if (getWidth() <= 0 || getHeight() <= 0) return;
      layer.clear();
      int horizontal = Gravity.getAbsoluteGravity(gravity, getLayoutDirection())
          & Gravity.HORIZONTAL_GRAVITY_MASK;
      Text.Alignment alignment = horizontal == Gravity.CENTER_HORIZONTAL
          ? Text.Alignment.CENTER : horizontal == Gravity.RIGHT
              ? Text.Alignment.END : Text.Alignment.START;
      layer.add(new Text.Builder(getContext(), "value", value,
          new RectF(getPaddingLeft(), getPaddingTop(),
              getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()))
          .setFont(NativeFonts.INTER)
          .setFontVariations(bold ? FontVariation.BOLD : FontVariation.REGULAR)
          .setTextColor(color).setTextSizePx(sizeSp * getResources().getDisplayMetrics().scaledDensity)
          .setAlignment(alignment).setVerticalAlignment(Text.VerticalAlignment.CENTER)
          .setMaxLines(maxLines));
      invalidate();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
      TextPaint paint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
      paint.setFakeBoldText(bold);
      int horizontalPadding = getPaddingLeft() + getPaddingRight();
      int widthMode = MeasureSpec.getMode(widthSpec);
      int widthSize = MeasureSpec.getSize(widthSpec);
      int contentWidth;
      if (widthMode == MeasureSpec.UNSPECIFIED) {
        float widest = 0f;
        for (String line : value.split("\\n", -1)) widest = Math.max(widest, paint.measureText(line));
        contentWidth = Math.max(1, (int) Math.ceil(widest));
      } else {
        contentWidth = Math.max(1, widthSize - horizontalPadding);
      }
      StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), paint, contentWidth)
          .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
          .setMaxLines(maxLines).build();
      float widestLine = 0f;
      for (int i = 0; i < layout.getLineCount(); i++)
        widestLine = Math.max(widestLine, layout.getLineWidth(i));
      int desiredWidth = (int) Math.ceil(widestLine) + horizontalPadding;
      int desiredHeight = layout.getHeight() + getPaddingTop() + getPaddingBottom();
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
    removeGroupPhotoCrop();
    closeProfilePhotoPreview();
    for (NativeImageSlot image : mediaImages.values()) image.release();
    removePrompt();
    if (detailsMenu != null) detailsMenu.release();
    detailsMenu = null;
    photoExecutor.shutdownNow();
    super.onDestroy();
  }
}
