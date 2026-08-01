package com.w3n.wavestream.Database.Firestore.Util;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

public class DocumentDeleteReference {
    private boolean acknowledged;
    private int deletedCount;

    public DocumentDeleteReference(Object data) {
        try {
            String jsonString = new Gson().toJson(data);
            JSONObject json = new JSONObject(jsonString);
            this.acknowledged = json.getBoolean("acknowledged");
            this.deletedCount = json.getInt("deletedCount");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public int getDeletedCount() {
        return deletedCount;
    }
}
