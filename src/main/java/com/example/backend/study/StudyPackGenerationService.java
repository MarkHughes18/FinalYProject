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

    // Generating Flashcards
    private List<StudyPack.Flashcard> generateFlashcards(List<String> sentences,
            List<String> keywords,
            int count) {
        List<StudyPack.Flashcard> cards = new ArrayList<>();
        Set<String> usedTerms = new HashSet<>();

        for (String kw : keywords) {
            if (cards.size() >= count)
                break;
            if (usedTerms.contains(kw))
                continue;

            String bestSentence = findBestSentenceContaining(sentences, kw);
            if (bestSentence == null)
                continue;

            // Keep definition shortish for UI
            String back = shorten(bestSentence, 160);

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(capitalize(kw));
            card.setBack(back);
            card.setSourceSnippet(bestSentence);
            card.setTags(Collections.emptyList());

            usedTerms.add(kw);
            cards.add(card);
        }

        // If fail to make enough, fallback, use random sentences as "front/back"
        Random r = new Random();
        while (cards.size() < count && !sentences.isEmpty()) {
            String s = sentences.get(r.nextInt(sentences.size()));
            String front = "Key idea";
            String back = shorten(s, 160);

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(front);
            card.setBack(back);
            card.setSourceSnippet(s);
            card.setTags(Collections.emptyList());
            cards.add(card);
        }
        return cards;
    }

    private String findBestSentenceContaining(List<String> sentences, String keyword) {
        String kwLower = keyword.toLowerCase(Locale.ROOT);

        String best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            String sLower = s.toLowerCase(Locale.ROOT);

            if (!sLower.contains(kwLower))
                continue;

            int wordCount = countWords(s);
            if (wordCount < 8 || wordCount > 28)
                continue;

            // scoring: earlier sentences get a boost
            int score = 1000 - i; // earlier = higher
            score -= Math.abs(16 - wordCount) * 10; // closer to ~16 words = better

            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    // Generating Matching Pairs using Flashcards
    private List<StudyPack.MatchingPair> generateMatchingPairs(List<StudyPack.Flashcard> flashcards, int count) {
        List<StudyPack.MatchingPair> pairs = new ArrayList<>();
        for (StudyPack.Flashcard fc : flashcards) {
            if (pairs.size() >= count)
                break;

            String left = fc.getFront();
            String right = shorten(fc.getBack(), 120);

            StudyPack.MatchingPair p = new StudyPack.MatchingPair();
            p.setLeft(left);
            p.setRight(right);
            pairs.add(p);
        }
        return pairs;
    }

    // Generating Cloze sentences
    private List<StudyPack.ClozeQuestion> generateClozeQuestions(List<String> sentences,
            List<StudyPack.Flashcard> flashcards,
            int count) {
        List<StudyPack.ClozeQuestion> out = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (StudyPack.Flashcard fc : flashcards) {
            if (out.size() >= count)
                break;

            String answer = fc.getFront();
            if (answer == null || answer.isBlank())
                continue;

            // Use original keyword lowercase for matching
            String answerLower = answer.toLowerCase(Locale.ROOT);
            String source = fc.getSourceSnippet() != null ? fc.getSourceSnippet() : fc.getBack();

            if (source == null)
                continue;

            // avoid duplicates
            String key = answerLower + "|" + source;
            if (used.contains(key))
                continue;

            String sentenceWithBlank = blankOut(source, answerLower);
            if (sentenceWithBlank == null)
                continue;

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setAnswer(answer);
            q.setSentenceWithBlank(sentenceWithBlank);
            q.setChoices(Collections.emptyList());
            q.setSourceSnippet(source);

            used.add(key);
            out.add(q);
        }

        // fallback, create from random sentences if needed
        Random r = new Random();
        while (out.size() < count && !sentences.isEmpty()) {
            String s = sentences.get(r.nextInt(sentences.size()));
            String candidate = pickAnyKeywordInSentence(s, flashcards);
            if (candidate == null)
                break;

            String sentenceWithBlank = blankOut(s, candidate.toLowerCase(Locale.ROOT));
            if (sentenceWithBlank == null)
                continue;

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setAnswer(candidate);
            q.setSentenceWithBlank(sentenceWithBlank);
            q.setChoices(Collections.emptyList());
            q.setSourceSnippet(s);
            out.add(q);
        }
        return out;
    }

    private String blankOut(String sentence, String answerLower) {
        // replace first whole-word occurrence caseinsensitive via lower compare
        String sLower = sentence.toLowerCase(Locale.ROOT);
        int idx = sLower.indexOf(answerLower);
        if (idx < 0)
            return null;

        // Ensure blanking a word boundary occurrence
        // Replace substring at idx with ____ keeping original casing around it
        String before = sentence.substring(0, idx);
        String after = sentence.substring(idx + answerLower.length());

        return before + "____" + after;
    }

    private String pickAnyKeywordInSentence(String sentence, List<StudyPack.Flashcard> flashcards) {
        String sLower = sentence.toLowerCase(Locale.ROOT);
        for (StudyPack.Flashcard fc : flashcards) {
            if (fc.getFront() == null)
                continue;
            String term = fc.getFront().toLowerCase(Locale.ROOT);
            if (term.length() < 4)
                continue;
            if (sLower.contains(term))
                return fc.getFront();
        }
        return null;
    }

    // Generating True/False
    private List<StudyPack.TrueFalseQuestion> generateTrueFalseQuestions(List<String> sentences,
            List<String> keywords,
            int count) {
        List<StudyPack.TrueFalseQuestion> out = new ArrayList<>();
        Random r = new Random();

        // True questions
        int trueCount = Math.max(1, count / 2);
        for (int i = 0; i < trueCount && i < sentences.size(); i++) {
            String s = sentences.get(i);
            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(shorten(s, 220));
            q.setAnswer(true);
            q.setExplanation("This statement appears in the uploaded document.");
            q.setSourceSnippet(s);
            out.add(q);
        }

        // False questions via keyword swap
        int attempts = 0;
        while (out.size() < count && attempts < 500 && !sentences.isEmpty() && keywords.size() >= 5) {
            attempts++;

            String s = sentences.get(r.nextInt(sentences.size()));
            String sLower = s.toLowerCase(Locale.ROOT);

            String kwInSentence = null;
            for (String kw : keywords) {
                if (sLower.contains(kw)) {
                    kwInSentence = kw;
                    break;
                }
            }
            if (kwInSentence == null)
                continue;

            String replacement = pickDistractor(keywords, kwInSentence, r);
            if (replacement == null)
                continue;

            String falseStmt = replaceFirstCaseInsensitive(s, kwInSentence, replacement);
            if (falseStmt.equals(s))
                continue;

            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(shorten(falseStmt, 220));
            q.setAnswer(false);
            q.setExplanation("One key term was changed, so this statement does not match the document.");
            q.setSourceSnippet(s);
            out.add(q);
        }
        return out;
    }

    private String pickDistractor(List<String> keywords, String original, Random r) {
        // pick similar length keyword
        int targetLen = original.length();
        List<String> candidates = new ArrayList<>();
        for (String kw : keywords) {
            if (kw.equals(original))
                continue;
            if (Math.abs(kw.length() - targetLen) <= 2)
                candidates.add(kw);
        }
        if (candidates.isEmpty())
            return null;
        return candidates.get(r.nextInt(candidates.size()));
    }

    private String replaceFirstCaseInsensitive(String sentence, String fromLower, String toLower) {
        String sLower = sentence.toLowerCase(Locale.ROOT);
        int idx = sLower.indexOf(fromLower);
        if (idx < 0)
            return sentence;
        return sentence.substring(0, idx) + toLower + sentence.substring(idx + fromLower.length());
    }
}