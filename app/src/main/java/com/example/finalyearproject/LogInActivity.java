package com.example.finalyearproject;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LogInActivity extends AppCompatActivity
{
    EditText idET, passwordET;
    Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        idET = findViewById(R.id.idET);
        passwordET = findViewById(R.id.passwordET);
        loginBtn = findViewById(R.id.loginBtn);

        loginBtn.setOnClickListener(v -> validateLogin());
    }

    private void validateLogin() {
        String id = idET.getText().toString().trim();
        String password = passwordET.getText().toString().trim();

        //username/email parameters
        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, "Enter your Username/Password", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!id.contains("@") && !id.matches(".*\\d.*")) {
            Toast.makeText(this, "Username must have a number or Email must have @", Toast.LENGTH_SHORT).show();
            return;
        }

        //password parameters
        if (password.length() < 8) {
            Toast.makeText(this, "Password must be 8 characters long", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(".*\\d.*")) {
            Toast.makeText(this, "Password must contain 1 number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(".*[!@#$%^&*+=?<>].*")) {
            Toast.makeText(this, "Password must contain 1 special character", Toast.LENGTH_SHORT).show();
            return;
        }

        //need to check database for existing account

        Toast.makeText(this, "Login Valid, Ready to Start!", Toast.LENGTH_SHORT).show();
    }
}
