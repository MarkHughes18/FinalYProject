package com.example.finalyearproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity
{
    EditText usernameET, emailET, passwordET;
    Button signUpBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        usernameET = findViewById(R.id.usernameET);
        emailET = findViewById(R.id.emailET);
        passwordET = findViewById(R.id.passwordET);
        signUpBtn = findViewById(R.id.signUpBtn);

        signUpBtn.setOnClickListener(v -> validateInputs());
    }

    private void validateInputs()
    {
        String username = usernameET.getText().toString().trim();
        String email = emailET.getText().toString().trim();
        String password = passwordET.getText().toString().trim();

        //username parameters
        if (TextUtils.isEmpty(username))
        {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username.length() > 15)
        {
            Toast.makeText(this, "Username must be 15 characters or less", Toast.LENGTH_SHORT).show();
            return;
        }
        if(!username.matches(".*\\d.*"))
        {
            Toast.makeText(this, "Username must contain at least 1 number", Toast.LENGTH_SHORT).show();
            return;
        }

        // email parameters
        if (TextUtils.isEmpty(email) || !email.contains("@"))
        {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        // password parameters
        if (password.length() < 8)
        {
            Toast.makeText(this, "Password must be 8 characters long", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(".*\\d.*"))
        {
            Toast.makeText(this, "Password must contain 1 number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(".*[!@#$%^&*+=?<>].*"))
        {
            Toast.makeText(this, "Password must contain 1 special character", Toast.LENGTH_SHORT).show();
            return;
        }

        //need to add database function to create db for sign up

        Toast.makeText(this, "All inout correct. Creating Account.", Toast.LENGTH_SHORT).show();
    }
}
