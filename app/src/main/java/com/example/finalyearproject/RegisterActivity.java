package com.example.finalyearproject;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import retrofit2.*;

public class RegisterActivity extends AppCompatActivity {
    EditText fullNameET, regEmailET, regPasswordET, dobET;
    Button registerBtn;
    ApiService api;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_register);

        fullNameET = findViewById(R.id.fullNameET);
        regEmailET = findViewById(R.id.regEmailET);
        regPasswordET = findViewById(R.id.regPasswordET);
        dobET = findViewById(R.id.dobET);
        registerBtn = findViewById(R.id.registerBtn);

        api = RetrofitClient.getInstance().create(ApiService.class);

        dobET.setOnClickListener(v -> showDatePicker());
        registerBtn.setOnClickListener(v -> tryRegister());
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

        if (TextUtils.isEmpty(name)) { toast("Enter full name"); return; }
        if (TextUtils.isEmpty(email) || !email.contains("@")) { toast("Enter a valid email"); return; }
        if (!pwd.matches("^(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}\\[\\]|:;\"'<>,.?/]).{8,}$")) {
            toast("Password needs 8+ chars incl. a number & special char"); return;
        }
        if (TextUtils.isEmpty(dob)) { toast("Select your date of birth"); return; }

        RegisterRequest req = new RegisterRequest(name, email, pwd, dob);
        api.register(req).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(Call<ApiResponse> call, Response<ApiResponse> res) {
                if (res.isSuccessful() && res.body() != null && res.body().success) {
                    toast("Registered! You can sign in now."); finish();
                } else {
                    toast(res.body() != null ? res.body().message : "Registration failed");
                }
            }
            @Override public void onFailure(Call<ApiResponse> call, Throwable t) {
                toast("Network error: " + t.getMessage());
            }
        });
    }
    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
