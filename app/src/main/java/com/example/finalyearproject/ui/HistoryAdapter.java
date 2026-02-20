package com.example.finalyearproject.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.HistoryItem;

import java.util.List;

//simple adapter just to make things compile and show basic info

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<HistoryItem> items;
    private final OnItemClickListener listener;
    private final OnItemLongClickListener longListener;

    public HistoryAdapter(List<HistoryItem> items, OnItemClickListener listener, OnItemLongClickListener longListener) {
        this.items = items;
        this.listener = listener;
        this.longListener = longListener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longListener != null) longListener.onItemLongClick(item);
            return true;
        });
        holder.title.setText(item.fileName);
        holder.subtitle.setText(item.audioStatus != null
                ? item.audioStatus
                : "Uploaded");
        if ("READY".equalsIgnoreCase(item.audioStatus)) {
            holder.subtitle.setTextColor(Color.parseColor("#5A9C6E"));
        } else if ("PENDING".equalsIgnoreCase(item.audioStatus)) {
            holder.subtitle.setTextColor(Color.parseColor("#C2A14A"));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnItemClickListener {
        void onItemClick(HistoryItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(HistoryItem item);
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.itemFileName);
            subtitle = itemView.findViewById(R.id.itemStatus);
        }
    }
}
