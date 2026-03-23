package com.example.finalyearproject.data;

import java.util.List;

public class StudyPackResponse {
    public String id;
    public String userEmail;
    public String historyId;
    public String createdAt;
    public String updatedAt;
    public String sourceHash;
    public StudyPackSettings settings;

    public List<Flashcard> flashcards;
    public List<MatchingPair> matchingPairs;
    public List<ClozeQuestion> clozeQuestions;
    public List<TrueFalseQuestion> trueFalseQuestions;
    public List<McqQuestion> mcqQuestions;

    public static class StudyPackSettings {
        public int flashcardCount;
        public int matchingPairCount;
        public int clozeCount;
        public int trueFalseCount;
        public int mcqCount;
        public String difficulty;
    }

    public static class Flashcard {
        public String front;
        public String back;
        public String sourceSnippet;
        public List<String> tags;
    }

    public static class MatchingPair {
        public String left;
        public String right;
    }

    public static class ClozeQuestion {
        public String sentenceWithBlank;
        public String answer;
        public List<String> choices;
        public String sourceSnippet;
    }

    public static class TrueFalseQuestion {
        public String statement;
        public boolean answer;
        public String explanation;
        public String sourceSnippet;
    }

    public static class McqQuestion {
        public String question;
        public List<String> options;
        public int correctIndex;
        public String explanation;
        public String sourceSnippet;
    }
}