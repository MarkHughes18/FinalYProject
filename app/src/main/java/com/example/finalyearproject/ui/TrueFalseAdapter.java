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

public class TrueFalseAdapter extends RecyclerView.Adapter<TrueFalseAdapter.ViewHolder> {

    private final List<StudyPackResponse.TrueFalseQuestion> items;

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
        holder.answerTv.setText(item.answer ? "Answer: True" : "Answer: False");
        holder.explanationTv.setText(item.explanation);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView statementTv;
        TextView answerTv;
        TextView explanationTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statementTv = itemView.findViewById(R.id.tfStatementTV);
            answerTv = itemView.findViewById(R.id.tfAnswerTV);
            explanationTv = itemView.findViewById(R.id.tfExplanationTV);
        }
    }
}