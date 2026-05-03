package com.example.finalyearproject.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;
import com.example.finalyearproject.data.StudySessionStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClozePagerAdapter extends RecyclerView.Adapter<ClozePagerAdapter.ViewHolder> {

    private final List<StudyPackResponse.ClozeQuestion> items;
    // position -> selected option index
    private final Map<Integer, Integer> selectedAnswers = new HashMap<>();
    // position -> generated/displayed options for consistency
    private final Map<Integer, List<String>> optionsCache = new HashMap<>();
    private final StudySessionStats sessionStats;
    private final OnStatsChangedListener statsChangedListener;

    public ClozePagerAdapter(List<StudyPackResponse.ClozeQuestion> items, StudySessionStats sessionStats, OnStatsChangedListener statsChangedListener) {
        this.items = items;
        this.sessionStats = sessionStats;
        this.statsChangedListener = statsChangedListener;
    }

    public interface OnStatsChangedListener {
        void onStatsChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cloze_page, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.ClozeQuestion item = items.get(position);

        holder.questionTv.setText(item.sentenceWithBlank != null ? item.sentenceWithBlank : "");

        List<String> options = getOptionsForPosition(position, item);

        bindOptionButton(holder.option1Btn, item, options, 0, position);
        bindOptionButton(holder.option2Btn, item, options, 1, position);
        bindOptionButton(holder.option3Btn, item, options, 2, position);
        bindOptionButton(holder.option4Btn, item, options, 3, position);

        boolean alreadyAnswered = selectedAnswers.containsKey(position) || sessionStats.isClozeAnswered(position);

        if (alreadyAnswered) {
            Integer selectedIndex = sessionStats.getClozeAnswer(position);
            if (selectedIndex == null) return;
            String selectedText = getOptionText(options, selectedIndex);
            String correctAnswer = item.answer != null ? item.answer : "";
            boolean correct = correctAnswer.equalsIgnoreCase(selectedText != null ? selectedText : "");

            holder.resultTv.setVisibility(View.VISIBLE);
            holder.answerTv.setVisibility(View.VISIBLE);

            holder.resultTv.setText(correct ? "🟢 Correct" : "🔴 Incorrect");
            holder.answerTv.setText("Correct answer: " + correctAnswer);

            setButtonsEnabled(holder, false);

            highlightButton(holder.option1Btn, options, 0, selectedIndex, correctAnswer);
            highlightButton(holder.option2Btn, options, 1, selectedIndex, correctAnswer);
            highlightButton(holder.option3Btn, options, 2, selectedIndex, correctAnswer);
            highlightButton(holder.option4Btn, options, 3, selectedIndex, correctAnswer);

            holder.itemView.setOnClickListener(v -> {
                selectedAnswers.remove(position);
                notifyItemChanged(position);
            });

        } else {
            holder.resultTv.setVisibility(View.GONE);
            holder.answerTv.setVisibility(View.GONE);

            resetButtonStyle(holder.option1Btn);
            resetButtonStyle(holder.option2Btn);
            resetButtonStyle(holder.option3Btn);
            resetButtonStyle(holder.option4Btn);

            setButtonsEnabled(holder, true);
            holder.itemView.setOnClickListener(null);
        }
    }

    private List<String> getOptionsForPosition(int position, StudyPackResponse.ClozeQuestion item) {
        if (optionsCache.containsKey(position)) {
            return optionsCache.get(position);
        }

        List<String> options = new ArrayList<>();

        if (item.choices != null) {
            for (String choice : item.choices) {
                if (choice != null && !choice.isBlank()) {
                    options.add(choice.trim());
                }
            }
        }

        String correctAnswer = item.answer != null ? item.answer.trim() : "";

        Set<String> unique = new LinkedHashSet<>(options);

        if (!correctAnswer.isBlank()) {
            unique.add(correctAnswer);
        }

        // Fallback distractors if backend did not provide enough choices
        if (unique.size() < 4) {
            addFallbackDistractors(unique, correctAnswer);
        }

        List<String> finalOptions = new ArrayList<>(unique);

        // Trim to 4 if somehow more than 4
        if (finalOptions.size() > 4) {
            finalOptions = new ArrayList<>(finalOptions.subList(0, 4));
        }

        // Shuffle so correct answer is not always last
        Collections.shuffle(finalOptions);

        optionsCache.put(position, finalOptions);
        return finalOptions;
    }

    private void addFallbackDistractors(Set<String> unique, String correctAnswer) {
        if (correctAnswer.isBlank()) {
            unique.add("Option A");
            unique.add("Option B");
            unique.add("Option C");
            unique.add("Option D");
            return;
        }

        unique.add(correctAnswer + " system");
        unique.add(correctAnswer + " process");
        unique.add(correctAnswer + " strategy");
        unique.add("Personnel management");
        unique.add("Operations management");
        unique.add("Administrative planning");
    }

    private void bindOptionButton(Button button, StudyPackResponse.ClozeQuestion item, List<String> options,
                                  int optionIndex, int position) {

        String text = getOptionText(options, optionIndex);

        if (text == null || text.isBlank()) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(text);

        resetButtonStyle(button);

        if (selectedAnswers.containsKey(position) || sessionStats.isClozeAnswered(position)) {
            button.setOnClickListener(null);
            return;
        }

        button.setOnClickListener(v -> {
            sessionStats.setClozeAnswer(position, optionIndex);
            selectedAnswers.put(position, optionIndex);

            String selectedText = getOptionText(options, optionIndex);
            String correctAnswer = item.answer != null ? item.answer : "";
            boolean isCorrect = correctAnswer.equalsIgnoreCase(selectedText != null ? selectedText : "");

            if (sessionStats.markClozeAnswered(position)) {
                if (isCorrect) {
                    sessionStats.getClozeStats().recordCorrect();
                } else {
                    sessionStats.getClozeStats().recordIncorrect();
                }

                if (statsChangedListener != null) {
                    statsChangedListener.onStatsChanged();
                }
            }
            notifyItemChanged(position);
        });
    }

    private String getOptionText(List<String> options, int index) {
        if (options == null || index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index);
    }

    private void setButtonsEnabled(ViewHolder holder, boolean enabled) {
        holder.option1Btn.setEnabled(enabled);
        holder.option2Btn.setEnabled(enabled);
        holder.option3Btn.setEnabled(enabled);
        holder.option4Btn.setEnabled(enabled);
    }

    private void highlightButton(Button button, List<String> options, int optionIndex,
                                 int selectedIndex, String correctAnswer) {

        String optionText = getOptionText(options, optionIndex);
        if (optionText == null) {
            return;
        }

        if (optionText.equalsIgnoreCase(correctAnswer)) {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        } else if (optionIndex == selectedIndex) {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BDBDBD")));
        }
    }

    private void resetButtonStyle(Button button) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#66BB6A")));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView questionTv;
        Button option1Btn;
        Button option2Btn;
        Button option3Btn;
        Button option4Btn;
        TextView resultTv;
        TextView answerTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTv = itemView.findViewById(R.id.clozeQuestionTV);
            option1Btn = itemView.findViewById(R.id.clozeOption1Btn);
            option2Btn = itemView.findViewById(R.id.clozeOption2Btn);
            option3Btn = itemView.findViewById(R.id.clozeOption3Btn);
            option4Btn = itemView.findViewById(R.id.clozeOption4Btn);
            resultTv = itemView.findViewById(R.id.clozeResultTV);
            answerTv = itemView.findViewById(R.id.clozeAnswerTV);
        }
    }
}