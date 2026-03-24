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

public class FlashcardAdapter extends RecyclerView.Adapter<FlashcardAdapter.ViewHolder> {

    private final List<StudyPackResponse.Flashcard> items;

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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView frontTv;
        TextView backTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            frontTv = itemView.findViewById(R.id.flashcardFrontTV);
            backTv = itemView.findViewById(R.id.flashcardBackTV);
        }
    }
}