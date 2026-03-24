package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrueFalseAdapter extends RecyclerView.Adapter<TrueFalseAdapter.ViewHolder> {

    private final List<StudyPackResponse.TrueFalseQuestion> items;
    // position -> user's selected answer
    private final Map<Integer, Boolean> selectedAnswers = new HashMap<>();


    public TrueFalseAdapter(List<StudyPackResponse.TrueFalseQuestion> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_true_false, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.TrueFalseQuestion item = items.get(position);
        holder.statementTv.setText(item.statement);

        if (selectedAnswers.containsKey(position)) {
            boolean selected = selectedAnswers.get(position);
            boolean correct = selected == item.answer;

            holder.resultTv.setVisibility(View.VISIBLE);
            holder.answerTv.setVisibility(View.VISIBLE);
            holder.explanationTv.setVisibility(View.VISIBLE);

            holder.resultTv.setText(correct ? "Correct" : "Incorrect");
            holder.answerTv.setText(item.answer ? "Answer: True" : "Answer: False");
            holder.explanationTv.setText(item.explanation != null ? item.explanation : "");

            holder.trueBtn.setEnabled(false);
            holder.falseBtn.setEnabled(false);
            holder.trueBtn.setOnClickListener(null);
            holder.falseBtn.setOnClickListener(null);
            holder.itemView.setOnClickListener(v -> {
                selectedAnswers.remove(position);
                notifyItemChanged(position);
            });
        } else {
            holder.resultTv.setVisibility(View.GONE);
            holder.answerTv.setVisibility(View.GONE);
            holder.explanationTv.setVisibility(View.GONE);

            holder.trueBtn.setEnabled(true);
            holder.falseBtn.setEnabled(true);

            holder.trueBtn.setOnClickListener(v -> {
                selectedAnswers.put(position, true);
                notifyItemChanged(position);
            });

            holder.falseBtn.setOnClickListener(v -> {
                selectedAnswers.put(position, false);
                notifyItemChanged(position);
            });

            holder.itemView.setOnClickListener(null);
        }
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