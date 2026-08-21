package com.w3n.pinggo.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transfers", indices = {@Index("clientMessageId"), @Index("attachmentId")})
public class TransferEntity {
    @PrimaryKey @NonNull public String transferId;
    public String clientMessageId;
    public String attachmentId;
    public String direction;
    public String chatId;
    public String senderId;
    public String receiverId;
    public String kind;
    public String caption;
    public String repliedMessageId;
    public String fileName;
    public String mimeType;
    public String sourceUri;
    public String localUri;
    public String remoteUrl;
    public String stagedPath;
    public String uploadId;
    public String fileHash;
    public long totalSize;
    public long transferredBytes;
    public String status;
    public String error;
    public long updatedTime;

    public TransferEntity(@NonNull String transferId) { this.transferId = transferId; }
}
