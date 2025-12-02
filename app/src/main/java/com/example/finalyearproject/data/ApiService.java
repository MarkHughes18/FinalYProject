package com.example.finalyearproject.data;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/auth/register")
    Call<ApiResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<ApiResponse> login(@Body LoginRequest body);
}
