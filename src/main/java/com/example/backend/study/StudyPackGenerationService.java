package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import com.example.backend.repository.StudyPackRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class StudyPackGenerationService {

    // Pack sizes
    private static final int FLASHCARDS_COUNT = 20;
    private static final int MATCHING_COUNT = 10;
    private static final int CLOZE_COUNT = 15;
    private static final int TF_COUNT = 10;
    private static final int MCQ_COUNT = 10;

    // Safety caps for huge narrationText
    private static final int MAX_TEXT_CHARS = 300_000;
    private static final int MAX_SENTENCES = 2_000;
    private static final int MAX_KEYWORDS = 200;

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z\\-']{2,}");

    private final FileHistoryRepository fileHistoryRepository;
    private final StudyPackRepository studyPackRepository;

    public StudyPackGenerationService(FileHistoryRepository fileHistoryRepository,
            StudyPackRepository studyPackRepository) {
        this.fileHistoryRepository = fileHistoryRepository;
        this.studyPackRepository = studyPackRepository;
    }

    // Returns the stored StudyPack if it exists, otherwise generates and returns it
    // Ownership check is done against FileHistory.userEmail
    public StudyPack getOrGenerate(String userEmail, String historyId) {
        // If already generated, return it
        Optional<StudyPack> existing = studyPackRepository.findByUserEmailAndHistoryId(userEmail, historyId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Load FileHistory
        FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
        if (fh == null) {
            throw new IllegalArgumentException("File history not found for file id: " + historyId);
        }

        // Ownership validation
        if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new IllegalArgumentException("Not allowed: history file does not belong to user");
        }

        // We generate from narrationText, fallback to extractedText
        String text = fh.getNarrationText();
        if (text == null || text.isBlank()) {
            text = fh.getExtractedText();
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("No narration/extracted text available yet for this file.");
        }

        String bounded = boundText(normalize(text), MAX_TEXT_CHARS);
        String sourceHash = sha256Hex(bounded);

        List<String> sentences = splitIntoSentences(bounded, MAX_SENTENCES);

        // Keyword extraction
        List<String> keywords = extractTopKeywords(bounded, MAX_KEYWORDS);

        // Build Flashcards first
        List<StudyPack.Flashcard> flashcards = generateFlashcards(sentences, keywords, FLASHCARDS_COUNT);

        // Matching from flashcards
        List<StudyPack.MatchingPair> matchingPairs = generateMatchingPairs(flashcards, MATCHING_COUNT);

        // Cloze from flashcards/sentences
        List<StudyPack.ClozeQuestion> clozeQuestions = generateClozeQuestions(sentences, flashcards, CLOZE_COUNT);

        // True/False from sentences + keyword swapping
        List<StudyPack.TrueFalseQuestion> tfQuestions = generateTrueFalseQuestions(sentences, keywords, TF_COUNT);

        // MCQ from cloze-style questions
        List<StudyPack.McqQuestion> mcqQuestions = generateMcqQuestionsFromCloze(clozeQuestions, keywords, MCQ_COUNT);

        // Settings metadata
        StudyPack.StudyPackSettings settings = new StudyPack.StudyPackSettings(
                FLASHCARDS_COUNT, MATCHING_COUNT, CLOZE_COUNT, TF_COUNT, MCQ_COUNT, "EASY");

        Instant now = Instant.now();
        StudyPack pack = new StudyPack();
        pack.setUserEmail(userEmail);
        pack.setHistoryId(historyId);
        pack.setCreatedAt(now);
        pack.setUpdatedAt(now);
        pack.setSourceHash(sourceHash);
        pack.setSettings(settings);

        pack.setFlashcards(flashcards);
        pack.setMatchingPairs(matchingPairs);
        pack.setClozeQuestions(clozeQuestions);
        pack.setTrueFalseQuestions(tfQuestions);
        pack.setMcqQuestions(mcqQuestions);

        return studyPackRepository.save(pack);
    }

    // Text helpers
    private String normalize(String text) {
        String t = text.replace("\u00A0", " "); // non-breaking spaces
        t = t.replaceAll("[\\t\\r]+", " ");
        t = t.replaceAll(" +", " ");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    private String boundText(String text, int maxChars) {
        if (text.length() <= maxChars)
            return text;
        return text.substring(0, maxChars);
    }

    private List<String> splitIntoSentences(String text, int maxSentences) {
        // Simple sentence split
        String[] raw = text.split("(?<=[.!?])\\s+");
        List<String> out = new ArrayList<>(Math.min(raw.length, maxSentences));
        for (String s : raw) {
            String trimmed = s.trim();
            if (trimmed.length() < 20)
                continue; // skip tiny fragments
            out.add(trimmed);
            if (out.size() >= maxSentences)
                break;
        }
        return out;
    }

    private List<String> extractTopKeywords(String text, int maxKeywords) {
        Map<String, Integer> freq = new HashMap<>();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            String w = m.group().toLowerCase(Locale.ROOT);
            if (w.length() < 4)
                continue;
            if (STOPWORDS.contains(w))
                continue;
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> keywords = new ArrayList<>(Math.min(entries.size(), maxKeywords));
        for (Map.Entry<String, Integer> e : entries) {
            keywords.add(e.getKey());
            if (keywords.size() >= maxKeywords)
                break;
        }
        return keywords;
    }
}