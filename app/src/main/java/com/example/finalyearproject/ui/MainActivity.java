package com.example.finalyearproject.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiResponse;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.LoginRequest;
import com.example.finalyearproject.data.RetrofitClient;

import retrofit2.*;

public class MainActivity extends AppCompatActivity {
    EditText emailET, passwordET;
    Button signInBtn;
    TextView registerLink;
    ApiService api;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        emailET = findViewById(R.id.emailET);
        passwordET = findViewById(R.id.passwordET);
        signInBtn = findViewById(R.id.signInBtn);
        registerLink = findViewById(R.id.registerLink);

        api = RetrofitClient.getInstance().create(ApiService.class);

        signInBtn.setOnClickListener(v -> trySignIn());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void trySignIn() {
        String email = emailET.getText().toString().trim();
        String password = passwordET.getText().toString();

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            toast("Enter a valid email"); return;
        }
        if (password.length() < 8) {
            toast("Password must be at least 8 characters"); return;
        }

        LoginRequest req = new LoginRequest(email, password);
        api.login(req).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(Call<ApiResponse> call, Response<ApiResponse> res) {
                if (res.isSuccessful() && res.body() != null && res.body().success) {
                    toast("Signed in!");
                    // TODO: go to your next screen
                } else {
                    toast(res.body() != null ? res.body().message : "Login failed");
                }
            }
            @Override public void onFailure(Call<ApiResponse> call, Throwable t) {
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
