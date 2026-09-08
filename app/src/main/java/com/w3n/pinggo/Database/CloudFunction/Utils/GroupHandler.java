package com.w3n.pinggo.Database.CloudFunction.Utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.RestApi.AppRestAPI;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** REST bridge for non-call group lifecycle operations. */
public final class GroupHandler {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private GroupHandler() {}

    public static void create(AppRestAPI api, String userId, String name, String description,
                              java.util.List<String> memberIds,
                              AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, null);
        body.addProperty("name", name);
        body.addProperty("description", description == null ? "" : description);
        JsonArray ids = new JsonArray();
        if (memberIds != null) for (String id : memberIds) ids.add(normalize(id));
        body.add("memberIds", ids);
        enqueue(api.createGroup(request(body)), callback);
    }

    public static void get(AppRestAPI api, String userId, String groupId,
                           AppFunctionManager.Callback callback) {
        enqueue(api.getGroup(request(identity(userId, groupId))), callback);
    }

    public static void details(AppRestAPI api, String userId, String groupId,
                               AppFunctionManager.Callback callback) {
        enqueue(api.getGroupDetails(request(identity(userId, groupId))), callback);
    }

    public static void messages(AppRestAPI api, String userId, String groupId, int pageSize,
                                Long before, AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, groupId);
        body.addProperty("pageSize", pageSize);
        if (before != null) body.addProperty("before", before);
        enqueue(api.getGroupMessages(request(body)), callback);
    }

    public static void update(AppRestAPI api, String userId, String groupId, String name,
                              String description, AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, groupId);
        if (name != null) body.addProperty("name", name);
        if (description != null) body.addProperty("description", description);
        enqueue(api.updateGroup(request(body)), callback);
    }

    public static void members(AppRestAPI api, String userId, String groupId,
                               java.util.List<String> memberIds, boolean add,
                               AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, groupId); JsonArray ids = new JsonArray();
        if (memberIds != null) for (String id : memberIds) ids.add(normalize(id));
        body.add("memberIds", ids);
        enqueue(add ? api.addGroupMembers(request(body)) : api.removeGroupMembers(request(body)), callback);
    }

    public static void role(AppRestAPI api, String userId, String groupId, String memberId,
                            String role, AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, groupId);
        body.addProperty("memberId", normalize(memberId)); body.addProperty("role", role);
        enqueue(api.updateGroupMemberRole(request(body)), callback);
    }

    public static void leave(AppRestAPI api, String userId, String groupId,
                             AppFunctionManager.Callback callback) {
        enqueue(api.leaveGroup(request(identity(userId, groupId))), callback);
    }

    public static void report(AppRestAPI api, String userId, String groupId, String reason,
                              AppFunctionManager.Callback callback) {
        JsonObject body = identity(userId, groupId);
        body.addProperty("reason", reason == null ? "" : reason.trim());
        enqueue(api.reportGroup(request(body)), callback);
    }

    private static JsonObject identity(String userId, String groupId) {
        JsonObject body = new JsonObject(); body.addProperty("userId", normalize(userId));
        if (groupId != null) body.addProperty("groupId", groupId.trim()); return body;
    }
    private static String normalize(String id) {
        if (id == null) return ""; String value = id.trim();
        if (value.startsWith("<plus>")) return value.substring(6);
        return value.startsWith("+") ? value.substring(1) : value;
    }
    private static RequestBody request(JsonObject body) { return RequestBody.create(body.toString(), JSON); }
    private static void enqueue(Call<JsonObject> call, AppFunctionManager.Callback callback) {
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) callback.onSuccess(response.body());
                else callback.onError("Group request failed (HTTP " + response.code() + ").");
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable error) {
                callback.onError(error.getMessage() == null ? "Network error." : error.getMessage());
            }
        });
    }
}
