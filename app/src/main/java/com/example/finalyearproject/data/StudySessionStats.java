package com.example.finalyearproject.data;

public class StudySessionStats {

    private final ModeStats mcqStats = new ModeStats();
    private final ModeStats clozeStats = new ModeStats();
    private final ModeStats trueFalseStats = new ModeStats();
    private final ModeStats matchingStats = new ModeStats();

    private int flashcardsViewed = 0;
    private int flashcardsFlipped = 0;

    public ModeStats getMcqStats() {
        return mcqStats;
    }

    public ModeStats getClozeStats() {
        return clozeStats;
    }

    public ModeStats getTrueFalseStats() {
        return trueFalseStats;
    }

    public ModeStats getMatchingStats() {
        return matchingStats;
    }

    public void recordFlashcardViewed() {
        flashcardsViewed++;
    }

    public void recordFlashcardFlipped() {
        flashcardsFlipped++;
    }

    public int getFlashcardsViewed() {
        return flashcardsViewed;
    }

    public int getFlashcardsFlipped() {
        return flashcardsFlipped;
    }

    public int getTotalAttempted() {
        return mcqStats.getAttempted()
                + clozeStats.getAttempted()
                + trueFalseStats.getAttempted()
                + matchingStats.getAttempted();
    }

    public int getTotalCorrect() {
        return mcqStats.getCorrect()
                + clozeStats.getCorrect()
                + trueFalseStats.getCorrect()
                + matchingStats.getCorrect();
    }

    public int getTotalIncorrect() {
        return mcqStats.getIncorrect()
                + clozeStats.getIncorrect()
                + trueFalseStats.getIncorrect()
                + matchingStats.getIncorrect();
    }

    public int getOverallAccuracyPercent() {
        int attempted = getTotalAttempted();
        if (attempted == 0) {
            return 0;
        }
        return (int) Math.round((getTotalCorrect() * 100.0) / attempted);
    }
}