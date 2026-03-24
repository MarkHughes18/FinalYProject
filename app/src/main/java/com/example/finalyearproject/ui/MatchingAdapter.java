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

public class MatchingAdapter extends RecyclerView.Adapter<MatchingAdapter.ViewHolder> {

    private final List<StudyPackResponse.MatchingPair> items;

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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView leftTv;
        TextView rightTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            leftTv = itemView.findViewById(R.id.matchLeftTV);
            rightTv = itemView.findViewById(R.id.matchRightTV);
        }
    }
}