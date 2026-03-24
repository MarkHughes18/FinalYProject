package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.List;

public class McqAdapter extends RecyclerView.Adapter<McqAdapter.ViewHolder> {

    private final List<StudyPackResponse.McqQuestion> items;

    public McqAdapter(List<StudyPackResponse.McqQuestion> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mcq, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.McqQuestion item = items.get(position);

        holder.questionTv.setText(item.question);

        StringBuilder sb = new StringBuilder();
        if (item.options != null) {
            for (int i = 0; i < item.options.size(); i++) {
                sb.append(i + 1).append(". ").append(item.options.get(i)).append("\n");
            }
        }

        holder.optionsTv.setText(sb.toString().trim());

        String correct = "";
        if (item.options != null && item.correctIndex >= 0 && item.correctIndex < item.options.size()) {
            correct = item.options.get(item.correctIndex);
        }

        holder.answerTv.setText("Answer: " + correct);
        holder.explanationTv.setText(item.explanation);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView questionTv;
        TextView optionsTv;
        TextView answerTv;
        TextView explanationTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTv = itemView.findViewById(R.id.mcqQuestionTV);
            optionsTv = itemView.findViewById(R.id.mcqOptionsTV);
            answerTv = itemView.findViewById(R.id.mcqAnswerTV);
            explanationTv = itemView.findViewById(R.id.mcqExplanationTV);
        }
    }
}