package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ChatEntity> chats);

    @Query("UPDATE chats SET lastMessage = :preview, lastMessageTime = :sentTime, "
            + "lastMessageSenderId = :senderId, lastMessageDeliveredTime = :deliveredTime, "
            + "lastMessageReadTime = :readTime, "
            + "lastMessageType = :messageType, lastMessageAttachmentName = :attachmentName, "
            + "updatedAt = :updatedAt WHERE chatId = :chatId "
            + "AND lastMessageTime <= :sentTime")
    int updateLastMessage(String chatId, String preview, long sentTime, String senderId,
                          Long deliveredTime, Long readTime, String messageType,
                          String attachmentName, long updatedAt);

    @Query("UPDATE chats SET lastMessageDeliveredTime = :deliveredTime, "
            + "lastMessageReadTime = :readTime WHERE chatId = :chatId "
            + "AND lastMessageTime = :sentTime")
    void updateLastMessageReceipt(String chatId, long sentTime, Long deliveredTime, Long readTime);

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    ChatEntity findByChatId(String chatId);

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    void deleteByChatId(String chatId);

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    void incrementUnreadCount(String chatId);

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    void clearUnreadCount(String chatId);

    @Query("UPDATE chats SET unreadCount = :unreadCount WHERE chatId = :chatId")
    void setUnreadCount(String chatId, int unreadCount);

    @Query("UPDATE chats SET localProfilePhotoPath = :localPath, updatedAt = :updatedAt "
            + "WHERE chatId = :chatId AND profilePhotoUrl = :profilePhotoUrl")
    int updateLocalProfilePhotoPath(String chatId, String profilePhotoUrl,
                                    String localPath, long updatedAt);

    @Query("SELECT * FROM chats ORDER BY pinned DESC, lastMessageTime DESC, updatedAt DESC")
    LiveData<List<ChatEntity>> observeChats();
}
