package com.example.backend.study.dto;

import java.util.List;

public class TrueFalsePackResponse {
    private List<TrueFalseQuestionDto> trueFalseQuestions;

    public List<TrueFalseQuestionDto> getTrueFalseQuestions() {
        return trueFalseQuestions;
    }

    public void setTrueFalseQuestions(List<TrueFalseQuestionDto> trueFalseQuestions) {
        this.trueFalseQuestions = trueFalseQuestions;
    }
}