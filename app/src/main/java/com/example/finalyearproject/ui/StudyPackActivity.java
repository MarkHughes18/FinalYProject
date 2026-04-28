package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.content.res.ColorStateList;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.ContinueLearningPrefs;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.data.StudyPackResponse;
import com.example.finalyearproject.data.StudySessionStats;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.*;

public class StudyPackActivity extends AppCompatActivity {

    private TextView statusTv;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private Button btnFlashcards;
    private Button btnCloze;
    private Button btnTrueFalse;
    private Button btnMcq;
    private Button btnMatching;
    private Button toolbarRegenerateBtn;
    private ApiService api;
    private String historyId;
    private String fileName;
    private StudyPackResponse currentPack;
    private ViewPager2 studyViewPager;
    private TextView pagerCounterTv;
    private TextView statsAttemptedTv;
    private TextView statsCorrectTv;
    private TextView statsIncorrectTv;
    private TextView statsAccuracyTv;
    private View statsBarLayout;
    private String currentMode = "FLASHCARDS";
    private int currentPosition = 0;
    private String currentHistoryId;
    private String currentFileName;
    private FlashcardPagerAdapter flashcardPagerAdapter;
    private ClozePagerAdapter clozePagerAdapter;
    private McqPagerAdapter mcqPagerAdapter;
    private TrueFalsePagerAdapter trueFalsePagerAdapter;
    private boolean pagerCallbackRegistered = false;
    private boolean sessionStatsSaved = false;
    private LinearLayout matchingGameContainer;
    private TextView matchingProgressTv;
    private RecyclerView matchingUnmatchedRecyclerView;
    private RecyclerView matchingDoneRecyclerView;
    private MatchingGameAdapter matchingGameAdapter;
    private MatchingDoneAdapter matchingDoneAdapter;
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageButton toolbarBackBtn;
    private final List<MatchingGameItem> matchedItems = new ArrayList<>();
    private final StudySessionStats sessionStats = new StudySessionStats();
    private String currentPagerMode = "";
    private String resumeMode;
    private int resumePosition = 0;
    private boolean hasAppliedResumeState = false;
    private BottomSheetDialog generatingDialog;
    private TextView generatingTitleTv;
    private TextView generatingMessageTv;
    private Handler loaderHandler = new Handler(Looper.getMainLooper());
    private Runnable loaderRunnable;
    private boolean fromContinueLearning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_pack);

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
        matchingGameContainer = findViewById(R.id.matchingGameContainer);
        matchingProgressTv = findViewById(R.id.matchingProgressTV);
        matchingUnmatchedRecyclerView = findViewById(R.id.matchingUnmatchedRecyclerView);
        matchingDoneRecyclerView = findViewById(R.id.matchingDoneRecyclerView);
        statsAttemptedTv = findViewById(R.id.statsAttemptedTv);
        statsCorrectTv = findViewById(R.id.statsCorrectTv);
        statsIncorrectTv = findViewById(R.id.statsIncorrectTv);
        statsAccuracyTv = findViewById(R.id.statsAccuracyTv);
        statsBarLayout = findViewById(R.id.statsBarLayout);
        toolbar = findViewById(R.id.studyToolbar);
        toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarBackBtn = findViewById(R.id.toolbarBackBtn);
        toolbarRegenerateBtn = findViewById(R.id.toolbarRegenerateBtn);

        api = RetrofitClient.getApiService();

        historyId = getIntent().getStringExtra("historyId");
        fileName = getIntent().getStringExtra("fileName");
        currentHistoryId = getIntent().getStringExtra("historyId");
        currentFileName = getIntent().getStringExtra("fileName");
        resumeMode = getIntent().getStringExtra("resumeMode");
        resumePosition = getIntent().getIntExtra("resumePosition", 0);
        fromContinueLearning = getIntent().getBooleanExtra("fromContinueLearning", false);

        String trimmedFileName = fileName != null ? fileName.trim() : "";

        if (trimmedFileName.length() > 18) {
            trimmedFileName = trimmedFileName.substring(0, 15) + "...";
        }

        String displayTitle = !trimmedFileName.isBlank()
                ? "Study Pack • " + trimmedFileName
                : "Study Pack";

        toolbarTitle.setText(displayTitle);
        toolbarBackBtn.setOnClickListener(v -> finish());

        matchingUnmatchedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        matchingDoneRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (!pagerCallbackRegistered) {
            studyViewPager.registerOnPageChangeCallback(pageChangeCallback);
            pagerCallbackRegistered = true;
        }

        setButtonsEnabled(false);
        toolbarRegenerateBtn.setEnabled(false);

        btnFlashcards.setOnClickListener(v -> showFlashcards());
        btnCloze.setOnClickListener(v -> showCloze());
        btnTrueFalse.setOnClickListener(v -> showTrueFalse());
        btnMcq.setOnClickListener(v -> showMcq());
        btnMatching.setOnClickListener(v -> showMatching());
        toolbarRegenerateBtn.setOnClickListener(v -> showRegenerateConfirmation());


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
        setButtonsEnabled(false);
        toolbarRegenerateBtn.setEnabled(false);

        showGeneratingDialog( "Generating Your Study Pack \uD83D\uDCD6");

        api.getStudyPack(historyId, email).enqueue(new Callback<StudyPackResponse>() {
            @Override
            public void onResponse(Call<StudyPackResponse> call, Response<StudyPackResponse> response) {
                progressBar.setVisibility(View.GONE);
                hideGeneratingDialog();

                if (response.isSuccessful() && response.body() != null) {
                    currentPack = response.body();
                    if (fromContinueLearning) {
                        ContinueLearningPrefs.ContinueLearningData data =
                                ContinueLearningPrefs.getContinueLearning(StudyPackActivity.this);

                        if (data != null && data.getStats() != null) {
                            StudySessionStats saved = data.getStats();

                            sessionStats.getFlashcardStats().setAttempted(saved.getFlashcardStats().getAttempted());
                            sessionStats.getFlashcardStats().setCorrect(saved.getFlashcardStats().getCorrect());
                            sessionStats.getFlashcardStats().setIncorrect(saved.getFlashcardStats().getIncorrect());

                            sessionStats.getClozeStats().setAttempted(saved.getClozeStats().getAttempted());
                            sessionStats.getClozeStats().setCorrect(saved.getClozeStats().getCorrect());
                            sessionStats.getClozeStats().setIncorrect(saved.getClozeStats().getIncorrect());

                            sessionStats.getTrueFalseStats().setAttempted(saved.getTrueFalseStats().getAttempted());
                            sessionStats.getTrueFalseStats().setCorrect(saved.getTrueFalseStats().getCorrect());
                            sessionStats.getTrueFalseStats().setIncorrect(saved.getTrueFalseStats().getIncorrect());

                            sessionStats.getMcqStats().setAttempted(saved.getMcqStats().getAttempted());
                            sessionStats.getMcqStats().setCorrect(saved.getMcqStats().getCorrect());
                            sessionStats.getMcqStats().setIncorrect(saved.getMcqStats().getIncorrect());

                            sessionStats.getMatchingStats().setAttempted(saved.getMatchingStats().getAttempted());
                            sessionStats.getMatchingStats().setCorrect(saved.getMatchingStats().getCorrect());
                            sessionStats.getMatchingStats().setIncorrect(saved.getMatchingStats().getIncorrect());
                        }
                    }
                    refreshStatsUi();

                    flashcardPagerAdapter = null;
                    clozePagerAdapter = null;
                    mcqPagerAdapter = null;
                    trueFalsePagerAdapter = null;
                    matchingGameAdapter = null;
                    matchingDoneAdapter = null;
                    matchedItems.clear();

                    statusTv.setText("Study Pack Loaded");
                    setButtonsEnabled(true);
                    toolbarRegenerateBtn.setEnabled(true);

                    if (!hasAppliedResumeState && resumeMode != null && !resumeMode.isBlank()) {
                        openResumeMode(resumeMode, resumePosition);
                        hasAppliedResumeState = true;
                    } else {
                        showFlashcards();
                    }

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
                hideGeneratingDialog();
                toolbarRegenerateBtn.setEnabled(true);
                statusTv.setText("Network error loading study pack.");
                Toast.makeText(StudyPackActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openResumeMode(String resumeMode, int resumePosition) {
        if (resumeMode == null || resumeMode.isBlank()) {
            showFlashcards();
            return;
        }

        switch (resumeMode) {
            case "FLASHCARDS":
                showFlashcards();
                if (currentPack != null && currentPack.flashcards != null && !currentPack.flashcards.isEmpty()) {
                    int safePosition = Math.min(Math.max(resumePosition, 0), currentPack.flashcards.size() - 1);
                    studyViewPager.setCurrentItem(safePosition, false);
                    currentPosition = safePosition;
                    saveContinueLearningState();
                }
                break;

            case "CLOZE":
                showCloze();
                if (currentPack != null && currentPack.clozeQuestions != null && !currentPack.clozeQuestions.isEmpty()) {
                    int safePosition = Math.min(Math.max(resumePosition, 0), currentPack.clozeQuestions.size() - 1);
                    studyViewPager.setCurrentItem(safePosition, false);
                    currentPosition = safePosition;
                    saveContinueLearningState();
                }
                break;

            case "TRUE_FALSE":
                showTrueFalse();
                if (currentPack != null && currentPack.trueFalseQuestions != null && !currentPack.trueFalseQuestions.isEmpty()) {
                    int safePosition = Math.min(Math.max(resumePosition, 0), currentPack.trueFalseQuestions.size() - 1);
                    studyViewPager.setCurrentItem(safePosition, false);
                    currentPosition = safePosition;
                    saveContinueLearningState();
                }
                break;

            case "MCQ":
                showMcq();
                if (currentPack != null && currentPack.mcqQuestions != null && !currentPack.mcqQuestions.isEmpty()) {
                    int safePosition = Math.min(Math.max(resumePosition, 0), currentPack.mcqQuestions.size() - 1);
                    studyViewPager.setCurrentItem(safePosition, false);
                    currentPosition = safePosition;
                    saveContinueLearningState();
                }
                break;

            case "MATCHING":
                showMatching();
                currentPosition = 0;
                saveContinueLearningState();
                break;

            default:
                showFlashcards();
                break;
        }
    }

    private void showFlashcards() {
        updateSelectedTab(btnFlashcards);
        currentPagerMode = "flashcards";
        currentMode = "FLASHCARDS";
        currentPosition = 0;
        refreshStatsUi();
        saveContinueLearningState();

        if (currentPack == null || currentPack.flashcards == null || currentPack.flashcards.isEmpty()) {
            statusTv.setText("No flashcards available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Card 0 of 0");
            return;
        }

        statusTv.setText("Flashcards • " + currentPack.flashcards.size() + " cards");
        showPagerMode();

        if (flashcardPagerAdapter == null){
            flashcardPagerAdapter = new FlashcardPagerAdapter(currentPack.flashcards,
                    sessionStats,
                    this::refreshStatsUi);
        }
        studyViewPager.setAdapter(flashcardPagerAdapter);
        studyViewPager.setCurrentItem(0, false);
        updatePagerCounter("Card", 0, currentPack.flashcards.size());
    }

    private void showCloze() {
        updateSelectedTab(btnCloze);
        currentPagerMode = "cloze";
        currentMode = "CLOZE";
        currentPosition = 0;
        refreshStatsUi();
        saveContinueLearningState();

        if (currentPack == null || currentPack.clozeQuestions == null || currentPack.clozeQuestions.isEmpty()) {
            statusTv.setText("No cloze questions available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }

        statusTv.setText("Cloze • " + currentPack.clozeQuestions.size() + " questions");
        showPagerMode();

        if (clozePagerAdapter == null){
            clozePagerAdapter = new ClozePagerAdapter(currentPack.clozeQuestions, sessionStats, this::refreshStatsUi);
        }
        studyViewPager.setAdapter(clozePagerAdapter);
        studyViewPager.setCurrentItem(0, false);
        updatePagerCounter("Question", 0, currentPack.clozeQuestions.size());
    }

    private void showTrueFalse() {
        updateSelectedTab(btnTrueFalse);
        currentPagerMode = "truefalse";
        currentMode = "TRUE_FALSE";
        currentPosition = 0;
        refreshStatsUi();
        saveContinueLearningState();

        if (currentPack == null || currentPack.trueFalseQuestions == null || currentPack.trueFalseQuestions.isEmpty()) {
            statusTv.setText("No true/false questions available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }
        statusTv.setText("True/False: " + currentPack.trueFalseQuestions.size());
        showPagerMode();

        if (trueFalsePagerAdapter == null){
            trueFalsePagerAdapter = new TrueFalsePagerAdapter(currentPack.trueFalseQuestions, sessionStats, this::refreshStatsUi);
        }
        studyViewPager.setAdapter(trueFalsePagerAdapter);
        studyViewPager.setCurrentItem(0, false);
        updatePagerCounter("Question", 0, currentPack.trueFalseQuestions.size());
    }

    private void showMcq() {
        updateSelectedTab(btnMcq);
        currentPagerMode = "mcq";
        currentMode = "MCQ";
        currentPosition = 0;
        refreshStatsUi();
        saveContinueLearningState();

        if (currentPack == null || currentPack.mcqQuestions == null || currentPack.mcqQuestions.isEmpty()) {
            statusTv.setText("No MCQ questions available.");
            studyViewPager.setAdapter(null);
            showPagerMode();
            pagerCounterTv.setText("Question 0 of 0");
            return;
        }

        statusTv.setText("MCQ • " + currentPack.mcqQuestions.size() + " questions");
        showPagerMode();

        if (mcqPagerAdapter == null){
            mcqPagerAdapter = new McqPagerAdapter(currentPack.mcqQuestions, sessionStats, this::refreshStatsUi);
        }
        studyViewPager.setAdapter(mcqPagerAdapter);
        studyViewPager.setCurrentItem(0, false);
        updatePagerCounter("Question", 0, currentPack.mcqQuestions.size());
    }

    private void showMatching() {
        updateSelectedTab(btnMatching);
        currentPagerMode = "matching";
        currentMode = "MATCHING";
        currentPosition = 0;
        refreshStatsUi();
        showMatchingGameMode();
        saveContinueLearningState();

        if (currentPack == null || currentPack.matchingPairs == null || currentPack.matchingPairs.isEmpty()) {
            statusTv.setText("No matching pairs available.");
            matchingUnmatchedRecyclerView.setAdapter(null);
            matchingDoneRecyclerView.setAdapter(null);
            matchingProgressTv.setText("Matched 0 of 0");
            return;
        }

        statusTv.setText("Matching • " + currentPack.matchingPairs.size() + " pairs");

        if (matchingGameAdapter == null || matchingDoneAdapter == null) {
            List<MatchingGameItem> leftItems = new ArrayList<>();
            List<MatchingGameItem> rightItems = new ArrayList<>();
            matchedItems.clear();

            for (int i = 0; i < currentPack.matchingPairs.size(); i++) {
                StudyPackResponse.MatchingPair pair = currentPack.matchingPairs.get(i);
                leftItems.add(new MatchingGameItem(pair.left, pair.right, i));
                rightItems.add(new MatchingGameItem(pair.left, pair.right, i));
            }

            Collections.shuffle(rightItems);

            matchingDoneAdapter = new MatchingDoneAdapter(matchedItems);
            matchingDoneRecyclerView.setAdapter(matchingDoneAdapter);

            matchingGameAdapter = new MatchingGameAdapter(
                    leftItems,
                    rightItems,
                    sessionStats,
                    new MatchingGameAdapter.Listener() {
                        @Override
                        public void onPairMatched(MatchingGameItem item) {
                            matchedItems.add(item);
                            matchingDoneAdapter.notifyItemInserted(matchedItems.size() - 1);
                            matchingProgressTv.setText("Matched " + matchedItems.size() + " of " + currentPack.matchingPairs.size());
                        }

                        @Override
                        public void onStatsChanged() {
                            refreshStatsUi();
                        }
                    }
            );
        }

        matchingUnmatchedRecyclerView.setAdapter(matchingGameAdapter);
        matchingDoneRecyclerView.setAdapter(matchingDoneAdapter);
        matchingProgressTv.setText("Matched " + matchedItems.size() + " of " + currentPack.matchingPairs.size());
    }

    private void showMatchingGameMode() {
        studyViewPager.setVisibility(View.GONE);
        pagerCounterTv.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        matchingGameContainer.setVisibility(View.VISIBLE);
    }

    private void showRegenerateConfirmation() {
        if (historyId == null || historyId.isBlank()) {
            Toast.makeText(this, "Missing study pack id.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Regenerate study pack?")
                .setMessage("This will create a new version of the study pack and reload it.")
                .setPositiveButton("Regenerate", (dialog, which) -> regenerateStudyPack())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void regenerateStudyPack() {
        String email = getLoggedInEmail();
        if (email == null) {
            Toast.makeText(this, "No logged in user found.", Toast.LENGTH_SHORT).show();
            return;
        }

        setButtonsEnabled(false);
        toolbarRegenerateBtn.setEnabled(false);
        toolbarRegenerateBtn.setText("Regenerating...");
        progressBar.setVisibility(View.VISIBLE);
        statusTv.setText("Regenerating study pack...");

        showGeneratingDialog("Refreshing Study Pack \uD83D\uDCD6");

        api.regenerateStudyPack(historyId, email).enqueue(new Callback<StudyPackResponse>() {
            @Override
            public void onResponse(Call<StudyPackResponse> call, Response<StudyPackResponse> response) {
                progressBar.setVisibility(View.GONE);
                hideGeneratingDialog();

                toolbarRegenerateBtn.setEnabled(true);
                toolbarRegenerateBtn.setText("Regenerate");

                if (response.isSuccessful() && response.body() != null) {
                    currentPack = response.body();

                    flashcardPagerAdapter = null;
                    clozePagerAdapter = null;
                    mcqPagerAdapter = null;
                    trueFalsePagerAdapter = null;
                    matchingGameAdapter = null;
                    matchingDoneAdapter = null;
                    matchedItems.clear();

                    sessionStatsSaved = false;
                    currentPosition = 0;
                    hasAppliedResumeState = false;

                    statusTv.setText("Study Pack Regenerated");
                    setButtonsEnabled(true);

                    showFlashcards();
                    Toast.makeText(StudyPackActivity.this, "Study pack regenerated", Toast.LENGTH_SHORT).show();
                } else if (response.code() == 403) {
                    statusTv.setText("You are not allowed to regenerate this study pack.");
                    setButtonsEnabled(true);
                } else if (response.code() == 404) {
                    statusTv.setText("Study pack source file not found.");
                    setButtonsEnabled(true);
                } else {
                    statusTv.setText("Failed to regenerate study pack. Code: " + response.code());
                    setButtonsEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<StudyPackResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                hideGeneratingDialog();
                toolbarRegenerateBtn.setEnabled(true);
                toolbarRegenerateBtn.setText("Regenerate");
                setButtonsEnabled(true);
                statusTv.setText("Network error regenerating study pack.");
                Toast.makeText(StudyPackActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideMatchingGameMode() {
        matchingGameContainer.setVisibility(View.GONE);
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

    private void saveContinueLearningState() {
        if (currentHistoryId == null || currentFileName == null || currentMode == null) {
            return;
        }

        ContinueLearningPrefs.saveContinueLearning(
                this,
                currentHistoryId,
                currentFileName,
                currentMode,
                currentPosition,
                System.currentTimeMillis(),
                sessionStats
        );
    }

    private void persistSessionStatsOnce() {
        if (sessionStatsSaved) {
            return;
        }

        if (sessionStats.getTotalAttempted() == 0
                && sessionStats.getTotalCorrect() == 0
                && sessionStats.getTotalIncorrect() == 0) {
            return;
        }

        com.example.finalyearproject.data.LearningStatsPrefs.mergeSessionStats(this, sessionStats);
        sessionStatsSaved = true;
    }

    private void refreshStatsUi() {
        if (statsBarLayout == null) {
            return;
        }

        statsBarLayout.setVisibility(View.VISIBLE);

        int attempted = 0;
        int correct = 0;
        int incorrect = 0;
        int accuracy = 0;

        if ("flashcards".equals(currentPagerMode)){
            attempted = sessionStats.getFlashcardStats().getAttempted();
            correct = sessionStats.getFlashcardStats().getCorrect();
            incorrect = sessionStats.getFlashcardStats().getIncorrect();
            accuracy = sessionStats.getFlashcardStats().getAccuracyPercent();

        }else if ("mcq".equals(currentPagerMode)){
            attempted = sessionStats.getMcqStats().getAttempted();
            correct = sessionStats.getMcqStats().getCorrect();
            incorrect = sessionStats.getMcqStats().getIncorrect();
            accuracy = sessionStats.getMcqStats().getAccuracyPercent();

        } else if ("cloze".equals(currentPagerMode)) {
            attempted = sessionStats.getClozeStats().getAttempted();
            correct = sessionStats.getClozeStats().getCorrect();
            incorrect = sessionStats.getClozeStats().getIncorrect();
            accuracy = sessionStats.getClozeStats().getAccuracyPercent();

        } else if ("truefalse".equals(currentPagerMode)) {
            attempted = sessionStats.getTrueFalseStats().getAttempted();
            correct = sessionStats.getTrueFalseStats().getCorrect();
            incorrect = sessionStats.getTrueFalseStats().getIncorrect();
            accuracy = sessionStats.getTrueFalseStats().getAccuracyPercent();

        } else if ("matching".equals(currentPagerMode)) {
            attempted = sessionStats.getMatchingStats().getAttempted();
            correct = sessionStats.getMatchingStats().getCorrect();
            incorrect = sessionStats.getMatchingStats().getIncorrect();
            accuracy = sessionStats.getMatchingStats().getAccuracyPercent();
        }

        if (accuracy >= 80) {
            statsAccuracyTv.setTextColor(Color.parseColor("#4CAF50")); // green
        } else if (accuracy >= 50) {
            statsAccuracyTv.setTextColor(Color.parseColor("#FFC107")); // amber
        } else {
            statsAccuracyTv.setTextColor(Color.parseColor("#E53935")); // red
        }

        statsAttemptedTv.setText(String.valueOf(attempted));
        statsCorrectTv.setText(String.valueOf(correct));
        statsIncorrectTv.setText(String.valueOf(incorrect));
        statsAccuracyTv.setText(accuracy + "%");
        saveContinueLearningState();
    }

    private void showPagerMode() {
        studyViewPager.setVisibility(View.VISIBLE);
        pagerCounterTv.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        matchingGameContainer.setVisibility(View.GONE);
    }

    private void showGeneratingDialog(String title) {
        if (generatingDialog != null && generatingDialog.isShowing()) {
            if (generatingTitleTv != null) {
                generatingTitleTv.setText(title);
            }
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.bottomsheet_generating_pack, null);

        generatingTitleTv = view.findViewById(R.id.generatingTitleTv);
        generatingMessageTv = view.findViewById(R.id.generatingMessageTv);

        generatingTitleTv.setText(title);

        generatingDialog = new BottomSheetDialog(this);
        generatingDialog.setContentView(view);
        generatingDialog.setCancelable(false);
        generatingDialog.setCanceledOnTouchOutside(false);
        generatingDialog.show();

        startLoaderMessages();
    }

    private void hideGeneratingDialog() {
        stopLoaderMessages();

        if (generatingDialog != null && generatingDialog.isShowing()) {
            generatingDialog.dismiss();
        }

        generatingDialog = null;
        generatingTitleTv = null;
        generatingMessageTv = null;
    }

    private void startLoaderMessages() {
        String[] messages = new String[]{
                "Analyzing your document...",
                "Extracting key concepts...",
                "Building flashcards...",
                "Creating quiz questions...",
                "Preparing your study pack..."
        };

        loaderRunnable = new Runnable() {
            int index = 0;

            @Override
            public void run() {
                if (generatingMessageTv != null) {
                    generatingMessageTv.setText(messages[index]);
                    index = (index + 1) % messages.length;
                    loaderHandler.postDelayed(this, 2000);
                }
            }
        };

        loaderHandler.post(loaderRunnable);
    }

    private void stopLoaderMessages() {
        if (loaderRunnable != null) {
            loaderHandler.removeCallbacks(loaderRunnable);
            loaderRunnable = null;
        }
    }

    private void showRecyclerMode() {
        studyViewPager.setVisibility(View.GONE);
        pagerCounterTv.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        matchingGameContainer.setVisibility(View.GONE);
    }

    private void updatePagerCounter(String label, int position, int total) {
        pagerCounterTv.setText(label + " " + (position + 1) + " of " + total);
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);

            currentPosition = position;
            saveContinueLearningState();

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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        persistSessionStatsOnce();
    }

    private String getLoggedInEmail() {
        SharedPreferences prefs = getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }
}