package com.example.backend.study.dto;

import java.util.List;

public class ClozeQuestionDto {
    private String sentenceWithBlank;
    private String answer;
    private String sourceSnippet;
    private List<String> choices;

    public String getSentenceWithBlank() {
        return sentenceWithBlank;
    }

    public void setSentenceWithBlank(String sentenceWithBlank) {
        this.sentenceWithBlank = sentenceWithBlank;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public void setSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet;
    }

    public List<String> getChoices() {
        return choices;
    }

    public void setChoices(List<String> choices) {
        this.choices = choices;
    }
}