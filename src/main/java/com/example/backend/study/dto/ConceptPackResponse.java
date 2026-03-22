package com.example.backend.study.dto;

import java.util.List;

public class ConceptPackResponse {
    private List<FlashcardDto> flashcards;
    private List<ClozeQuestionDto> clozeQuestions;
    private List<McqQuestionDto> mcqQuestions;

    public List<FlashcardDto> getFlashcards() {
        return flashcards;
    }

    public void setFlashcards(List<FlashcardDto> flashcards) {
        this.flashcards = flashcards;
    }

    public List<ClozeQuestionDto> getClozeQuestions() {
        return clozeQuestions;
    }

    public void setClozeQuestions(List<ClozeQuestionDto> clozeQuestions) {
        this.clozeQuestions = clozeQuestions;
    }

    public List<McqQuestionDto> getMcqQuestions() {
        return mcqQuestions;
    }

    public void setMcqQuestions(List<McqQuestionDto> mcqQuestions) {
        this.mcqQuestions = mcqQuestions;
    }
}