package com.example.finalyearproject.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

//saves and restores users last study position and in progress session stats
public class ContinueLearningPrefs {

    private static final String PREF_NAME = "continue_learning_prefs";
    private static final String KEY_HISTORY_ID = "history_id";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_LAST_MODE = "last_mode";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_OPENED_AT = "last_opened_at";
    private static final String KEY_FLASH_ATTEMPTED = "flash_attempted";
    private static final String KEY_FLASH_CORRECT = "flash_correct";
    private static final String KEY_FLASH_INCORRECT = "flash_incorrect";

    private static final String KEY_CLOZE_ATTEMPTED = "cloze_attempted";
    private static final String KEY_CLOZE_CORRECT = "cloze_correct";
    private static final String KEY_CLOZE_INCORRECT = "cloze_incorrect";

    private static final String KEY_TF_ATTEMPTED = "tf_attempted";
    private static final String KEY_TF_CORRECT = "tf_correct";
    private static final String KEY_TF_INCORRECT = "tf_incorrect";

    private static final String KEY_MCQ_ATTEMPTED = "mcq_attempted";
    private static final String KEY_MCQ_CORRECT = "mcq_correct";
    private static final String KEY_MCQ_INCORRECT = "mcq_incorrect";

    private static final String KEY_MATCH_ATTEMPTED = "match_attempted";
    private static final String KEY_MATCH_CORRECT = "match_correct";
    private static final String KEY_MATCH_INCORRECT = "match_incorrect";

    private static final String KEY_ANSWERED_CLOZE = "answered_cloze";
    private static final String KEY_ANSWERED_TF = "answered_tf";
    private static final String KEY_ANSWERED_MCQ = "answered_mcq";
    private static final String KEY_ANSWERED_MATCHING = "answered_matching";

    private static final String KEY_SELECTED_MCQ = "selected_mcq";
    private static final String KEY_SELECTED_CLOZE = "selected_cloze";
    private static final String KEY_SELECTED_TF = "selected_tf";

    //saves current studypack, mode, position and session stats
    public static void saveContinueLearning(Context context, String historyId, String fileName, String lastMode,
                                            int lastPosition, long lastOpenedAt, StudySessionStats stats) {

        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY_ID, historyId)
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_LAST_MODE, lastMode)
                .putInt(KEY_LAST_POSITION, lastPosition)
                .putLong(KEY_LAST_OPENED_AT, lastOpenedAt);

        if (stats != null) {
            editor.putInt(KEY_FLASH_ATTEMPTED, stats.getFlashcardStats().getAttempted());
            editor.putInt(KEY_FLASH_CORRECT, stats.getFlashcardStats().getCorrect());
            editor.putInt(KEY_FLASH_INCORRECT, stats.getFlashcardStats().getIncorrect());

            editor.putInt(KEY_CLOZE_ATTEMPTED, stats.getClozeStats().getAttempted());
            editor.putInt(KEY_CLOZE_CORRECT, stats.getClozeStats().getCorrect());
            editor.putInt(KEY_CLOZE_INCORRECT, stats.getClozeStats().getIncorrect());

            editor.putInt(KEY_TF_ATTEMPTED, stats.getTrueFalseStats().getAttempted());
            editor.putInt(KEY_TF_CORRECT, stats.getTrueFalseStats().getCorrect());
            editor.putInt(KEY_TF_INCORRECT, stats.getTrueFalseStats().getIncorrect());

            editor.putInt(KEY_MCQ_ATTEMPTED, stats.getMcqStats().getAttempted());
            editor.putInt(KEY_MCQ_CORRECT, stats.getMcqStats().getCorrect());
            editor.putInt(KEY_MCQ_INCORRECT, stats.getMcqStats().getIncorrect());

            editor.putInt(KEY_MATCH_ATTEMPTED, stats.getMatchingStats().getAttempted());
            editor.putInt(KEY_MATCH_CORRECT, stats.getMatchingStats().getCorrect());
            editor.putInt(KEY_MATCH_INCORRECT, stats.getMatchingStats().getIncorrect());

            editor.putString(KEY_ANSWERED_CLOZE, setToString(stats.getAnsweredClozePositions()));
            editor.putString(KEY_ANSWERED_TF, setToString(stats.getAnsweredTrueFalsePositions()));
            editor.putString(KEY_ANSWERED_MCQ, setToString(stats.getAnsweredMcqPositions()));
            editor.putString(KEY_ANSWERED_MATCHING, setToString(stats.getAnsweredMatchingPositions()));

            editor.putString(KEY_SELECTED_MCQ, intMapToString(stats.getMcqSelectedAnswers()));
            editor.putString(KEY_SELECTED_CLOZE, intMapToString(stats.getClozeSelectedAnswers()));
            editor.putString(KEY_SELECTED_TF, boolMapToString(stats.getTrueFalseSelectedAnswers()));
        }

        editor.apply();
    }

    //restores the saved data, inc answered positions and selected answers
    public static ContinueLearningData getContinueLearning(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String historyId = prefs.getString(KEY_HISTORY_ID, null);
        String fileName = prefs.getString(KEY_FILE_NAME, null);
        String lastMode = prefs.getString(KEY_LAST_MODE, null);
        int lastPosition = prefs.getInt(KEY_LAST_POSITION, 0);
        long lastOpenedAt = prefs.getLong(KEY_LAST_OPENED_AT, 0);

        if (historyId == null || fileName == null || lastMode == null) {
            return null;
        }

        StudySessionStats stats = new StudySessionStats();

        stats.getFlashcardStats().setAttempted(prefs.getInt(KEY_FLASH_ATTEMPTED, 0));
        stats.getFlashcardStats().setCorrect(prefs.getInt(KEY_FLASH_CORRECT, 0));
        stats.getFlashcardStats().setIncorrect(prefs.getInt(KEY_FLASH_INCORRECT, 0));

        stats.getClozeStats().setAttempted(prefs.getInt(KEY_CLOZE_ATTEMPTED, 0));
        stats.getClozeStats().setCorrect(prefs.getInt(KEY_CLOZE_CORRECT, 0));
        stats.getClozeStats().setIncorrect(prefs.getInt(KEY_CLOZE_INCORRECT, 0));

        stats.getTrueFalseStats().setAttempted(prefs.getInt(KEY_TF_ATTEMPTED, 0));
        stats.getTrueFalseStats().setCorrect(prefs.getInt(KEY_TF_CORRECT, 0));
        stats.getTrueFalseStats().setIncorrect(prefs.getInt(KEY_TF_INCORRECT, 0));

        stats.getMcqStats().setAttempted(prefs.getInt(KEY_MCQ_ATTEMPTED, 0));
        stats.getMcqStats().setCorrect(prefs.getInt(KEY_MCQ_CORRECT, 0));
        stats.getMcqStats().setIncorrect(prefs.getInt(KEY_MCQ_INCORRECT, 0));

        stats.getMatchingStats().setAttempted(prefs.getInt(KEY_MATCH_ATTEMPTED, 0));
        stats.getMatchingStats().setCorrect(prefs.getInt(KEY_MATCH_CORRECT, 0));
        stats.getMatchingStats().setIncorrect(prefs.getInt(KEY_MATCH_INCORRECT, 0));

        restoreSet(stats.getAnsweredClozePositions(), prefs.getString(KEY_ANSWERED_CLOZE, ""));
        restoreSet(stats.getAnsweredTrueFalsePositions(), prefs.getString(KEY_ANSWERED_TF, ""));
        restoreSet(stats.getAnsweredMcqPositions(), prefs.getString(KEY_ANSWERED_MCQ, ""));
        restoreSet(stats.getAnsweredMatchingPositions(), prefs.getString(KEY_ANSWERED_MATCHING, ""));

        restoreIntMap(stats.getMcqSelectedAnswers(), prefs.getString(KEY_SELECTED_MCQ, ""));
        restoreIntMap(stats.getClozeSelectedAnswers(), prefs.getString(KEY_SELECTED_CLOZE, ""));
        restoreBoolMap(stats.getTrueFalseSelectedAnswers(), prefs.getString(KEY_SELECTED_TF, ""));

        return new ContinueLearningData(historyId, fileName, lastMode, lastPosition, lastOpenedAt, stats);
    }

    //converts positions into a string for SharedPref
    private static String setToString(Set<Integer> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Integer value : set) {
            if (value == null) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(",");
            }

            sb.append(value);
        }

        return sb.toString();
    }

    //restores answered q position from the string ^^^
    private static void restoreSet(Set<Integer> target, String saved) {
        if (target == null || saved == null || saved.isBlank()) {
            return;
        }

        String[] parts = saved.split(",");

        for (String part : parts) {
            try {
                target.add(Integer.parseInt(part.trim()));
            } catch (Exception ignored) {
            }
        }
    }

    //converts selected answer map into a key=value string
    private static String intMapToString(Map<Integer, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(",");
            }

            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return sb.toString();
    }

    //restores selected answer maps from saved key=value string
    private static void restoreIntMap(Map<Integer, Integer> target, String saved) {
        if (target == null || saved == null || saved.isBlank()) {
            return;
        }

        String[] entries = saved.split(",");

        for (String entry : entries) {
            try {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    target.put(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim())
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String boolMapToString(Map<Integer, Boolean> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, Boolean> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(",");
            }

            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return sb.toString();
    }

    private static void restoreBoolMap(Map<Integer, Boolean> target, String saved) {
        if (target == null || saved == null || saved.isBlank()) {
            return;
        }

        String[] entries = saved.split(",");

        for (String entry : entries) {
            try {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    target.put(
                            Integer.parseInt(parts[0].trim()),
                            Boolean.parseBoolean(parts[1].trim())
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static class ContinueLearningData {
        private final String historyId;
        private final String fileName;
        private final String lastMode;
        private final int lastPosition;
        private final long lastOpenedAt;
        private final StudySessionStats stats;

        public ContinueLearningData(String historyId, String fileName, String lastMode, int lastPosition, long lastOpenedAt, StudySessionStats stats) {
            this.historyId = historyId;
            this.fileName = fileName;
            this.lastMode = lastMode;
            this.lastPosition = lastPosition;
            this.lastOpenedAt = lastOpenedAt;
            this.stats = stats;
        }

        public String getHistoryId() {
            return historyId;
        }

        public String getFileName() {
            return fileName;
        }

        public String getLastMode() {
            return lastMode;
        }

        public int getLastPosition() {
            return lastPosition;
        }

        public long getLastOpenedAt() {
            return lastOpenedAt;
        }
        public StudySessionStats getStats() { return stats; }
    }
}