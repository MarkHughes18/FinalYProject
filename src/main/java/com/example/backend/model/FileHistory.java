package com.example.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "History")
public class FileHistory {

    @Id
    private String id;

    private String userEmail; // link to User by email
    private String fileName;
    private String fileType; // "pdf" etc
    private long fileSize;

    private Instant uploadedAt;
    private Instant updatedAt; // last status update time

    private String audioStatus; // "PENDING" etc
    private String audioUrl; // URL or path to audio file
    private String sourcePath; // where uploaded doc is stored on disk
    private String audioPath; // where generated mp3 is stored on disk
    private String errorMessage; // for when audioStatus = Failure

    private String extractedText; // extracted text from the document
    private String textStatus; // "PENDING" etc

    private String narrationText;
    private String narrationStatus; // PENDING/PROCESSING/READY/FAILED

    public FileHistory() {
    }

    public FileHistory(String userEmail, String fileName, String fileType, long fileSize, Instant uploadedAt,
            String audioStatus, String audioUrl, String sourcePath, String audioPath, Instant updatedAt,
            String errorMessage, String extractedText, String textStatus, String narrationText,
            String narrationStatus) {
        this.userEmail = userEmail;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.audioStatus = audioStatus;
        this.audioUrl = audioUrl;
        this.sourcePath = sourcePath;
        this.audioPath = audioPath;
        this.updatedAt = updatedAt;
        this.errorMessage = errorMessage;
        this.extractedText = extractedText;
        this.textStatus = textStatus;
        this.narrationText = narrationText;
        this.narrationStatus = narrationStatus;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getAudioStatus() {
        return audioStatus;
    }

    public void setAudioStatus(String audioStatus) {
        this.audioStatus = audioStatus;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getTextStatus() {
        return textStatus;
    }

    public void setTextStatus(String textStatus) {
        this.textStatus = textStatus;
    }

    public String getNarrationText() {
        return narrationText;
    }

    public void setNarrationText(String narrationText) {
        this.narrationText = narrationText;
    }

    public String getNarrationStatus() {
        return narrationStatus;
    }

    public void setNarrationStatus(String narrationStatus) {
        this.narrationStatus = narrationStatus;
    }

}
