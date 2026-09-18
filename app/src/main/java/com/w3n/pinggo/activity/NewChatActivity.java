package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.Util.PhoneNumberFormatter;
import com.w3n.pinggo.Util.login.CountryDetector;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.NewChatView;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NewChatActivity extends AppCompatActivity implements NewChatView.Listener {
  public static final String EXTRA_SHOW_CHAT_LIST = "com.w3n.pinggo.EXTRA_SHOW_CHAT_LIST";
  private boolean showChatList;
  public static final String EXTRA_CREATE_GROUP = "com.w3n.pinggo.EXTRA_CREATE_GROUP";
  public static final String EXTRA_PRESELECTED_MEMBER_IDS =
      "com.w3n.pinggo.EXTRA_PRESELECTED_MEMBER_IDS";
  public static final String EXTRA_ADD_TO_GROUP_ID = "com.w3n.pinggo.EXTRA_ADD_TO_GROUP_ID";
  public static final String EXTRA_SELECT_CALL_MEMBERS = "com.w3n.pinggo.EXTRA_SELECT_CALL_MEMBERS";
  public static final String EXTRA_EXCLUDED_MEMBER_IDS = "com.w3n.pinggo.EXTRA_EXCLUDED_MEMBER_IDS";
  public static final String RESULT_MEMBER_IDS = "com.w3n.pinggo.RESULT_MEMBER_IDS";
  public static final String EXTRA_FORWARD_SOURCE_CHAT_ID = "com.w3n.pinggo.EXTRA_FORWARD_SOURCE_CHAT_ID";
  public static final String EXTRA_FORWARD_MESSAGE_IDS = "com.w3n.pinggo.EXTRA_FORWARD_MESSAGE_IDS";
  private static final int CONTACTS_PERMISSION_REQUEST = 42, DISCOVER_BATCH_SIZE = 50;
  private static final String DISCOVERY_CACHE = "discovered_accounts_v1";
  private static final long DISCOVERY_CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
  private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService photoExecutor = Executors.newFixedThreadPool(3);
  private final List<JsonObject> found = new ArrayList<>();
  private final List<String> invites = new ArrayList<>();
  private final List<ChatEntity> orderedChats = new ArrayList<>();
  private final Set<String> rendered = new LinkedHashSet<>();
  private final Set<String> photoDownloads = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicInteger discoveryGeneration = new AtomicInteger();
  private NewChatView newChatView;
  private boolean creatingGroup;
  private com.w3n.pinggo.views.chat.CreateGroupView groupDetails;
  private String groupPhotoBase64;
  private com.w3n.pinggo.views.common.NativeCropView groupCropView;
  private final List<String> groupMembers = new ArrayList<>();
  private final androidx.activity.result.ActivityResultLauncher<String> groupPhotoPicker =
      registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
        if (uri == null || groupDetails == null || creatingGroup) return;
        photoExecutor.execute(() -> {
          try {
            android.graphics.Bitmap bitmap;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
              bitmap = android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, source) -> {
                  float scale = Math.min(1f, 512f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
                  decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                      Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                  decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
              android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
              options.inJustDecodeBounds = true;
              try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(input, null, options);
              }
              options.inSampleSize = 1;
              while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512)
                options.inSampleSize *= 2;
              options.inJustDecodeBounds = false;
              try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                bitmap = android.graphics.BitmapFactory.decodeStream(input, null, options);
              }
              if (bitmap == null) throw new java.io.IOException("Invalid image");
            }
            runOnUiThread(() -> { if (!isClosing() && groupDetails != null && !creatingGroup) {
              showGroupCrop(bitmap);
            } });
          } catch (Exception error) {
            runOnUiThread(() -> Toast.makeText(this, "Unable to load group photo.", Toast.LENGTH_SHORT).show());
          }
        });
      });
  private ChatRepository repository;
  private NativePromptDialogView promptDialog;
  private String forwardSourceChatId;
  private ArrayList<String> forwardMessageIds;
  private boolean createGroupMode;
  private String addToGroupId;
  private boolean selectCallMembers;
  private final Set<String> excludedMemberIds = new LinkedHashSet<>();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() { onBack(); }
    });
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    newChatView = new NewChatView(this, this);
    forwardSourceChatId = getIntent().getStringExtra(EXTRA_FORWARD_SOURCE_CHAT_ID);
    forwardMessageIds = getIntent().getStringArrayListExtra(EXTRA_FORWARD_MESSAGE_IDS);
    repository = ChatRepository.getInstance(this);
    createGroupMode = getIntent().getBooleanExtra(EXTRA_CREATE_GROUP, false);
    showChatList = getIntent().getBooleanExtra(EXTRA_SHOW_CHAT_LIST, false);
    if (showChatList) createGroupMode = true;
    addToGroupId = getIntent().getStringExtra(EXTRA_ADD_TO_GROUP_ID);
    selectCallMembers = getIntent().getBooleanExtra(EXTRA_SELECT_CALL_MEMBERS, false);
    ArrayList<String> excluded = getIntent().getStringArrayListExtra(EXTRA_EXCLUDED_MEMBER_IDS);
    if (excluded != null) for (String id : excluded) {
      String normalized = normalize(id);
      if (!normalized.isEmpty()) excludedMemberIds.add(normalized);
    }
    if (selectCallMembers) createGroupMode = true;
    if (addToGroupId != null && !addToGroupId.trim().isEmpty()) createGroupMode = true;
    if (createGroupMode)
      newChatView.setGroupMode(true);
    ArrayList<String> preselected = getIntent().getStringArrayListExtra(
        EXTRA_PRESELECTED_MEMBER_IDS);
    if (createGroupMode && preselected != null)
      newChatView.setSelectedMembers(preselected);
    if (selectCallMembers) {
      newChatView.setTitle("Add to call");
      newChatView.setGroupActionLabel("Invite");
    } else if (showChatList) {
      newChatView.setTitle("New call");
      newChatView.setGroupActionLabel("Call");
    } else if (addToGroupId != null && !addToGroupId.trim().isEmpty()) {
      newChatView.setTitle("Add members");
      newChatView.setGroupActionLabel("Add");
    } else if (isForwarding())
      newChatView.setTitle("Forward to");
    if (createGroupMode || showChatList) {
      repository.observeChats().observe(this, chats -> {
        orderedChats.clear();
        if (chats != null) orderedChats.addAll(chats);
        render();
      });
    }
    FrameLayout root = new FrameLayout(this);
    root.addView(newChatView, new FrameLayout.LayoutParams(-1, -1));
    EditText search = new EditText(this);
    search.setSingleLine(true);
    search.setHint("Search name or phone number");
    search.setTextSize(15f);
    search.setPadding(dp(16), 0, dp(16), 0);
    search.setBackgroundColor(0xFFFFFFFF);
    search.setElevation(dp(2));
    FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(-1, dp(48));
    searchParams.leftMargin = dp(16);
    searchParams.rightMargin = dp(16);
    root.addView(search, searchParams);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        newChatView,
        (v, i) -> {
          Insets b = i.getInsets(WindowInsetsCompat.Type.systemBars());
          newChatView.setInsets(b.top, b.bottom);
          FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) search.getLayoutParams();
          params.topMargin = b.top + dp(64);
          search.setLayoutParams(params);
          return i;
        });
    ViewCompat.requestApplyInsets(newChatView);
    search.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        newChatView.setSearchQuery(s == null ? "" : s.toString());
      }
      @Override public void afterTextChanged(Editable s) {}
    });
    loadContactsWithPermission();
  }

  private void loadContactsWithPermission() {
    if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
      DeviceContactResolver.warmUp(this, () -> {
        if (!loadDiscoveryCache()) discoverContacts();
      });
      return;
    }
    // Cached results may be rendered immediately, but never suppress the permission request.
    // Once permission is granted, discovery restarts from the device's current contacts.
    loadDiscoveryCache();
    ActivityCompat.requestPermissions(
        this, new String[] { Manifest.permission.READ_CONTACTS }, CONTACTS_PERMISSION_REQUEST);
  }

  @Override
  public void onRequestPermissionsResult(
      int code, @NonNull String[] permissions, @NonNull int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code != CONTACTS_PERMISSION_REQUEST) return;
    if (results.length > 0
        && results[0] == PackageManager.PERMISSION_GRANTED) {
      DeviceContactResolver.warmUp(this, this::discoverContacts);
    } else if (!found.isEmpty() || !invites.isEmpty())
      render();
    else if (createGroupMode || showChatList)
      render();
    else
      newChatView.showStatus("Contacts permission is required to discover chats.");
  }

  private void discoverContacts() {
    final int generation = discoveryGeneration.incrementAndGet();
    if (!selectCallMembers) newChatView.showStatus("Loading contacts...");
    discoveryExecutor.execute(
        () -> {
          List<String> contacts = readPhoneContacts();
          if (generation != discoveryGeneration.get()) return;
          runOnUiThread(
              () -> {
                if (generation != discoveryGeneration.get()) return;
                if (contacts.isEmpty()) {
                  if (createGroupMode || showChatList) render();
                  else newChatView.showStatus("No contacts found.");
                  return;
                }
                found.clear();
                invites.clear();
                rendered.clear();
                if (!selectCallMembers) newChatView.showStatus("Discovering contacts...");
              });
          if (!contacts.isEmpty())
            discoverNextBatch(contacts, 0, generation);
        });
  }

  private void discoverNextBatch(List<String> contacts, int start, int generation) {
    if (isClosing() || generation != discoveryGeneration.get())
      return;
    if (start >= contacts.size()) {
      runOnUiThread(() -> {
        if (generation == discoveryGeneration.get()) { render(); saveDiscoveryCache(); }
      });
      return;
    }
    int end = Math.min(start + DISCOVER_BATCH_SIZE, contacts.size());
    List<String> batch = new ArrayList<>(contacts.subList(start, end));
    discoverBatch(batch)
        .whenCompleteAsync(
            (response, error) -> {
              if (isClosing() || generation != discoveryGeneration.get())
                return;
              if (error == null) {
                BatchResult result = parseBatch(response);
                runOnUiThread(() -> {
                  if (generation == discoveryGeneration.get()) append(result);
                });
                prefetchProfilePhotos(result.foundContacts);
              } else {
                runOnUiThread(
                    () -> {
                      if (generation != discoveryGeneration.get()) return;
                      Toast.makeText(
                        this,
                        error.getMessage() == null
                            ? "Contact discovery failed."
                            : error.getMessage(),
                        Toast.LENGTH_SHORT)
                        .show();
                    });
              }
              discoverNextBatch(contacts, end, generation);
            },
            discoveryExecutor);
  }

  private CompletableFuture<JsonObject> discoverBatch(List<String> contacts) {
    CompletableFuture<JsonObject> future = new CompletableFuture<>();
    AppFunctionManager.getInstance()
        .discoverContacts(
            currentPhone(),
            contacts,
            new AppFunctionManager.Callback() {
              @Override
              public void onSuccess(Object o) {
                if (o instanceof JsonObject)
                  future.complete((JsonObject) o);
                else
                  future.completeExceptionally(
                      new IllegalStateException("Unable to load contacts."));
              }

              @Override
              public void onError(String e) {
                future.completeExceptionally(new IllegalStateException(e));
              }
            });
    return future;
  }

  private List<String> readPhoneContacts() {
    Set<String> values = new LinkedHashSet<>();
    String own = normalize(currentPhone());
    String defaultRegion = CountryDetector.detectCountryIso(this);
    String[] projection = { ContactsContract.CommonDataKinds.Phone.NUMBER };
    try (Cursor cursor = getContentResolver()
        .query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC")) {
      if (cursor == null)
        return new ArrayList<>();
      int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
      while (cursor.moveToNext()) {
        String number = PhoneNumberFormatter.toE164Digits(
            this, cursor.getString(index), defaultRegion);
        if (!number.isEmpty() && !number.equals(own))
          values.add(number);
      }
    }
    return new ArrayList<>(values);
  }

  private BatchResult parseBatch(JsonObject response) {
    BatchResult result = new BatchResult();
    JsonArray contacts = response.getAsJsonArray("contacts");
    if (contacts == null)
      return result;
    String own = normalize(currentPhone());
    for (JsonElement element : contacts) {
      if (element == null || !element.isJsonObject())
        continue;
      JsonObject contact = element.getAsJsonObject();
      String phone = normalize(string(contact, "phoneNumber"));
      if (phone.isEmpty() || phone.equals(own))
        continue;
      if (bool(contact, "found"))
        result.foundContacts.add(contact);
      else
        result.invitePhones.add(phone);
    }
    return result;
  }

  private void append(BatchResult result) {
    if (isClosing())
      return;
    for (JsonObject contact : result.foundContacts) {
      String phone = normalize(string(contact, "phoneNumber"));
      if (rendered.add(phone))
        found.add(contact);
    }
    for (String phone : result.invitePhones) {
      if (rendered.add(phone))
        invites.add(phone);
    }
    render();
  }

  private void prefetchProfilePhotos(List<JsonObject> contacts) {
    Context appContext = getApplicationContext();
    for (JsonObject contact : contacts) {
      String phone = normalize(string(contact, "phoneNumber"));
      String url = string(contact, "profilePhotoUrl");
      if (phone.isEmpty()
          || url.trim().isEmpty()
          || ChatProfilePhotoStore.getLocalPath(appContext, phone) != null
          || !photoDownloads.add(phone)
          || photoExecutor.isShutdown())
        continue;
      photoExecutor.execute(
          () -> {
            try {
              String path = ChatProfilePhotoStore.downloadAndStore(appContext, phone, url);
              if (path != null && !isClosing())
                runOnUiThread(this::render);
            } finally {
              photoDownloads.remove(phone);
            }
          });
    }
  }

  private void render() {
    if (isClosing() || newChatView == null)
      return;
    List<NewChatView.Item> items = new ArrayList<>();
    Set<String> addedAccounts = new LinkedHashSet<>();
    List<String> existingAccounts = new ArrayList<>();
    if (createGroupMode || showChatList) {
      // ChatDao already supplies the Chats screen order: pinned first, then most recent.
      // Include existing one-to-one chat users even when they are not saved in
      // Android contacts. Saved contacts below are merged and deduplicated.
      for (ChatEntity chat : orderedChats) {
        if (chat == null || chat.isGroup) continue;
        String phone = chat.isGroup ? chat.chatId : normalize(chat.otherUserId);
        if (phone.isEmpty() || excludedMemberIds.contains(phone)
            || !addedAccounts.add(phone)) continue;
        items.add(NewChatView.Item.found(phone, chat.chatId, chat.profilePhotoUrl,
            chat.contactName));
        existingAccounts.add(phone);
      }
    }
    for (JsonObject contact : found) {
      String phone = normalize(string(contact, "phoneNumber"));
      if (excludedMemberIds.contains(phone) || !addedAccounts.add(phone)) continue;
      items.add(NewChatView.Item.found(phone, string(contact, "chatId"),
          string(contact, "profilePhotoUrl")));
    }
    if (!invites.isEmpty()) {
      boolean dividerAdded = false;
      for (String phone : invites)
        if (!excludedMemberIds.contains(normalize(phone))
            && addedAccounts.add(normalize(phone))) {
          if (!dividerAdded) {
            items.add(NewChatView.Item.divider("Invite"));
            dividerAdded = true;
          }
          items.add(NewChatView.Item.invite(phone));
        }
    }
    if (showChatList) newChatView.setChatSections(existingAccounts);
    newChatView.submitItems(items);
  }

  private boolean loadDiscoveryCache() {
    android.content.SharedPreferences cache = getSharedPreferences(
        DISCOVERY_CACHE, MODE_PRIVATE);
    String owner = normalize(currentPhone());
    if (!owner.equals(cache.getString("owner", ""))) return false;
    long savedAt = cache.getLong("savedAt", 0L);
    if (savedAt <= 0L || System.currentTimeMillis() - savedAt >= DISCOVERY_CACHE_TTL_MS)
      return false;
    try {
      JsonObject value = com.google.gson.JsonParser.parseString(
          cache.getString("value", "{}")).getAsJsonObject();
      found.clear(); invites.clear(); rendered.clear();
      JsonArray cachedFound = value.getAsJsonArray("found");
      if (cachedFound != null) for (JsonElement item : cachedFound) {
        if (!item.isJsonObject()) continue;
        JsonObject contact = item.getAsJsonObject();
        String phone = normalize(string(contact, "phoneNumber"));
        if (!phone.isEmpty() && rendered.add(phone)) found.add(contact);
      }
      JsonArray cachedInvites = value.getAsJsonArray("invites");
      if (cachedInvites != null) for (JsonElement item : cachedInvites) {
        String phone = normalize(item.getAsString());
        if (!phone.isEmpty() && rendered.add(phone)) invites.add(phone);
      }
      render();
      android.util.Log.i("PingGoContacts", "discovery_cache_hit ageMs="
          + (System.currentTimeMillis() - savedAt) + " found=" + found.size());
      return true;
    } catch (RuntimeException error) {
      cache.edit().clear().apply();
      return false;
    }
  }

  private void saveDiscoveryCache() {
    JsonObject value = new JsonObject();
    JsonArray cachedFound = new JsonArray();
    for (JsonObject contact : found) cachedFound.add(contact.deepCopy());
    JsonArray cachedInvites = new JsonArray();
    for (String phone : invites) cachedInvites.add(phone);
    value.add("found", cachedFound);
    value.add("invites", cachedInvites);
    getSharedPreferences(DISCOVERY_CACHE, MODE_PRIVATE).edit()
        .putString("owner", normalize(currentPhone()))
        .putLong("savedAt", System.currentTimeMillis())
        .putString("value", value.toString()).apply();
    android.util.Log.i("PingGoContacts", "discovery_cache_saved found="
        + found.size() + " invites=" + invites.size());
  }

  private void refreshContacts() {
    discoveryGeneration.incrementAndGet();
    getSharedPreferences(DISCOVERY_CACHE, MODE_PRIVATE).edit().clear().apply();
    DeviceContactResolver.clearCache();
    found.clear();
    invites.clear();
    rendered.clear();
    newChatView.showStatus("Loading contacts...");
    if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[] { Manifest.permission.READ_CONTACTS }, CONTACTS_PERMISSION_REQUEST);
      return;
    }
    DeviceContactResolver.warmUp(this, this::discoverContacts);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onBack() {
    if (promptDialog != null) { removePrompt(); return; }
    if (groupCropView != null) { groupCropView.dismissIfShowing(); return; }
    if (groupDetails != null) {
      if (!creatingGroup) {
        if (groupDetails.getVisibility() == android.view.View.GONE) {
          groupDetails.setVisibility(android.view.View.VISIBLE);
          newChatView.setSelectedMembers(groupMembers);
          return;
        }
        ((ViewGroup) groupDetails.getParent()).removeView(groupDetails);
        groupDetails = null;
        groupPhotoBase64 = null;
      }
      return;
    }
    finish();
  }

  @Override public void onBackPressed() {
    if (promptDialog != null || groupCropView != null || groupDetails != null) onBack(); else super.onBackPressed();
  }

  @Override
  public void onMore() {
    showPrompt(NativePromptDialogView.actions(this,
        Collections.singletonList("Refresh"), index -> {
          if (index == 0) refreshContacts();
        }, this::removePrompt));
  }

  @Override
  public void onOpenChat(NewChatView.Item item) {
    if (isForwarding()) {
      if (item.chatId == null || item.chatId.trim().isEmpty()) {
        Toast.makeText(this, "This contact has no chat yet.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.forwardMessages(
          forwardSourceChatId, forwardMessageIds, item.chatId, item.phoneNumber);
    }
    Intent intent = new Intent(this, ChatActivity.class);
    intent.putExtra(ChatActivity.EXTRA_CHAT_NAME,
        item.displayName == null || item.displayName.trim().isEmpty()
            ? DeviceContactResolver.nameOrPhone(this, item.phoneNumber) : item.displayName);
    intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId);
    intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, item.profilePhotoUrl);
    intent.putExtra(
        ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH,
        ChatProfilePhotoStore.getLocalPath(this, item.phoneNumber));
    startActivity(intent);
    if (isForwarding())
      finish();
  }

  private boolean isForwarding() {
    return forwardSourceChatId != null && !forwardSourceChatId.trim().isEmpty()
        && forwardMessageIds != null && !forwardMessageIds.isEmpty();
  }

  @Override
  public void onInvite(String phone) {
    Intent intent = new Intent(Intent.ACTION_SENDTO);
    intent.setData(Uri.parse("smsto:" + phone));
    intent.putExtra("sms_body", "Join me on PingGo");
    try {
      startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(this, "No SMS app available.", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onGroupSelectionRequired() {
    Toast.makeText(this, "Select at least one member.", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onCreateGroup(List<String> memberIds) {
    if (!createGroupMode || creatingGroup || memberIds == null || memberIds.isEmpty())
      return;
    if (showChatList) {
      showCallOptions(new ArrayList<>(memberIds));
      return;
    }
    if (selectCallMembers) {
      setResult(RESULT_OK, new Intent().putStringArrayListExtra(
          RESULT_MEMBER_IDS, new ArrayList<>(memberIds)));
      finish();
      return;
    }
    if (addToGroupId != null && !addToGroupId.trim().isEmpty()) {
      creatingGroup = true;
      AppFunctionManager.getInstance().updateGroupMembers(
          currentPhone(), addToGroupId, memberIds, true, new AppFunctionManager.Callback() {
            @Override public void onSuccess(Object value) {
              creatingGroup = false;
              Toast.makeText(NewChatActivity.this, "Members added.", Toast.LENGTH_SHORT).show();
              setResult(RESULT_OK);
              finish();
            }
            @Override public void onError(String error) {
              creatingGroup = false;
              Toast.makeText(NewChatActivity.this,
                  error == null || error.trim().isEmpty() ? "Unable to add members." : error,
                  Toast.LENGTH_SHORT).show();
            }
          });
      return;
    }
    groupMembers.clear();
    groupMembers.addAll(memberIds);
    if (groupDetails != null) {
      groupDetails.setMembers(groupMemberLabels());
      groupDetails.setVisibility(android.view.View.VISIBLE);
      return;
    }
    List<String> selected = groupMembers;
    List<String> labels = groupMemberLabels();
    groupDetails = new com.w3n.pinggo.views.chat.CreateGroupView(this, labels, this::onBack,
        () -> groupPhotoPicker.launch("image/*"), () -> {
          if (creatingGroup) return;
          if (selected.isEmpty()) { onGroupSelectionRequired(); return; }
          String name = groupDetails.groupName();
          if (name.isEmpty()) {
            groupDetails.showNameError();
            return;
          }
          creatingGroup = true;
          groupDetails.setBusy(true);
          AppFunctionManager.getInstance().createGroup(currentPhone(), name, "", selected,
              groupPhotoBase64, groupDetails.adminsOnly(),
              new AppFunctionManager.Callback() {
                @Override
                public void onSuccess(Object value) {
                  creatingGroup = false;
                  if (isClosing()) return;
                  if (!(value instanceof JsonObject)) {
                    Toast.makeText(NewChatActivity.this, "Invalid group response.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                  }
                  JsonObject response = (JsonObject) value;
                  JsonObject group = response.has("group") && response.get("group").isJsonObject()
                      ? response.getAsJsonObject("group")
                      : null;
                  String groupId = group == null ? "" : string(group, "groupId");
                  if (groupId.isEmpty()) {
                    Toast.makeText(NewChatActivity.this, "Group was created but could not be opened.",
                        Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                  }
                  repository.refreshChatList(currentPhone());
                  Intent intent = new Intent(NewChatActivity.this, ChatActivity.class);
                  intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, name);
                  intent.putExtra(ChatActivity.EXTRA_CHAT_ID, groupId);
                  intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, string(group, "icon"));
                  startActivity(intent);
                  finish();
                }

                @Override
                public void onError(String error) {
                  creatingGroup = false;
                  if (groupDetails != null) groupDetails.setBusy(false);
                  runOnUiThread(() -> Toast.makeText(NewChatActivity.this,
                        error == null || error.trim().isEmpty() ? "Unable to create group." : error,
                        Toast.LENGTH_SHORT).show());
                }
              });
        }, () -> {
          newChatView.setSelectedMembers(groupMembers);
          groupDetails.setVisibility(android.view.View.GONE);
          ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(groupDetails.getWindowToken(), 0);
        }, () -> {
          groupPhotoBase64 = null;
          groupDetails.clearPhoto();
        }, index -> {
          if (creatingGroup || index < 0 || index >= groupMembers.size()) return;
          groupMembers.remove(index);
          newChatView.setSelectedMembers(groupMembers);
          groupDetails.setMembers(groupMemberLabels());
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(groupDetails, new ViewGroup.LayoutParams(-1, -1));
    ViewCompat.setOnApplyWindowInsetsListener(groupDetails, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(groupDetails);
  }

  private void showPrompt(NativePromptDialogView prompt) {
    removePrompt();
    promptDialog = prompt;
    ((ViewGroup) findViewById(android.R.id.content)).addView(prompt,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private List<String> groupMemberLabels() {
    List<String> labels = new ArrayList<>();
    for (String id : groupMembers) {
      String label = DeviceContactResolver.cachedNameOrPhone(id);
      for (ChatEntity chat : orderedChats)
        if (id.equals(normalize(chat.otherUserId)) && chat.contactName != null && !chat.contactName.trim().isEmpty()) {
          label = chat.contactName; break;
        }
      labels.add(label.equals(id) ? id : label + " · " + id);
    }
    return labels;
  }

  private void showCallOptions(ArrayList<String> members) {
    boolean multiple = members.size() > 1;
    if (members.size() > 19) {
      Toast.makeText(this, "Select up to 19 people for a call.", Toast.LENGTH_SHORT).show();
      return;
    }
    List<String> choices = multiple
        ? java.util.Arrays.asList("Voice call · LiveKit", "Video call · LiveKit")
        : java.util.Arrays.asList("Voice call · LiveKit", "Video call · LiveKit",
            "Voice call · WebRTC", "Video call · WebRTC");
    showPrompt(NativePromptDialogView.actions(this, choices, index -> {
      removePrompt();
      startSelectedCall(members, index % 2 == 1, index < 2);
    }, this::removePrompt));
  }

  private void startSelectedCall(ArrayList<String> members, boolean video, boolean liveKit) {
    if (members.isEmpty() || isClosing()) return;
    com.w3n.pinggo.call.ActiveCallRegistry registry = com.w3n.pinggo.call.ActiveCallRegistry.getInstance();
    if (registry.hasActiveCall()) {
      Toast.makeText(this, "A call is already active.", Toast.LENGTH_SHORT).show();
      registry.openExisting(this);
      return;
    }
    String receiver = members.get(0);
    String callChatId = "";
    String displayName = DeviceContactResolver.cachedNameOrPhone(receiver);
    for (ChatEntity chat : orderedChats) {
      if (!chat.isGroup && receiver.equals(normalize(chat.otherUserId))) {
        callChatId = chat.chatId;
        if (chat.contactName != null && !chat.contactName.trim().isEmpty()) displayName = chat.contactName;
        break;
      }
    }
    if (callChatId == null || callChatId.isEmpty()) {
      for (JsonObject contact : found) if (receiver.equals(normalize(string(contact, "phoneNumber")))) {
        callChatId = string(contact, "chatId"); break;
      }
    }
    if (callChatId == null || callChatId.isEmpty()) {
      List<String> pair = new ArrayList<>(java.util.Arrays.asList(currentPhone(), receiver));
      Collections.sort(pair);
      callChatId = pair.get(0) + "_" + pair.get(1);
    }
    Intent intent = new Intent(this, liveKit ? LiveKitCallActivity.class
        : video ? VideoCallActivity.class : VoiceCallActivity.class);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, callChatId);
    String outgoingCallId = java.util.UUID.randomUUID().toString();
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, outgoingCallId);
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, receiver);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER, displayName);
    intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, ChatProfilePhotoStore.getLocalPath(this, receiver));
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ENGINE, liveKit
        ? com.w3n.pinggo.call.CallEngineToggle.LIVEKIT : com.w3n.pinggo.call.CallEngineToggle.LEGACY);
    if (liveKit) {
      intent.putExtra(LiveKitCallActivity.EXTRA_MEDIA_TYPE, video ? "video" : "audio");
      intent.putExtra(LiveKitCallActivity.EXTRA_CONFERENCE_CALL, members.size() > 1);
      intent.putStringArrayListExtra(LiveKitCallActivity.EXTRA_PARTICIPANT_IDS, members);
    }
    registry.closePickerWhenConnected(this, outgoingCallId);
    startActivity(intent);
  }

  private void showGroupCrop(android.graphics.Bitmap bitmap) {
    removeGroupCrop();
    androidx.core.view.WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    bars.hide(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
    groupCropView = new com.w3n.pinggo.views.common.NativeCropView(this, bitmap, 495, 1155,
        new com.w3n.pinggo.views.common.NativeCropView.Listener() {
          @Override public void onRetry() { groupPhotoPicker.launch("image/*"); }
          @Override public void onConfirm(android.graphics.Bitmap cropped) {
            if (groupDetails == null || isClosing()) return;
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bytes);
            groupPhotoBase64 = android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP);
            groupDetails.setPhoto(cropped);
          }
          @Override public void onInvalidCrop() {
            Toast.makeText(NewChatActivity.this, "Unable to crop image.", Toast.LENGTH_SHORT).show();
          }
          @Override public void onDismiss() { removeGroupCrop(); }
        });
    ((ViewGroup) findViewById(android.R.id.content)).addView(groupCropView, new ViewGroup.LayoutParams(-1, -1));
  }

  private void removeGroupCrop() {
    com.w3n.pinggo.views.common.NativeCropView current = groupCropView;
    groupCropView = null;
    if (current == null) return;
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).show(WindowInsetsCompat.Type.systemBars());
    if (current.getParent() instanceof ViewGroup) ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  private void removePrompt() {
    NativePromptDialogView current = promptDialog;
    promptDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup)
      ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  private String currentPhone() {
    String uid = LoginStateManager.getInstance().getUID(this);
    return normalize(uid);
  }

  private static String normalize(String v) {
    if (v == null)
      return "";
    String n = v.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
    if (n.startsWith("<plus>"))
      n = n.substring(6);
    return n.startsWith("+") ? n.substring(1) : n;
  }

  private static String string(JsonObject o, String k) {
    JsonElement e = o.get(k);
    return e == null || e.isJsonNull() ? "" : e.getAsString();
  }

  private static boolean bool(JsonObject o, String k) {
    JsonElement e = o.get(k);
    return e != null && !e.isJsonNull() && e.getAsBoolean();
  }

  private boolean isClosing() {
    return isFinishing() || isDestroyed();
  }

  @Override
  protected void onDestroy() {
    com.w3n.pinggo.call.ActiveCallRegistry.getInstance().clearCallPicker(this);
    removeGroupCrop();
    discoveryGeneration.incrementAndGet();
    removePrompt();
    discoveryExecutor.shutdownNow();
    photoExecutor.shutdownNow();
    photoDownloads.clear();
    if (newChatView != null)
      newChatView.release();
    newChatView = null;
    super.onDestroy();
  }

  private static final class BatchResult {
    final List<JsonObject> foundContacts = new ArrayList<>();
    final List<String> invitePhones = new ArrayList<>();
  }
}
