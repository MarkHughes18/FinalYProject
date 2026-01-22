package com.example.finalyearproject.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiResponse;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.LoginRequest;
import com.example.finalyearproject.data.RetrofitClient;

import retrofit2.*;

public class MainActivity extends AppCompatActivity {
    private EditText emailET, passwordET;
    private Button signInBtn;
    private TextView registerLink;
    ApiService api;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emailET = findViewById(R.id.emailET);
        passwordET = findViewById(R.id.passwordET);
        signInBtn = findViewById(R.id.signInBtn);
        registerLink = findViewById(R.id.registerLink);

        api = RetrofitClient.getApiService();

        signInBtn.setOnClickListener(v -> trySignIn());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void trySignIn() {
        String email = emailET.getText().toString().trim();
        String password = passwordET.getText().toString();

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            toast("Enter a Valid Email"); return;
        }
        if (password.length() < 8) {
            toast("Password must be at least 8 Characters"); return;
        }

        LoginRequest req = new LoginRequest(email, password);
        api.login(req).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(Call<ApiResponse> call, Response<ApiResponse> res) {
                if (res.isSuccessful() && res.body() != null && res.body().success) {
                    toast("Signed In!");
                    startActivity(new Intent(MainActivity.this, HomePGActivity.class));
                    finish();
                } else {
                    toast(res.body() != null ? res.body().message : "Login Failed");
                }
            }
            @Override public void onFailure(Call<ApiResponse> call, Throwable t) {
                toast("Network Error: " + t.getMessage());
            }
        });
    }

    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
