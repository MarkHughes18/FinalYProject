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

public class MatchingAdapter extends RecyclerView.Adapter<MatchingAdapter.ViewHolder> {

    private final List<StudyPackResponse.MatchingPair> items;
    private final Set<Integer> revealedPositions = new HashSet<>();


    public MatchingAdapter(List<StudyPackResponse.MatchingPair> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_matching, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.MatchingPair item = items.get(position);
        holder.leftTv.setText(item.left);
        holder.rightTv.setText(item.right);

        boolean revealed = revealedPositions.contains(position);

        if (revealed) {
            holder.rightTv.setVisibility(View.VISIBLE);
            holder.hintTv.setVisibility(View.VISIBLE);
            holder.revealBtn.setEnabled(false);
            holder.revealBtn.setText("Match Revealed");

            holder.itemView.setOnClickListener(v -> {
                revealedPositions.remove(position);
                notifyItemChanged(position);
            });
        } else {
            holder.rightTv.setVisibility(View.GONE);
            holder.hintTv.setVisibility(View.GONE);
            holder.revealBtn.setEnabled(true);
            holder.revealBtn.setText("Reveal Match");

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
        TextView leftTv;
        Button revealBtn;
        TextView rightTv;
        TextView hintTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            leftTv = itemView.findViewById(R.id.matchLeftTV);
            revealBtn = itemView.findViewById(R.id.matchRevealBtn);
            rightTv = itemView.findViewById(R.id.matchRightTV);
            hintTv = itemView.findViewById(R.id.matchHintTV);
        }
    }
}