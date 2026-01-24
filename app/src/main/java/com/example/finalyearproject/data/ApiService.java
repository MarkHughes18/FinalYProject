package com.example.finalyearproject.data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
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

}
