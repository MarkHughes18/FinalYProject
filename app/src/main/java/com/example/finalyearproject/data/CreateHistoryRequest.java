package com.example.finalyearproject.data;

public class CreateHistoryRequest {
    public String userEmail;
    public String fileName;
    public String fileType;
    public long fileSize;
    public String ttsLanguageCode;
    public String ttsVoice;
    public CreateHistoryRequest(String userEmail,
                                String fileName,
                                String fileType,
                                long fileSize,
                                String ttsLanguageCode,
                                String ttsVoice) {
        this.userEmail = userEmail;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.ttsLanguageCode = ttsLanguageCode;
        this.ttsVoice = ttsVoice;
    }
    //odl constructor keeps old calls working
    public CreateHistoryRequest(String userEmail,
                                String fileName,
                                String fileType,
                                long fileSize) {
        this(userEmail, fileName, fileType, fileSize, "en-GB", "female");
    }
}
