package com.example.finalyearproject.data;

import android.content.Context;
import android.content.SharedPreferences;

public class ContinueLearningPrefs {

    private static final String PREF_NAME = "continue_learning_prefs";

    private static final String KEY_HISTORY_ID = "history_id";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_LAST_MODE = "last_mode";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_OPENED_AT = "last_opened_at";

    public static void saveContinueLearning(
            Context context,
            String historyId,
            String fileName,
            String lastMode,
            int lastPosition,
            long lastOpenedAt
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_HISTORY_ID, historyId)
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_LAST_MODE, lastMode)
                .putInt(KEY_LAST_POSITION, lastPosition)
                .putLong(KEY_LAST_OPENED_AT, lastOpenedAt)
                .apply();
    }

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

        return new ContinueLearningData(historyId, fileName, lastMode, lastPosition, lastOpenedAt);
    }

    public static void clearContinueLearning(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public static class ContinueLearningData {
        private final String historyId;
        private final String fileName;
        private final String lastMode;
        private final int lastPosition;
        private final long lastOpenedAt;

        public ContinueLearningData(String historyId, String fileName, String lastMode, int lastPosition, long lastOpenedAt) {
            this.historyId = historyId;
            this.fileName = fileName;
            this.lastMode = lastMode;
            this.lastPosition = lastPosition;
            this.lastOpenedAt = lastOpenedAt;
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
    }
}