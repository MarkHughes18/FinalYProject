package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashcardPagerAdapter extends RecyclerView.Adapter<FlashcardPagerAdapter.ViewHolder> {

    private final List<StudyPackResponse.Flashcard> items;
    private final Set<Integer> revealedPositions = new HashSet<>();

    public FlashcardPagerAdapter(List<StudyPackResponse.Flashcard> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard_page, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.Flashcard item = items.get(position);
        boolean revealed = revealedPositions.contains(position);

        if (revealed) {
            holder.outerCard.setBackgroundResource(R.drawable.flashcard_back_background);
            holder.sideLabelTv.setText("Answer");
            holder.hintTv.setText("Tap card to show question");
            holder.swipeHintTv.setText("Swipe for next card");
            holder.contentTv.setText(item.back != null ? item.back : "");
            holder.contentTv.setTextSize(20f);
        } else {
            holder.outerCard.setBackgroundResource(R.drawable.flashcard_front_background);
            holder.sideLabelTv.setText("Question");
            holder.hintTv.setText("Tap card to reveal answer");
            holder.swipeHintTv.setText("Swipe for next card");
            holder.contentTv.setText(item.front != null ? item.front : "");
            holder.contentTv.setTextSize(22f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (revealedPositions.contains(position)) {
                revealedPositions.remove(position);
            } else {
                revealedPositions.add(position);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void resetAllToFront() {
        if (!revealedPositions.isEmpty()) {
            revealedPositions.clear();
            notifyDataSetChanged();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout outerCard;
        TextView sideLabelTv;
        TextView hintTv;
        TextView contentTv;
        TextView swipeHintTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            outerCard = itemView.findViewById(R.id.flashcardOuterCard);
            sideLabelTv = itemView.findViewById(R.id.flashcardSideLabelTV);
            hintTv = itemView.findViewById(R.id.flashcardPageHintTV);
            contentTv = itemView.findViewById(R.id.flashcardPageContentTV);
            swipeHintTv = itemView.findViewById(R.id.flashcardSwipeHintTV);
        }
    }
}