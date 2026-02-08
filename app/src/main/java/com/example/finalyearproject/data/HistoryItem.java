package com.example.finalyearproject.data;

public class HistoryItem {
    public String id;
    public String fileName;
    public String fileType;
    public long fileSize;
    public String uploadedAt;// ISO string from backend
    public String textStatus;
    public String narrationStatus;
    public String audioStatus;  // PENDING / PROCESSING / READY / FAILED
    public String audioUrl;
    public String updatedAt;
    public String errorMessage;

    // Empty constructor needed by Retrofit / Gson
    public HistoryItem() {
    }
}
