package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashcardAdapter extends RecyclerView.Adapter<FlashcardAdapter.ViewHolder> {

    private final List<StudyPackResponse.Flashcard> items;
    private final Set<Integer> revealedPositions = new HashSet<>();

    public FlashcardAdapter(List<StudyPackResponse.Flashcard> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.Flashcard item = items.get(position);

        holder.frontTv.setText(item.front);
        holder.backTv.setText(item.back);

        boolean revealed = revealedPositions.contains(position);
        bindRevealState(holder, revealed);

        holder.itemView.setOnClickListener(v -> {
            if (revealedPositions.contains(position)) {
                revealedPositions.remove(position);
                bindRevealState(holder, false);
            } else {
                revealedPositions.add(position);
                bindRevealState(holder, true);
            }
        });
    }

    private void bindRevealState(@NonNull ViewHolder holder, boolean revealed) {
        if (revealed) {
            holder.labelTv.setText("Tap to hide answer");
            holder.divider.setVisibility(View.VISIBLE);
            holder.backTv.setVisibility(View.VISIBLE);
        } else {
            holder.labelTv.setText("Tap to reveal answer");
            holder.divider.setVisibility(View.GONE);
            holder.backTv.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView labelTv;
        TextView frontTv;
        TextView backTv;
        View divider;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            labelTv = itemView.findViewById(R.id.flashcardLabelTV);
            frontTv = itemView.findViewById(R.id.flashcardFrontTV);
            backTv = itemView.findViewById(R.id.flashcardBackTV);
            divider = itemView.findViewById(R.id.flashcardDivider);
        }
    }
}