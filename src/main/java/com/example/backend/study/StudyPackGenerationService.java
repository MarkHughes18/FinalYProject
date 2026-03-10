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
    private static final int TOPIC_LABELS_COUNT = 6;
    private static final String TOPIC_GENERAL = "General";

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

        // sentence first pipeline
        List<String> factSentences = extractEducationalSentences(sentences, 120);
        if (factSentences.isEmpty()) {
            factSentences = new ArrayList<>(sentences);
        }

        List<String> factConcepts = extractConceptsFromFacts(factSentences, MAX_KEYWORDS);

        // Pick some topic labels from keywords
        List<String> topicLabels = pickTopicLabels(factConcepts, TOPIC_LABELS_COUNT);

        // Build Flashcards first
        List<StudyPack.Flashcard> flashcards = generateFlashcards(factSentences, factConcepts, FLASHCARDS_COUNT,
                topicLabels);

        // Matching from flashcards
        List<StudyPack.MatchingPair> matchingPairs = generateMatchingPairs(flashcards, MATCHING_COUNT);

        // Cloze from flashcards/sentences
        List<StudyPack.ClozeQuestion> clozeQuestions = generateClozeQuestions(factSentences, flashcards, CLOZE_COUNT,
                random);

        // True/False from sentences + keyword swapping
        List<StudyPack.TrueFalseQuestion> tfQuestions = generateTrueFalseQuestions(factSentences, factConcepts,
                TF_COUNT, random);

        // MCQ from cloze-style questions
        List<StudyPack.McqQuestion> mcqQuestions = generateMcqQuestionsFromCloze(clozeQuestions, factConcepts,
                MCQ_COUNT);

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

    private List<String> pickTopicLabels(List<String> keywords, int maxTopics) {
        List<String> topics = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String kw : keywords) {
            if (kw == null)
                continue;
            String t = kw.trim().toLowerCase(Locale.ROOT);
            if (t.isBlank())
                continue;

            // Prefer phrases as topics (more meaningful)
            boolean isPhrase = t.contains(" ");
            if (!isPhrase && isBadFlashcardTerm(t))
                continue;

            // Avoid super-generic single words as topic headers
            if (!isPhrase && (t.equals("world") || t.equals("future") || t.equals("conflict")))
                continue;

            if (seen.add(t)) {
                topics.add(t);
                if (topics.size() >= maxTopics)
                    break;
            }
        }
        return topics;
    }

    private String assignTopicTag(String sentence, String termLower, List<String> topicLabels) {
        if (sentence == null)
            return TOPIC_GENERAL;

        String s = sentence.toLowerCase(Locale.ROOT);

        String best = null;
        int bestScore = 0;

        for (String topic : topicLabels) {
            if (topic == null)
                continue;
            String t = topic.trim().toLowerCase(Locale.ROOT);
            if (t.isBlank())
                continue;

            // Don't tag a card with itself as a “topic”
            if (t.equals(termLower))
                continue;

            // Simple scoring: does the sentence contain the topic?
            int score = 0;
            if (s.contains(t))
                score += 3;

            // Bonus if it's a phrase (more meaningful)
            if (t.contains(" "))
                score += 2;

            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }

        if (best == null)
            return TOPIC_GENERAL;

        // Format label nicely
        return best.contains(" ") ? titleCasePhrase(best) : capitalize(best);
    }

    private List<String> extractEducationalSentences(List<String> sentences, int maxFacts) {
        List<String> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String s : sentences) {
            if (s == null)
                continue;

            String trimmed = s.trim();
            if (trimmed.isBlank())
                continue;

            if (seen.contains(trimmed))
                continue;

            if (!isStrongEducationalSentence(trimmed))
                continue;

            seen.add(trimmed);
            candidates.add(trimmed);
        }

        candidates.sort((a, b) -> Integer.compare(scoreEducationalSentence(b), scoreEducationalSentence(a)));

        if (candidates.size() > maxFacts)
            return new ArrayList<>(candidates.subList(0, maxFacts));

        return candidates;
    }

    private boolean isStrongEducationalSentence(String s) {
        if (s == null)
            return false;

        String t = s.trim();
        if (t.length() < 35 || t.length() > 320)
            return false;

        if (t.endsWith("?"))
            return false;

        if (!Character.isUpperCase(t.charAt(0)))
            return false;

        String lower = t.toLowerCase(Locale.ROOT);

        if (lower.startsWith("today, we will")
                || lower.startsWith("today we will")
                || lower.startsWith("today, we’ll")
                || lower.startsWith("today we’ll")
                || lower.startsWith("to recap")
                || lower.startsWith("let’s consider")
                || lower.startsWith("let's consider")
                || lower.startsWith("in summary"))
            return false;

        if (t.contains("\n"))
            return false;

        return scoreEducationalSentence(t) >= 2;
    }

    private int scoreEducationalSentence(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        int score = 0;

        if (lower.contains(" is ") || lower.contains(" are ") || lower.contains(" was ") || lower.contains(" were "))
            score += 3;

        if (lower.contains(" refers to ")
                || lower.contains(" means ")
                || lower.contains(" states that ")
                || lower.contains(" consists of ")
                || lower.contains(" is divided into ")
                || lower.contains(" includes ")
                || lower.contains(" involves ")
                || lower.contains(" occurs when ")
                || lower.contains(" happens when "))
            score += 3;

        if (lower.startsWith("at ") && lower.contains(","))
            score += 2;

        if (lower.startsWith("when ") && lower.contains(","))
            score += 1;

        int wc = countWords(s);
        if (wc >= 8 && wc <= 32)
            score += 2;

        if (extractConceptFromSentence(s) != null)
            score += 3;

        return score;
    }

    private List<String> extractConceptsFromFacts(List<String> factSentences, int maxConcepts) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String s : factSentences) {
            String concept = extractConceptFromSentence(s);
            if (concept == null || concept.isBlank()) {
                continue;
            }

            String key = concept.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(key);
                if (out.size() >= maxConcepts) {
                    break;
                }
            }
        }

        return out;
    }

    private String extractConceptFromSentence(String sentence) {
        if (sentence == null)
            return null;

        String s = sentence.trim();

        // Pattern: "X is/are/was/were ..."
        String[] markers = {
                " is ", " are ", " was ", " were ",
                " refers to ", " means ", " states that ",
                " consists of ", " includes ", " involves ",
                " occurs when ", " happens when "
        };

        for (String marker : markers) {
            int idx = s.toLowerCase(Locale.ROOT).indexOf(marker);
            if (idx > 0) {
                String candidate = s.substring(0, idx).trim();
                candidate = cleanConcept(candidate);
                if (isUsableConcept(candidate)) {
                    return candidate;
                }
            }
        }

        // Pattern
        if (s.startsWith("At ") && s.contains(",")) {
            String candidate = s.substring(3, s.indexOf(',')).trim();
            candidate = cleanConcept(candidate);
            if (isUsableConcept(candidate)) {
                return candidate;
            }
        }

        // Pattern
        if (s.startsWith("The ") && s.contains(",")) {
            String candidate = s.substring(0, s.indexOf(',')).trim();
            candidate = cleanConcept(candidate);
            if (isUsableConcept(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String cleanConcept(String concept) {
        if (concept == null)
            return null;

        String c = concept.trim();

        c = c.replaceAll("^[\"“”'`]+", "");
        c = c.replaceAll("[\"“”'`.,:;]+$", "");
        c = c.replaceAll("^To begin,\\s*", "");
        c = c.replaceAll("^Another\\s+", "");
        c = c.replaceAll("^For example,\\s*", "");
        c = c.replaceAll("^For instance,\\s*", "");
        c = c.replaceAll("^In summary,\\s*", "");

        return c.trim();
    }

    private boolean isUsableConcept(String concept) {
        if (concept == null || concept.isBlank())
            return false;

        String c = concept.trim();
        String lower = c.toLowerCase(Locale.ROOT);

        if (c.length() < 4 || c.length() > 60)
            return false;

        if (lower.equals("it")
                || lower.equals("this")
                || lower.equals("that")
                || lower.equals("these")
                || lower.equals("those")
                || lower.equals("theory")
                || lower.equals("method")
                || lower.equals("movement")
                || lower.equals("form")
                || lower.equals("important")
                || lower.equals("examples"))
            return false;

        return true;
    }

    private String formatConceptLabel(String concept) {
        if (concept == null)
            return null;

        String c = concept.trim();
        if (c.contains(" "))
            return titleCasePhrase(c.toLowerCase(Locale.ROOT));

        return capitalize(c.toLowerCase(Locale.ROOT));
    }

    private String buildQuestionFromConcept(String concept) {
        String clean = formatConceptLabel(concept);
        if (clean == null || clean.isBlank())
            return "What concept is described here?";

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("s") && !lower.endsWith("ss"))
            return "What are " + clean + "?";

        return "What is " + clean + "?";
    }

    // Generating Flashcards
    private List<StudyPack.Flashcard> generateFlashcards(List<String> sentences,
            List<String> keywords,
            int count, List<String> topicLabels) {
        List<StudyPack.Flashcard> cards = new ArrayList<>();
        Set<String> usedConcepts = new HashSet<>();
        Set<String> usedSnippets = new HashSet<>();

        for (String sentence : sentences) {
            if (cards.size() >= count)
                break;
            if (sentence == null || sentence.isBlank())
                continue;
            if (usedSnippets.contains(sentence))
                continue;
            if (!isStrongEducationalSentence(sentence))
                continue;

            String concept = extractConceptFromSentence(sentence);
            if (concept == null || concept.isBlank())
                continue;

            String conceptKey = concept.toLowerCase(Locale.ROOT);
            if (usedConcepts.contains(conceptKey))
                continue;

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(buildQuestionFromConcept(concept));
            card.setBack(shorten(sentence, 160));
            card.setSourceSnippet(sentence);

            String topicTag = assignTopicTag(sentence, conceptKey, topicLabels);
            card.setTags(Collections.singletonList(topicTag));

            usedTerms.add(conceptKey);
            usedSnippets.add(sentence);
            cards.add(card);
        }

        // If fail to make enough, fallback, use random sentences as "front/back"
        Random r = new Random();
        int attempts = 0;
        while (cards.size() < count && !sentences.isEmpty() && attempts < 5000) {
            attempts++;
            String s = sentences.get(r.nextInt(sentences.size()));
            if (s == null || s.isBlank())
                continue;
            if (usedSnippets.contains(s))
                continue;
            if (!isStrongEducationalSentence(s))
                continue;

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(buildFallbackFlashcardFront(s));
            card.setBack(shorten(s, 160));
            card.setSourceSnippet(s);
            card.setTags(Collections.singletonList(TOPIC_GENERAL));
            usedSnippets.add(s);
            cards.add(card);
        }
        cards.sort(Comparator.comparing(fc -> {
            List<String> tags = fc.getTags();
            return (tags == null || tags.isEmpty()) ? "General" : tags.get(0);
        }));
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
            if (s == null)
                continue;

            String trimmed = s.trim();
            if (trimmed.length() < 40)
                continue; // too short
            if (trimmed.endsWith("?"))
                continue; // questions are bad for flashcards
            if (!Character.isUpperCase(trimmed.charAt(0)))
                continue; // fragment
            if (!endsLikeSentence(trimmed))
                continue; // fragment-ish

            String sLower = s.toLowerCase(Locale.ROOT);
            if (!sLower.contains(kwLower))
                continue;

            int score = 0;

            // Earlier sentences slightly preferred
            score += (2000 - i);

            // Keyword position, earlier in sentence is better
            int idx = sLower.indexOf(kwLower);
            if (idx >= 0) {
                // strong bonus if appears early
                if (idx < 20)
                    score += 400;
                else if (idx < 50)
                    score += 200;
                else
                    score -= 50;
            }

            // Prefer "definition/explanation" patterns
            score += definitionPatternBonus(sLower, kwLower);

            // Word count, too short bad, too long also bad
            int wc = countWords(trimmed);
            if (wc < 10)
                score -= 300;
            else if (wc <= 26)
                score += 250;
            else if (wc <= 35)
                score += 80;
            else
                score -= (wc - 35) * 15; // too long

            // Penalize listy sentences, too many commas / semicolons
            int commaCount = countChar(trimmed, ',');
            int semiCount = countChar(trimmed, ';');
            score -= (commaCount * 25);
            score -= (semiCount * 40);

            // Bonus if it contains helpful cue words
            if (sLower.contains("because") || sLower.contains("therefore") || sLower.contains("as a result"))
                score += 60;

            // Penalize quotes-heavy/citation-heavy
            int quoteCount = countChar(trimmed, '"') + countChar(trimmed, '“') + countChar(trimmed, '”');
            if (quoteCount >= 2)
                score -= 30;

            if (score > bestScore) {
                bestScore = score;
                best = trimmed;
            }
        }

        return best;
    }

    private boolean endsLikeSentence(String s) {
        if (s.isBlank())
            return false;
        char c = s.charAt(s.length() - 1);
        return c == '.' || c == '!' || c == ')' || c == ']' || c == '"'
                || c == '”' || c == '\''; // allow citations/quotes at end
    }

    private int definitionPatternBonus(String sentenceLower, String kwLower) {
        int bonus = 0;

        // Try to detect keyword, is/are/was/were
        // We'll look for the keyword followed shortly by a linking verb.
        int idx = sentenceLower.indexOf(kwLower);
        if (idx < 0)
            return 0;

        String tail = sentenceLower.substring(idx);
        if (tail.matches(
                "^" + Pattern.quote(kwLower) + "\\b.{0,25}\\b(is|are|was|were|means|refers to|defined as)\\b.*"))
            bonus += 500;

        // Reward patterns
        if (tail.contains("is known as") || tail.contains("is called"))
            bonus += 200;

        return bonus;
    }

    private int countChar(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ch)
                n++;
        }
        return n;
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

        for (String sentence : sentences) {
            if (out.size() >= count)
                break;

            if (!isStrongEducationalSentence(sentence))
                continue;

            String concept = extractConceptFromSentence(sentence);
            if (concept == null || concept.isBlank())
                continue;

            // Try to blank the answer (whole word)
            String sentenceWithBlank = blankOutWholeWord(sentence, concept);
            if (sentenceWithBlank == null || sentenceWithBlank.equals(sentence))
                continue;

            String key = concept.toLowerCase(Locale.ROOT) + "|" + sentence;
            if (used.contains(key))
                continue;

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setAnswer(formatConceptLabel(concept));
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

            if (!isGoodQuizAnswer(answer))
                continue;

            List<String> options = new ArrayList<>();
            options.add(formatConceptLabel(answer));

            // add 3 distractors
            int attempts = 0;
            while (options.size() < 4 && attempts < 200) {
                attempts++;
                String d = pickSmartDistractorFromConcepts(answer, keywords, r);
                if (d == null || d.isBlank())
                    continue;
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

                options.add(formatConceptLabel(d));
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

    private boolean isGoodQuizAnswer(String answer) {
        if (answer == null)
            return false;
        String a = answer.trim();
        if (a.length() < 4)
            return false;

        String lower = a.toLowerCase(Locale.ROOT);

        // Avoid generic/connector/verb answers
        if (WEAK_TERMS.contains(lower))
            return false;
        if (lower.endsWith("ing") || lower.endsWith("ed") || lower.endsWith("ly"))
            return false;
        // Avoid common filler words even if not in stopwords
        if (lower.equals("great") || lower.equals("future") || lower.equals("including") || lower.equals("leading"))
            return false;

        return true;
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

    private String buildFallbackFlashcardFront(String sentence) {

        String s = sentence.trim();

        // If sentence starts with a named concept
        String[] words = s.split("\\s+");
        if (words.length > 0 && Character.isUpperCase(words[0].charAt(0))) {

            String first = words[0];

            if (words.length > 1 && Character.isUpperCase(words[1].charAt(0))) {
                first += " " + words[1];
            }

            return "What is " + first + "?";
        }

        return "What concept is described here?";
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
        if (w.endsWith("ing") || w.endsWith("ed") || w.endsWith("ly") || w.endsWith("tion"))
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
        if (s.toLowerCase().contains("today, we’ll explore") || s.toLowerCase().contains("today, we'll explore")
                || s.toLowerCase().contains("in this lesson") || s.toLowerCase().contains("in this video")
                || s.toLowerCase().contains("today we will"))
            return true;
        return false;
    }

    private String pickSmartDistractor(String answer, List<String> keywords, Random r) {

        String a = answer.trim();
        int len = a.length();
        boolean answerIsPhrase = a.contains(" ");
        boolean answerIsAcronym = a.matches("^[A-Z]{2,}.*");
        boolean answerIsCapitalized = !a.isEmpty() && Character.isUpperCase(a.charAt(0));
        List<String> candidates = new ArrayList<>();

        for (String kw : keywords) {
            if (kw == null)
                continue;

            String k = kw.trim();
            if (k.isBlank())
                continue;
            if (k.equalsIgnoreCase(a))
                continue;

            // filter weak distractors
            if (!isGoodQuizAnswer(k))
                continue;

            boolean kIsPhrase = k.contains(" ");
            boolean kIsAcronym = k.matches("^[A-Z]{2,}.*");
            boolean kIsCapitalized = !k.isEmpty() && Character.isUpperCase(k.charAt(0));

            // Prefer same "type"
            if (answerIsPhrase && !kIsPhrase)
                continue;
            if (answerIsAcronym && !kIsAcronym)
                continue;
            if (answerIsCapitalized && !kIsCapitalized && !kIsPhrase) {
                // allow phrases, otherwise try to keep capitalization similar
                continue;
            }

            // Similar length
            if (Math.abs(k.length() - len) > 6)
                continue;

            candidates.add(k);
        }

        if (candidates.isEmpty()) {
            // fallback, pick any decent keyword
            for (int tries = 0; tries < 200; tries++) {
                String k = keywords.get(r.nextInt(keywords.size()));
                if (k != null && isGoodQuizAnswer(k) && !k.equalsIgnoreCase(a)) {
                    return formatTerm(k);
                }
            }
            return formatTerm(keywords.get(r.nextInt(keywords.size())));
        }

        String chosen = candidates.get(r.nextInt(candidates.size()));
        return formatTerm(chosen);
    }

    private String formatTerm(String term) {
        if (term == null)
            return null;
        String t = term.trim();
        if (t.contains(" "))
            return titleCasePhrase(t.toLowerCase(Locale.ROOT));
        return capitalize(t.toLowerCase(Locale.ROOT));
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