package com.example.finalyearproject.data;

public class CreateHistoryRequest {
    public String userEmail;
    public String fileName;
    public String fileType;
    public long fileSize;

    public CreateHistoryRequest(String userEmail,
                                String fileName,
                                String fileType,
                                long fileSize) {
        this.userEmail = userEmail;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }
}
