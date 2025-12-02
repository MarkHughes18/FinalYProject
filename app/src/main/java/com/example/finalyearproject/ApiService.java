package com.example.finalyearproject;

import com.example.finalyearproject.ApiResponse;
import com.example.finalyearproject.LoginRequest;
import com.example.finalyearproject.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/auth/register")
    Call<ApiResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<ApiResponse> login(@Body LoginRequest body);
}
