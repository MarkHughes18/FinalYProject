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

        // Build sentence pools for different features to pull from
        Map<String, List<String>> pools = buildSentencePools(factSentences);
        List<String> definitionSentences = pools.getOrDefault("definitions", Collections.emptyList());
        List<String> processSentences = pools.getOrDefault("processes", Collections.emptyList());
        List<String> detailSentences = pools.getOrDefault("details", Collections.emptyList());

        List<String> flashcardPool = new ArrayList<>(definitionSentences);
        List<String> clozePool = new ArrayList<>(definitionSentences);
        clozePool.addAll(processSentences);
        List<String> tfPool = new ArrayList<>(processSentences);
        tfPool.addAll(detailSentences);

        // Fallback in case one pool is too small/ empty
        if (flashcardPool.isEmpty())
            flashcardPool = new ArrayList<>(factSentences);
        if (clozePool.isEmpty())
            clozePool = new ArrayList<>(factSentences);
        if (tfPool.isEmpty())
            tfPool = new ArrayList<>(factSentences);

        List<String> factConcepts = extractConceptsFromFacts(factSentences, MAX_KEYWORDS);

        // Pick some topic labels from keywords
        List<String> topicLabels = pickTopicLabels(factConcepts, TOPIC_LABELS_COUNT);

        // Build Flashcards first
        List<StudyPack.Flashcard> flashcards = generateFlashcards(flashcardPool, factConcepts, FLASHCARDS_COUNT,
                topicLabels);

        // Matching from flashcards
        List<StudyPack.MatchingPair> matchingPairs = generateMatchingPairs(flashcards, MATCHING_COUNT);

        // Cloze from flashcards/sentences
        List<StudyPack.ClozeQuestion> clozeQuestions = generateClozeQuestions(clozePool, flashcards, CLOZE_COUNT,
                random);

        // True/False from sentences + keyword swapping
        List<StudyPack.TrueFalseQuestion> tfQuestions = generateTrueFalseQuestions(tfPool, factConcepts,
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

    // Building sentence pools for fetures to pull from
    private Map<String, List<String>> buildSentencePools(List<String> factSentences) {
        List<String> definitionSentences = new ArrayList<>();
        List<String> processSentences = new ArrayList<>();
        List<String> detailSentences = new ArrayList<>();

        for (String s : factSentences) {
            if (s == null)
                continue;

            String lower = s.toLowerCase(Locale.ROOT);

            boolean isDefinition = lower.contains(" is ") || lower.contains(" are ") || lower.contains(" was ")
                    || lower.contains(" were ")
                    || lower.contains(" refers to ") || lower.contains(" means ") || lower.contains(" states that ")
                    || lower.contains(" consists of ") || lower.contains(" includes ") || lower.contains(" involves ")
                    || lower.contains(" occurs when ") || lower.contains(" is defined as ");

            boolean isProcess = lower.contains(" when ") || lower.contains(" because ") || lower.contains(" led to ")
                    || lower.contains(" resulted in ") || lower.contains(" caused ")
                    || lower.contains(" brought about ") || lower.contains(" during ")
                    || lower.contains(" after ") || lower.contains(" before ") || lower.contains(" following ");

            if (isDefinition) {
                definitionSentences.add(s);
            } else if (isProcess) {
                processSentences.add(s);
            } else {
                detailSentences.add(s);
            }
        }

        Map<String, List<String>> pools = new HashMap<>();
        pools.put("definitions", definitionSentences);
        pools.put("processes", processSentences);
        pools.put("details", detailSentences);

        return pools;
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

            // Does the sentence contain the topic?
            int score = 0;
            if (s.contains(t))
                score += 3;

            // Bonus if it's a phrase
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
                || lower.startsWith("in summary")
                || lower.startsWith("for example,")
                || lower.startsWith("for example ")
                || lower.startsWith("for instance,")
                || lower.startsWith("for instance ")
                || lower.startsWith("whether ")
                || lower.startsWith("whether in ")
                || lower.startsWith("it ")
                || lower.startsWith("this ")
                || lower.startsWith("that ")
                || lower.startsWith("these ")
                || lower.startsWith("to begin, ")
                || lower.startsWith("to begin "))
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
            if (!isValidStudyConcept(concept)) {
                continue;
            }

            String cleaned = cleanConcept(concept);
            String key = cleaned.toLowerCase(Locale.ROOT);
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
        String strip = stripLeadingPhrases(sentence);
        if (strip == null)
            return null;

        String s = strip.trim();
        if (s.isBlank())
            return null;

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

                if (isGoodExtractedConcept(candidate))
                    return candidate;
            }
        }

        // Pattern
        if (s.startsWith("The ") && s.contains(",")) {
            String candidate = s.substring(0, s.indexOf(',')).trim();
            candidate = cleanConcept(candidate);

            if (isSpecificThePhrase(candidate) && isGoodExtractedConcept(candidate))
                return candidate;
        }

        String fallback = extractFallbackNounPhrase(s);
        if (isGoodExtractedConcept(fallback)) {
            return fallback;
        }

        return null;
    }

    private String extractFallbackNounPhrase(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return null;
        }

        String s = sentence.trim();

        // Remove opening markers that remain
        s = s.replaceFirst("(?i)^another example is\\s+", "");
        s = s.replaceFirst("(?i)^one example is\\s+", "");
        s = s.replaceFirst("(?i)^an example is\\s+", "");
        s = s.replaceFirst("(?i)^this example is\\s+", "");
        s = s.replaceFirst("(?i)^moving into the [^,]+,\\s*", "");
        s = s.replaceFirst("(?i)^at the [^,]+,\\s*", "");
        s = s.replaceFirst("(?i)^in the [^,]+,\\s*", "");

        // Try "X such as Y"
        int suchAsIdx = s.toLowerCase(Locale.ROOT).indexOf(" such as ");
        if (suchAsIdx > 0) {
            String candidate = s.substring(0, suchAsIdx).trim();
            candidate = cleanConcept(candidate);
            if (looksLikeGoodFallbackConcept(candidate)) {
                return candidate;
            }
        }

        // Try "X including Y"
        int includingIdx = s.toLowerCase(Locale.ROOT).indexOf(" including ");
        if (includingIdx > 0) {
            String candidate = s.substring(0, includingIdx).trim();
            candidate = cleanConcept(candidate);
            if (looksLikeGoodFallbackConcept(candidate)) {
                return candidate;
            }
        }

        String properName = extractLeadingProperName(s);
        if (looksLikeGoodFallbackConcept(properName)) {
            return properName;
        }

        // Try taking the first short noun-like phrase before comma
        if (s.contains(",")) {
            String beforeComma = s.substring(0, s.indexOf(',')).trim();
            String lowerBeforeComma = beforeComma.toLowerCase(Locale.ROOT);

            if (!(lowerBeforeComma.startsWith("born")
                    || lowerBeforeComma.startsWith("driven")
                    || lowerBeforeComma.startsWith("yet")
                    || lowerBeforeComma.startsWith("although")
                    || lowerBeforeComma.startsWith("after")
                    || lowerBeforeComma.startsWith("before")
                    || lowerBeforeComma.startsWith("during")
                    || lowerBeforeComma.startsWith("in ")
                    || lowerBeforeComma.startsWith("at ")
                    || lowerBeforeComma.startsWith("his ")
                    || lowerBeforeComma.startsWith("her ")
                    || lowerBeforeComma.startsWith("their "))) {

                String candidate = s.substring(0, s.indexOf(',')).trim();
                candidate = cleanConcept(candidate);
                if (looksLikeGoodFallbackConcept(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean looksLikeGoodFallbackConcept(String candidate) {
        if (candidate == null || candidate.isBlank())
            return false;

        String cleaned = cleanConcept(candidate);
        if (cleaned == null || cleaned.isBlank())
            return false;

        String lower = cleaned.toLowerCase(Locale.ROOT);

        if (countWords(cleaned) < 1 || countWords(cleaned) > 4)
            return false;

        if (cleaned.length() < 4 || cleaned.length() > 50)
            return false;

        // Reject obvious sentence openers / discourse phrases
        if (lower.startsWith("another example")
                || lower.startsWith("one example")
                || lower.startsWith("an example")
                || lower.startsWith("this example")
                || lower.startsWith("for example")
                || lower.startsWith("for instance")
                || lower.startsWith("moving into")
                || lower.startsWith("at the same time")
                || lower.startsWith("in this session")
                || lower.startsWith("on the other hand")
                || lower.startsWith("on the other hand")
                || lower.startsWith("yet ")
                || lower.startsWith("but "))
            return false;

        // Reject time/location/opening markers
        if (lower.startsWith("at ")
                || lower.startsWith("in ")
                || lower.startsWith("on ")
                || lower.startsWith("by ")
                || lower.startsWith("from ")
                || lower.startsWith("during ")
                || lower.startsWith("after ")
                || lower.startsWith("before ")
                || lower.startsWith("following ")
                || lower.startsWith("while ")
                || lower.startsWith("when ")
                || lower.startsWith("since ")) {
            return false;
        }

        // Reject pronoun phrases
        if (lower.startsWith("his ")
                || lower.startsWith("her ")
                || lower.startsWith("their ")
                || lower.startsWith("its ")
                || lower.startsWith("he ")
                || lower.startsWith("she ")
                || lower.startsWith("they ")
                || lower.startsWith("it ")) {
            return false;
        }

        // Reject opening starts
        if (lower.startsWith("born ")
                || lower.startsWith("using ")
                || lower.startsWith("called ")
                || lower.startsWith("named ")
                || lower.startsWith("led ")
                || lower.startsWith("made ")
                || lower.startsWith("set ")) {
            return false;
        }

        // Reject clause phrases
        if (lower.contains(" is ")
                || lower.contains(" are ")
                || lower.contains(" was ")
                || lower.contains(" were ")
                || lower.contains(" means ")
                || lower.contains(" refers to ")
                || lower.contains(" involves ")
                || lower.contains(" caused ")
                || lower.contains(" brought ")
                || lower.contains(" described ")
                || lower.contains(" emphasized ")
                || lower.contains(" highlighted ")
                || lower.contains(" believed ")
                || lower.contains(" proved ")
                || lower.contains(" made ")
                || lower.contains(" had ")
                || lower.contains(" set ")
                || lower.contains(" led ")
                || lower.contains(" called ")) {
            return false;
        }

        if (lower.equals("yet")
                || lower.equals("but")
                || lower.equals("although")
                || lower.equals("however")
                || lower.equals("therefore")
                || lower.equals("born")
                || lower.equals("driven")
                || lower.equals("named")
                || lower.equals("called")
                || lower.equals("made")
                || lower.equals("set")
                || lower.equals("led"))
            return false;

        // Reject comma heavy fragments
        if (cleaned.contains(",") || cleaned.contains(";") || cleaned.contains(":")) {
            return false;
        }
        ;

        return isUsableConcept(cleaned);
    }

    private String extractLeadingProperName(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return null;
        }

        String[] words = sentence.trim().split("\\s+");
        List<String> parts = new ArrayList<>();

        for (String word : words) {
            String cleaned = word.replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "");
            if (cleaned.isBlank()) {
                break;
            }

            if (Character.isUpperCase(cleaned.charAt(0))) {
                parts.add(cleaned);
                if (parts.size() == 4) {
                    break;
                }
            } else {
                break;
            }
        }

        if (parts.isEmpty()) {
            return null;
        }

        return String.join(" ", parts);
    }

    private boolean isGoodExtractedConcept(String candidate) {
        if (candidate == null)
            return false;

        candidate = cleanConcept(candidate);
        if (candidate == null || candidate.isBlank())
            return false;

        if (countWords(candidate) > 4)
            return false;

        if (looksLikeClauseNotConcept(candidate))
            return false;

        if (!isUsableConcept(candidate))
            return false;

        String lower = candidate.toLowerCase(Locale.ROOT);

        // Reject conjunction/opening leftovers
        if (startsWithBadConceptWord(lower))
            return false;

        // Reject generic phrases
        if (isGenericConcept(lower))
            return false;

        // Reject clause-like concepts that contain verb structures
        if (containsConceptBreakingVerb(lower))
            return false;

        return true;
    }

    private boolean looksLikeClauseNotConcept(String candidate) {
        if (candidate == null || candidate.isBlank())
            return true;

        String lower = candidate.toLowerCase(Locale.ROOT).trim();

        if (lower.equals("both types")
                || lower.equals("understanding")
                || lower.equals("similarly")
                || lower.equals("first")
                || lower.equals("next")
                || lower.equals("then")
                || lower.equals("finally"))
            return true;

        if (lower.startsWith("first")
                || lower.startsWith("next")
                || lower.startsWith("then")
                || lower.startsWith("finally")
                || lower.startsWith("similarly")
                || lower.startsWith("understanding")
                || lower.startsWith("both "))
            return true;

        if (lower.contains(" ensures ")
                || lower.contains(" follows ")
                || lower.contains(" decodes ")
                || lower.contains(" helps ")
                || lower.contains(" improves ")
                || lower.contains(" allows ")
                || lower.contains(" creates ")
                || lower.contains(" requires ")
                || lower.contains(" completes "))
            return true;

        if (lower.contains(" that ")
                || lower.contains(" and "))
            return true;

        return false;
    }

    private boolean startsWithBadConceptWord(String lower) {
        return lower.startsWith("although ")
                || lower.startsWith("because ")
                || lower.startsWith("during ")
                || lower.startsWith("following ")
                || lower.startsWith("after ")
                || lower.startsWith("before ")
                || lower.startsWith("when ")
                || lower.startsWith("while ")
                || lower.startsWith("since ")
                || lower.startsWith("if ")
                || lower.startsWith("however ")
                || lower.startsWith("therefore ");
    }

    private boolean isGenericConcept(String lower) {
        Set<String> generic = Set.of(
                "the country", "country",
                "the government", "government",
                "the state", "state",
                "the people", "people",
                "the law", "law",
                "the war", "war",
                "the system", "system",
                "the process", "process",
                "the event", "event");

        return generic.contains(lower);
    }

    private boolean containsConceptBreakingVerb(String lower) {
        return lower.contains(" caused ")
                || lower.contains(" led to ")
                || lower.contains(" resulted in ")
                || lower.contains(" brought about ")
                || lower.contains(" occurred ")
                || lower.contains(" happened ")
                || lower.contains(" was ")
                || lower.contains(" were ")
                || lower.contains(" is ")
                || lower.contains(" are ");
    }

    private boolean isSpecificThePhrase(String candidate) {
        if (candidate == null || candidate.isBlank())
            return false;

        String[] words = candidate.trim().split("\\s+");
        if (words.length < 2 || !words[0].equalsIgnoreCase("The"))
            return false;

        // Reject short/generic phrases like "The country"
        if (words.length == 2) {
            String second = words[1].toLowerCase(Locale.ROOT);
            Set<String> genericSecondWords = Set.of(
                    "country", "government", "state", "people", "war", "system", "process", "event");
            if (genericSecondWords.contains(second))
                return false;
        }

        return true;
    }

    private String cleanConcept(String concept) {
        if (concept == null)
            return null;

        String c = concept.trim();

        c = c.replaceAll("^[^A-Za-z0-9]+", "");
        c = c.replaceAll("[^A-Za-z0-9]+$", "");
        c = c.replaceAll("\\s{2,}", "").trim();

        return c;
    }

    private boolean isValidStudyConcept(String concept) {
        if (concept == null) {
            return false;
        }

        String c = cleanConcept(concept);
        if (c == null || c.isBlank()) {
            return false;
        }

        String lower = c.toLowerCase(Locale.ROOT);

        // Too short/too long
        if (c.length() < 3 || c.length() > 40) {
            return false;
        }

        // Too many words usually means clause, not concept
        String[] words = c.split("\\s+");
        if (words.length < 1 || words.length > 4) {
            return false;
        }

        // Bad sentence opener concepts
        Set<String> badStarts = Set.of(
                "although", "because", "during", "following", "after", "before",
                "when", "while", "since", "if", "then", "however", "therefore");

        if (badStarts.contains(words[0].toLowerCase(Locale.ROOT))) {
            return false;
        }

        // Overly generic concepts
        Set<String> bannedExact = Set.of(
                "the country", "country", "the government", "government",
                "the people", "people", "the state", "state", "the war", "war");

        if (bannedExact.contains(lower)) {
            return false;
        }

        // Looks too clauselike
        if (lower.contains(" caused ")
                || lower.contains(" led to ")
                || lower.contains(" resulted in ")
                || lower.contains(" was ")
                || lower.contains(" were ")
                || lower.contains(" is ")
                || lower.contains(" are ")) {
            return false;
        }

        // Avoid incomplete phrases like "The Pro"
        if (words.length == 2
                && (words[0].equalsIgnoreCase("the")
                        || words[0].equalsIgnoreCase("a")
                        || words[0].equalsIgnoreCase("an"))
                && words[1].length() <= 3) {
            return false;
        }

        return true;
    }

    private boolean isUsableConcept(String concept) {
        if (concept == null || concept.isBlank())
            return false;

        String c = concept.trim();
        String lower = c.toLowerCase(Locale.ROOT);

        if (c.length() < 4 || c.length() > 60)
            return false;

        if (countWords(c) > 4)
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
                || lower.equals("examples")
                || lower.equals("example")
                || lower.equals("for")
                || lower.equals("to")
                || lower.equals("whether")
                || lower.equals("in")
                || lower.equals("on")
                || lower.equals("at")
                || lower.equals("both types")
                || lower.equals("understanding")
                || lower.equals("similarly")
                || lower.equals("first")
                || lower.equals("next")
                || lower.equals("then")
                || lower.equals("finally")
                || lower.equals("another example")
                || lower.equals("one example")
                || lower.equals("this example")
                || lower.equals("an example")
                || lower.equals("although")
                || lower.equals("however")
                || lower.equals("therefore")
                || lower.equals("yet")
                || lower.equals("but")
                || lower.equals("that time")
                || lower.equals("this time")
                || lower.equals("that day")
                || lower.equals("this day")
                || lower.equals("today")
                || lower.equals("yesterday")
                || lower.equals("tomorrow"))
            return false;

        if (lower.startsWith("for ")
                || lower.startsWith("to ")
                || lower.startsWith("whether ")
                || lower.startsWith("these ")
                || lower.startsWith("those ")
                || lower.startsWith("this ")
                || lower.startsWith("in organisations")
                || lower.startsWith("in organizations")
                || lower.startsWith("it ")
                || lower.startsWith("another example")
                || lower.startsWith("one example")
                || lower.startsWith("an example")
                || lower.startsWith("example ")
                || lower.startsWith("his ")
                || lower.startsWith("her ")
                || lower.startsWith("their ")
                || lower.startsWith("its ")
                || lower.startsWith("he ")
                || lower.startsWith("she ")
                || lower.startsWith("they ")
                || lower.startsWith("although ")
                || lower.startsWith("however ")
                || lower.startsWith("therefore ")
                || lower.startsWith("yet ")
                || lower.startsWith("but ")
                || lower.startsWith("at that time")
                || lower.startsWith("at this time")
                || lower.startsWith("on that day")
                || lower.startsWith("in 1")
                || lower.startsWith("born ")
                || lower.startsWith("after ")
                || lower.startsWith("before ")
                || lower.startsWith("during ")
                || lower.startsWith("following "))
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
        if (concept == null || concept.isBlank())
            return "what is this concept";

        String clean = cleanConcept(concept);
        if (clean == null || clean.isBlank())
            return "what is this concept";

        if (looksLikePersonName(cleaned))
            return "Who was " + cleaned + "?";

        if (looksPluralConcept(cleaned))
            return "What are " + cleaned + "?";

        return "What is " + clean + "?";
    }

    private boolean looksLikePersonName(String concept) {
        if (concept == null || concept.isBlank())
            return false;

        String cleaned = cleanConcept(concept);
        if (cleaned == null || cleaned.isBlank())
            return false;

        String[] words = cleaned.split("\\s+");
        if (words.length < 2 || words.length > 4)
            return false;

        int capitalizedWords = 0;
        for (String word : words) {
            String w = word.replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "");
            if (w.isBlank()) {
                continue;
            }

            if (Character.isUpperCase(w.charAt(0))) {
                capitalizedWords++;
            }
        }

        return capitalizedWords >= 2;
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

            String front = buildQuestionFromConcept(concept);
            if (!isGoodFlashcardFront(front))
                continue;

            if (countWords(concept) > 4)
                continue;

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(front);
            card.setBack(shorten(sentence, 160));
            card.setSourceSnippet(sentence);

            String topicTag = assignTopicTag(sentence, conceptKey, topicLabels);
            card.setTags(Collections.singletonList(topicTag));

            usedConcepts.add(conceptKey);
            usedSnippets.add(sentence);
            cards.add(card);
        }

        // If fail to make enough, fallback, use random sentences as "front/back"
        /**
         * Random r = new Random();
         * int attempts = 0;
         * while (cards.size() < count && !sentences.isEmpty() && attempts < 5000) {
         * attempts++;
         * String s = sentences.get(r.nextInt(sentences.size()));
         * if (s == null || s.isBlank())
         * continue;
         * if (usedSnippets.contains(s))
         * continue;
         * if (!isStrongEducationalSentence(s))
         * continue;
         * 
         * StudyPack.Flashcard card = new StudyPack.Flashcard();
         * card.setFront(buildFallbackFlashcardFront(s));
         * card.setBack(shorten(s, 160));
         * card.setSourceSnippet(s);
         * card.setTags(Collections.singletonList(TOPIC_GENERAL));
         * usedSnippets.add(s);
         * cards.add(card);
         * }
         **/
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

    private String stripLeadingPhrases(String sentence) {
        if (sentence == null)
            return null;

        String s = sentence.trim();

        s = s.replaceFirst("(?i)^to begin,\\s*", "");
        s = s.replaceFirst("(?i)^for example,\\s*", "");
        s = s.replaceFirst("(?i)^for instance,\\s*", "");
        s = s.replaceFirst("(?i)^in organizations,\\s*", "");
        s = s.replaceFirst("(?i)^in summary,\\s*", "");
        s = s.replaceFirst("(?i)^whether in [^,]+,\\s*", "");
        s = s.replaceFirst("(?i)^on the other hand,\\s*", "");

        s = s.replaceFirst("(?i)^another example is\\s+", "");
        s = s.replaceFirst("(?i)^another example of [^,]+ is\\s+", "");
        s = s.replaceFirst("(?i)^one example is\\s+", "");
        s = s.replaceFirst("(?i)^this example is\\s+", "");
        s = s.replaceFirst("(?i)^an example is\\s+", "");
        s = s.replaceFirst("(?i)^in organisations,\\s*", "");

        return s.trim();
    }

    private boolean isGoodFlashcardFront(String front) {
        if (front == null || front.isBlank())
            return false;

        String f = front.trim().toLowerCase(Locale.ROOT);

        if (f.equals("what is the?")
                || f.equals("what is in?")
                || f.equals("what is though?")
                || f.equals("what is despite?")
                || f.equals("what is with?")
                || f.equals("what is on?")
                || f.equals("what is at?")
                || f.equals("what is for?")
                || f.equals("what is to?")
                || f.equals("what is the?")
                || f.equals("what is similarly,?")
                || f.equals("what is whether?")
                || f.equals("what is next,?")
                || f.equals("what is then,?")
                || f.equals("what is finally,?")
                || f.equals("what is first,?")
                || f.equals("what is it?")
                || f.equals("what is this?")
                || f.equals("what is next,?")) {
            return false;
        }

        if (f.matches("what is (the|in|on|at|for|to|though|despite|with)\\??"))
            return false;

        if (f.matches("what is [a-z]+,\\??"))
            return false;

        return true;
    }

    // Generating Matching Pairs using Flashcards
    private List<StudyPack.MatchingPair> generateMatchingPairs(List<StudyPack.Flashcard> flashcards, int count) {
        List<StudyPack.MatchingPair> pairs = new ArrayList<>();
        for (StudyPack.Flashcard fc : flashcards) {
            if (pairs.size() >= count)
                break;

            if (fc == null)
                continue;
            String left = fc.getFront();
            String right = shorten(fc.getBack(), 120);

            if (!isGoodFlashcardFront(left))
                continue;

            if (right == null || right.isBlank())
                continue;
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
            if (!isValidStudyConcept(concept))
                continue;

            String cleanedConcept = cleanConcept(concept);
            // Try to blank the answer (whole word)
            String sentenceWithBlank = blankOutWholeWord(sentence, cleanedConcept);
            if (sentenceWithBlank == null || sentenceWithBlank.equals(sentence))
                continue;

            // Reject ugly blanks at the start that break grammar
            if (sentenceWithBlank.startsWith("____ was quickly suppressed")
                    || sentenceWithBlank.startsWith("____,")
                    || sentenceWithBlank.startsWith("____ was governed")) {
                // allow some start blanks later if needed, but reject the current ugly cases
                continue;
            }

            String key = cleanedConcept.toLowerCase(Locale.ROOT) + "|" + sentence;
            if (!used.add(key))
                continue;

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setAnswer(formatConceptLabel(cleanedConcept));
            q.setSentenceWithBlank(sentenceWithBlank);
            q.setChoices(Collections.emptyList());
            q.setSourceSnippet(sentence);

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
    private List<StudyPack.TrueFalseQuestion> generateTrueFalseQuestions(List<String> factSentences,
            List<String> factConcepts,
            int count, Random r) {
        List<StudyPack.TrueFalseQuestion> out = new ArrayList<>();
        Set<String> usedStatements = new HashSet<>();

        int trueCount = Math.max(1, count / 2);
        int falseCount = Math.max(1, count - trueCount);
        // True questions
        for (int i = 0; out.size() < trueCount && i < factSentences.size(); i++) {
            String s = factSentences.get(i);
            String shortened = shorten(s, 220);
            if (!usedStatements.add(shortened.toLowerCase(Locale.ROOT))) {
                continue;
            }

            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(shortened);
            q.setAnswer(true);
            q.setExplanation("This statement appears in the uploaded document.");
            q.setSourceSnippet(s);
            out.add(q);
        }

        // False questions via keyword swap
        int attempts = 0;
        int falseAdded = 0;
        while (falseAdded < falseCount && attempts < 600) {
            attempts++;
            if (factSentences.isEmpty() || factConcepts.size() < 2)
                break;
            String s = factSentences.get(r.nextInt(factSentences.size()));
            if (!isGoodFalseQuestionSentence(s))
                continue;

            String concept = extractConceptFromSentence(s);
            if (!isValidStudyConcept(concept))
                continue;

            String cleanedConcept = cleanConcept(concept);
            String replacement = pickSmartDistractorFromConcepts(cleanedConcept, factConcepts, r);
            if (!isValidStudyConcept(replacement))
                continue;

            replacement = cleanConcept(replacement);
            if (replacement.equalsIgnoreCase(cleanedConcept))
                continue;

            if (!isReasonableReplacementPair(cleanedConcept, replacement))
                continue;

            String falseStmt = replaceFirstWholePhraseCaseInsensitive(s, cleanedConcept, replacement);
            if (falseStmt.equals(s) || falseStmt == null)
                continue;

            String lowerFalse = falseStmt.toLowerCase(Locale.ROOT);
            if (lowerFalse.contains("although ") || lowerFalse.contains("because "))
                continue;

            String shortened = shorten(falseStmt, 220);
            if (!usedStatements.add(shortened.toLowerCase(Locale.ROOT)))
                continue;

            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(shortened);
            q.setAnswer(false);
            q.setExplanation("One key term was changed, so this statement does not match the document.");
            q.setSourceSnippet(s);
            out.add(q);
        }
        return out;
    }

    private boolean isGoodFalseQuestionSentence(String sentence) {
        if (sentence == null || sentence.isBlank())
            return false;

        String lower = sentence.toLowerCase(Locale.ROOT).trim();

        // Avoid opening connector sentences
        if (lower.startsWith("next,")
                || lower.startsWith("then,")
                || lower.startsWith("finally,")
                || lower.startsWith("after ")
                || lower.startsWith("during ")
                || lower.startsWith("because ")
                || lower.startsWith("although ")
                || lower.startsWith("when ")) {
            return false;
        }

        // Prefer definition/structured sentences
        return lower.contains(" is ")
                || lower.contains(" are ")
                || lower.contains(" was ")
                || lower.contains(" were ")
                || lower.contains(" involves ")
                || lower.contains(" includes ")
                || lower.contains(" consists of ")
                || lower.contains(" refers to ");
    }

    private boolean isReasonableReplacementPair(String original, String replacement) {
        if (original == null || replacement == null)
            return false;

        int originalWords = countWords(original);
        int replacementWords = countWords(replacement);

        // Do not swap a 1-word term with a 4-word phrase unless necessary
        if (Math.abs(originalWords - replacementWords) > 2)
            return false;

        // Avoid huge length mismatch
        if (Math.abs(original.length() - replacement.length()) > 20)
            return false;

        return true;
    }

    private boolean isNaturalFalseStatement(String statement) {
        if (statement == null || statement.isBlank())
            return false;

        String lower = statement.toLowerCase(Locale.ROOT).trim();

        // Generic bad starts
        if (lower.startsWith("although ")
                || lower.startsWith("because ")
                || lower.startsWith("when ")
                || lower.startsWith("while "))
            return false;

        // Reject obviously broken punctuation
        if (lower.contains("____"))
            return false;

        // Reject shortness
        if (statement.length() < 20)
            return false;

        return true;
    }

    private String pickSmartDistractorFromConcepts(String answer, List<String> concepts, Random random) {
        if (answer == null || concepts == null || concepts.isEmpty() || answer.isBlank() || random == null)
            return null;
        String cleanedAnswer = cleanConcept(answer);
        if (!isValidStudyConcept(cleanedAnswer))
            return null;

        String a = cleanedAnswer.toLowerCase(Locale.ROOT);
        int targetWords = countWords(cleanedAnswer);
        int targetLen = cleanedAnswer.length();
        boolean answerLooksPlural = looksPluralConcept(cleanedAnswer);
        boolean asnwerStartsWithArticle = startsWithArticle(cleanedAnswer);

        List<String> candidates = new ArrayList<>();
        List<String> fallbackCandidates = new ArrayList<>();

        for (String c : concepts) {
            if (c == null || c.isBlank()) {
                continue;
            }

            String cleaned = cleanConcept(c);
            if (!isValidStudyConcept(cleaned)) {
                continue;
            }

            String lower = cleaned.trim().toLowerCase(Locale.ROOT);
            if (lower.equals(a)) {
                continue;
            }

            int wordCount = countWords(cleaned);
            int len = cleaned.length();
            boolean candidateLooksPlural = looksPluralConcept(cleaned);
            boolean candidateStartsWithArticle = startsWithArticle(cleaned);

            if (Math.abs(wordCount - targetWords) > 1) {
                continue;
            }
            if (Math.abs(lower.length() - targetLen) > 20) {
                continue;
            }

            boolean similarPlurality = (answerLooksPlural == candidateLooksPlural);
            boolean similarArticleStyle = (asnwerStartsWithArticle == candidateStartsWithArticle);
            if (similarPlurality && similarArticleStyle) {
                candidates.add(cleaned);
            } else {
                fallbackCandidates.add(cleaned);
            }
        }

        if (!candidates.isEmpty()) {
            int idx = random.nextInt(candidates.size());
            return formatConceptLabel(candidates.get(idx));
        }

        if (!fallbackCandidates.isEmpty()) {
            int idx = random.nextInt(fallbackCandidates.size());
            return formatConceptLabel(fallbackCandidates.get(idx));
        }

        return null;
    }

    private boolean startsWithArticle(String text) {
        if (text == null || text.isBlank())
            return false;

        String lower = text.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("a ")
                || lower.startsWith("an ")
                || lower.startsWith("the ");
    }

    private boolean looksPluralConcept(String text) {
        if (text == null || text.isBlank())
            return false;

        String lower = text.trim().toLowerCase(Locale.ROOT);

        if (lower.contains(" and "))
            return true;

        String[] words = lower.split("\\s+");
        if (words.length == 0)
            return false;

        String last = words[words.length - 1];

        // crude but useful heuristic
        return last.endsWith("s") && !last.endsWith("ss");
    }

    private String replaceFirstWholePhraseCaseInsensitive(String sentence, String from, String to) {
        if (sentence == null || from == null || to == null)
            return null;

        Pattern p = Pattern.compile("\\b" + Pattern.quote(from) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sentence);
        if (!m.find())
            return sentence;

        return m.replaceFirst(Matcher.quoteReplacement(formatConceptLabel(to)));
    }

    // Generating MCQ
    private List<StudyPack.McqQuestion> generateMcqQuestionsFromCloze(List<StudyPack.ClozeQuestion> cloze,
            List<String> keywords,
            int count) {
        List<StudyPack.McqQuestion> out = new ArrayList<>();
        Set<String> usedQuestions = new HashSet<>();

        long seed = Objects.hash(cloze != null ? cloze.size() : 0,
                keywords != null ? keywords.size() : 0,
                count);
        Random r = new Random(seed);

        if (cloze == null || cloze.isEmpty() || keywords == null || keywords.isEmpty()) {
            return out;
        }

        for (StudyPack.ClozeQuestion cq : cloze) {
            if (out.size() >= count || cq == null)
                break;

            String question = cq.getSentenceWithBlank();
            String answer = cq.getAnswer();
            if (answer == null || answer.isBlank() || question == null || question.isEmpty())
                continue;

            if (!usedQuestions.add(question.toLowerCase(Locale.ROOT)))
                continue;

            LinkedHashSet<String> optionSet = new LinkedHashSet<>();
            optionSet.add(answer);

            // add 3 distractors
            int attempts = 0;
            while (optionSet.size() < 4 && attempts < 200) {
                attempts++;
                String d = pickSmartDistractorFromConcepts(answer, keywords, r);
                if (d == null || d.isBlank())
                    continue;
                if (d.equalsIgnoreCase(answer))
                    continue;

                optionSet.add(d);
            }

            // if cant get enough distractors skip
            if (optionSet.size() < 4)
                continue;

            List<String> options = new ArrayList<>(optionSet);
            Collections.shuffle(options, r);
            int correctIndex = options.indexOf(answer);
            if (correctIndex < 0)
                continue;

            StudyPack.McqQuestion q = new StudyPack.McqQuestion();
            q.setQuestion(question);
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