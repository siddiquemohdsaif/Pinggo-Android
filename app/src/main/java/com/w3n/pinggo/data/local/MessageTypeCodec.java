package com.w3n.pinggo.data.local;

/** Stable compact values persisted in Room. Keep these numbers backward compatible. */
public final class MessageTypeCodec {
    // Shortcuts: 0 text, 1 image, 2 video, 3 audio, 4 file, 5 location,
    // 6 voice_call, 7 video_call, 8 report, 9 chat_report, 10 chat_block, 11 chat_unblock.
    //
    // Compact message-field shortcuts (documentation only; UI model keeps descriptive names):
    // id=messageId, cid=clientMessageId, c=chatId, s=senderId, r=receiverId,
    // t=messageTypeCode, txt=text/caption, rt=repliedMessageId, st=sentTime,
    // dt=deliveredTime, rdt=readTime, sts=status, aid=attachmentId.
    // Attachment: ak=kind, an=name, am=mimeType, az=size, au=remoteUrl,
    // al=localUri (Android only), aw=width, ah=height, ao=orientation,
    // ad=durationMs, ash=sha256.
    // Location: lat=latitude, lng=longitude, acc=accuracy.
    // Message state: p=pinned, pat=pinnedAt, pby=pinnedBy, ff=forwardedFrom,
    // del=deletedText, inv=invisible.
    // Calls: call=callId, dur=callDurationSeconds, cr=callCreatedAt,
    // rng=callRingingAt, con=callConnectedAt, end=callEndedAt,
    // term=callTerminationReason.
    public static final int TEXT = 0, IMAGE = 1, VIDEO = 2, AUDIO = 3, FILE = 4,
            LOCATION = 5, VOICE_CALL = 6, VIDEO_CALL = 7, REPORT = 8,
            CHAT_REPORT = 9, CHAT_BLOCK = 10, CHAT_UNBLOCK = 11;

    private MessageTypeCodec() {}

    public static int encode(String value) {
        if (value == null) throw new IllegalArgumentException("messageType is required");
        switch (value.trim().toLowerCase(java.util.Locale.US)) {
            case "text": return TEXT;
            case "image": return IMAGE;
            case "video": return VIDEO;
            case "audio": return AUDIO;
            case "file": return FILE;
            case "location": return LOCATION;
            case "voice_call": return VOICE_CALL;
            case "video_call": return VIDEO_CALL;
            case "report": return REPORT;
            case "chat_report": return CHAT_REPORT;
            case "chat_block": return CHAT_BLOCK;
            case "chat_unblock": return CHAT_UNBLOCK;
            default: throw new IllegalArgumentException("Unknown messageType: " + value);
        }
    }

    public static String decode(int value) {
        switch (value) {
            case IMAGE: return "image";
            case VIDEO: return "video";
            case AUDIO: return "audio";
            case FILE: return "file";
            case LOCATION: return "location";
            case VOICE_CALL: return "voice_call";
            case VIDEO_CALL: return "video_call";
            case REPORT: return "report";
            case CHAT_REPORT: return "chat_report";
            case CHAT_BLOCK: return "chat_block";
            case CHAT_UNBLOCK: return "chat_unblock";
            case TEXT: return "text";
            default: throw new IllegalArgumentException("Unknown messageTypeCode: " + value);
        }
    }
}
