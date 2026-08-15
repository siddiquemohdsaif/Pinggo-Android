package com.w3n.pinggo.Database.Firestore.Util;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

public class DocumentUpdateReference {
    private boolean acknowledged;
    private int modifiedCount;
    private String upsertedId;
    private int upsertedCount;
    private int matchedCount;

    public DocumentUpdateReference(Object data) {
        try {
            String jsonString = new Gson().toJson(data);
            JSONObject json = new JSONObject(jsonString);
            this.acknowledged = json.getBoolean("acknowledged");
            this.modifiedCount = json.getInt("modifiedCount");
            this.upsertedId = json.getString("upsertedId");
            this.upsertedCount = json.getInt("upsertedCount");
            this.matchedCount = json.getInt("matchedCount");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public int getModifiedCount() {
        return modifiedCount;
    }

    public String getUpsertedId() {
        return upsertedId;
    }

    public int getUpsertedCount() {
        return upsertedCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }
}
