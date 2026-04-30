package com.example.backend.study.dto;

import java.util.List;

public class CustomStudyPackRequest {
    private String userEmail;
    private String sourceHistoryId;
    private String title;
    private List<String> flashcardSnippets;
    private List<String> clozeSnippets;
    private List<String> trueFalseSnippets;
    private List<String> mcqSnippets;
    private List<String> matchingSnippets;

    public String getUserEmail() {
        return userEmail;
    }

    public String getSourceHistoryId() {
        return sourceHistoryId;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getFlashcardSnippets() {
        return flashcardSnippets;
    }

    public List<String> getClozeSnippets() {
        return clozeSnippets;
    }

    public List<String> getTrueFalseSnippets() {
        return trueFalseSnippets;
    }

    public List<String> getMcqSnippets() {
        return mcqSnippets;
    }

    public List<String> getMatchingSnippets() {
        return matchingSnippets;
    }
}