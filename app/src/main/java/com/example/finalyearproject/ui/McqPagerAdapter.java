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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McqPagerAdapter extends RecyclerView.Adapter<McqPagerAdapter.ViewHolder> {

    private final List<StudyPackResponse.McqQuestion> items;
    private final StudySessionStats sessionStats;
    private final OnStatsChangedListener statsChangedListener;

    public McqPagerAdapter(List<StudyPackResponse.McqQuestion> items, StudySessionStats sessionStats, OnStatsChangedListener statsChangedListener) {
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
                .inflate(R.layout.item_mcq_page, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.McqQuestion item = items.get(position);

        holder.questionTv.setText(item.question != null ? item.question : "");

        bindOptionButton(holder.option1Btn, item, 0, position);
        bindOptionButton(holder.option2Btn, item, 1, position);
        bindOptionButton(holder.option3Btn, item, 2, position);
        bindOptionButton(holder.option4Btn, item, 3, position);

        boolean alreadyAnswered = sessionStats.isMcqAnswered(position);

        if (alreadyAnswered) {
            Integer selectedIndex = sessionStats.getMcqAnswer(position);
            if (selectedIndex == null) return;
            boolean correct = item.correctIndex >= 0 && selectedIndex == item.correctIndex;

            holder.resultTv.setVisibility(View.VISIBLE);
            holder.answerTv.setVisibility(View.VISIBLE);
            holder.explanationTv.setVisibility(View.VISIBLE);

            holder.resultTv.setText(correct ? "🟢 Correct" : "🔴 Incorrect");

            String correctAnswer = getOptionText(item, item.correctIndex);
            holder.answerTv.setText("Correct answer: " + (correctAnswer != null ? correctAnswer : ""));
            holder.explanationTv.setText(item.explanation != null ? item.explanation : "");

            setButtonsEnabled(holder, false);

            holder.itemView.setOnClickListener(null);

            highlightButton(holder.option1Btn, item, 0, selectedIndex);
            highlightButton(holder.option2Btn, item, 1, selectedIndex);
            highlightButton(holder.option3Btn, item, 2, selectedIndex);
            highlightButton(holder.option4Btn, item, 3, selectedIndex);


        } else {
            holder.resultTv.setVisibility(View.GONE);
            holder.answerTv.setVisibility(View.GONE);
            holder.explanationTv.setVisibility(View.GONE);

            setButtonsEnabled(holder, true);
            holder.itemView.setOnClickListener(null);
        }
    }

    private void bindOptionButton(Button button,
                                  StudyPackResponse.McqQuestion item,
                                  int optionIndex,
                                  int position) {

        String text = getOptionText(item, optionIndex);

        if (text == null || text.isBlank()) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(text);

        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#66BB6A")) // your default green
        );

        button.setOnClickListener(v -> {
            sessionStats.setMcqAnswer(position, optionIndex);

            boolean isCorrect = item.correctIndex >= 0 && optionIndex == item.correctIndex;

            if (sessionStats.markMcqAnswered(position)) {
                if (isCorrect) {
                    sessionStats.getMcqStats().recordCorrect();
                } else {
                    sessionStats.getMcqStats().recordIncorrect();
                }

                if (statsChangedListener != null) {
                    statsChangedListener.onStatsChanged();
                }
            }

            notifyItemChanged(position);
        });
    }

    private String getOptionText(StudyPackResponse.McqQuestion item, int index) {
        if (item.options == null || index < 0 || index >= item.options.size()) {
            return null;
        }
        return item.options.get(index);
    }

    private void setButtonsEnabled(ViewHolder holder, boolean enabled) {
        holder.option1Btn.setEnabled(enabled);
        holder.option2Btn.setEnabled(enabled);
        holder.option3Btn.setEnabled(enabled);
        holder.option4Btn.setEnabled(enabled);
    }

    private void highlightButton(Button button,
                                 StudyPackResponse.McqQuestion item,
                                 int optionIndex,
                                 int selectedIndex) {

        int correctIndex = item.correctIndex;

        if (optionIndex == correctIndex) {
            // ✅ correct answer → green
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            );
        } else if (optionIndex == selectedIndex) {
            // ❌ wrong selected → red
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#F44336"))
            );
        } else {
            // neutral
            button.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
            );
        }
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
        TextView explanationTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTv = itemView.findViewById(R.id.mcqQuestionTV);
            option1Btn = itemView.findViewById(R.id.mcqOption1Btn);
            option2Btn = itemView.findViewById(R.id.mcqOption2Btn);
            option3Btn = itemView.findViewById(R.id.mcqOption3Btn);
            option4Btn = itemView.findViewById(R.id.mcqOption4Btn);
            resultTv = itemView.findViewById(R.id.mcqResultTV);
            answerTv = itemView.findViewById(R.id.mcqAnswerTV);
            explanationTv = itemView.findViewById(R.id.mcqExplanationTV);
        }
    }
}