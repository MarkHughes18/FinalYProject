package com.example.finalyearproject.data;

import android.content.Context;
import android.content.SharedPreferences;

public class LearningStatsPrefs {

    private static final String PREF_NAME = "learning_stats_prefs";

    private static final String KEY_TOTAL_ATTEMPTED = "total_attempted";
    private static final String KEY_TOTAL_CORRECT = "total_correct";
    private static final String KEY_TOTAL_INCORRECT = "total_incorrect";

    private static final String KEY_MCQ_ATTEMPTED = "mcq_attempted";
    private static final String KEY_MCQ_CORRECT = "mcq_correct";

    private static final String KEY_CLOZE_ATTEMPTED = "cloze_attempted";
    private static final String KEY_CLOZE_CORRECT = "cloze_correct";

    private static final String KEY_TRUE_FALSE_ATTEMPTED = "true_false_attempted";
    private static final String KEY_TRUE_FALSE_CORRECT = "true_false_correct";

    private static final String KEY_MATCHING_ATTEMPTED = "matching_attempted";
    private static final String KEY_MATCHING_CORRECT = "matching_correct";

    private static final String KEY_FLASHCARD_ATTEMPTED = "flashcard_attempted";
    private static final String KEY_SESSIONS_COMPLETED = "sessions_completed";

    public static void mergeSessionStats(Context context, StudySessionStats sessionStats) {
        if (context == null || sessionStats == null) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        int totalAttempted = prefs.getInt(KEY_TOTAL_ATTEMPTED, 0) + sessionStats.getTotalAttempted();
        int totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0) + sessionStats.getTotalCorrect();
        int totalIncorrect = prefs.getInt(KEY_TOTAL_INCORRECT, 0) + sessionStats.getTotalIncorrect();

        int mcqAttempted = prefs.getInt(KEY_MCQ_ATTEMPTED, 0) + sessionStats.getMcqStats().getAttempted();
        int mcqCorrect = prefs.getInt(KEY_MCQ_CORRECT, 0) + sessionStats.getMcqStats().getCorrect();

        int clozeAttempted = prefs.getInt(KEY_CLOZE_ATTEMPTED, 0) + sessionStats.getClozeStats().getAttempted();
        int clozeCorrect = prefs.getInt(KEY_CLOZE_CORRECT, 0) + sessionStats.getClozeStats().getCorrect();

        int trueFalseAttempted = prefs.getInt(KEY_TRUE_FALSE_ATTEMPTED, 0) + sessionStats.getTrueFalseStats().getAttempted();
        int trueFalseCorrect = prefs.getInt(KEY_TRUE_FALSE_CORRECT, 0) + sessionStats.getTrueFalseStats().getCorrect();

        int matchingAttempted = prefs.getInt(KEY_MATCHING_ATTEMPTED, 0) + sessionStats.getMatchingStats().getAttempted();
        int matchingCorrect = prefs.getInt(KEY_MATCHING_CORRECT, 0) + sessionStats.getMatchingStats().getCorrect();

        int flashcardAttempted = prefs.getInt(KEY_FLASHCARD_ATTEMPTED, 0) + sessionStats.getFlashcardStats().getAttempted();
        int sessionsCompleted = prefs.getInt(KEY_SESSIONS_COMPLETED, 0) + 1;

        prefs.edit()
                .putInt(KEY_TOTAL_ATTEMPTED, totalAttempted)
                .putInt(KEY_TOTAL_CORRECT, totalCorrect)
                .putInt(KEY_TOTAL_INCORRECT, totalIncorrect)
                .putInt(KEY_MCQ_ATTEMPTED, mcqAttempted)
                .putInt(KEY_MCQ_CORRECT, mcqCorrect)
                .putInt(KEY_CLOZE_ATTEMPTED, clozeAttempted)
                .putInt(KEY_CLOZE_CORRECT, clozeCorrect)
                .putInt(KEY_TRUE_FALSE_ATTEMPTED, trueFalseAttempted)
                .putInt(KEY_TRUE_FALSE_CORRECT, trueFalseCorrect)
                .putInt(KEY_MATCHING_ATTEMPTED, matchingAttempted)
                .putInt(KEY_MATCHING_CORRECT, matchingCorrect)
                .putInt(KEY_FLASHCARD_ATTEMPTED, flashcardAttempted)
                .putInt(KEY_SESSIONS_COMPLETED, sessionsCompleted)
                .apply();
    }

    public static DashboardStats getDashboardStats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        int totalAttempted = prefs.getInt(KEY_TOTAL_ATTEMPTED, 0);
        int totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0);
        int totalIncorrect = prefs.getInt(KEY_TOTAL_INCORRECT, 0);

        int mcqAttempted = prefs.getInt(KEY_MCQ_ATTEMPTED, 0);
        int mcqCorrect = prefs.getInt(KEY_MCQ_CORRECT, 0);

        int clozeAttempted = prefs.getInt(KEY_CLOZE_ATTEMPTED, 0);
        int clozeCorrect = prefs.getInt(KEY_CLOZE_CORRECT, 0);

        int trueFalseAttempted = prefs.getInt(KEY_TRUE_FALSE_ATTEMPTED, 0);
        int trueFalseCorrect = prefs.getInt(KEY_TRUE_FALSE_CORRECT, 0);

        int matchingAttempted = prefs.getInt(KEY_MATCHING_ATTEMPTED, 0);
        int matchingCorrect = prefs.getInt(KEY_MATCHING_CORRECT, 0);

        int flashcardAttempted = prefs.getInt(KEY_FLASHCARD_ATTEMPTED, 0);
        int sessionsCompleted = prefs.getInt(KEY_SESSIONS_COMPLETED, 0);

        int overallAccuracy = 0;
        int scoredAttempted = totalCorrect + totalIncorrect;
        if (scoredAttempted > 0) {
            overallAccuracy = (int) Math.round((totalCorrect * 100.0) / scoredAttempted);
        }

        String bestMode = calculateBestMode(
                mcqAttempted, mcqCorrect,
                clozeAttempted, clozeCorrect,
                trueFalseAttempted, trueFalseCorrect,
                matchingAttempted, matchingCorrect
        );

        return new DashboardStats(
                totalAttempted,
                totalCorrect,
                totalIncorrect,
                overallAccuracy,
                bestMode,
                sessionsCompleted,
                flashcardAttempted
        );
    }

    private static String calculateBestMode(
            int mcqAttempted, int mcqCorrect,
            int clozeAttempted, int clozeCorrect,
            int trueFalseAttempted, int trueFalseCorrect,
            int matchingAttempted, int matchingCorrect
    ) {
        double bestAccuracy = -1;
        String bestMode = "N/A";

        if (mcqAttempted > 0) {
            double acc = mcqCorrect * 100.0 / mcqAttempted;
            if (acc > bestAccuracy) {
                bestAccuracy = acc;
                bestMode = "MCQ";
            }
        }

        if (clozeAttempted > 0) {
            double acc = clozeCorrect * 100.0 / clozeAttempted;
            if (acc > bestAccuracy) {
                bestAccuracy = acc;
                bestMode = "Cloze";
            }
        }

        if (trueFalseAttempted > 0) {
            double acc = trueFalseCorrect * 100.0 / trueFalseAttempted;
            if (acc > bestAccuracy) {
                bestAccuracy = acc;
                bestMode = "True / False";
            }
        }

        if (matchingAttempted > 0) {
            double acc = matchingCorrect * 100.0 / matchingAttempted;
            if (acc > bestAccuracy) {
                bestAccuracy = acc;
                bestMode = "Matching";
            }
        }

        return bestMode;
    }

    public static void clearStats(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public static class DashboardStats {
        private final int totalAttempted;
        private final int totalCorrect;
        private final int totalIncorrect;
        private final int overallAccuracy;
        private final String bestMode;
        private final int sessionsCompleted;
        private final int flashcardAttempted;

        public DashboardStats(int totalAttempted,
                              int totalCorrect,
                              int totalIncorrect,
                              int overallAccuracy,
                              String bestMode,
                              int sessionsCompleted,
                              int flashcardAttempted) {
            this.totalAttempted = totalAttempted;
            this.totalCorrect = totalCorrect;
            this.totalIncorrect = totalIncorrect;
            this.overallAccuracy = overallAccuracy;
            this.bestMode = bestMode;
            this.sessionsCompleted = sessionsCompleted;
            this.flashcardAttempted = flashcardAttempted;
        }

        public int getTotalAttempted() {
            return totalAttempted;
        }

        public int getTotalCorrect() {
            return totalCorrect;
        }

        public int getTotalIncorrect() {
            return totalIncorrect;
        }

        public int getOverallAccuracy() {
            return overallAccuracy;
        }

        public String getBestMode() {
            return bestMode;
        }

        public int getSessionsCompleted() {
            return sessionsCompleted;
        }

        public int getFlashcardAttempted() {
            return flashcardAttempted;
        }
    }
}