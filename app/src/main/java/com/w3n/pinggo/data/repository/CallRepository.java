package com.w3n.pinggo.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.data.local.CallDao;
import com.w3n.pinggo.data.local.CallEntity;
import com.w3n.pinggo.data.local.PingGoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Room cache for the home call list and per-chat call history. */
public final class CallRepository {
    private static volatile CallRepository instance;
    private final CallDao dao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private CallRepository(Context context) {
        dao = PingGoDatabase.getInstance(context.getApplicationContext()).callDao();
    }

    public static CallRepository getInstance(Context context) {
        if (instance == null) synchronized (CallRepository.class) {
            if (instance == null) instance = new CallRepository(context);
        }
        return instance;
    }

    public LiveData<List<CallEntity>> observeLatestCalls(String ownerId) {
        return dao.observeLatestCalls(normalize(ownerId));
    }

    public LiveData<List<CallEntity>> observeCallHistory(String ownerId, String chatId, int limit) {
        return dao.observeCallHistory(normalize(ownerId), chatId, Math.max(1, limit));
    }

    public void cachePage(String ownerId, JsonArray values) {
        String owner = normalize(ownerId);
        List<CallEntity> calls = new ArrayList<>();
        if (values != null) for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject call = element.getAsJsonObject();
            long createdAt = number(call, "createdAt");
            long endedAt = number(call, "endedAt");
            String callId = string(call, "callId");
            if (callId.isEmpty()) callId = string(call, "messageId");
            if (callId.isEmpty()) callId = string(call, "chatId") + ':'
                    + (endedAt > 0 ? endedAt : createdAt);
            calls.add(new CallEntity(owner, callId, string(call, "messageId"),
                    string(call, "chatId"), normalize(string(call, "callerId")),
                    normalize(string(call, "receiverId")), string(call, "mediaType"),
                    string(call, "status"), string(call, "terminationReason"), createdAt,
                    nullableNumber(call, "ringingAt"), nullableNumber(call, "connectedAt"),
                    endedAt, number(call, "durationSeconds")));
        }
        if (!calls.isEmpty()) ioExecutor.execute(() -> dao.upsertAll(calls));
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
    private static long number(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? 0L : element.getAsLong();
    }
    private static Long nullableNumber(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsLong();
    }
    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("<plus>")) result = result.substring(6);
        return result.startsWith("+") ? result.substring(1) : result;
    }
}
