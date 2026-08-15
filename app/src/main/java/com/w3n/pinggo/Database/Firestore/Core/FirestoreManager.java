package com.w3n.pinggo.Database.Firestore.Core;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.Firestore.Util.ApiService;
import com.w3n.pinggo.Database.Firestore.Util.ApiServiceAuth;
import com.w3n.pinggo.Database.Firestore.Util.DOC_IDS;
import com.w3n.pinggo.Database.Firestore.Util.DocumentSnapshot;
import com.w3n.pinggo.Database.Firestore.Util.ListenerCallback.OnFailureListener;
import com.w3n.pinggo.Database.Firestore.Util.ListenerCallback.OnSuccessListener;
import com.w3n.pinggo.Database.Firestore.Util.Projection.Projection;
import com.w3n.pinggo.Database.Firestore.Util.RestApi;
import com.w3n.pinggo.Database.Firestore.Util.Validator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FirestoreManager {
    private static FirestoreManager instance;
    private static RestApi restApi;
    private static final Object lock = new Object();
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private FirestoreManager() {
        if (AppContextProvider.isDevelopment){
            restApi = ApiService.devRetrofit.create(RestApi.class);
        }else {
            restApi = ApiService.retrofit.create(RestApi.class);
        }
    }

    public void applyAuth(Context context) {
        String authToken = LoginStateManager.getInstance().getENC(context);
        ApiServiceAuth apiAuth = new ApiServiceAuth(authToken);
        restApi = apiAuth.getRetrofit().create(RestApi.class);
    }


    public static FirestoreManager getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (lock) {
            if (instance == null) {
                instance = new FirestoreManager();
            }
        }
        return instance;
    }

    public void readDocument(String collName, String docName, String parentPath, OnSuccessListener<DocumentSnapshot> onSuccessListener, OnFailureListener onFailureListener) {

        //validate docName and collectionName
        Validator.validateCollectionName(collName);
        Validator.validateDocumentName(docName);

        Call<Object> readDocumentCall = restApi.readDocument(collName, docName, parentPath);
        readDocumentCall.enqueue(new Callback<Object>() {
            @Override
            public void onResponse(@NonNull Call<Object> call, @NonNull Response<Object> response) {
                if (response.isSuccessful()) {
                    if (onSuccessListener != null) {
                        mainThreadHandler.post(() -> onSuccessListener.onSuccess(new DocumentSnapshot(response.body())));
                    }
                } else {
                    if (onFailureListener != null) {
                        mainThreadHandler.post(() -> onFailureListener.onFailure(new Exception(response.code()+"")));
                    }
                }
            }

            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                if (onFailureListener != null) {
                    mainThreadHandler.post(() -> onFailureListener.onFailure((Exception) t));
                }
            }
        });
    }


    public void readDocument(String collName,String docName, String parentPath, @NonNull Projection Projection, OnSuccessListener<DocumentSnapshot> onSuccessListener, OnFailureListener onFailureListener) {
        List<String> ids = new ArrayList<>();
        ids.add(docName);
        bulkReadDocuments(collName, parentPath, DOC_IDS.LIST(ids), Projection, documentSnapshots -> onSuccessListener.onSuccess(documentSnapshots.get(0)),onFailureListener);
    }


    public void bulkReadDocuments(String collName, String parentPath, DOC_IDS DocIds, @Nullable Projection Projection, OnSuccessListener<List<DocumentSnapshot>> onSuccessListener, OnFailureListener onFailureListener) {

        // Validate inputs
        Validator.validateCollectionName(collName);
        if (DocIds == null || DocIds.getIds().isEmpty()) {
            throw new RuntimeException("docIds should be a non-empty list of strings.");
        }

        List<String> docIds = DocIds.getIds();
        for (String docId : docIds) {
            Validator.validateDocumentName(docId);
        }

        Map<String, Integer> projection;
        if (Projection == null) {
            projection = new HashMap<>();
        }else {
            projection = Projection.getProjection();
        }

        // Construct the request body
        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("docIds", new JSONArray(docIds));
            bodyJson.put("projection", new JSONObject(projection));
        } catch (JSONException e) {
            throw new RuntimeException("Error constructing request body: " + e.getMessage());
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), MediaType.parse("application/json; charset=utf-8"));

        Call<List<Object>> bulkReadDocumentsCall = restApi.bulkReadDocuments(collName, parentPath, body);
        bulkReadDocumentsCall.enqueue(new Callback<List<Object>>() {
            @Override
            public void onResponse(@NonNull Call<List<Object>> call, @NonNull Response<List<Object>> response) {
                if (response.isSuccessful()) {
                    if (onSuccessListener != null) {
                        List<DocumentSnapshot> documentSnapshots = new ArrayList<>();
                        for (Object obj : response.body()) {
                            documentSnapshots.add(new DocumentSnapshot(obj));
                        }
                        mainThreadHandler.post(() -> onSuccessListener.onSuccess(documentSnapshots));
                    }
                } else {
                    if (onFailureListener != null) {
                        mainThreadHandler.post(() -> onFailureListener.onFailure(new Exception(response.message())));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Object>> call, Throwable t) {
                if (onFailureListener != null) {
                    mainThreadHandler.post(() -> onFailureListener.onFailure((Exception) t));
                }
            }
        });
    }





//    public void updateDocument(String collName, String docName, String parentPath, Object document, OnSuccessListener<DocumentUpdateReference> onSuccessListener, OnFailureListener onFailureListener) {
//
//        String body;
//
//        try {
//
//            Gson gson = new Gson();
//            String json = gson.toJson(document);
//            JSONObject jsonObjectDocument = new JSONObject(json);
//            if (jsonObjectDocument.has("_id")) {
//                // document id is already exist
//                throw new RuntimeException("document class should not have '_id' field.");
//            }
//
//            //validate docName and collectionName
//            Validator.validateCollectionName(collName);
//            Validator.validateDocumentName(docName);
//            jsonObjectDocument.put("_id",docName);
//
//            body = jsonObjectDocument.toString();
//
//        }catch (Exception e){
//            throw new RuntimeException("error parsing document :"+e.getMessage());
//        }
//
//
//        Call<Object> updateDocumentCall = restApi.updateDocument(collName, parentPath, RequestBody.create( body , MediaType.parse("application/json; charset=utf-8")));
//        updateDocumentCall.enqueue(new Callback<Object>() {
//            @Override
//            public void onResponse(@NonNull Call<Object> call, @NonNull Response<Object> response) {
//                if (response.isSuccessful()) {
//                    if (onSuccessListener != null) {
//                        mainThreadHandler.post(() -> onSuccessListener.onSuccess(new DocumentUpdateReference(response.body())));
//                    }
//                } else {
//                    if (onFailureListener != null) {
//                        mainThreadHandler.post(() -> onFailureListener.onFailure(new Exception(response.message())));
//                    }
//                }
//            }
//
//            @Override
//            public void onFailure(Call<Object> call, Throwable t) {
//                if (onFailureListener != null) {
//                    mainThreadHandler.post(() -> onFailureListener.onFailure((Exception) t));
//                }
//            }
//        });
//    }
//
//
//    public void deleteDocument(String collName, String docName, String parentPath, OnSuccessListener<DocumentDeleteReference> onSuccessListener, OnFailureListener onFailureListener) {
//
//        //validate docName and collectionName
//        Validator.validateCollectionName(collName);
//        Validator.validateDocumentName(docName);
//
//        Call<Object> deleteDocumentCall = restApi.deleteDocument(collName, docName, parentPath);
//        deleteDocumentCall.enqueue(new Callback<Object>() {
//            @Override
//            public void onResponse(@NonNull Call<Object> call, @NonNull Response<Object> response) {
//                if (response.isSuccessful()) {
//                    if (onSuccessListener != null) {
//                        mainThreadHandler.post(() -> onSuccessListener.onSuccess(new DocumentDeleteReference(response.body())));
//                    }
//                } else {
//                    if (onFailureListener != null) {
//                        mainThreadHandler.post(() -> onFailureListener.onFailure(new Exception(response.message())));
//                    }
//                }
//            }
//
//            @Override
//            public void onFailure(Call<Object> call, Throwable t) {
//                if (onFailureListener != null) {
//                    mainThreadHandler.post(() -> onFailureListener.onFailure((Exception) t));
//                }
//            }
//        });
//    }
//


}
