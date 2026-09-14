package com.w3n.pinggo.modals;

public class CallLog {
    public static final int ICON_INCOMING = 0;
    public static final int ICON_OUTGOING = 1;
    public static final int ICON_MISSED = 2;
    private final String chatId;
    private final String callId;
    private final String messageId;
    private final String phoneNumber;
    private final String contactName;
    private final String calledTime;
    private final String fullCalledDateTime;
    private final String duration;
    private final boolean videoCall;
    private final boolean outgoing;
    private final boolean missed;
    private final boolean conference;
    private final String localProfilePhotoPath;

    public CallLog(String contactName, String calledTime, String fullCalledDateTime, String duration, boolean videoCall) {
        this("", "", "", contactName, calledTime, fullCalledDateTime, duration,
                videoCall, false, false);
    }

    public CallLog(String chatId, String phoneNumber, String contactName, String calledTime,
                   String fullCalledDateTime, String duration, boolean videoCall) {
        this(chatId, "", phoneNumber, contactName, calledTime, fullCalledDateTime, duration,
                videoCall, false, false);
    }

    public CallLog(String chatId, String phoneNumber, String contactName, String calledTime,
                   String fullCalledDateTime, String duration, boolean videoCall,
                   boolean outgoing, boolean missed) {
        this(chatId, "", phoneNumber, contactName, calledTime, fullCalledDateTime, duration,
                videoCall, outgoing, missed);
    }

    public CallLog(String chatId, String messageId, String phoneNumber, String contactName,
                   String calledTime,
                   String fullCalledDateTime, String duration, boolean videoCall,
                   boolean outgoing, boolean missed) {
        this(chatId, messageId, phoneNumber, contactName, calledTime, fullCalledDateTime,
                duration, videoCall, outgoing, missed, false, null);
    }

    public CallLog(String chatId, String messageId, String phoneNumber, String contactName,
                   String calledTime, String fullCalledDateTime, String duration,
                   boolean videoCall, boolean outgoing, boolean missed, boolean conference) {
        this(chatId, messageId, phoneNumber, contactName, calledTime, fullCalledDateTime,
                duration, videoCall, outgoing, missed, conference, null);
    }

    public CallLog(String chatId, String messageId, String phoneNumber, String contactName,
                   String calledTime, String fullCalledDateTime, String duration,
                   boolean videoCall, boolean outgoing, boolean missed, boolean conference,
                   String localProfilePhotoPath) {
        this(chatId, "", messageId, phoneNumber, contactName, calledTime,
                fullCalledDateTime, duration, videoCall, outgoing, missed, conference,
                localProfilePhotoPath);
    }

    public CallLog(String chatId, String callId, String messageId, String phoneNumber,
                   String contactName, String calledTime, String fullCalledDateTime,
                   String duration, boolean videoCall, boolean outgoing, boolean missed,
                   boolean conference, String localProfilePhotoPath) {
        this.chatId = chatId == null ? "" : chatId;
        this.callId = callId == null ? "" : callId;
        this.messageId = messageId == null ? "" : messageId;
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber;
        this.contactName = contactName;
        this.calledTime = calledTime;
        this.fullCalledDateTime = fullCalledDateTime;
        this.duration = duration;
        this.videoCall = videoCall;
        this.outgoing = outgoing;
        this.missed = missed;
        this.conference = conference;
        this.localProfilePhotoPath = localProfilePhotoPath;
    }

    public String getChatId() { return chatId; }
    public String getCallId() { return callId; }
    public String getMessageId() { return messageId; }
    public String getPhoneNumber() { return phoneNumber; }

    public String getContactName() {
        return contactName;
    }

    public String getCalledTime() {
        return calledTime;
    }

    public String getFullCalledDateTime() {
        return fullCalledDateTime;
    }

    public String getDuration() {
        return duration;
    }

    public boolean isVideoCall() {
        return videoCall;
    }

    public boolean isOutgoing() { return outgoing; }
    public boolean isMissed() { return missed; }
    public boolean isConference() { return conference; }
    public String getLocalProfilePhotoPath() { return localProfilePhotoPath; }
    public boolean isGroupCall() { return chatId.startsWith("grp_"); }

    /** Mirrors the call-icon rules used by ChatsView and ChatMessageAdapter. */
    public int getIconDirection() {
        if (missed && !outgoing) return ICON_MISSED;
        return outgoing ? ICON_OUTGOING : ICON_INCOMING;
    }
}
