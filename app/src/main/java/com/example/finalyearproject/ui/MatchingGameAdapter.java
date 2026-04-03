package com.example.finalyearproject.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;

import java.util.List;

public class MatchingGameAdapter extends RecyclerView.Adapter<MatchingGameAdapter.ViewHolder> {

    public interface Listener {
        void onPairMatched(MatchingGameItem item);
    }

    private final List<MatchingGameItem> leftItems;
    private final List<MatchingGameItem> rightItems;
    private final Listener listener;

    private Integer selectedLeftPairId = null;
    private Integer selectedRightPairId = null;
    private int selectedLeftPos = -1;
    private int selectedRightPos = -1;

    private boolean locked = false;

    public MatchingGameAdapter(List<MatchingGameItem> leftItems,
                               List<MatchingGameItem> rightItems,
                               Listener listener) {
        this.leftItems = leftItems;
        this.rightItems = rightItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_matching_game_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MatchingGameItem leftItem = position < leftItems.size() ? leftItems.get(position) : null;
        MatchingGameItem rightItem = position < rightItems.size() ? rightItems.get(position) : null;

        bindChoice(holder.leftTv, leftItem, true, position);
        bindChoice(holder.rightTv, rightItem, false, position);
    }

    private void bindChoice(TextView tv, MatchingGameItem item, boolean isLeft, int position) {
        if (item == null) {
            tv.setVisibility(View.INVISIBLE);
            tv.setOnClickListener(null);
            return;
        }

        tv.setVisibility(View.VISIBLE);
        tv.setText(isLeft ? item.left : item.right);
        resetCard(tv);

        if (isLeft && position == selectedLeftPos) {
            setSelected(tv);
        }
        if (!isLeft && position == selectedRightPos) {
            setSelected(tv);
        }

        tv.setOnClickListener(v -> {
            if (locked) return;

            if (isLeft) {
                selectedLeftPairId = item.pairId;
                selectedLeftPos = position;
            } else {
                selectedRightPairId = item.pairId;
                selectedRightPos = position;
            }

            notifyDataSetChanged();
            checkMatch();
        });
    }

    private void checkMatch() {
        if (selectedLeftPairId == null || selectedRightPairId == null) {
            return;
        }

        locked = true;

        if (selectedLeftPairId.equals(selectedRightPairId)) {
            int pairId = selectedLeftPairId;

            MatchingGameItem matchedLeft = null;
            MatchingGameItem matchedRight = null;

            for (MatchingGameItem item : leftItems) {
                if (item.pairId == pairId) {
                    matchedLeft = item;
                    break;
                }
            }

            for (MatchingGameItem item : rightItems) {
                if (item.pairId == pairId) {
                    matchedRight = item;
                    break;
                }
            }

            if (matchedLeft != null && matchedRight != null) {
                MatchingGameItem merged = new MatchingGameItem(matchedLeft.left, matchedRight.right, pairId);
                leftItems.remove(matchedLeft);
                rightItems.remove(matchedRight);
                listener.onPairMatched(merged);
            }

            clearSelection();
            locked = false;
            notifyDataSetChanged();

        } else {
            notifyItemChanged(selectedLeftPos);
            notifyItemChanged(selectedRightPos);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                clearSelection();
                locked = false;
                notifyDataSetChanged();
            }, 500);
        }
    }

    private void clearSelection() {
        selectedLeftPairId = null;
        selectedRightPairId = null;
        selectedLeftPos = -1;
        selectedRightPos = -1;
    }

    private void resetCard(TextView tv) {
        tv.setBackgroundTintList(null);
        tv.setBackgroundResource(R.drawable.study_card_background);
    }

    private void setSelected(TextView tv) {
        tv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#90CAF9")));
    }

    @Override
    public int getItemCount() {
        return Math.max(leftItems.size(), rightItems.size());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView leftTv;
        TextView rightTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            leftTv = itemView.findViewById(R.id.matchLeftChoiceTV);
            rightTv = itemView.findViewById(R.id.matchRightChoiceTV);
        }
    }
}