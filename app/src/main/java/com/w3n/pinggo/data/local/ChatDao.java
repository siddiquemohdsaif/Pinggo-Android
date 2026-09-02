package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Dao
public interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ChatEntity> chats);

    @Query("SELECT * FROM chats")
    List<ChatEntity> getAllChats();

    @Transaction
    default void mergeServerPage(List<ChatEntity> serverChats,
                                 Set<String> chatsWithServerUnreadCount,
                                 String activeChatId) {
        if (serverChats == null || serverChats.isEmpty()) return;
        Map<String, ChatEntity> existingById = new HashMap<>();
        for (ChatEntity existing : getAllChats()) {
            existingById.put(existing.chatId, existing);
        }
        for (ChatEntity chat : serverChats) {
            ChatEntity existing = existingById.get(chat.chatId);
            if (chat.chatId.equals(activeChatId)) {
                chat.unreadCount = 0;
            } else if (existing != null
                    && (chatsWithServerUnreadCount == null
                    || !chatsWithServerUnreadCount.contains(chat.chatId))) {
                chat.unreadCount = existing.unreadCount;
            }
        }
        upsertAll(serverChats);
    }

    @Query("UPDATE chats SET lastMessage = :preview, lastMessageId = :messageId, "
            + "lastMessageTime = :sentTime, "
            + "lastMessageSenderId = :senderId, lastMessageDeliveredTime = :deliveredTime, "
            + "lastMessageReadTime = :readTime, lastMessageStatus = :status, "
            + "lastMessageType = :messageType, lastMessageAttachmentName = :attachmentName, "
            + "updatedAt = :updatedAt WHERE chatId = :chatId "
            + "AND lastMessageTime <= :sentTime")
    int updateLastMessage(String chatId, String messageId, String preview, long sentTime,
                          String senderId, Long deliveredTime, Long readTime, String status,
                          String messageType,
                          String attachmentName, long updatedAt);

    @Query("UPDATE chats SET lastMessageDeliveredTime = :deliveredTime, "
            + "lastMessageReadTime = :readTime, lastMessageStatus = :status "
            + "WHERE chatId = :chatId AND (lastMessageId = :messageId OR "
            + "((lastMessageId IS NULL OR lastMessageId = '') AND lastMessageTime = :sentTime))")
    void updateLastMessageReceipt(String chatId, String messageId, long sentTime,
                                  Long deliveredTime, Long readTime, String status);

    @Query("UPDATE chats SET lastMessageId = :serverMessageId, lastMessageTime = :sentTime, "
            + "lastMessageStatus = :status, updatedAt = :updatedAt "
            + "WHERE chatId = :chatId AND lastMessageId = :clientMessageId")
    void applyLastMessageAck(String chatId, String clientMessageId, String serverMessageId,
                             long sentTime, String status, long updatedAt);

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    ChatEntity findByChatId(String chatId);

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    LiveData<ChatEntity> observeChat(String chatId);

    @Query("UPDATE chats SET notificationMuted = :value, updatedAt = :updatedAt WHERE chatId = :chatId")
    void updateNotificationMuted(String chatId, long value, long updatedAt);

    @Query("UPDATE chats SET lastMessage = NULL, lastMessageId = NULL, "
            + "lastMessageSenderId = NULL, lastMessageDeliveredTime = NULL, "
            + "lastMessageReadTime = NULL, lastMessageStatus = NULL, lastMessageType = NULL, "
            + "lastMessageAttachmentName = NULL, unreadCount = 0, updatedAt = :updatedAt "
            + "WHERE chatId = :chatId")
    void clearLastMessage(String chatId, long updatedAt);

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    void deleteByChatId(String chatId);

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    void incrementUnreadCount(String chatId);

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    void clearUnreadCount(String chatId);

    @Query("SELECT COUNT(*) FROM chats WHERE unreadCount > 0")
    int countUnreadChats();

    @Query("UPDATE chats SET unreadCount = :unreadCount WHERE chatId = :chatId")
    void setUnreadCount(String chatId, int unreadCount);

    @Query("UPDATE chats SET localProfilePhotoPath = :localPath, updatedAt = :updatedAt "
            + "WHERE chatId = :chatId AND profilePhotoUrl = :profilePhotoUrl")
    int updateLocalProfilePhotoPath(String chatId, String profilePhotoUrl,
                                    String localPath, long updatedAt);

    @Transaction
    default void updateLocalProfilePhotoPaths(List<ChatEntity> chats, long updatedAt) {
        if (chats == null) return;
        for (ChatEntity chat : chats) {
            if (chat != null && chat.localProfilePhotoPath != null) {
                updateLocalProfilePhotoPath(chat.chatId, chat.profilePhotoUrl,
                        chat.localProfilePhotoPath, updatedAt);
            }
        }
    }

    @Query("SELECT * FROM chats ORDER BY pinned DESC, lastMessageTime DESC, updatedAt DESC")
    LiveData<List<ChatEntity>> observeChats();
}
