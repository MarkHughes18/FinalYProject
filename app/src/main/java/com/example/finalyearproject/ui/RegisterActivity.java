package com.example.finalyearproject.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiResponse;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.RegisterRequest;
import com.example.finalyearproject.data.RetrofitClient;

import java.util.Calendar;
import retrofit2.*;

public class RegisterActivity extends AppCompatActivity {
    private EditText fullNameET, regEmailET, regPasswordET, dobET;
    private Button registerBtn, bckToSignInBtn; 
    private ApiService api;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        );
        setContentView(R.layout.activity_register);

        fullNameET = findViewById(R.id.fullNameET);
        regEmailET = findViewById(R.id.regEmailET);
        regPasswordET = findViewById(R.id.regPasswordET);
        dobET = findViewById(R.id.dobET);
        registerBtn = findViewById(R.id.registerBtn);
        bckToSignInBtn = findViewById(R.id.bckToSignInBtn);

        api = RetrofitClient.getApiService();

        dobET.setOnClickListener(v -> showDatePicker());
        registerBtn.setOnClickListener(v -> tryRegister());
        bckToSignInBtn.setOnClickListener(v -> finish());
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        new DatePickerDialog(this, (view, yy, mm, dd) -> {
            String dob = String.format("%04d-%02d-%02d", yy, mm+1, dd);
            dobET.setText(dob);
        }, y, m, d).show();
    }

    private void tryRegister() {
        String name = fullNameET.getText().toString().trim();
        String email = regEmailET.getText().toString().trim();
        String pwd = regPasswordET.getText().toString();
        String dob = dobET.getText().toString().trim();

        if (TextUtils.isEmpty(name)) { toast("Enter Full Name"); return; }
        if (TextUtils.isEmpty(email) || !email.contains("@")) { toast("Enter a Valid Email"); return; }
        if (!pwd.matches("^(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>,.?/]).{8,}$")) {
            toast("Password needs 8+ Chars incl. a Number & Special Char"); return;
        }
        if (TextUtils.isEmpty(dob)) { toast("Select your Date of Birth"); return; }

        RegisterRequest req = new RegisterRequest(name, email, pwd, dob);
        api.register(req).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(Call<ApiResponse> call, Response<ApiResponse> res) {
                if (res.isSuccessful() && res.body() != null && res.body().success) {
                    SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
                    prefs.edit()
                            .putString("email", email)   // <-- the email user typed in
                            .apply();

                    // 2) Navigate to your host activity (the one with bottom nav)
                    Intent intent = new Intent(RegisterActivity.this, HomePGActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    toast(res.body() != null ? res.body().message : "Login failed");
                }
            }
            @Override public void onFailure(Call<ApiResponse> call, Throwable t) {
                toast("Network Error: " + t.getMessage());
            }
        });
    }
    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
