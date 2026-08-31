package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.NewChatView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewChatActivity extends AppCompatActivity implements NewChatView.Listener {
  public static final String EXTRA_FORWARD_SOURCE_CHAT_ID =
      "com.w3n.pinggo.EXTRA_FORWARD_SOURCE_CHAT_ID";
  public static final String EXTRA_FORWARD_MESSAGE_IDS =
      "com.w3n.pinggo.EXTRA_FORWARD_MESSAGE_IDS";
  private static final int CONTACTS_PERMISSION_REQUEST = 42, DISCOVER_BATCH_SIZE = 50;
  private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService photoExecutor = Executors.newFixedThreadPool(3);
  private final List<JsonObject> found = new ArrayList<>();
  private final List<String> invites = new ArrayList<>();
  private final Set<String> rendered = new LinkedHashSet<>();
  private final Set<String> photoDownloads = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private NewChatView newChatView;
  private ChatRepository repository;
  private String forwardSourceChatId;
  private ArrayList<String> forwardMessageIds;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    newChatView = new NewChatView(this, this);
    forwardSourceChatId = getIntent().getStringExtra(EXTRA_FORWARD_SOURCE_CHAT_ID);
    forwardMessageIds = getIntent().getStringArrayListExtra(EXTRA_FORWARD_MESSAGE_IDS);
    repository = ChatRepository.getInstance(this);
    if (isForwarding()) newChatView.setTitle("Forward to");
    setContentView(newChatView);
    ViewCompat.setOnApplyWindowInsetsListener(
        newChatView,
        (v, i) -> {
          Insets b = i.getInsets(WindowInsetsCompat.Type.systemBars());
          newChatView.setInsets(b.top, b.bottom);
          return i;
        });
    ViewCompat.requestApplyInsets(newChatView);
    loadContactsWithPermission();
  }

  private void loadContactsWithPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
        == PackageManager.PERMISSION_GRANTED) {
      discoverContacts();
      return;
    }
    ActivityCompat.requestPermissions(
        this, new String[] {Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST);
  }

  @Override
  public void onRequestPermissionsResult(
      int code, @NonNull String[] permissions, @NonNull int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == CONTACTS_PERMISSION_REQUEST
        && results.length > 0
        && results[0] == PackageManager.PERMISSION_GRANTED) discoverContacts();
    else newChatView.showStatus("Contacts permission is required to discover chats.");
  }

  private void discoverContacts() {
    newChatView.showStatus("Loading contacts...");
    discoveryExecutor.execute(
        () -> {
          List<String> contacts = readPhoneContacts();
          runOnUiThread(
              () -> {
                if (contacts.isEmpty()) {
                  newChatView.showStatus("No contacts found.");
                  return;
                }
                found.clear();
                invites.clear();
                rendered.clear();
                newChatView.showStatus("Discovering contacts...");
              });
          if (!contacts.isEmpty()) discoverNextBatch(contacts, 0);
        });
  }

  private void discoverNextBatch(List<String> contacts, int start) {
    if (isClosing()) return;
    if (start >= contacts.size()) {
      runOnUiThread(this::render);
      return;
    }
    int end = Math.min(start + DISCOVER_BATCH_SIZE, contacts.size());
    List<String> batch = new ArrayList<>(contacts.subList(start, end));
    discoverBatch(batch)
        .whenCompleteAsync(
            (response, error) -> {
              if (isClosing()) return;
              if (error == null) {
                BatchResult result = parseBatch(response);
                runOnUiThread(() -> append(result));
                prefetchProfilePhotos(result.foundContacts);
              } else {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                this,
                                error.getMessage() == null
                                    ? "Contact discovery failed."
                                    : error.getMessage(),
                                Toast.LENGTH_SHORT)
                            .show());
              }
              discoverNextBatch(contacts, end);
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
                if (o instanceof JsonObject) future.complete((JsonObject) o);
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
    String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
    try (Cursor cursor =
        getContentResolver()
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC")) {
      if (cursor == null) return new ArrayList<>();
      int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
      while (cursor.moveToNext()) {
        String number = normalize(cursor.getString(index));
        if (!number.isEmpty() && !number.equals(own)) values.add(number);
      }
    }
    return new ArrayList<>(values);
  }

  private BatchResult parseBatch(JsonObject response) {
    BatchResult result = new BatchResult();
    JsonArray contacts = response.getAsJsonArray("contacts");
    if (contacts == null) return result;
    String own = normalize(currentPhone());
    for (JsonElement element : contacts) {
      if (element == null || !element.isJsonObject()) continue;
      JsonObject contact = element.getAsJsonObject();
      String phone = normalize(string(contact, "phoneNumber"));
      if (phone.isEmpty() || phone.equals(own)) continue;
      if (bool(contact, "found")) result.foundContacts.add(contact);
      else result.invitePhones.add(phone);
    }
    return result;
  }

  private void append(BatchResult result) {
    if (isClosing()) return;
    for (JsonObject contact : result.foundContacts) {
      String phone = normalize(string(contact, "phoneNumber"));
      if (rendered.add(phone)) found.add(contact);
    }
    for (String phone : result.invitePhones) {
      if (rendered.add(phone)) invites.add(phone);
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
          || photoExecutor.isShutdown()) continue;
      photoExecutor.execute(
          () -> {
            try {
              String path = ChatProfilePhotoStore.downloadAndStore(appContext, phone, url);
              if (path != null && !isClosing()) runOnUiThread(this::render);
            } finally {
              photoDownloads.remove(phone);
            }
          });
    }
  }

  private void render() {
    if (isClosing() || newChatView == null) return;
    List<NewChatView.Item> items = new ArrayList<>();
    for (JsonObject contact : found)
      items.add(
          NewChatView.Item.found(
              normalize(string(contact, "phoneNumber")),
              string(contact, "chatId"),
              string(contact, "profilePhotoUrl")));
    if (!invites.isEmpty()) {
      items.add(NewChatView.Item.divider("Invite"));
      for (String phone : invites) items.add(NewChatView.Item.invite(phone));
    }
    newChatView.submitItems(items);
  }

  @Override
  public void onBack() {
    finish();
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
    intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, item.phoneNumber);
    intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId);
    intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, item.profilePhotoUrl);
    intent.putExtra(
        ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH,
        ChatProfilePhotoStore.getLocalPath(this, item.phoneNumber));
    startActivity(intent);
    if (isForwarding()) finish();
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

  private String currentPhone() {
    String uid = LoginStateManager.getInstance().getUID(this);
    return normalize(uid);
  }

  private static String normalize(String v) {
    if (v == null) return "";
    String n = v.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
    if (n.startsWith("<plus>")) n = n.substring(6);
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
    discoveryExecutor.shutdownNow();
    photoExecutor.shutdownNow();
    photoDownloads.clear();
    if (newChatView != null) newChatView.release();
    newChatView = null;
    super.onDestroy();
  }

  private static final class BatchResult {
    final List<JsonObject> foundContacts = new ArrayList<>();
    final List<String> invitePhones = new ArrayList<>();
  }
}
