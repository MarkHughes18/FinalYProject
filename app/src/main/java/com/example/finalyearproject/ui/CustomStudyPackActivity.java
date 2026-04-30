package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.CustomStudyPackRequest;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.data.StudyPackResponse;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CustomStudyPackActivity extends AppCompatActivity {

    private EditText titleEt;
    private EditText[] flashcardEts;
    private EditText[] clozeEts;
    private EditText[] trueFalseEts;
    private EditText[] mcqEts;
    private EditText[] matchingEts;
    private ProgressBar progressBar;
    private Button createBtn;
    private ApiService api;
    private String sourceHistoryId;
    private String sourceFileName;
    private List<EditText> allInputs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_study_pack);

        titleEt = findViewById(R.id.customPackNameET);
        flashcardEts = new EditText[]{findViewById(R.id.flashcardSnippet1ET), findViewById(R.id.flashcardSnippet2ET),
                findViewById(R.id.flashcardSnippet3ET), findViewById(R.id.flashcardSnippet4ET),
                findViewById(R.id.flashcardSnippet5ET)};

        clozeEts = new EditText[]{findViewById(R.id.clozeSnippet1ET), findViewById(R.id.clozeSnippet2ET),
                findViewById(R.id.clozeSnippet3ET), findViewById(R.id.clozeSnippet4ET),
                findViewById(R.id.clozeSnippet5ET)};

        trueFalseEts = new EditText[]{findViewById(R.id.trueFalseSnippet1ET), findViewById(R.id.trueFalseSnippet2ET),
                findViewById(R.id.trueFalseSnippet3ET), findViewById(R.id.trueFalseSnippet4ET),
                findViewById(R.id.trueFalseSnippet5ET)};

        mcqEts = new EditText[]{findViewById(R.id.mcqSnippet1ET), findViewById(R.id.mcqSnippet2ET),
                findViewById(R.id.mcqSnippet3ET), findViewById(R.id.mcqSnippet4ET),
                findViewById(R.id.mcqSnippet5ET)};

        matchingEts = new EditText[]{findViewById(R.id.matchingSnippet1ET), findViewById(R.id.matchingSnippet2ET),
                findViewById(R.id.matchingSnippet3ET), findViewById(R.id.matchingSnippet4ET),
                findViewById(R.id.matchingSnippet5ET)};

        progressBar = findViewById(R.id.customPackProgress);
        createBtn = findViewById(R.id.createCustomPackBtn);
        ImageButton customBackBtn = findViewById(R.id.customBackBtn);
        customBackBtn.setOnClickListener(v -> finish());

        api = RetrofitClient.getApiService();

        sourceHistoryId = getIntent().getStringExtra("sourceHistoryId");
        sourceFileName = getIntent().getStringExtra("sourceFileName");

        if (sourceFileName != null && !sourceFileName.isBlank()) {
            titleEt.setHint("Custom pack for " + sourceFileName);
        }

        createBtn.setOnClickListener(v -> createCustomPack());

        collectInputs();
        setupValidation();
        checkAllFieldsFilled();
    }

    private void createCustomPack() {
        String email = getLoggedInEmail();

        if (email == null || email.isBlank()) {
            toast("No logged in user found.");
            return;
        }

        String enteredTitle = titleEt.getText().toString().trim();
        final String packTitle = enteredTitle.isBlank() ? "Custom Study Pack" : enteredTitle;

        List<String> flashcards = readSnippetFields(flashcardEts);
        List<String> matching = readSnippetFields(matchingEts);
        List<String> cloze = readSnippetFields(clozeEts);
        List<String> trueFalse = readSnippetFields(trueFalseEts);
        List<String> mcq = readSnippetFields(mcqEts);

        if (flashcards.size() < 5 || cloze.size() < 5 || trueFalse.size() < 5 || mcq.size() < 5 || matching.size() < 5) {
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
                mcq,
                matching
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

    private List<String> readSnippetFields(EditText[] fields) {
        List<String> snippets = new ArrayList<>();

        if (fields == null) {
            return snippets;
        }

        for (EditText field : fields) {
            if (field == null) {
                continue;
            }

            String value = field.getText().toString().trim();
            if (!value.isBlank()) {
                snippets.add(value);
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

    private void collectInputs() {
        allInputs.clear();

        allInputs.add(titleEt);

        int[] ids = {
                R.id.flashcardSnippet1ET, R.id.flashcardSnippet2ET, R.id.flashcardSnippet3ET, R.id.flashcardSnippet4ET, R.id.flashcardSnippet5ET,
                R.id.matchingSnippet1ET, R.id.matchingSnippet2ET, R.id.matchingSnippet3ET, R.id.matchingSnippet4ET, R.id.matchingSnippet5ET,
                R.id.clozeSnippet1ET, R.id.clozeSnippet2ET, R.id.clozeSnippet3ET, R.id.clozeSnippet4ET, R.id.clozeSnippet5ET,
                R.id.trueFalseSnippet1ET, R.id.trueFalseSnippet2ET, R.id.trueFalseSnippet3ET, R.id.trueFalseSnippet4ET, R.id.trueFalseSnippet5ET,
                R.id.mcqSnippet1ET, R.id.mcqSnippet2ET, R.id.mcqSnippet3ET, R.id.mcqSnippet4ET, R.id.mcqSnippet5ET
        };

        for (int id : ids) {
            allInputs.add(findViewById(id));
        }
    }

    private void setupValidation() {
        for (EditText et : allInputs) {
            et.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    checkAllFieldsFilled();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    private void checkAllFieldsFilled() {
        for (EditText et : allInputs) {
            if (et.getText().toString().trim().isEmpty()) {
                createBtn.setEnabled(false);
                return;
            }
        }
        createBtn.setEnabled(true);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}