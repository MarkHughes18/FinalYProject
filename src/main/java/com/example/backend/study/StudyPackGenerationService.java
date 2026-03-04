package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import com.example.backend.repository.StudyPackRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.regex.Matcher;

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
    private static final Pattern PHRASE_PATTERN = Pattern.compile(
            "\\b([A-Z][\\p{L}'-]*(?:\\s+(?:of|the|and|in|to|for|on|at|with|by|from))?\\s+[A-Z][\\p{L}'-]*(?:\\s+[A-Z][\\p{L}'-]*)*)\\b");

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

        // Seed randomness so generation is deterministic for the same text
        int seed = (sourceHash != null) ? sourceHash.hashCode() : Objects.hash(userEmail, historyId);
        Random random = new Random(seed);

        List<String> sentences = splitIntoSentences(bounded, MAX_SENTENCES);

        // Keyword extraction
        List<String> keywords = extractTopKeywords(bounded, MAX_KEYWORDS);

        // Build Flashcards first
        List<StudyPack.Flashcard> flashcards = generateFlashcards(sentences, keywords, FLASHCARDS_COUNT);

        // Matching from flashcards
        List<StudyPack.MatchingPair> matchingPairs = generateMatchingPairs(flashcards, MATCHING_COUNT);

        // Cloze from flashcards/sentences
        List<StudyPack.ClozeQuestion> clozeQuestions = generateClozeQuestions(sentences, flashcards, CLOZE_COUNT,
                random);

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
        Map<String, Integer> phraseFreq = new HashMap<>();
        Map<String, Integer> wordFreq = new HashMap<>();

        Matcher p = PHRASE_PATTERN.matcher(text);
        while (p.find()) {
            String phrase = p.group().trim();
            if (phrase.length() < 6)
                continue;

            String lower = phrase.toLowerCase(Locale.ROOT);

            // Skip phrases that are basically stopwords-only
            String[] parts = lower.split("\\s+");
            if (parts.length < 2)
                continue;
            if (STOPWORDS.contains(parts[0]))
                continue;
            // Reject phrases with too many words
            if (parts.length > 5)
                continue;

            phraseFreq.put(lower, phraseFreq.getOrDefault(lower, 0) + 2); // weight phrases higher
        }

        // Word blacklist
        Set<String> phraseWords = new HashSet<>();
        for (String phrase : phraseFreq.keySet()) {
            for (String part : phrase.split("\\s+")) {
                phraseWords.add(part.toLowerCase(Locale.ROOT));
            }
        }

        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            String w = m.group().toLowerCase(Locale.ROOT);
            if (w.length() < 4)
                continue;
            if (STOPWORDS.contains(w))
                continue;
            if (phraseWords.contains(w))
                continue;
            if (isBadFlashcardTerm(w))
                continue;
            wordFreq.put(w, wordFreq.getOrDefault(w, 0) + 1);
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        entries.addAll(phraseFreq.entrySet());
        entries.addAll(wordFreq.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue())); // sort by frequency

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
        Set<String> usedSnippets = new HashSet<>();

        for (String kw : keywords) {
            if (cards.size() >= count)
                break;
            if (kw == null || kw.isBlank())
                continue;
            if (kw.length() < 4)
                continue;
            String termLower = kw.trim().toLowerCase(Locale.ROOT);
            if (termLower.isBlank())
                continue;
            if (usedTerms.contains(termLower))
                continue;

            // For single-word terms, apply strong filtering.
            // For multi-word phrases, allow them through
            boolean isPhrase = termLower.contains(" ");
            if (!isPhrase && isBadFlashcardTerm(termLower))
                continue;

            String bestSentence = findBestSentenceContaining(sentences, kw);
            if (bestSentence == null)
                continue;

            // Don’t reuse the same sentence
            if (usedSnippets.contains(bestSentence))
                continue;

            if (isBadSentence(bestSentence))
                continue;

            // Skip a sentence if it looks like a question
            if (bestSentence.trim().endsWith("?"))
                continue;

            // Skip if it looks like an intro sentence
            String lower = bestSentence.toLowerCase(Locale.ROOT);
            if (lower.startsWith("today, we’ll explore") || lower.startsWith("today, we'll explore"))
                continue;

            if (isBadFlashcardTerm(kw))
                continue;

            // Keep definition shortish for UI
            String front = isPhrase ? titleCasePhrase(termLower) : capitalize(termLower);
            String back = shorten(bestSentence, 160);

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(buildFlashcardFront(kw, bestSentence));
            card.setBack(back);
            card.setSourceSnippet(bestSentence);
            card.setTags(Collections.emptyList());

            usedTerms.add(termLower);
            usedSnippets.add(bestSentence);
            cards.add(card);
        }

        // If fail to make enough, fallback, use random sentences as "front/back"
        Random r = new Random();
        int attempts = 0;
        while (cards.size() < count && !sentences.isEmpty() && attempts < 5000) {
            attempts++;
            String s = sentences.get(r.nextInt(sentences.size()));
            if (usedSnippets.contains(s))
                continue;
            if (isBadSentence(s))
                continue;
            if (s.trim().endsWith("?"))
                continue;

            String front = "Concept: " + shorten(s, 40);
            String back = shorten(s, 160);

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(buildFlashcardFront(kw, bestSentence));
            card.setBack(back);
            card.setSourceSnippet(s);
            card.setTags(Collections.emptyList());
            usedSnippets.add(s);
            cards.add(card);
        }
        return cards;
    }

    private String titleCasePhrase(String phraseLower) {
        // "treaty of versailles" -> "Treaty of Versailles"
        String[] parts = phraseLower.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.isBlank())
                continue;

            // keep small connector words lowercase unless first word
            if (i != 0 && (w.equals("of") || w.equals("and") || w.equals("the") || w.equals("to") || w.equals("in"))) {
                sb.append(w);
            } else {
                sb.append(capitalize(w));
            }

            if (i < parts.length - 1)
                sb.append(" ");
        }
        return sb.toString().trim();
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
            int count,
            Random random) {

        List<StudyPack.ClozeQuestion> out = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // Prefer flashcard fronts that are not key idea
        // Fall back to scanning sentences for any keywords-like tokens
        List<String> answerPool = new ArrayList<>();
        for (StudyPack.Flashcard fc : flashcards) {
            if (fc.getFront() == null)
                continue;
            String front = fc.getFront().trim();
            if (front.equalsIgnoreCase("Key idea"))
                continue;
            if (front.length() < 4)
                continue;
            answerPool.add(front);
        }

        // If pool is small, use words from sentences, pick words that look like
        // keywords
        if (answerPool.size() < 10) {
            for (String s : sentences) {
                Matcher m = WORD_PATTERN.matcher(s);
                while (m.find()) {
                    String w = m.group();
                    if (w.length() < 5)
                        continue;
                    // skip common stopwords
                    String wl = w.toLowerCase(Locale.ROOT);
                    if (STOPWORDS.contains(wl))
                        continue;
                    answerPool.add(capitalize(wl));
                }
                if (answerPool.size() >= 200)
                    break;
            }
        }

        // Shuffle to avoid pulling early terms
        Collections.shuffle(answerPool, random);

        int attempts = 0;
        while (out.size() < count && attempts < 3000 && !sentences.isEmpty() && !answerPool.isEmpty()) {
            attempts++;

            String sentence = sentences.get(random.nextInt(sentences.size()));
            String answer = answerPool.get(random.nextInt(answerPool.size()));

            // Try to blank the answer (whole word)
            String sentenceWithBlank = blankOutWholeWord(sentence, answer);
            if (sentenceWithBlank == null)
                continue;

            String key = answer.toLowerCase(Locale.ROOT) + "|" + sentence;
            if (used.contains(key))
                continue;

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setAnswer(answer);
            q.setSentenceWithBlank(sentenceWithBlank);
            q.setChoices(Collections.emptyList());
            q.setSourceSnippet(sentence);

            used.add(key);
            out.add(q);
        }

        return out;
    }

    private String blankOutWholeWord(String sentence, String answer) {
        if (sentence == null || answer == null)
            return null;

        String escaped = Pattern.quote(answer);
        Pattern p = Pattern.compile("\\b" + escaped + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sentence);
        if (!m.find())
            return null;

        return m.replaceFirst("____");
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

    // Generating MCQ
    private List<StudyPack.McqQuestion> generateMcqQuestionsFromCloze(List<StudyPack.ClozeQuestion> cloze,
            List<String> keywords,
            int count) {
        List<StudyPack.McqQuestion> out = new ArrayList<>();
        Random r = new Random();

        for (StudyPack.ClozeQuestion cq : cloze) {
            if (out.size() >= count)
                break;

            String answer = cq.getAnswer();
            if (answer == null || answer.isBlank())
                continue;

            List<String> options = new ArrayList<>();
            options.add(answer);

            // add 3 distractors
            int attempts = 0;
            while (options.size() < 4 && attempts < 200) {
                attempts++;
                String d = pickSmartDistractor(answer, keywords, r);
                d = capitalize(d);
                if (d.equalsIgnoreCase(answer))
                    continue;
                boolean alreadyExists = false;
                for (String o : options) {
                    if (o.equalsIgnoreCase(d)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (alreadyExists)
                    continue;

                options.add(d);
            }

            // if cant get enough distractors skip
            if (options.size() < 4)
                continue;

            Collections.shuffle(options, r);
            int correctIndex = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equalsIgnoreCase(answer)) {
                    correctIndex = i;
                    break;
                }
            }
            if (correctIndex < 0)
                continue;

            StudyPack.McqQuestion q = new StudyPack.McqQuestion();
            q.setQuestion(cq.getSentenceWithBlank());
            q.setOptions(options);
            q.setCorrectIndex(correctIndex);
            q.setExplanation("Choose the term that best completes the sentence based on the document.");
            q.setSourceSnippet(cq.getSourceSnippet());
            out.add(q);
        }
        return out;
    }

    // Helpers
    private String shorten(String s, int maxLen) {
        if (s == null)
            return null;
        String t = s.trim();
        if (t.length() <= maxLen)
            return t;
        return t.substring(0, maxLen - 3).trim() + "...";
    }

    private int countWords(String s) {
        int count = 0;
        Matcher m = WORD_PATTERN.matcher(s);
        while (m.find())
            count++;
        return count;
    }

    private String capitalize(String w) {
        if (w == null || w.isBlank())
            return w;
        String t = w.trim().toLowerCase(Locale.ROOT);
        if (t.length() == 1)
            return t.toUpperCase(Locale.ROOT);
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Worst case no hash
            return null;
        }
    }

    private String buildFlashcardFront(String keyword, String sentence) {

        String lower = sentence.toLowerCase(Locale.ROOT);
        String kw = keyword.toLowerCase(Locale.ROOT);

        if (lower.startsWith(kw)) {
            return "What is " + capitalize(keyword) + "?";
        }

        if (lower.contains("is " + kw) || lower.contains("are " + kw)) {
            return "What is " + capitalize(keyword) + "?";
        }

        if (lower.contains("caused by") || lower.contains("led to")) {
            return "What caused " + capitalize(keyword) + "?";
        }

        if (lower.contains("introduced") || lower.contains("developed")) {
            return "What was " + capitalize(keyword) + " used for?";
        }
        return "Explain: " + capitalize(keyword);
    }

    private boolean isBadFlashcardTerm(String kw) {
        if (kw == null)
            return true;
        String w = kw.trim().toLowerCase(Locale.ROOT);

        // Too short/ too long
        if (w.length() < 4 || w.length() > 25)
            return true;

        // Common weak words, verbs/adjectives/connectors
        if (WEAK_TERMS.contains(w))
            return true;

        // Ends with common
        if (w.endsWith("ing") || w.endsWith("ed") || w.endsWith("ly"))
            return true;

        return false;
    }

    private boolean isBadSentence(String s) {
        if (s == null)
            return true;
        String t = s.trim();
        if (t.length() < 40) // 2 short to be a definition
            return true;
        if (!Character.isUpperCase(t.charAt(0)))// Fragment starts lowercase
            return true;
        if (t.startsWith("entry "))
            return true;
        return false;
    }

    private String pickSmartDistractor(String answer, List<String> keywords, Random r) {

        int len = answer.length();
        List<String> candidates = new ArrayList<>();

        for (String kw : keywords) {

            if (kw.equalsIgnoreCase(answer))
                continue;

            if (Math.abs(kw.length() - len) <= 3) {
                candidates.add(capitalize(kw));
            }
        }

        if (candidates.isEmpty()) {
            return capitalize(keywords.get(r.nextInt(keywords.size())));
        }
        return candidates.get(r.nextInt(candidates.size()));
    }

    // Minimal stopword set
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "and", "that", "this", "with", "from", "have", "been", "will", "were", "they", "their", "there",
            "your", "you", "for", "are", "but", "not", "into", "about", "over", "under", "than", "then", "them",
            "what", "when", "where", "which", "while", "who", "whom", "why", "how", "can", "could", "should",
            "would", "may", "might", "also", "such", "some", "more", "most", "many", "much", "each", "other",
            "these", "those", "between", "within", "without", "because", "through", "during", "before", "after",
            "above", "below", "here", "very", "just", "like", "only", "same", "any", "all", "has", "had", "its",
            "our", "out", "off", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"));

    private static final Set<String> WEAK_TERMS = new HashSet<>(Arrays.asList(
            "world", "future", "conflict", "including", "leading", "introduced", "complex", "great",
            "because", "entry", "helped", "help", "late", "early", "many", "most", "also", "used",
            "need", "needs", "make", "made", "makes", "caused", "cause", "effects", "effect"));

}