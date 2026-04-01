package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.content.res.ColorStateList;

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
    private RecyclerView recyclerView;
    private Button btnFlashcards;
    private Button btnCloze;
    private Button btnTrueFalse;
    private Button btnMcq;
    private Button btnMatching;
    private ApiService api;
    private String historyId;
    private String fileName;
    private StudyPackResponse currentPack;
    private ViewPager2 studyViewPager;
    private TextView pagerCounterTv;
    private FlashcardPagerAdapter flashcardPagerAdapter;
    private ClozePagerAdapter clozePagerAdapter;
    private McqPagerAdapter mcqPagerAdapter;
    private TrueFalsePagerAdapter trueFalsePagerAdapter;
    private boolean pagerCallbackRegistered = false;
    private String currentPagerMode = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_pack);

        titleTv = findViewById(R.id.studyPackTitleTV);
        statusTv = findViewById(R.id.studyPackStatusTV);
        progressBar = findViewById(R.id.studyPackProgress);
        recyclerView = findViewById(R.id.studyPackRecyclerView);
        btnFlashcards = findViewById(R.id.btnFlashcards);
        btnCloze = findViewById(R.id.btnCloze);
        btnTrueFalse = findViewById(R.id.btnTrueFalse);
        btnMcq = findViewById(R.id.btnMcq);
        btnMatching = findViewById(R.id.btnMatching);
        studyViewPager = findViewById(R.id.studyViewPager);
        pagerCounterTv = findViewById(R.id.pagerCounterTV);

        if (!pagerCallbackRegistered) {
            studyViewPager.registerOnPageChangeCallback(pageChangeCallback);
            pagerCallbackRegistered = true;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        api = RetrofitClient.getApiService();

        historyId = getIntent().getStringExtra("historyId");
        fileName = getIntent().getStringExtra("fileName");

        titleTv.setText(fileName != null ? fileName : "Study Pack");

        setButtonsEnabled(false);

        btnFlashcards.setOnClickListener(v -> showFlashcards());
        btnCloze.setOnClickListener(v -> showCloze());
        btnTrueFalse.setOnClickListener(v -> showTrueFalse());
        btnMcq.setOnClickListener(v -> showMcq());
        btnMatching.setOnClickListener(v -> showMatching());


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
                    currentPack = response.body();
                    statusTv.setText("Study Pack Loaded");
                    setButtonsEnabled(true);
                    showFlashcards();
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

    private void showFlashcards() {
        updateSelectedTab(btnFlashcards);
        currentPagerMode = "flashcards";

        if (currentPack == null || currentPack.flashcards == null || currentPack.flashcards.isEmpty()) {
            statusTv.setText("No flashcards available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Card 0 of 0");
            return;
        }

        statusTv.setText("Flashcards • " + currentPack.flashcards.size() + " cards");
        showPagerMode();

        flashcardPagerAdapter = new FlashcardPagerAdapter(currentPack.flashcards);
        studyViewPager.setAdapter(flashcardPagerAdapter);
        studyViewPager.setCurrentItem(0, false);

        updatePagerCounter("Card", 0, currentPack.flashcards.size());
    }

    private void showCloze() {
        updateSelectedTab(btnCloze);
        currentPagerMode = "cloze";

        if (currentPack == null || currentPack.clozeQuestions == null || currentPack.clozeQuestions.isEmpty()) {
            statusTv.setText("No cloze questions available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }

        statusTv.setText("Cloze • " + currentPack.clozeQuestions.size() + " questions");
        showPagerMode();

        clozePagerAdapter = new ClozePagerAdapter(currentPack.clozeQuestions);
        studyViewPager.setAdapter(clozePagerAdapter);
        studyViewPager.setCurrentItem(0, false);

        updatePagerCounter("Question", 0, currentPack.clozeQuestions.size());
    }

    private void showTrueFalse() {
        updateSelectedTab(btnTrueFalse);
        currentPagerMode = "truefalse";
        if (currentPack == null || currentPack.trueFalseQuestions == null || currentPack.trueFalseQuestions.isEmpty()) {
            statusTv.setText("No true/false questions available.");
            recyclerView.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }
        statusTv.setText("True/False: " + currentPack.trueFalseQuestions.size());
        showPagerMode();

        trueFalsePagerAdapter = new TrueFalsePagerAdapter(currentPack.trueFalseQuestions);
        studyViewPager.setAdapter(trueFalsePagerAdapter);
        studyViewPager.setCurrentItem(0, false);

        updatePagerCounter("Question", 0, currentPack.trueFalseQuestions.size());
    }

    private void showMcq() {
        updateSelectedTab(btnMcq);
        currentPagerMode = "mcq";

        if (currentPack == null || currentPack.mcqQuestions == null || currentPack.mcqQuestions.isEmpty()) {
            statusTv.setText("No MCQ questions available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }

        statusTv.setText("MCQ • " + currentPack.mcqQuestions.size() + " questions");
        showPagerMode();

        mcqPagerAdapter = new McqPagerAdapter(currentPack.mcqQuestions);
        studyViewPager.setAdapter(mcqPagerAdapter);
        studyViewPager.setCurrentItem(0, false);

        updatePagerCounter("Question", 0, currentPack.mcqQuestions.size());
    }

    private void showMatching() {
        updateSelectedTab(btnMatching);
        showRecyclerMode();
        if (currentPack == null || currentPack.matchingPairs == null || currentPack.matchingPairs.isEmpty()) {
            statusTv.setText("No matching pairs available.");
            recyclerView.setAdapter(null);
            return;
        }
        statusTv.setText("Matching: " + currentPack.matchingPairs.size());
        recyclerView.setAdapter(new MatchingAdapter(currentPack.matchingPairs));
    }

    private void setButtonsEnabled(boolean enabled) {
        btnFlashcards.setEnabled(enabled);
        btnCloze.setEnabled(enabled);
        btnTrueFalse.setEnabled(enabled);
        btnMcq.setEnabled(enabled);
        btnMatching.setEnabled(enabled);
    }

    private void updateSelectedTab(Button selectedButton) {
        Button[] buttons = {btnFlashcards, btnCloze, btnTrueFalse, btnMcq, btnMatching};

        for (Button button : buttons) {
            if (button == selectedButton) {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        getResources().getColor(R.color.study_tab_selected)
                ));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        getResources().getColor(R.color.study_tab_unselected)
                ));
            }
        }
    }

    private void showPagerMode() {
        studyViewPager.setVisibility(View.VISIBLE);
        pagerCounterTv.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showRecyclerMode() {
        studyViewPager.setVisibility(View.GONE);
        pagerCounterTv.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void updatePagerCounter(String label, int position, int total) {
        pagerCounterTv.setText(label + " " + (position + 1) + " of " + total);
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);

            if ("flashcards".equals(currentPagerMode) && currentPack != null && currentPack.flashcards != null) {
                updatePagerCounter("Card", position, currentPack.flashcards.size());
            } else if ("cloze".equals(currentPagerMode) && currentPack != null && currentPack.clozeQuestions != null) {
                updatePagerCounter("Question", position, currentPack.clozeQuestions.size());
            } else if ("mcq".equals(currentPagerMode) && currentPack != null && currentPack.mcqQuestions != null) {
                updatePagerCounter("Question", position, currentPack.mcqQuestions.size());
            } else if ("truefalse".equals(currentPagerMode) && currentPack != null && currentPack.trueFalseQuestions != null) {
                updatePagerCounter("Question", position, currentPack.trueFalseQuestions.size());
            }
        }
    };

    private String getLoggedInEmail() {
        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }
}