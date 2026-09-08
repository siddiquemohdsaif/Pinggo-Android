package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MessageEntity> messages);

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND invisible = 0 AND (pinned = 1 OR "
            + "messageId IN (SELECT messageId FROM messages WHERE chatId = :chatId "
            + "AND invisible = 0 ORDER BY sentTime DESC, messageId DESC LIMIT :limit)) "
            + "ORDER BY sentTime ASC, messageId ASC")
    LiveData<List<MessageEntity>> observeLatestMessages(String chatId, int limit);

    @Query("SELECT * FROM messages WHERE clientMessageId = :clientMessageId LIMIT 1")
    MessageEntity findByClientMessageId(String clientMessageId);

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    MessageEntity findByMessageId(String messageId);

    @Query("UPDATE messages SET groupReceiptsJson = :receipts WHERE messageId = :messageId")
    void updateGroupReceipts(String messageId, String receipts);

    @Query("SELECT * FROM messages WHERE messageId IN (:messageIds)")
    List<MessageEntity> findByMessageIds(List<String> messageIds);

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND invisible = 0 "
            + "AND (messageTypeCode IN (1, 2, 4) OR text LIKE '%http://%' OR text LIKE '%https://%') "
            + "AND sentTime < :before ORDER BY sentTime DESC, messageId DESC LIMIT :limit")
    List<MessageEntity> findStoredMediaPage(String chatId, long before, int limit);

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND "
            + "(messageId IN (:messageIds) OR clientMessageId IN (:messageIds))")
    List<MessageEntity> findReplyTargets(String chatId, List<String> messageIds);

    @Query("SELECT * FROM messages WHERE receiverId = :receiverId "
            + "AND invisible = 0 AND deliveredTime IS NULL AND readTime IS NULL")
    List<MessageEntity> findUndeliveredIncoming(String receiverId);

    @Query("SELECT * FROM messages WHERE senderId = :senderId AND status = 'sending' "
            + "AND clientMessageId IS NOT NULL AND clientMessageId != ''")
    List<MessageEntity> findPendingOutgoing(String senderId);

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE messageId = :messageId)")
    boolean existsByMessageId(String messageId);

    @Query("UPDATE messages SET messageId = :serverMessageId, status = :status, sentTime = :sentTime WHERE clientMessageId = :clientMessageId")
    void applyAck(String clientMessageId, String serverMessageId, String status, long sentTime);

    @Query("DELETE FROM messages WHERE clientMessageId = :clientMessageId AND messageId != :serverMessageId")
    void deleteOptimisticAckDuplicate(String clientMessageId, String serverMessageId);

    @Query("UPDATE messages SET attachmentLocalUri = COALESCE(attachmentLocalUri, "
            + "(SELECT attachmentLocalUri FROM messages WHERE clientMessageId = :clientMessageId "
            + "AND messageId != :serverMessageId AND attachmentLocalUri IS NOT NULL LIMIT 1)) "
            + "WHERE messageId = :serverMessageId")
    void preserveAckAttachmentUri(String clientMessageId, String serverMessageId);

    @Query("UPDATE messages SET clientMessageId = CASE WHEN clientMessageId IS NULL OR clientMessageId = '' "
            + "THEN :clientMessageId ELSE clientMessageId END, status = :status, sentTime = :sentTime "
            + "WHERE messageId = :serverMessageId")
    void updateAcknowledgedServerMessage(String clientMessageId, String serverMessageId,
                                         String status, long sentTime);

    /** Merges an optimistic row with a server row that may have arrived first over the socket. */
    @Transaction
    default void reconcileAck(String clientMessageId, String serverMessageId,
                              String status, long sentTime) {
        if (existsByMessageId(serverMessageId)) {
            preserveAckAttachmentUri(clientMessageId, serverMessageId);
            deleteOptimisticAckDuplicate(clientMessageId, serverMessageId);
            updateAcknowledgedServerMessage(clientMessageId, serverMessageId, status, sentTime);
        } else {
            applyAck(clientMessageId, serverMessageId, status, sentTime);
        }
    }

    @Query("UPDATE messages SET status = :status WHERE clientMessageId = :clientMessageId")
    void updateStatusByClientMessageId(String clientMessageId, String status);

    @Query("UPDATE messages SET attachmentId = :attachmentId, attachmentKind = :kind, attachmentName = :name, attachmentMimeType = :mimeType, attachmentUrl = :url, attachmentSize = :size WHERE clientMessageId = :clientMessageId")
    void applyAttachmentUpload(String clientMessageId, String attachmentId, String kind,
                               String name, String mimeType, String url, long size);

    @Query("UPDATE messages SET attachmentLocalUri=:localUri WHERE attachmentId=:attachmentId")
    void updateAttachmentLocalUri(String attachmentId, String localUri);

    @Query("UPDATE messages SET text = :text WHERE messageId = :messageId")
    void updateText(String messageId, String text);

    @Query("UPDATE messages SET deletedText = CASE WHEN deletedText IS NULL THEN text ELSE deletedText END, "
            + "text = :replacement WHERE messageId = :messageId AND deletedText IS NULL")
    void markDeleted(String messageId, String replacement);

    @Query("UPDATE messages SET invisible = 1 WHERE messageId = :messageId")
    void markInvisible(String messageId);

    @Query("UPDATE messages SET invisible = 1 WHERE chatId = :chatId")
    void markChatInvisible(String chatId);

    @Query("UPDATE messages SET pinned = :pinned, pinnedAt = :pinnedAt, pinnedBy = :pinnedBy "
            + "WHERE messageId = :messageId")
    void updatePinState(String messageId, boolean pinned, Long pinnedAt, String pinnedBy);

    @Query("UPDATE messages SET status = :status, readTime = :readTime WHERE messageId IN (:messageIds)")
    void markSeen(List<String> messageIds, String status, long readTime);

    @Query("UPDATE messages SET status = :status, deliveredTime = :deliveredTime WHERE messageId IN (:messageIds) AND readTime IS NULL")
    void markDelivered(List<String> messageIds, String status, long deliveredTime);

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    void deleteByMessageId(String messageId);

    @Query("DELETE FROM messages WHERE messageId IN (:messageIds)")
    void deleteByMessageIds(List<String> messageIds);

    @Query("SELECT MAX(sentTime) FROM messages")
    Long getLastSyncTime();
}
