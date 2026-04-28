package com.example.finalyearproject.data;

import java.util.List;

public class CustomStudyPackRequest {
    public String userEmail;
    public String sourceHistoryId;
    public String title;

    public List<String> flashcardSnippets;
    public List<String> clozeSnippets;
    public List<String> trueFalseSnippets;
    public List<String> mcqSnippets;

    public CustomStudyPackRequest(
            String userEmail,
            String sourceHistoryId,
            String title,
            List<String> flashcardSnippets,
            List<String> clozeSnippets,
            List<String> trueFalseSnippets,
            List<String> mcqSnippets
    ) {
        this.userEmail = userEmail;
        this.sourceHistoryId = sourceHistoryId;
        this.title = title;
        this.flashcardSnippets = flashcardSnippets;
        this.clozeSnippets = clozeSnippets;
        this.trueFalseSnippets = trueFalseSnippets;
        this.mcqSnippets = mcqSnippets;
    }
}