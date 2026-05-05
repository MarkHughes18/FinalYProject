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

import java.util.List;

public class TrueFalsePagerAdapter extends RecyclerView.Adapter<TrueFalsePagerAdapter.ViewHolder> {

    private final List<StudyPackResponse.TrueFalseQuestion> items;
    private final StudySessionStats sessionStats;
    private final OnStatsChangedListener statsChangedListener;

    public TrueFalsePagerAdapter(List<StudyPackResponse.TrueFalseQuestion> items, StudySessionStats sessionStats, OnStatsChangedListener statsChangedListener) {
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
                .inflate(R.layout.item_true_false_page, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.TrueFalseQuestion item = items.get(position);

        holder.statementTv.setText(item.statement != null ? item.statement : "");

        boolean alreadyAnswered = sessionStats.isTrueFalseAnswered(position);

        if (alreadyAnswered) {
            Boolean selectedValue = sessionStats.getTrueFalseAnswer(position);
            if (selectedValue == null) return;
            boolean selected = selectedValue;
            boolean correct = selected == item.answer;

            holder.resultTv.setVisibility(View.VISIBLE);
            holder.answerTv.setVisibility(View.VISIBLE);
            holder.explanationTv.setVisibility(View.VISIBLE);

            holder.resultTv.setText(correct ? "🟢 Correct" : "🔴 Incorrect");
            holder.answerTv.setText(item.answer ? "Correct answer: True" : "Correct answer: False");
            holder.explanationTv.setText(item.explanation != null ? item.explanation : "");

            holder.trueBtn.setEnabled(false);
            holder.falseBtn.setEnabled(false);

            highlightButtons(holder, selected, item.answer);

            holder.itemView.setOnClickListener(null);

        } else {
            holder.resultTv.setVisibility(View.GONE);
            holder.answerTv.setVisibility(View.GONE);
            holder.explanationTv.setVisibility(View.GONE);

            holder.trueBtn.setEnabled(true);
            holder.falseBtn.setEnabled(true);

            resetButtonStyle(holder.trueBtn);
            resetButtonStyle(holder.falseBtn);

            holder.trueBtn.setOnClickListener(v -> {
                sessionStats.setTrueFalseAnswer(position, true);

                boolean isCorrect = item.answer;

                if (sessionStats.markTrueFalseAnswered(position)) {
                    if (isCorrect) {
                        sessionStats.getTrueFalseStats().recordCorrect();
                    } else {
                        sessionStats.getTrueFalseStats().recordIncorrect();
                    }

                    if (statsChangedListener != null) {
                        statsChangedListener.onStatsChanged();
                    }
                }
                notifyItemChanged(position);
            });

            holder.falseBtn.setOnClickListener(v -> {
                sessionStats.setTrueFalseAnswer(position, false);

                boolean isCorrect = !item.answer;

                if (sessionStats.markTrueFalseAnswered(position)) {
                    if (isCorrect) {
                        sessionStats.getTrueFalseStats().recordCorrect();
                    } else {
                        sessionStats.getTrueFalseStats().recordIncorrect();
                    }

                    if (statsChangedListener != null) {
                        statsChangedListener.onStatsChanged();
                    }
                }
                notifyItemChanged(position);
            });

            holder.itemView.setOnClickListener(null);
        }
    }

    private void highlightButtons(ViewHolder holder, boolean selected, boolean correctAnswer) {
        if (selected == correctAnswer) {
            // User chose correctly
            if (correctAnswer) {
                holder.trueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                holder.falseBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BDBDBD")));
            } else {
                holder.falseBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                holder.trueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#BDBDBD")));
            }
        } else {
            // User chose incorrectly
            if (selected) {
                // User picked True, but False is correct
                holder.trueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
                holder.falseBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            } else {
                // User picked False, but True is correct
                holder.falseBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
                holder.trueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            }
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
        TextView statementTv;
        Button trueBtn;
        Button falseBtn;
        TextView resultTv;
        TextView answerTv;
        TextView explanationTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statementTv = itemView.findViewById(R.id.tfStatementTV);
            trueBtn = itemView.findViewById(R.id.tfTrueBtn);
            falseBtn = itemView.findViewById(R.id.tfFalseBtn);
            resultTv = itemView.findViewById(R.id.tfResultTV);
            answerTv = itemView.findViewById(R.id.tfAnswerTV);
            explanationTv = itemView.findViewById(R.id.tfExplanationTV);
        }
    }
}