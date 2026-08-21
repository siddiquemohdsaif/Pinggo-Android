package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void upsert(TransferEntity transfer);
    @Query("SELECT * FROM transfers WHERE transferId = :id LIMIT 1") TransferEntity find(String id);
    @Query("SELECT * FROM transfers WHERE clientMessageId = :clientId LIMIT 1") TransferEntity findByClientMessageId(String clientId);
    @Query("SELECT * FROM transfers WHERE attachmentId = :attachmentId LIMIT 1") TransferEntity findByAttachmentId(String attachmentId);
    @Query("SELECT * FROM transfers WHERE chatId = :chatId") LiveData<List<TransferEntity>> observeChat(String chatId);
    @Query("SELECT * FROM transfers WHERE direction='upload' AND status='completed'") List<TransferEntity> completedUploadsAwaitingAck();
    @Query("UPDATE transfers SET uploadId=:uploadId, fileHash=:hash, stagedPath=:path, totalSize=:size, status=:status, updatedTime=:time WHERE transferId=:id")
    void staged(String id, String uploadId, String hash, String path, long size, String status, long time);
    @Query("UPDATE transfers SET transferredBytes=:bytes, status=:status, updatedTime=:time WHERE transferId=:id")
    void progress(String id, long bytes, String status, long time);
    @Query("UPDATE transfers SET attachmentId=:attachmentId, remoteUrl=:url, localUri=:localUri, transferredBytes=totalSize, status='completed', error=NULL, updatedTime=:time WHERE transferId=:id")
    void completed(String id, String attachmentId, String url, String localUri, long time);
    @Query("UPDATE transfers SET status=:status, error=:error, updatedTime=:time WHERE transferId=:id")
    void failed(String id, String status, String error, long time);
    @Query("UPDATE transfers SET status='message_sent', updatedTime=:time WHERE clientMessageId=:clientMessageId AND direction='upload'")
    void messageSent(String clientMessageId, long time);
}
