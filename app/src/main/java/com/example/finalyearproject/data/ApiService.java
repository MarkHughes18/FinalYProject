package com.example.finalyearproject.data;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/auth/register")
    Call<ApiResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<ApiResponse> login(@Body LoginRequest body);

    @POST("api/files/history")
    Call<HistoryItem> createHistory(@Body CreateHistoryRequest req);

    @GET("api/files/history")
    Call<List<HistoryItem>> getHistory(@Query("email") String email);

    @GET("api/users/profile")
    Call<UserProfile> getUserProfile(@Query("email") String email);

    @Multipart
    @POST("api/files/upload")
    Call<HistoryItem> uploadFile(@Query("historyId") String historyId, @Part MultipartBody.Part file);

    @DELETE("api/files/history")
    Call<Void> clearHistory(@Query("email") String email);

    @DELETE("api/files/history/{id}")
    Call<Void> deleteHistoryItem(@Path("id") String id);

    @GET("api/study/packs/{historyId}")
    Call<StudyPackResponse> getStudyPack( @Path("historyId") String historyId, @Query("email") String email);

    @PATCH("api/files/history/{id}/label")
    Call<HistoryItem> updateHistoryLabel(@Path("id") String id, @Body UpdateLabelRequest req);

    @POST("api/study/packs/{historyId}/regenerate")
    Call<StudyPackResponse> regenerateStudyPack(@Path("historyId") String historyId, @Query("email") String email);

    @POST("api/study/custom-pack")
    Call<StudyPackResponse> createCustomStudyPack(@Body CustomStudyPackRequest request);
}
