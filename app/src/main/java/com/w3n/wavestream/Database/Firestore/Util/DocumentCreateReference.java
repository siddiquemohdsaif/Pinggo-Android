package com.w3n.wavestream.Database.Firestore.Util;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

public class DocumentCreateReference {
    private String documentId;
    
    public DocumentCreateReference(Object data) {
        try {
            String jsonString = new Gson().toJson(data);
            JSONObject json = new JSONObject(jsonString);
            this.documentId = json.getString("insertedId");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    public String getDocumentId() {
        return documentId;
    }
}
