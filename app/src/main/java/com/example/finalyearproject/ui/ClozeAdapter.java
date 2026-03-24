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

public class ClozeAdapter extends RecyclerView.Adapter<ClozeAdapter.ViewHolder> {

    private final List<StudyPackResponse.ClozeQuestion> items;

    public ClozeAdapter(List<StudyPackResponse.ClozeQuestion> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cloze, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.ClozeQuestion item = items.get(position);
        holder.sentenceTv.setText(item.sentenceWithBlank);
        holder.answerTv.setText("Answer: " + item.answer);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView sentenceTv;
        TextView answerTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            sentenceTv = itemView.findViewById(R.id.clozeSentenceTV);
            answerTv = itemView.findViewById(R.id.clozeAnswerTV);
        }
    }
}