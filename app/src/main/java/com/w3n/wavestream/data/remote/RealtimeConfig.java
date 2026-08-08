package com.w3n.wavestream.data.remote;

import com.w3n.wavestream.AppContextProvider;

public final class RealtimeConfig {
    private static final String DEV_SERVER_HOST = "function.cloudsw3.com";
    private static final String RELEASE_SERVER_HOST = "function.cloudsw3.com";
    private static final int SERVER_PORT = 4100;

    public static final String REST_BASE_URL = AppContextProvider.isDevelopment
            ? "https://function.cloudsw3.com/pinggo-app-api_dev/"
            : "https://function.cloudsw3.com/pinggo-app-api/";

    // WebSocket must point to the Node server that serves createWebSocketServer(server) on /ws.
    // If your reverse proxy exposes another host/port, change only these constants.
    public static final String WS_URL = AppContextProvider.isDevelopment
            ? "ws://" + DEV_SERVER_HOST + ":" + SERVER_PORT + "/ws"
            : "ws://" + RELEASE_SERVER_HOST + ":" + SERVER_PORT + "/ws";

    private RealtimeConfig() {
    }
}
