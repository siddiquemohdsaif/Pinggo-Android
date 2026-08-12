package com.w3n.wavestream.utils;

import com.w3n.wavestream.modals.CallLog;

public final class CallLogsData {
    private static final String[] CALL_TIMES = {
            "10 mins ago", "18 mins ago", "35 mins ago", "52 mins ago", "1 hour ago",
            "2 hours ago", "4 hours ago", "7 hours ago", "10 hours ago", "14 hours ago",
            "18 hours ago", "22 hours ago", "1 day ago", "2 days ago", "3 days ago",
            "27 Jul 2026", "26 Jul 2026", "25 Jul 2026", "24 Jul 2026", "22 Jul 2026",
            "20 Jul 2026", "18 Jul 2026", "17 Jul 2026", "14 Jul 2026", "11 Jul 2026",
            "07 Jul 2026", "03 Jul 2026", "01 Jul 2026", "18 Jun 2026", "31 May 2026",
            "30 Apr 2026", "31 Mar 2026", "28 Feb 2026", "31 Jan 2026", "31 Dec 2025",
            "30 Nov 2025", "31 Oct 2025", "30 Sep 2025", "31 Aug 2025", "31 Jul 2025",
            "30 Jun 2025", "31 May 2025", "30 Apr 2025", "31 Mar 2025", "28 Feb 2025",
            "31 Jan 2025", "31 Dec 2024", "30 Nov 2024", "31 Oct 2024", "31 Jul 2024"
    };

    private static final String[] DURATIONS = {
            "2 min 14 sec", "48 sec", "5 min 02 sec", "1 min 30 sec", "9 min 10 sec",
            "22 sec", "3 min 41 sec", "6 min 18 sec", "1 min 05 sec", "12 min 44 sec"
    };

    private static final String[] FULL_CALL_DATE_TIMES = {
            "31/07/2026 12:20", "31/07/2026 12:12", "31/07/2026 11:55", "31/07/2026 11:38", "31/07/2026 11:30",
            "31/07/2026 10:30", "31/07/2026 08:30", "31/07/2026 05:30", "31/07/2026 02:30", "30/07/2026 22:30",
            "30/07/2026 18:30", "30/07/2026 14:30", "30/07/2026 12:30", "29/07/2026 12:30", "28/07/2026 12:30",
            "27/07/2026 10:08", "26/07/2026 09:42", "25/07/2026 21:16", "24/07/2026 18:05", "22/07/2026 14:55",
            "20/07/2026 08:22", "18/07/2026 23:40", "17/07/2026 17:31", "14/07/2026 13:08", "11/07/2026 20:15",
            "07/07/2026 11:44", "03/07/2026 16:29", "01/07/2026 10:08", "18/06/2026 19:52", "31/05/2026 07:36",
            "30/04/2026 22:18", "31/03/2026 12:04", "28/02/2026 15:50", "31/01/2026 09:19", "31/12/2025 23:11",
            "30/11/2025 18:46", "31/10/2025 13:27", "30/09/2025 08:58", "31/08/2025 20:34", "31/07/2025 10:08",
            "30/06/2025 17:13", "31/05/2025 12:49", "30/04/2025 21:07", "31/03/2025 06:26", "28/02/2025 15:39",
            "31/01/2025 11:14", "31/12/2024 22:44", "30/11/2024 16:23", "31/10/2024 09:51", "31/07/2024 10:08"
    };

    private CallLogsData() {
    }

    public static CallLog[] getCallLogs() {
        String[] contactNames = ContactsData.getContactNames();
        CallLog[] callLogs = new CallLog[contactNames.length];
        for (int i = 0; i < contactNames.length; i++) {
            callLogs[i] = new CallLog(
                    contactNames[i],
                    CALL_TIMES[i],
                    FULL_CALL_DATE_TIMES[i],
                    DURATIONS[i % DURATIONS.length],
                    i % 2 != 0
            );
        }
        return callLogs;
    }
}
