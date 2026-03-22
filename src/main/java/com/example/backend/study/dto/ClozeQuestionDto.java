package com.example.backend.study.dto;

public class ClozeQuestionDto {
    private String sentenceWithBlank;
    private String answer;
    private String sourceSnippet;

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
}