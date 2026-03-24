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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClozeAdapter extends RecyclerView.Adapter<ClozeAdapter.ViewHolder> {

    private final List<StudyPackResponse.ClozeQuestion> items;
    private final Set<Integer> revealedPositions = new HashSet<>();


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

        boolean revealed = revealedPositions.contains(position);

        if (revealed) {
            holder.answerTv.setVisibility(View.VISIBLE);
            holder.hintTv.setVisibility(View.VISIBLE);
            holder.revealBtn.setEnabled(false);
            holder.revealBtn.setText("Answer Revealed");

            holder.itemView.setOnClickListener(v -> {
                revealedPositions.remove(position);
                notifyItemChanged(position);
            });
        } else {
            holder.answerTv.setVisibility(View.GONE);
            holder.hintTv.setVisibility(View.GONE);
            holder.revealBtn.setEnabled(true);
            holder.revealBtn.setText("Reveal Answer");

            holder.revealBtn.setOnClickListener(v -> {
                revealedPositions.add(position);
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
        TextView sentenceTv;
        Button revealBtn;
        TextView answerTv;
        TextView hintTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            sentenceTv = itemView.findViewById(R.id.clozeSentenceTV);
            revealBtn = itemView.findViewById(R.id.clozeRevealBtn);
            answerTv = itemView.findViewById(R.id.clozeAnswerTV);
            hintTv = itemView.findViewById(R.id.clozeHintTV);
        }
    }
}