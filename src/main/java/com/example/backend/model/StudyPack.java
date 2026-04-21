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

    private int versionNumber;
    private boolean active;
    private String regeneratedFromPackId; // Previous pack id, if regenerated

    private String fileName;
    private String fileLabel;

    private StudyPackSettings settings;
    private CandidateUsage usedCandidates = new CandidateUsage();// Tracks which items were used

    private List<Flashcard> flashcards = new ArrayList<>();
    private List<MatchingPair> matchingPairs = new ArrayList<>();
    private List<ClozeQuestion> clozeQuestions = new ArrayList<>();
    private List<TrueFalseQuestion> trueFalseQuestions = new ArrayList<>();
    private List<McqQuestion> mcqQuestions = new ArrayList<>();

    public StudyPack() {
    }

    public StudyPack(String userEmail, String historyId, Instant createdAt, Instant updatedAt,
            String sourceHash, int versionNumber, boolean active, String regeneratedFromPackId,
            String fileName, String fileLabel, StudyPackSettings settings, CandidateUsage usedCandidates,
            List<Flashcard> flashcards, List<MatchingPair> matchingPairs,
            List<ClozeQuestion> clozeQuestions, List<TrueFalseQuestion> trueFalseQuestions,
            List<McqQuestion> mcqQuestions) {
        this.userEmail = userEmail;
        this.historyId = historyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sourceHash = sourceHash;
        this.versionNumber = versionNumber;
        this.active = active;
        this.regeneratedFromPackId = regeneratedFromPackId;
        this.fileName = fileName;
        this.fileLabel = fileLabel;
        this.settings = settings;
        this.usedCandidates = usedCandidates != null ? usedCandidates : new CandidateUsage();
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

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getRegeneratedFromPackId() {
        return regeneratedFromPackId;
    }

    public void setRegeneratedFromPackId(String regeneratedFromPackId) {
        this.regeneratedFromPackId = regeneratedFromPackId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileLabel() {
        return fileLabel;
    }

    public void setFileLabel(String fileLabel) {
        this.fileLabel = fileLabel;
    }

    public StudyPackSettings getSettings() {
        return settings;
    }

    public void setSettings(StudyPackSettings settings) {
        this.settings = settings;
    }

    public CandidateUsage getUsedCandidates() {
        return usedCandidates;
    }

    public void setUsedCandidates(CandidateUsage usedCandidates) {
        this.usedCandidates = usedCandidates;
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

    public static class CandidateUsage {
        private List<String> flashcardIds = new ArrayList<>();
        private List<String> matchingIds = new ArrayList<>();
        private List<String> clozeIds = new ArrayList<>();
        private List<String> trueFalseIds = new ArrayList<>();
        private List<String> mcqIds = new ArrayList<>();

        public CandidateUsage() {
        }

        public List<String> getFlashcardIds() {
            return flashcardIds;
        }

        public void setFlashcardIds(List<String> flashcardIds) {
            this.flashcardIds = flashcardIds != null ? flashcardIds : new ArrayList<>();
        }

        public List<String> getMatchingIds() {
            return matchingIds;
        }

        public void setMatchingIds(List<String> matchingIds) {
            this.matchingIds = matchingIds != null ? matchingIds : new ArrayList<>();
        }

        public List<String> getClozeIds() {
            return clozeIds;
        }

        public void setClozeIds(List<String> clozeIds) {
            this.clozeIds = clozeIds != null ? clozeIds : new ArrayList<>();
        }

        public List<String> getTrueFalseIds() {
            return trueFalseIds;
        }

        public void setTrueFalseIds(List<String> trueFalseIds) {
            this.trueFalseIds = trueFalseIds != null ? trueFalseIds : new ArrayList<>();
        }

        public List<String> getMcqIds() {
            return mcqIds;
        }

        public void setMcqIds(List<String> mcqIds) {
            this.mcqIds = mcqIds != null ? mcqIds : new ArrayList<>();
        }
    }

    public static class StudyPackSettings {
        private int flashcardCount;
        private int matchingPairCount;
        private int clozeCount;
        private int trueFalseCount;
        private int mcqCount;
        private String difficulty; // "EASY", "MEDIUM", "HARD"

        public StudyPackSettings() {
        }

        public StudyPackSettings(int flashcardCount, int matchingPairCount, int clozeCount,
                int trueFalseCount, int mcqCount, String difficulty) {
            this.flashcardCount = flashcardCount;
            this.matchingPairCount = matchingPairCount;
            this.clozeCount = clozeCount;
            this.trueFalseCount = trueFalseCount;
            this.mcqCount = mcqCount;
            this.difficulty = difficulty;
        }

        public int getFlashcardCount() {
            return flashcardCount;
        }

        public void setFlashcardCount(int flashcardCount) {
            this.flashcardCount = flashcardCount;
        }

        public int getMatchingPairCount() {
            return matchingPairCount;
        }

        public void setMatchingPairCount(int matchingPairCount) {
            this.matchingPairCount = matchingPairCount;
        }

        public int getClozeCount() {
            return clozeCount;
        }

        public void setClozeCount(int clozeCount) {
            this.clozeCount = clozeCount;
        }

        public int getTrueFalseCount() {
            return trueFalseCount;
        }

        public void setTrueFalseCount(int trueFalseCount) {
            this.trueFalseCount = trueFalseCount;
        }

        public int getMcqCount() {
            return mcqCount;
        }

        public void setMcqCount(int mcqCount) {
            this.mcqCount = mcqCount;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }
    }

    public static class Flashcard {
        private String front; // term / keyword
        private String back; // definition / context sentence
        private String sourceSnippet; // where it came from
        private List<String> tags = new ArrayList<>();

        public Flashcard() {
        }

        public Flashcard(String front, String back, String sourceSnippet, List<String> tags) {
            this.front = front;
            this.back = back;
            this.sourceSnippet = sourceSnippet;
            this.tags = tags != null ? tags : new ArrayList<>();
        }

        public String getFront() {
            return front;
        }

        public void setFront(String front) {
            this.front = front;
        }

        public String getBack() {
            return back;
        }

        public void setBack(String back) {
            this.back = back;
        }

        public String getSourceSnippet() {
            return sourceSnippet;
        }

        public void setSourceSnippet(String sourceSnippet) {
            this.sourceSnippet = sourceSnippet;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class MatchingPair {
        private String left;
        private String right;

        public MatchingPair() {
        }

        public MatchingPair(String left, String right) {
            this.left = left;
            this.right = right;
        }

        public String getLeft() {
            return left;
        }

        public void setLeft(String left) {
            this.left = left;
        }

        public String getRight() {
            return right;
        }

        public void setRight(String right) {
            this.right = right;
        }
    }

    public static class ClozeQuestion {
        private String sentenceWithBlank; // e.g. "The ____ is responsible for..."
        private String answer; // removed word/phrase
        private List<String> choices = new ArrayList<>();
        private String sourceSnippet;

        public ClozeQuestion() {
        }

        public ClozeQuestion(String sentenceWithBlank, String answer, List<String> choices, String sourceSnippet) {
            this.sentenceWithBlank = sentenceWithBlank;
            this.answer = answer;
            this.choices = choices != null ? choices : new ArrayList<>();
            this.sourceSnippet = sourceSnippet;
        }

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

        public List<String> getChoices() {
            return choices;
        }

        public void setChoices(List<String> choices) {
            this.choices = choices;
        }

        public String getSourceSnippet() {
            return sourceSnippet;
        }

        public void setSourceSnippet(String sourceSnippet) {
            this.sourceSnippet = sourceSnippet;
        }
    }

    public static class TrueFalseQuestion {
        private String statement;
        private boolean answer; // true or false
        private String explanation;
        private String sourceSnippet;

        public TrueFalseQuestion() {
        }

        public TrueFalseQuestion(String statement, boolean answer, String explanation, String sourceSnippet) {
            this.statement = statement;
            this.answer = answer;
            this.explanation = explanation;
            this.sourceSnippet = sourceSnippet;
        }

        public String getStatement() {
            return statement;
        }

        public void setStatement(String statement) {
            this.statement = statement;
        }

        public boolean isAnswer() {
            return answer;
        }

        public void setAnswer(boolean answer) {
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

    public static class McqQuestion {
        private String question;
        private List<String> options = new ArrayList<>();
        private int correctIndex;
        private String explanation;
        private String sourceSnippet;

        public McqQuestion() {
        }

        public McqQuestion(String question, List<String> options, int correctIndex, String explanation,
                String sourceSnippet) {
            this.question = question;
            this.options = options != null ? options : new ArrayList<>();
            this.correctIndex = correctIndex;
            this.explanation = explanation;
            this.sourceSnippet = sourceSnippet;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }

        public void setCorrectIndex(int correctIndex) {
            this.correctIndex = correctIndex;
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
}