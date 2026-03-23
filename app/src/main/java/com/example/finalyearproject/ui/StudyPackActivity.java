package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.data.StudyPackResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudyPackActivity extends AppCompatActivity {

    private TextView titleTv;
    private TextView statusTv;
    private ProgressBar progressBar;

    private ApiService api;
    private String historyId;
    private String fileName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_pack);

        titleTv = findViewById(R.id.studyPackTitleTV);
        statusTv = findViewById(R.id.studyPackStatusTV);
        progressBar = findViewById(R.id.studyPackProgress);

        api = RetrofitClient.getApiService();

        historyId = getIntent().getStringExtra("historyId");
        fileName = getIntent().getStringExtra("fileName");

        titleTv.setText(fileName != null ? fileName : "Study Pack");

        if (historyId == null || historyId.isBlank()) {
            statusTv.setText("Missing history id.");
            progressBar.setVisibility(View.GONE);
            return;
        }

        loadStudyPack();
    }

    private void loadStudyPack() {
        String email = getLoggedInEmail();
        if (email == null) {
            progressBar.setVisibility(View.GONE);
            statusTv.setText("No logged in user found.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        statusTv.setText("Generating study pack...");

        api.getStudyPack(historyId, email).enqueue(new Callback<StudyPackResponse>() {
            @Override
            public void onResponse(Call<StudyPackResponse> call, Response<StudyPackResponse> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    StudyPackResponse pack = response.body();

                    int flashcards = pack.flashcards != null ? pack.flashcards.size() : 0;
                    int cloze = pack.clozeQuestions != null ? pack.clozeQuestions.size() : 0;
                    int tf = pack.trueFalseQuestions != null ? pack.trueFalseQuestions.size() : 0;
                    int mcq = pack.mcqQuestions != null ? pack.mcqQuestions.size() : 0;
                    int matching = pack.matchingPairs != null ? pack.matchingPairs.size() : 0;

                    statusTv.setText(
                            "Study pack loaded.\n\n" +
                                    "Flashcards: " + flashcards + "\n" +
                                    "Cloze: " + cloze + "\n" +
                                    "True/False: " + tf + "\n" +
                                    "MCQ: " + mcq + "\n" +
                                    "Matching: " + matching
                    );
                } else if (response.code() == 202) {
                    statusTv.setText("Study pack is not ready yet.\nNarration is still processing.");
                } else if (response.code() == 403) {
                    statusTv.setText("You are not allowed to access this study pack.");
                } else if (response.code() == 404) {
                    statusTv.setText("Study pack source file not found.");
                } else {
                    statusTv.setText("Failed to load study pack. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<StudyPackResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                statusTv.setText("Network error loading study pack.");
                Toast.makeText(StudyPackActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getLoggedInEmail() {
        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }
}