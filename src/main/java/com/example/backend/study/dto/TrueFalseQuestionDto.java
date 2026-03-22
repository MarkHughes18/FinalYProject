package com.example.backend.study.dto;

public class TrueFalseQuestionDto {
    private String statement;
    private Boolean answer;
    private String explanation;
    private String sourceSnippet;

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public Boolean getAnswer() {
        return answer;
    }

    public void setAnswer(Boolean answer) {
        this.answer = answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSourceSnippet() {
        return sourceSnippet;
    }

    public void setSourceSnippet(String sourceSnippet) {
        this.sourceSnippet = sourceSnippet;
    }
}