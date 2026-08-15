package com.w3n.pinggo.Util;

import android.os.Handler;
import android.os.Looper;

public final class MainRunnerThread {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private MainRunnerThread() {
    }

    public static void run(Runnable runnable) {
        MAIN_HANDLER.post(runnable);
    }

    public static void runDelayed(Runnable runnable, long delayMillis) {
        MAIN_HANDLER.postDelayed(runnable, delayMillis);
    }
}
