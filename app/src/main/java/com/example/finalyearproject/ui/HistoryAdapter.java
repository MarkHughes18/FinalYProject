package com.example.finalyearproject.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        holder.title.setText(item.fileName != null ? item.fileName : "Unnamed");
        String status = (item.audioStatus != null && !item.audioStatus.isBlank())
                ? item.audioStatus.trim().toUpperCase()
                : "UPLOADED";
        holder.subtitle.setText(status);

        applyStatusChipStyle(holder.subtitle, status);

        TextView labelTv = holder.itemView.findViewById(R.id.itemLabel);

        if (item.label != null && !item.label.isBlank()) {
            labelTv.setText(item.label);
            labelTv.setVisibility(View.VISIBLE);
        } else {
            labelTv.setVisibility(View.GONE);
        }
    }

    private void applyStatusChipStyle(TextView tv, String status) {
        Context ctx = tv.getContext();

        int bg;
        int fg;
        if ("READY".equalsIgnoreCase(status)) {
            bg = R.color.status_ready_bg;
            fg = R.color.status_ready_text;
        } else if ("PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)) {
            bg = R.color.status_pending_bg;
            fg = R.color.status_pending_text;
        } else if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
            bg = R.color.status_failed_bg;
            fg = R.color.status_failed_text;
        } else {
            bg = R.color.status_pending_bg;
            fg = R.color.status_pending_text;
        }

        tv.setTextColor(ContextCompat.getColor(ctx, fg));
        // Tint the chip background
        if (tv.getBackground() != null) {
            tv.getBackground().setTint(ContextCompat.getColor(ctx, bg));
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
