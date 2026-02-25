package com.example.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "StudyPacks")
public class StudyPack {

    @Id
    private String id;

    @Indexed
    private String userEmail;

    @Indexed
    private String historyId; // FileHistory.id

    private Instant createdAt;
    private Instant updatedAt;

    // Helps detect if narration text changed / regeneration needed
    private String sourceHash;

    private StudyPackSettings settings;

    private List<Flashcard> flashcards = new ArrayList<>();
    private List<MatchingPair> matchingPairs = new ArrayList<>();
    private List<ClozeQuestion> clozeQuestions = new ArrayList<>();
    private List<TrueFalseQuestion> trueFalseQuestions = new ArrayList<>();
    private List<McqQuestion> mcqQuestions = new ArrayList<>();

    public StudyPack() {
    }

    public StudyPack(String userEmail, String historyId, Instant createdAt, Instant updatedAt,
            String sourceHash, StudyPackSettings settings,
            List<Flashcard> flashcards, List<MatchingPair> matchingPairs,
            List<ClozeQuestion> clozeQuestions, List<TrueFalseQuestion> trueFalseQuestions,
            List<McqQuestion> mcqQuestions) {
        this.userEmail = userEmail;
        this.historyId = historyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sourceHash = sourceHash;
        this.settings = settings;
        this.flashcards = flashcards != null ? flashcards : new ArrayList<>();
        this.matchingPairs = matchingPairs != null ? matchingPairs : new ArrayList<>();
        this.clozeQuestions = clozeQuestions != null ? clozeQuestions : new ArrayList<>();
        this.trueFalseQuestions = trueFalseQuestions != null ? trueFalseQuestions : new ArrayList<>();
        this.mcqQuestions = mcqQuestions != null ? mcqQuestions : new ArrayList<>();
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

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public StudyPackSettings getSettings() {
        return settings;
    }

    public void setSettings(StudyPackSettings settings) {
        this.settings = settings;
    }

    public List<Flashcard> getFlashcards() {
        return flashcards;
    }

    public void setFlashcards(List<Flashcard> flashcards) {
        this.flashcards = flashcards;
    }

    public List<MatchingPair> getMatchingPairs() {
        return matchingPairs;
    }

    public void setMatchingPairs(List<MatchingPair> matchingPairs) {
        this.matchingPairs = matchingPairs;
    }

    public List<ClozeQuestion> getClozeQuestions() {
        return clozeQuestions;
    }

    public void setClozeQuestions(List<ClozeQuestion> clozeQuestions) {
        this.clozeQuestions = clozeQuestions;
    }

    public List<TrueFalseQuestion> getTrueFalseQuestions() {
        return trueFalseQuestions;
    }

    public void setTrueFalseQuestions(List<TrueFalseQuestion> trueFalseQuestions) {
        this.trueFalseQuestions = trueFalseQuestions;
    }

    public List<McqQuestion> getMcqQuestions() {
        return mcqQuestions;
    }

    public void setMcqQuestions(List<McqQuestion> mcqQuestions) {
        this.mcqQuestions = mcqQuestions;
    }
}