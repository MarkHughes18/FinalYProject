package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;
import com.example.finalyearproject.data.StudySessionStats;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlashcardPagerAdapter extends RecyclerView.Adapter<FlashcardPagerAdapter.ViewHolder> {

    public interface OnStatsChangedListener{
        void onStatsChanged();
    }
    private final List<StudyPackResponse.Flashcard> items;
    private final StudySessionStats sessionStats;
    private final OnStatsChangedListener statsChangedListener;
    private final Set<Integer> revealedPositions = new HashSet<>();
    private final Set<Integer> ratedPositions = new HashSet<>();
    private final Set<Integer> ratedCorrectPositions = new HashSet<>();
    private final Set<Integer> ratedIncorrectPositions = new HashSet<>();

    public FlashcardPagerAdapter(List<StudyPackResponse.Flashcard> items, StudySessionStats sessionStats,
                                 OnStatsChangedListener statsChangedListener) {
        this.items = items;
        this.sessionStats = sessionStats;
        this.statsChangedListener = statsChangedListener;
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
        boolean alreadyRated = ratedPositions.contains(position);

        if (revealed) {
            holder.outerCard.setBackgroundResource(R.drawable.flashcard_back_background);
            holder.sideLabelTv.setText("Answer");
            holder.hintTv.setText("Did you get it right?");
            holder.swipeHintTv.setText("Swipe for next card");
            holder.contentTv.setText(item.back != null ? item.back : "");
            holder.contentTv.setTextSize(20f);

            holder.feedbackLayout.setVisibility(View.VISIBLE);

            if (alreadyRated) {
                holder.feedbackStatusTv.setVisibility(View.VISIBLE);

                if (ratedCorrectPositions.contains(position)) {
                    holder.feedbackStatusTv.setText("Marked as correct");
                } else if (ratedIncorrectPositions.contains(position)) {
                    holder.feedbackStatusTv.setText("Marked as incorrect");
                } else {
                    holder.feedbackStatusTv.setVisibility(View.GONE);
                }

                holder.btnGotIt.setEnabled(false);
                holder.btnNeedReview.setEnabled(false);
                holder.btnGotIt.setAlpha(0.5f);
                holder.btnNeedReview.setAlpha(0.5f);
            } else {
                holder.feedbackStatusTv.setVisibility(View.GONE);
                holder.btnGotIt.setEnabled(true);
                holder.btnNeedReview.setEnabled(true);
                holder.btnGotIt.setAlpha(1f);
                holder.btnNeedReview.setAlpha(1f);

                holder.btnGotIt.setOnClickListener(v -> {
                    if (!ratedPositions.contains(position)) {
                        sessionStats.getFlashcardStats().recordCorrect();
                        ratedPositions.add(position);
                        ratedCorrectPositions.add(position);
                        ratedIncorrectPositions.remove(position);

                        if (statsChangedListener != null) {
                            statsChangedListener.onStatsChanged();
                        }

                        notifyItemChanged(position);
                    }
                });

                holder.btnNeedReview.setOnClickListener(v -> {
                    if (!ratedPositions.contains(position)) {
                        sessionStats.getFlashcardStats().recordIncorrect();
                        ratedPositions.add(position);
                        ratedIncorrectPositions.add(position);
                        ratedCorrectPositions.remove(position);

                        if (statsChangedListener != null) {
                            statsChangedListener.onStatsChanged();
                        }

                        notifyItemChanged(position);
                    }
                });
            }

        } else {
            holder.outerCard.setBackgroundResource(R.drawable.flashcard_front_background);
            holder.sideLabelTv.setText("Question");
            holder.hintTv.setText("Tap card to reveal answer");
            holder.swipeHintTv.setText("Swipe for next card");
            holder.contentTv.setText(item.front != null ? item.front : "");
            holder.contentTv.setTextSize(22f);

            holder.feedbackLayout.setVisibility(View.GONE);
            holder.feedbackStatusTv.setVisibility(View.GONE);
            holder.btnGotIt.setOnClickListener(null);
            holder.btnNeedReview.setOnClickListener(null);
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout outerCard;
        TextView sideLabelTv;
        TextView hintTv;
        TextView contentTv;
        TextView swipeHintTv;
        LinearLayout feedbackLayout;
        Button btnGotIt;
        Button btnNeedReview;
        TextView feedbackStatusTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            outerCard = itemView.findViewById(R.id.flashcardOuterCard);
            sideLabelTv = itemView.findViewById(R.id.flashcardSideLabelTV);
            hintTv = itemView.findViewById(R.id.flashcardPageHintTV);
            contentTv = itemView.findViewById(R.id.flashcardPageContentTV);
            swipeHintTv = itemView.findViewById(R.id.flashcardSwipeHintTV);
            feedbackLayout = itemView.findViewById(R.id.flashcardFeedbackLayout);
            btnGotIt = itemView.findViewById(R.id.flashcardGotItBtn);
            btnNeedReview = itemView.findViewById(R.id.flashcardNeedReviewBtn);
            feedbackStatusTv = itemView.findViewById(R.id.flashcardFeedbackStatusTV);
        }
    }
}