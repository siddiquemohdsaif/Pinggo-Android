package com.w3n.pinggo.modals;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CallLogTest {
    @Test public void iconDirectionMatchesChatCallRules() {
        assertEquals(CallLog.ICON_OUTGOING, call(true, false).getIconDirection());
        assertEquals(CallLog.ICON_INCOMING, call(false, false).getIconDirection());
        assertEquals(CallLog.ICON_OUTGOING, call(true, true).getIconDirection());
        assertEquals(CallLog.ICON_MISSED, call(false, true).getIconDirection());
    }

    private static CallLog call(boolean outgoing, boolean notConnected) {
        return new CallLog("chat", "9199", "Contact", "now", "today", "0 sec",
                false, outgoing, notConnected);
    }
}
