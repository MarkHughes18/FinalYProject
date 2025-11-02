package com.example.finalyearproject;

import android.content.Intent;
import android.os.Bundle;
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

        signUpBtn.setOnClickListener(v ->
                {
                    String username = usernameET.getText().toString().trim();
                    String email = emailET.getText().toString().trim();
                    String password = passwordET.getText().toString().trim();

                    if (username.isEmpty() || email.isEmpty() || password.isEmpty())
                    {
                        Toast.makeText(SignUpActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    }else
                    {
                        //to add in database functions later//
                        Toast.makeText(SignUpActivity.this, "Database to be added in", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignUpActivity.this, LogInActivity.class));
                        finish();
                    }

                });
    }
}
