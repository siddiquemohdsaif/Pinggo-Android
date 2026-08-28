package com.w3n.pinggo.data.repository;

/** Immutable state for cache hydration, refresh, pagination, content, empty, and errors. */
public final class ChatListState {
    public enum Status {
        INITIAL_CACHE_LOADING,
        REFRESHING,
        PAGINATING,
        CONTENT,
        EMPTY,
        ERROR_WITH_CACHE,
        ERROR_WITHOUT_CACHE
    }

    private final Status status;
    private final int cachedChatCount;
    private final String errorMessage;

    private ChatListState(Status status, int cachedChatCount, String errorMessage) {
        this.status = status;
        this.cachedChatCount = Math.max(0, cachedChatCount);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ChatListState initial() {
        return new ChatListState(Status.INITIAL_CACHE_LOADING, 0, "");
    }

    static ChatListState create(boolean cacheLoaded, int cachedChatCount,
                                boolean refreshing, boolean paginating,
                                String errorMessage) {
        boolean hasCache = cachedChatCount > 0;
        String error = errorMessage == null ? "" : errorMessage.trim();
        Status status;
        if (!cacheLoaded || (refreshing && !hasCache)) {
            status = Status.INITIAL_CACHE_LOADING;
        } else if (!error.isEmpty()) {
            status = hasCache ? Status.ERROR_WITH_CACHE : Status.ERROR_WITHOUT_CACHE;
        } else if (refreshing) {
            status = Status.REFRESHING;
        } else if (paginating) {
            status = Status.PAGINATING;
        } else {
            status = hasCache ? Status.CONTENT : Status.EMPTY;
        }
        return new ChatListState(status, cachedChatCount, error);
    }

    public Status getStatus() { return status; }
    public int getCachedChatCount() { return cachedChatCount; }
    public String getErrorMessage() { return errorMessage; }
}
