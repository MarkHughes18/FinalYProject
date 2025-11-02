package com.example.finalyearproject;

import android.os.Bundle;
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

        loginBtn.setOnClickListener(v ->
        {
            String id = idET.getText().toString().trim();
            String password = passwordET.getText().toString().trim();

            if (id.isEmpty() || password.isEmpty())
            {
                Toast.makeText(LogInActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            }else
            {
                //need to add in database //
                Toast.makeText(LogInActivity.this, "Database to be added in", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
