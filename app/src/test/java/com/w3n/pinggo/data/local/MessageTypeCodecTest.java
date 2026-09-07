package com.w3n.pinggo.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MessageTypeCodecTest {
    @Test public void allMessageTypesRoundTrip() {
        String[] types = { "text", "image", "video", "audio", "file", "location",
                "voice_call", "video_call", "report", "chat_report",
                "chat_block", "chat_unblock" };
        for (int code = 0; code < types.length; code++) {
            assertEquals(code, MessageTypeCodec.encode(types[code]));
            assertEquals(types[code], MessageTypeCodec.decode(code));
        }
    }

    @Test public void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MessageTypeCodec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> MessageTypeCodec.encode("voice"));
        assertThrows(IllegalArgumentException.class, () -> MessageTypeCodec.encode("unknown"));
        assertThrows(IllegalArgumentException.class, () -> MessageTypeCodec.decode(-1));
        assertThrows(IllegalArgumentException.class, () -> MessageTypeCodec.decode(12));
    }
}
