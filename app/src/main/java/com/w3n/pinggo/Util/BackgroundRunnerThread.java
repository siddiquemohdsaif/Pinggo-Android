package com.w3n.pinggo.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundRunnerThread {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private BackgroundRunnerThread() {
    }

    public static void run(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }
}
