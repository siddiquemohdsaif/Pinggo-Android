package com.w3n.pinggo.Database.Firestore.Util;

import com.google.gson.Gson;

import android.util.Log;

import org.json.JSONObject;

import java.util.Map;

public class DocumentSnapshot {
    private Object data;

    public DocumentSnapshot(Object data) {
        this.data = data;
    }

    public Object getData() {
        return data;
    }

    public Map<String, Object> getDataMap() {
        try {
            String jsonString = new Gson().toJson(data);
            return new Gson().fromJson(jsonString, Map.class);
        } catch (Exception e) {
            Log.e("DocumentSnapshot", "getData cast failed , error: " + e);
            return null;
        }
    }

    public JSONObject getDataJson() {
        try {
            String jsonString = new Gson().toJson(data);
            return new JSONObject(jsonString);
        } catch (Exception e) {
            Log.e("DocumentSnapshot", "getData cast failed , error: " + e);
            return null;
        }
    }

    public String getDataJsonString() {
        try {
            return new Gson().toJson(data);
        } catch (Exception e) {
            Log.e("DocumentSnapshot", "getData cast failed , error: " + e);
            return null;
        }
    }

    public boolean exists() {
        return data != null;
    }

    public <T> T toObject(Class<T> classType) {

        try {
            String jsonString = new Gson().toJson(data);
            return new Gson().fromJson(jsonString,classType);
        }catch (Exception e){
            Log.e("DocumentSnapshot", "toObject cast failed , error: " + e );
            return null;
        }

    }
}