package com.w3n.pinggo.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        indices = {
                @Index("chatId"),
                @Index("clientMessageId"),
                @Index("sentTime")
        }
)
public class MessageEntity {
    @PrimaryKey
    @NonNull
    public String messageId;
    public String clientMessageId;
    public String chatId;
    public String senderId;
    public String receiverId;
    public String text;
    public String repliedMessageId;
    public long sentTime;
    public Long deliveredTime;
    public Long readTime;
    public String status;
    public String messageType;
    public String attachmentId;
    public String attachmentKind;
    public String attachmentName;
    public String attachmentMimeType;
    public String attachmentUrl;
    public String attachmentLocalUri;
    public Long attachmentSize;
    public String attachmentSha256;
    public Double latitude;
    public Double longitude;
    public Float locationAccuracy;
    public boolean pinned;
    public Long pinnedAt;
    public String forwardedFrom;
    public String deletedText;
    public boolean invisible;

    public MessageEntity(
            @NonNull String messageId,
            String clientMessageId,
            String chatId,
            String senderId,
            String receiverId,
            String text,
            String repliedMessageId,
            long sentTime,
            Long deliveredTime,
            Long readTime,
            String status,
            String messageType,
            String attachmentId,
            String attachmentKind,
            String attachmentName,
            String attachmentMimeType,
            String attachmentUrl,
            String attachmentLocalUri,
            Long attachmentSize,
            Double latitude,
            Double longitude,
            Float locationAccuracy
    ) {
        this.messageId = messageId;
        this.clientMessageId = clientMessageId;
        this.chatId = chatId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.repliedMessageId = repliedMessageId;
        this.sentTime = sentTime;
        this.deliveredTime = deliveredTime;
        this.readTime = readTime;
        this.status = status;
        this.messageType = messageType;
        this.attachmentId = attachmentId;
        this.attachmentKind = attachmentKind;
        this.attachmentName = attachmentName;
        this.attachmentMimeType = attachmentMimeType;
        this.attachmentUrl = attachmentUrl;
        this.attachmentLocalUri = attachmentLocalUri;
        this.attachmentSize = attachmentSize;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
    }
}
