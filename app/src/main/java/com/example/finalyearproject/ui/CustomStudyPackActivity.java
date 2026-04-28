package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.CustomStudyPackRequest;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CustomStudyPackActivity extends AppCompatActivity {

    private EditText titleEt;
    private EditText flashcardEt;
    private EditText clozeEt;
    private EditText trueFalseEt;
    private EditText mcqEt;
    private ProgressBar progressBar;
    private Button createBtn;

    private ApiService api;
    private String sourceHistoryId;
    private String sourceFileName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_study_pack);

        titleEt = findViewById(R.id.customPackNameET);
        flashcardEt = findViewById(R.id.flashcardSnippetsET);
        clozeEt = findViewById(R.id.clozeSnippetsET);
        trueFalseEt = findViewById(R.id.trueFalseSnippetsET);
        mcqEt = findViewById(R.id.mcqSnippetsET);
        progressBar = findViewById(R.id.customPackProgress);
        createBtn = findViewById(R.id.createCustomPackBtn);

        api = RetrofitClient.getApiService();

        sourceHistoryId = getIntent().getStringExtra("sourceHistoryId");
        sourceFileName = getIntent().getStringExtra("sourceFileName");

        if (sourceFileName != null && !sourceFileName.isBlank()) {
            titleEt.setHint("Custom pack for " + sourceFileName);
        }

        createBtn.setOnClickListener(v -> createCustomPack());
    }

    private void createCustomPack() {
        String email = getLoggedInEmail();

        if (email == null || email.isBlank()) {
            toast("No logged in user found.");
            return;
        }

        String enteredTitle = titleEt.getText().toString().trim();
        final String packTitle = enteredTitle.isBlank() ? "Custom Study Pack" : enteredTitle;

        List<String> flashcards = readSnippets(flashcardEt);
        List<String> cloze = readSnippets(clozeEt);
        List<String> trueFalse = readSnippets(trueFalseEt);
        List<String> mcq = readSnippets(mcqEt);

        if (flashcards.size() < 5 || cloze.size() < 5 || trueFalse.size() < 5 || mcq.size() < 5) {
            toast("Please enter at least 5 snippets for each section.");
            return;
        }

        CustomStudyPackRequest request = new CustomStudyPackRequest(
                email,
                sourceHistoryId,
                packTitle,
                flashcards,
                cloze,
                trueFalse,
                mcq
        );

        setLoading(true);

        api.createCustomStudyPack(request).enqueue(new Callback<StudyPackResponse>() {
            @Override
            public void onResponse(Call<StudyPackResponse> call, Response<StudyPackResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    StudyPackResponse pack = response.body();

                    Intent intent = new Intent(CustomStudyPackActivity.this, StudyPackActivity.class);
                    intent.putExtra("historyId", pack.historyId);
                    intent.putExtra("fileName", packTitle);
                    startActivity(intent);
                    finish();
                } else {
                    toast("Failed to create custom study pack. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<StudyPackResponse> call, Throwable t) {
                setLoading(false);
                toast("Error: " + t.getMessage());
            }
        });
    }

    private List<String> readSnippets(EditText editText) {
        List<String> snippets = new ArrayList<>();

        String raw = editText.getText().toString();
        String[] lines = raw.split("\\r?\\n");

        for (String line : lines) {
            String cleaned = line.trim();
            if (!cleaned.isBlank()) {
                snippets.add(cleaned);
            }
        }

        return snippets;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        createBtn.setEnabled(!loading);
        createBtn.setText(loading ? "Creating..." : "Create Study Pack");
    }

    private String getLoggedInEmail() {
        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}