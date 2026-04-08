package com.example.finalyearproject.data;

public class ModeStats {
    private int attempted;
    private int correct;
    private int incorrect;

    public void recordCorrect() {
        attempted++;
        correct++;
    }

    public void recordIncorrect() {
        attempted++;
        incorrect++;
    }

    public int getAttempted() {
        return attempted;
    }

    public int getCorrect() {
        return correct;
    }

    public int getIncorrect() {
        return incorrect;
    }

    public int getAccuracyPercent() {
        if (attempted == 0) {
            return 0;
        }
        return (int) Math.round((correct * 100.0) / attempted);
    }
}