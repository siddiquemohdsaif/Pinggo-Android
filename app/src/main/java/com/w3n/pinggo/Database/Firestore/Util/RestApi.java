package com.w3n.pinggo.Database.Firestore.Util;

import androidx.annotation.Keep;

import java.util.List;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
@Keep
public interface RestApi {
//    @POST("cr")
//    Call<Object> createDocument(
//            @Query("collName") String collName,
//            @Query("parentPath") String parentPath,
//            @Body RequestBody body
//    );

    @GET("rd")
    Call<Object> readDocument(
            @Query("collName") String collName,
            @Query("docName") String docName,
            @Query("parentPath") String parentPath
    );

    @POST("rdbulk")
    Call<List<Object>> bulkReadDocuments(
            @Query("collName") String collName,
            @Query("parentPath") String parentPath,
            @Body RequestBody body
    );

//    @POST("upd")
//    Call<Object> updateDocument(
//            @Query("collName") String collName,
//            @Query("parentPath") String parentPath,
//            @Body RequestBody body
//    );
//
//    @GET("deld")
//    Call<Object> deleteDocument(
//            @Query("collName") String collName,
//            @Query("docName") String docName,
//            @Query("parentPath") String parentPath
//    );
//
//    @POST("dfd")
//    Call<Object> deleteField(
//            @Query("collName") String collName,
//            @Query("parentPath") String parentPath,
//            @Body RequestBody body
//    );

}
