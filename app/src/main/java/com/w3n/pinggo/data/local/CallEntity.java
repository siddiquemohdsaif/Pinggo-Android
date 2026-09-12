package com.w3n.pinggo.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "calls", primaryKeys = {"ownerId", "callId"}, indices = {
        @Index(value = {"ownerId", "endedAt"}),
        @Index(value = {"ownerId", "chatId", "endedAt"})
})
public class CallEntity {
    @NonNull public String ownerId;
    @NonNull public String callId;
    public String messageId;
    public String chatId;
    public String callerId;
    public String receiverId;
    public String mediaType;
    public String status;
    public String terminationReason;
    public long createdAt;
    public Long ringingAt;
    public Long connectedAt;
    public long endedAt;
    public long durationSeconds;
    public boolean conference;
    public String participantIdsJson;

    public CallEntity(@NonNull String ownerId, @NonNull String callId, String messageId,
                      String chatId, String callerId, String receiverId, String mediaType,
                      String status, String terminationReason, long createdAt, Long ringingAt,
                      Long connectedAt, long endedAt, long durationSeconds, boolean conference,
                      String participantIdsJson) {
        this.ownerId = ownerId;
        this.callId = callId;
        this.messageId = messageId;
        this.chatId = chatId;
        this.callerId = callerId;
        this.receiverId = receiverId;
        this.mediaType = mediaType;
        this.status = status;
        this.terminationReason = terminationReason;
        this.createdAt = createdAt;
        this.ringingAt = ringingAt;
        this.connectedAt = connectedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.conference = conference;
        this.participantIdsJson = participantIdsJson;
    }
}
