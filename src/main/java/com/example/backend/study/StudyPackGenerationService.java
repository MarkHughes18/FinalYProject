package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import com.example.backend.repository.StudyPackRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.*;
import java.util.function.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.regex.Matcher;

import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.study.dto.FlashcardDto;
import com.example.backend.study.dto.ClozeQuestionDto;
import com.example.backend.study.dto.McqQuestionDto;
import com.example.backend.study.dto.TrueFalseQuestionDto;
import com.example.backend.study.StudyPackLlmService;
import com.example.backend.study.StudyPackValidator;
import com.example.backend.study.dto.CustomStudyPackRequest;

@Service
public class StudyPackGenerationService {

    // Pack sizes
    private static final int FLASHCARDS_COUNT = 7;
    private static final int MATCHING_COUNT = 5;
    private static final int CLOZE_COUNT = 7;
    private static final int TF_COUNT = 10;
    private static final int MCQ_COUNT = 7;

    // Safety caps for huge narrationText
    private static final int MAX_TEXT_CHARS = 300_000;
    private static final int MAX_SENTENCES = 2_000;
    private static final int MAX_KEYWORDS = 200;
    private static final int TOPIC_LABELS_COUNT = 6;
    private static final String TOPIC_GENERAL = "General";
    private static final int MAX_LATEST_REPEATS = 3;

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z\\-']{2,}");

    private final FileHistoryRepository fileHistoryRepository;
    private final StudyPackRepository studyPackRepository;
    private final StudyPackLlmService studyPackLlmService;

    public StudyPackGenerationService(FileHistoryRepository fileHistoryRepository,
            StudyPackRepository studyPackRepository, StudyPackLlmService studyPackLlmService) {
        this.fileHistoryRepository = fileHistoryRepository;
        this.studyPackRepository = studyPackRepository;
        this.studyPackLlmService = studyPackLlmService;
    }

    public StudyPack getOrGenerate(String userEmail, String historyId) {
        Optional<StudyPack> existingActive = studyPackRepository
                .findByUserEmailAndHistoryIdAndActiveTrue(userEmail, historyId);

        if (existingActive.isPresent()) {
            return existingActive.get();
        }

        FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
        if (fh == null) {
            throw new IllegalArgumentException("File history not found for file id: " + historyId);
        }

        if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new IllegalArgumentException("Not allowed: history file does not belong to user");
        }

        return generatePackVersion(userEmail, fh, 1, null);
    }

    public StudyPack regenerate(String userEmail, String historyId) {
        FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
        if (fh == null) {
            throw new IllegalArgumentException("File history not found for file id: " + historyId);
        }

        if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new IllegalArgumentException("Not allowed: history file does not belong to user");
        }

        // Find current active pack, if any
        Optional<StudyPack> activePackOpt = studyPackRepository.findByUserEmailAndHistoryIdAndActiveTrue(userEmail,
                historyId);

        // Find latest version number
        Optional<StudyPack> latestPackOpt = studyPackRepository
                .findFirstByUserEmailAndHistoryIdOrderByVersionNumberDesc(userEmail, historyId);

        int nextVersion = latestPackOpt
                .map(StudyPack::getVersionNumber)
                .filter(Objects::nonNull)
                .map(version -> version + 1)
                .orElse(1);

        String regeneratedFromPackId = null;

        if (activePackOpt.isPresent()) {
            StudyPack activePack = activePackOpt.get();
            activePack.setActive(false);
            activePack.setUpdatedAt(Instant.now());
            studyPackRepository.save(activePack);
            regeneratedFromPackId = activePack.getId();
        }

        return generatePackVersion(userEmail, fh, nextVersion, regeneratedFromPackId);
    }

    public StudyPack createCustomStudyPack(CustomStudyPackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Custom study pack request is missing");
        }

        String userEmail = request.getUserEmail();
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }

        List<String> flashcardSources = cleanCustomSnippets(request.getFlashcardSnippets());
        List<String> clozeSources = cleanCustomSnippets(request.getClozeSnippets());
        List<String> trueFalseSources = cleanCustomSnippets(request.getTrueFalseSnippets());
        List<String> mcqSources = cleanCustomSnippets(request.getMcqSnippets());
        List<String> matchingSources = cleanCustomSnippets(request.getMatchingSnippets());

        if (flashcardSources.size() < 5 || clozeSources.size() < 5
                || trueFalseSources.size() < 5 || mcqSources.size() < 5 || matchingSources.size() < 5) {
            throw new IllegalArgumentException("At least 5 snippets are required for each study mode");
        }

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            title = "Custom Study Pack";
        }

        StudyPack.StudyPackSettings settings = new StudyPack.StudyPackSettings(
                FLASHCARDS_COUNT, MATCHING_COUNT, CLOZE_COUNT, TF_COUNT, MCQ_COUNT, "EASY");

        List<FlashcardDto> flashcardDtos;
        List<ClozeQuestionDto> clozeDtos;
        TrueFalsePackResponse tfPack;
        ConceptPackResponse mcqPack;

        try {
            flashcardDtos = studyPackLlmService.generateFlashcardsFromSnippets(flashcardSources);
            clozeDtos = studyPackLlmService.generateClozeQuestionsFromSnippets(clozeSources);

            tfPack = studyPackLlmService.generateTrueFalsePack(
                    trueFalseSources,
                    trueFalseSources,
                    settings);

            mcqPack = studyPackLlmService.generateConceptPack(
                    mcqSources,
                    mcqSources,
                    List.of("Custom Study Pack"),
                    new StudyPack.StudyPackSettings(0, 0, 0, 0, MCQ_COUNT, "EASY"));

        } catch (Exception e) {
            throw new RuntimeException("Custom study pack generation failed: " + e.getMessage(), e);
        }

        List<StudyPack.Flashcard> flashcards = mapFlashcardsFromDtos(flashcardDtos);
        List<StudyPack.ClozeQuestion> clozeQuestions = mapClozeQuestionsFromDtos(clozeDtos);
        List<StudyPack.TrueFalseQuestion> tfQuestions = mapTrueFalseQuestions(tfPack);
        List<StudyPack.McqQuestion> mcqQuestions = mapMcqQuestions(mcqPack);

        StudyPack tempPack = new StudyPack();
        tempPack.setFlashcards(flashcards);
        tempPack.setClozeQuestions(clozeQuestions);
        tempPack.setTrueFalseQuestions(tfQuestions);
        tempPack.setMcqQuestions(mcqQuestions);

        enforceFinalStudyPackQuality(tempPack);
        ensureMinimumCountsForCustomPack(tempPack, clozeSources, mcqSources);

        flashcards = tempPack.getFlashcards();
        clozeQuestions = tempPack.getClozeQuestions();
        tfQuestions = tempPack.getTrueFalseQuestions();
        mcqQuestions = tempPack.getMcqQuestions();

        List<StudyPack.MatchingPair> matchingPairs = generateCustomMatchingPairs(matchingSources, MATCHING_COUNT);

        Instant now = Instant.now();

        FileHistory fh = new FileHistory();
        fh.setUserEmail(userEmail);
        fh.setFileName(title);
        fh.setFileType("custom");
        fh.setLabel("Study");
        fh.setUploadedAt(now);
        fh.setUpdatedAt(now);
        fh.setTextStatus("READY");
        fh.setNarrationStatus("READY");
        fh.setExtractedText(String.join("\n", mergeSnippetLists(
                flashcardSources, clozeSources, trueFalseSources, mcqSources, matchingSources)));
        fh.setNarrationText(fh.getExtractedText());

        FileHistory savedHistory = fileHistoryRepository.save(fh);

        StudyPack pack = new StudyPack();
        pack.setUserEmail(userEmail);
        pack.setHistoryId(savedHistory.getId());
        pack.setCreatedAt(now);
        pack.setUpdatedAt(now);
        pack.setSourceHash(sha256Hex(savedHistory.getNarrationText()));

        pack.setVersionNumber(1);
        pack.setActive(true);
        pack.setRegeneratedFromPackId(null);

        pack.setFileName(title);
        pack.setFileLabel("Study");
        pack.setSettings(settings);

        pack.setFlashcards(flashcards);
        pack.setMatchingPairs(matchingPairs);
        pack.setClozeQuestions(clozeQuestions);
        pack.setTrueFalseQuestions(tfQuestions);
        pack.setMcqQuestions(mcqQuestions);

        pack.setUsedCandidates(buildCandidateUsage(pack));

        return studyPackRepository.save(pack);
    }

    private StudyPack generatePackVersion(String userEmail,
            FileHistory fh,
            int versionNumber,
            String regeneratedFromPackId) {

        // Generate from narrationText, fallback to extractedText
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

        // Sentence-first pipeline
        List<String> factSentences = extractEducationalSentences(sentences, 120);
        if (factSentences.isEmpty()) {
            factSentences = new ArrayList<>(sentences);
        }

        // Build sentence pools for different features to pull from
        Map<String, List<String>> pools = buildSentencePools(factSentences);
        List<String> definitionSentences = pools.getOrDefault("definitions", Collections.emptyList());
        List<String> processSentences = pools.getOrDefault("processes", Collections.emptyList());
        List<String> detailSentences = pools.getOrDefault("details", Collections.emptyList());

        List<String> flashcardPool = buildBalancedPool(definitionSentences, processSentences, detailSentences, 8, 6, 6,
                factSentences);

        List<String> clozePool = buildBalancedPool(definitionSentences, processSentences, detailSentences, 8, 6, 6,
                factSentences);

        List<String> tfPool = buildBalancedPool(processSentences, detailSentences, definitionSentences, 10, 10, 8,
                factSentences);

        List<String> safeProcessPool = processSentences.isEmpty() ? new ArrayList<>(tfPool)
                : new ArrayList<>(processSentences);

        List<String> safeDetailPool = detailSentences.isEmpty() ? new ArrayList<>(tfPool)
                : new ArrayList<>(detailSentences);

        List<String> factConcepts = extractConceptsFromFacts(factSentences, MAX_KEYWORDS);

        List<StudyPack> previousPacks = studyPackRepository
                .findByUserEmailAndHistoryIdOrderByVersionNumberDesc(userEmail, fh.getId());

        Set<String> latestFlashcardSourceIds = collectLatestVersionFlashcardSourceIds(previousPacks);
        Set<String> latestClozeSourceIds = collectLatestVersionClozeSourceIds(previousPacks);
        Set<String> latestTrueFalseSourceIds = collectLatestVersionTrueFalseSourceIds(previousPacks);

        // Preselect source snippets for regeneration
        // Keep some strong repeated anchors, but force some fresher snippets too
        List<String> selectedFlashcardSources = selectFreshSourceSnippetsForMode(flashcardPool,
                latestFlashcardSourceIds,
                "flashcard-src", 10, versionNumber);

        List<String> selectedClozeSources = selectFreshSourceSnippetsForMode(clozePool, latestClozeSourceIds,
                "cloze-src", 10, versionNumber);

        List<String> selectedTrueFalseProcessSources = selectFreshSourceSnippetsForMode(safeProcessPool,
                latestTrueFalseSourceIds,
                "tf-src", 12, versionNumber);

        List<String> selectedTrueFalseDetailSources = selectFreshSourceSnippetsForMode(safeDetailPool,
                latestTrueFalseSourceIds,
                "tf-src", 12, versionNumber);

        // Pick some topic labels from keywords
        List<String> topicLabels = pickTopicLabels(factConcepts, TOPIC_LABELS_COUNT);

        // Settings metadata
        StudyPack.StudyPackSettings settings = new StudyPack.StudyPackSettings(
                FLASHCARDS_COUNT, MATCHING_COUNT, CLOZE_COUNT, TF_COUNT, MCQ_COUNT, "EASY");

        List<FlashcardDto> flashcardDtos;
        List<ClozeQuestionDto> clozeDtos;
        TrueFalsePackResponse tfPack;
        ConceptPackResponse mcqConceptPack;

        try {
            flashcardDtos = studyPackLlmService.generateFlashcardsFromSnippets(selectedFlashcardSources);

            clozeDtos = studyPackLlmService.generateClozeQuestionsFromSnippets(selectedClozeSources);

            tfPack = studyPackLlmService.generateTrueFalsePack(
                    selectedTrueFalseProcessSources,
                    selectedTrueFalseDetailSources,
                    settings);

            // Keep MCQ generation on existing concept-pack path for now
            mcqConceptPack = studyPackLlmService.generateConceptPack(selectedFlashcardSources, selectedClozeSources,
                    topicLabels,
                    new StudyPack.StudyPackSettings(0, 0, 0, 0, MCQ_COUNT, "EASY"));

        } catch (Exception e) {
            throw new RuntimeException("Study pack generation failed: " + e.getMessage(), e);
        }

        List<StudyPack.Flashcard> flashcards = mapFlashcardsFromDtos(flashcardDtos);
        List<StudyPack.ClozeQuestion> clozeQuestions = mapClozeQuestionsFromDtos(clozeDtos);
        List<StudyPack.McqQuestion> mcqQuestions = mapMcqQuestions(mcqConceptPack);
        List<StudyPack.TrueFalseQuestion> tfQuestions = mapTrueFalseQuestions(tfPack);

        StudyPack tempPack = new StudyPack();
        tempPack.setFlashcards(flashcards);
        tempPack.setClozeQuestions(clozeQuestions);
        tempPack.setTrueFalseQuestions(tfQuestions);
        tempPack.setMcqQuestions(mcqQuestions);

        // Top up first, because top-up can add older/repeated items
        topUpMinimumCountsFromPreviousPack(tempPack, previousPacks);

        // Final duplicate guard AFTER top-up
        enforceFinalStudyPackQuality(tempPack);

        flashcards = tempPack.getFlashcards();
        clozeQuestions = tempPack.getClozeQuestions();
        tfQuestions = tempPack.getTrueFalseQuestions();
        mcqQuestions = tempPack.getMcqQuestions();

        // Matching must be rebuilt from the final cleaned flashcards
        List<StudyPack.MatchingPair> matchingPairs = generateMatchingPairs(flashcards, MATCHING_COUNT);

        Instant now = Instant.now();

        StudyPack pack = new StudyPack();
        pack.setUserEmail(userEmail);
        pack.setHistoryId(fh.getId());
        pack.setCreatedAt(now);
        pack.setUpdatedAt(now);
        pack.setSourceHash(sourceHash);

        pack.setVersionNumber(versionNumber);
        pack.setActive(true);
        pack.setRegeneratedFromPackId(regeneratedFromPackId);

        pack.setFileName(fh.getFileName());
        pack.setFileLabel(fh.getLabel());

        pack.setSettings(settings);

        pack.setFlashcards(flashcards);
        pack.setMatchingPairs(matchingPairs);
        pack.setClozeQuestions(clozeQuestions);
        pack.setTrueFalseQuestions(tfQuestions);
        pack.setMcqQuestions(mcqQuestions);

        // Build usage LAST so it reflects the final saved pack
        pack.setUsedCandidates(buildCandidateUsage(pack));

        return studyPackRepository.save(pack);
    }

    private Set<String> collectLatestVersionFlashcardSourceIds(List<StudyPack> previousPacks) {
        Set<String> ids = new HashSet<>();
        if (previousPacks == null || previousPacks.isEmpty()) {
            return ids;
        }

        StudyPack latest = previousPacks.get(0);
        if (latest.getFlashcards() == null) {
            return ids;
        }

        for (StudyPack.Flashcard card : latest.getFlashcards()) {
            if (card == null || card.getSourceSnippet() == null || card.getSourceSnippet().isBlank()) {
                continue;
            }
            ids.add(buildSourceSentenceId("flashcard-src", card.getSourceSnippet()));
        }

        return ids;
    }

    private Set<String> collectLatestVersionClozeSourceIds(List<StudyPack> previousPacks) {
        Set<String> ids = new HashSet<>();
        if (previousPacks == null || previousPacks.isEmpty()) {
            return ids;
        }

        StudyPack latest = previousPacks.get(0);
        if (latest.getClozeQuestions() == null) {
            return ids;
        }

        for (StudyPack.ClozeQuestion q : latest.getClozeQuestions()) {
            if (q == null || q.getSourceSnippet() == null || q.getSourceSnippet().isBlank()) {
                continue;
            }
            ids.add(buildSourceSentenceId("cloze-src", q.getSourceSnippet()));
        }

        return ids;
    }

    private Set<String> collectLatestVersionTrueFalseSourceIds(List<StudyPack> previousPacks) {
        Set<String> ids = new HashSet<>();
        if (previousPacks == null || previousPacks.isEmpty()) {
            return ids;
        }

        StudyPack latest = previousPacks.get(0);
        if (latest.getTrueFalseQuestions() == null) {
            return ids;
        }

        for (StudyPack.TrueFalseQuestion q : latest.getTrueFalseQuestions()) {
            if (q == null || q.getSourceSnippet() == null || q.getSourceSnippet().isBlank()) {
                continue;
            }
            ids.add(buildSourceSentenceId("tf-src", q.getSourceSnippet()));
        }

        return ids;
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

    private List<String> buildBalancedPool(List<String> definitions,
            List<String> processes,
            List<String> details,
            int definitionLimit,
            int processLimit,
            int detailLimit,
            List<String> fallbackPool) {

        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addUpTo(result, seen, definitions, definitionLimit);
        addUpTo(result, seen, processes, processLimit);
        addUpTo(result, seen, details, detailLimit);

        if (result.isEmpty() && fallbackPool != null) {
            for (String item : fallbackPool) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                if (seen.add(item)) {
                    result.add(item);
                }
            }
        }

        return result;
    }

    private void addUpTo(List<String> target,
            Set<String> seen,
            List<String> source,
            int limit) {
        if (source == null || limit <= 0) {
            return;
        }

        int added = 0;
        for (String item : source) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (seen.add(item)) {
                target.add(item);
                added++;
            }
            if (added >= limit) {
                break;
            }
        }
    }

    private List<String> selectFreshSourceSnippetsForMode(
            List<String> pool,
            Set<String> latestUsedIds,
            String prefix,
            int targetCount,
            int versionNumber) {

        List<String> fresh = new ArrayList<>();
        List<String> latestRepeated = new ArrayList<>();

        if (pool == null) {
            return new ArrayList<>();
        }

        Set<String> seen = new LinkedHashSet<>();

        for (String item : pool) {
            if (item == null || item.isBlank()) {
                continue;
            }

            if (!seen.add(normalizeText(item))) {
                continue;
            }

            String id = buildSourceSentenceId(prefix, item);

            if (latestUsedIds != null && latestUsedIds.contains(id)) {
                latestRepeated.add(item);
            } else {
                fresh.add(item);
            }
        }

        List<String> rotatedFresh = rotateList(fresh, versionNumber - 1);
        List<String> rotatedLatest = rotateList(latestRepeated, versionNumber - 1);

        List<String> result = new ArrayList<>();
        Set<String> resultSeen = new LinkedHashSet<>();

        addUpTo(result, resultSeen, rotatedFresh, targetCount);

        // Emergency fallback only. This prevents v2 from collapsing below minimum.
        if (result.size() < 5) {
            addUpTo(result, resultSeen, rotatedLatest, 5 - result.size());
        }

        return result;
    }

    private List<String> cleanCustomSnippets(List<String> snippets) {
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (snippets == null) {
            return cleaned;
        }

        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }

            String value = normalize(snippet);
            String key = normalizeText(value);

            if (value.length() < 20) {
                continue;
            }

            if (seen.add(key)) {
                cleaned.add(value);
            }
        }

        return cleaned;
    }

    @SafeVarargs
    private final List<String> mergeSnippetLists(List<String>... lists) {
        List<String> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (List<String> list : lists) {
            if (list == null) {
                continue;
            }

            for (String item : list) {
                if (item == null || item.isBlank()) {
                    continue;
                }

                String key = normalizeText(item);
                if (seen.add(key)) {
                    merged.add(item);
                }
            }
        }

        return merged;
    }

    private void ensureMinimumCountsForCustomPack(StudyPack pack, List<String> clozeSources, List<String> mcqSources) {
        if (pack == null)
            return;

        // Flashcards fallback
        if (pack.getFlashcards().size() < 5) {
            pack.setFlashcards(new ArrayList<>(pack.getFlashcards()));
        }

        // Cloze fallback
        if (pack.getClozeQuestions().size() < 5) {
            pack.setClozeQuestions(new ArrayList<>(pack.getClozeQuestions()));
        }

        // MCQ fallback
        if (pack.getMcqQuestions().size() < 5) {
            pack.setMcqQuestions(new ArrayList<>(pack.getMcqQuestions()));
        }

        if (pack.getTrueFalseQuestions() == null || pack.getTrueFalseQuestions().isEmpty()) {
            pack.setTrueFalseQuestions(generateBasicTrueFalseFallback(pack.getFlashcards()));
        }

        topUpCustomClozeFallback(pack, clozeSources, 5);
        topUpCustomMcqFallback(pack, mcqSources, 5);
    }

    private void topUpCustomClozeFallback(StudyPack pack, List<String> sources, int minimum) {
        if (pack.getClozeQuestions().size() >= minimum || sources == null) {
            return;
        }

        Set<String> existing = pack.getClozeQuestions().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.ClozeQuestion::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        for (String source : sources) {
            if (pack.getClozeQuestions().size() >= minimum) {
                return;
            }

            if (!notBlank(source)) {
                continue;
            }

            String key = normalizeText(source);
            if (!existing.add(key)) {
                continue;
            }

            String[] words = source.trim().split("\\s+");
            if (words.length < 4) {
                continue;
            }

            String answer = words[0].replaceAll("[^A-Za-z0-9]", "");
            if (!notBlank(answer)) {
                continue;
            }

            String sentenceWithBlank = source.replaceFirst(Pattern.quote(words[0]), "_____");

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setSentenceWithBlank(sentenceWithBlank);
            q.setAnswer(answer);
            q.setChoices(buildBasicChoices(answer));
            q.setSourceSnippet(source);

            pack.getClozeQuestions().add(q);
        }
    }

    private void topUpCustomMcqFallback(StudyPack pack, List<String> sources, int minimum) {
        if (pack.getMcqQuestions().size() >= minimum || sources == null) {
            return;
        }

        Set<String> existing = pack.getMcqQuestions().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.McqQuestion::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        List<String> concepts = sources.stream()
                .filter(this::notBlank)
                .map(s -> s.trim().split("\\s+")[0].replaceAll("[^A-Za-z0-9]", ""))
                .filter(this::notBlank)
                .distinct()
                .collect(Collectors.toList());

        for (String source : sources) {
            if (pack.getMcqQuestions().size() >= minimum) {
                return;
            }

            if (!notBlank(source)) {
                continue;
            }

            String key = normalizeText(source);
            if (!existing.add(key)) {
                continue;
            }

            String[] words = source.trim().split("\\s+");
            if (words.length < 4) {
                continue;
            }

            String answer = words[0].replaceAll("[^A-Za-z0-9]", "");
            if (!notBlank(answer)) {
                continue;
            }

            List<String> options = new ArrayList<>();
            options.add(answer);

            for (String concept : concepts) {
                if (options.size() >= 4) {
                    break;
                }

                if (!concept.equalsIgnoreCase(answer)) {
                    options.add(concept);
                }
            }

            while (options.size() < 4) {
                options.add("Option " + options.size());
            }

            Collections.shuffle(options);
            int correctIndex = options.indexOf(answer);

            StudyPack.McqQuestion q = new StudyPack.McqQuestion();
            q.setQuestion("Which concept best matches this statement: \"" + shorten(source, 90) + "\"?");
            q.setOptions(options);
            q.setCorrectIndex(correctIndex);
            q.setCorrectAnswer(answer);
            q.setExplanation("This question was created from your custom snippet.");
            q.setSourceSnippet(source);

            pack.getMcqQuestions().add(q);
        }
    }

    private List<StudyPack.TrueFalseQuestion> generateBasicTrueFalseFallback(
            List<StudyPack.Flashcard> flashcards) {

        List<StudyPack.TrueFalseQuestion> out = new ArrayList<>();

        if (flashcards == null)
            return out;

        for (StudyPack.Flashcard fc : flashcards) {
            if (fc == null || fc.getBack() == null)
                continue;

            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(fc.getBack());
            q.setAnswer(true);
            q.setExplanation("This statement comes directly from your input.");
            q.setSourceSnippet(fc.getSourceSnippet());

            out.add(q);

            if (out.size() >= 5)
                break;
        }

        return out;
    }

    private List<String> buildBasicChoices(String answer) {
        List<String> choices = new ArrayList<>();
        choices.add(answer);

        List<String> defaults = List.of("Inheritance", "Encapsulation", "Polymorphism", "Abstraction", "Interface");

        for (String option : defaults) {
            if (choices.size() >= 4) {
                break;
            }

            if (!option.equalsIgnoreCase(answer)) {
                choices.add(option);
            }
        }

        return choices;
    }

    private List<String> rotateList(List<String> source, int steps) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> rotated = new ArrayList<>(source);
        int size = rotated.size();

        int shift = steps % size;
        if (shift < 0) {
            shift += size;
        }

        if (shift == 0) {
            return rotated;
        }

        List<String> result = new ArrayList<>(size);
        result.addAll(rotated.subList(shift, size));
        result.addAll(rotated.subList(0, shift));
        return result;
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

            // Prefer phrases as topics
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
                out.add(cleaned);
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
        c = c.replaceAll("\\s{2,}", " ").trim();

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
                || lower.equals("tomorrow")
                || lower.equals("during")
                || lower.equals("throughout")
                || lower.equals("they")
                || lower.equals("them")
                || lower.equals("their")
                || lower.equals("this phase")
                || lower.equals("these phases")
                || lower.equals("those phases")
                || lower.equals("phase")
                || lower.equals("phases"))
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
                || lower.startsWith("following ")
                || lower.startsWith("throughout ")
                || lower.startsWith("this "))
            return false;

        return true;
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

    // Generating Matching Pairs using Flashcards
    private List<StudyPack.MatchingPair> generateMatchingPairs(List<StudyPack.Flashcard> flashcards, int count) {
        List<StudyPack.MatchingPair> pairs = new ArrayList<>();
        for (StudyPack.Flashcard fc : flashcards) {
            if (pairs.size() >= count)
                break;

            if (fc == null) {
                continue;
            }

            String left = fc.getFront();
            String right = fc.getBack();

            if (left == null || left.isBlank()) {
                continue;
            }

            if (right == null || right.isBlank()) {
                continue;
            }

            StudyPack.MatchingPair p = new StudyPack.MatchingPair();
            p.setLeft(left);
            p.setRight(right);
            pairs.add(p);
        }
        return pairs;
    }

    private List<StudyPack.MatchingPair> generateCustomMatchingPairs(List<String> matchingSources, int count) {
        List<StudyPack.MatchingPair> pairs = new ArrayList<>();

        if (matchingSources == null) {
            return pairs;
        }

        for (String source : matchingSources) {
            if (pairs.size() >= count) {
                break;
            }

            if (source == null || source.isBlank()) {
                continue;
            }

            String cleaned = normalize(source);

            StudyPack.MatchingPair pair = new StudyPack.MatchingPair();
            pair.setLeft(buildCustomMatchingLeft(cleaned));
            pair.setRight(shorten(cleaned, 90));

            pairs.add(pair);
        }

        return pairs;
    }

    private String buildCustomMatchingLeft(String source) {
        if (source == null || source.isBlank()) {
            return "Match this concept";
        }

        String[] words = source.trim().split("\\s+");

        if (words.length > 0) {
            String firstWord = words[0].replaceAll("[^A-Za-z0-9]", "");
            if (firstWord != null && !firstWord.isBlank()) {
                return "Match: " + firstWord;
            }
        }

        return "Match this concept";
    }

    // Helpers
    private String shorten(String s, int maxLen) {
        if (s == null) {
            return null;
        }

        String t = s.trim().replaceAll("\\s+", " ");
        ;

        if (t.length() <= maxLen) {
            return t;
        }

        int cut = t.lastIndexOf(" ", maxLen - 3);

        if (cut < 30) {
            cut = maxLen - 3;
        }

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

    private List<StudyPack.McqQuestion> mapMcqQuestions(ConceptPackResponse response) {
        List<StudyPack.McqQuestion> out = new ArrayList<>();
        if (response == null || response.getMcqQuestions() == null) {
            return out;
        }

        for (McqQuestionDto dto : response.getMcqQuestions()) {
            if (dto == null) {
                continue;
            }

            StudyPack.McqQuestion q = new StudyPack.McqQuestion();
            q.setQuestion(dto.getQuestion());
            q.setOptions(dto.getOptions() != null ? dto.getOptions() : Collections.emptyList());
            q.setCorrectIndex(dto.getCorrectIndex());
            q.setCorrectAnswer(dto.getCorrectAnswer());
            q.setExplanation(dto.getExplanation());
            q.setSourceSnippet(dto.getSourceSnippet());
            out.add(q);
        }

        return out;
    }

    private List<StudyPack.TrueFalseQuestion> mapTrueFalseQuestions(TrueFalsePackResponse response) {
        List<StudyPack.TrueFalseQuestion> out = new ArrayList<>();
        if (response == null || response.getTrueFalseQuestions() == null) {
            return out;
        }

        for (TrueFalseQuestionDto dto : response.getTrueFalseQuestions()) {
            if (dto == null) {
                continue;
            }

            StudyPack.TrueFalseQuestion q = new StudyPack.TrueFalseQuestion();
            q.setStatement(dto.getStatement());
            q.setAnswer(Boolean.TRUE.equals(dto.getAnswer()));
            q.setExplanation(dto.getExplanation());
            q.setSourceSnippet(dto.getSourceSnippet());
            out.add(q);
        }

        return out;
    }

    private List<StudyPack.Flashcard> mapFlashcardsFromDtos(List<FlashcardDto> dtos) {
        List<StudyPack.Flashcard> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }

        for (FlashcardDto dto : dtos) {
            if (dto == null || isWeakSourceSnippet(dto.getSourceSnippet())) {
                continue;
            }

            StudyPack.Flashcard card = new StudyPack.Flashcard();
            card.setFront(dto.getFront());
            card.setBack(dto.getBack());
            card.setSourceSnippet(dto.getSourceSnippet());
            card.setTags(Collections.singletonList(TOPIC_GENERAL));
            out.add(card);
        }

        return out;
    }

    private List<StudyPack.ClozeQuestion> mapClozeQuestionsFromDtos(List<ClozeQuestionDto> dtos) {
        List<StudyPack.ClozeQuestion> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }

        for (ClozeQuestionDto dto : dtos) {
            if (dto == null || isWeakSourceSnippet(dto.getSourceSnippet())) {
                continue;
            }

            StudyPack.ClozeQuestion q = new StudyPack.ClozeQuestion();
            q.setSentenceWithBlank(dto.getSentenceWithBlank());
            q.setAnswer(dto.getAnswer());
            q.setChoices(dto.getChoices() != null ? dto.getChoices() : Collections.emptyList());
            q.setSourceSnippet(dto.getSourceSnippet());
            out.add(q);
        }

        return out;
    }

    private String buildCandidateId(String prefix, String... parts) {
        StringBuilder sb = new StringBuilder(prefix);

        if (parts != null) {
            for (String part : parts) {
                sb.append("|");
                sb.append(part == null ? "" : normalizeIdPart(part));
            }
        }

        return prefix + "|" + sha256Hex(sb.toString());
    }

    private String normalizeIdPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private List<String> collectFlashcardIds(List<StudyPack.Flashcard> flashcards) {
        List<String> ids = new ArrayList<>();
        if (flashcards == null) {
            return ids;
        }

        for (StudyPack.Flashcard card : flashcards) {
            if (card == null) {
                continue;
            }
            ids.add(buildCandidateId(
                    "flashcard",
                    card.getFront(),
                    card.getSourceSnippet()));
        }
        return ids;
    }

    private List<String> collectClozeIds(List<StudyPack.ClozeQuestion> questions) {
        List<String> ids = new ArrayList<>();
        if (questions == null) {
            return ids;
        }

        for (StudyPack.ClozeQuestion q : questions) {
            if (q == null) {
                continue;
            }
            ids.add(buildCandidateId(
                    "cloze",
                    q.getAnswer(),
                    q.getSourceSnippet()));
        }
        return ids;
    }

    private List<String> collectTrueFalseIds(List<StudyPack.TrueFalseQuestion> questions) {
        List<String> ids = new ArrayList<>();
        if (questions == null) {
            return ids;
        }

        for (StudyPack.TrueFalseQuestion q : questions) {
            if (q == null) {
                continue;
            }
            ids.add(buildCandidateId(
                    "tf",
                    q.getStatement(),
                    q.getSourceSnippet()));
        }
        return ids;
    }

    private List<String> collectMcqIds(List<StudyPack.McqQuestion> questions) {
        List<String> ids = new ArrayList<>();
        if (questions == null) {
            return ids;
        }

        for (StudyPack.McqQuestion q : questions) {
            if (q == null) {
                continue;
            }
            ids.add(buildCandidateId(
                    "mcq",
                    q.getQuestion(),
                    q.getSourceSnippet()));
        }
        return ids;
    }

    private List<String> collectMatchingIds(List<StudyPack.MatchingPair> pairs) {
        List<String> ids = new ArrayList<>();
        if (pairs == null) {
            return ids;
        }

        for (StudyPack.MatchingPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            ids.add(buildCandidateId(
                    "matching",
                    pair.getLeft(),
                    pair.getRight()));
        }
        return ids;
    }

    private String buildSourceSentenceId(String prefix, String sentence) {
        return buildCandidateId(prefix, sentence);
    }

    private boolean isWeakSourceSnippet(String sourceSnippet) {
        if (!notBlank(sourceSnippet)) {
            return true;
        }

        String s = normalizeText(sourceSnippet);

        if (s.length() < 45) {
            return true;
        }

        Set<String> weakStarts = Set.of("another example", "an example", "for example",
                "this example", "this involves", "it involves",
                "this means", "it means", "this is", "it is",
                "these are", "they are", "another simple example",
                "another common example", "another classic example",
                "a simple example", "a classic example", "one example",
                "one common example", "one classic example",
                "a common example", "a typical example",
                "a simple code example", "a code example", "code example");

        for (String phrase : weakStarts) {
            if (s.startsWith(phrase)) {
                System.out.println("Rejected weak snippet: " + sourceSnippet);
                return true;
            }
        }

        return false;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }

        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private void topUpMinimumCountsFromPreviousPack(StudyPack pack, List<StudyPack> previousPacks) {
        if (pack == null || previousPacks == null || previousPacks.isEmpty()) {
            return;
        }

        int minimum = 5;

        // Important: previousPacks is ordered newest -> oldest
        // Want older packs first, latest pack last.
        List<StudyPack> olderFirst = new ArrayList<>(previousPacks);
        Collections.reverse(olderFirst);

        topUpFlashcardsToMinimum(pack, olderFirst, minimum);
        topUpClozeToMinimum(pack, olderFirst, minimum);
        topUpMcqToMinimum(pack, olderFirst, minimum);
        topUpTrueFalseToMinimum(pack, olderFirst, minimum);
    }

    private void topUpFlashcardsToMinimum(StudyPack pack, List<StudyPack> previousPacks, int minimum) {
        if (pack.getFlashcards() == null) {
            pack.setFlashcards(new ArrayList<>());
        }

        if (pack.getFlashcards().size() >= minimum) {
            return;
        }

        Set<String> existing = pack.getFlashcards().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.Flashcard::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        for (StudyPack previous : previousPacks) {
            if (previous == null || previous.getFlashcards() == null) {
                continue;
            }

            for (StudyPack.Flashcard old : previous.getFlashcards()) {
                if (pack.getFlashcards().size() >= minimum) {
                    return;
                }

                if (old == null || !notBlank(old.getFront()) || !notBlank(old.getBack())
                        || !notBlank(old.getSourceSnippet())) {
                    continue;
                }

                String key = normalizeText(old.getSourceSnippet());
                if (existing.add(key)) {
                    pack.getFlashcards().add(old);
                }
            }
        }
    }

    private void topUpClozeToMinimum(StudyPack pack, List<StudyPack> previousPacks, int minimum) {
        if (pack.getClozeQuestions() == null) {
            pack.setClozeQuestions(new ArrayList<>());
        }

        if (pack.getClozeQuestions().size() >= minimum) {
            return;
        }

        Set<String> existing = pack.getClozeQuestions().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.ClozeQuestion::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        for (StudyPack previous : previousPacks) {
            if (previous == null || previous.getClozeQuestions() == null) {
                continue;
            }

            for (StudyPack.ClozeQuestion old : previous.getClozeQuestions()) {
                if (pack.getClozeQuestions().size() >= minimum) {
                    return;
                }

                if (old == null || !notBlank(old.getSentenceWithBlank()) || !notBlank(old.getAnswer())
                        || old.getChoices() == null || old.getChoices().size() < 4
                        || !notBlank(old.getSourceSnippet())) {
                    continue;
                }

                String key = normalizeText(old.getSourceSnippet());
                if (existing.add(key)) {
                    pack.getClozeQuestions().add(old);
                }
            }
        }
    }

    private void topUpMcqToMinimum(StudyPack pack, List<StudyPack> previousPacks, int minimum) {
        if (pack.getMcqQuestions() == null) {
            pack.setMcqQuestions(new ArrayList<>());
        }

        if (pack.getMcqQuestions().size() >= minimum) {
            return;
        }

        Set<String> existing = pack.getMcqQuestions().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.McqQuestion::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        for (StudyPack previous : previousPacks) {
            if (previous == null || previous.getMcqQuestions() == null) {
                continue;
            }

            for (StudyPack.McqQuestion old : previous.getMcqQuestions()) {
                if (pack.getMcqQuestions().size() >= minimum) {
                    return;
                }

                if (old == null || !notBlank(old.getQuestion())
                        || old.getOptions() == null || old.getOptions().size() != 4
                        || old.getCorrectIndex() < 0 || old.getCorrectIndex() > 3
                        || !notBlank(old.getExplanation())
                        || !notBlank(old.getSourceSnippet())) {
                    continue;
                }

                String key = normalizeText(old.getSourceSnippet());
                if (existing.add(key)) {
                    pack.getMcqQuestions().add(old);
                }
            }
        }
    }

    private void topUpTrueFalseToMinimum(StudyPack pack, List<StudyPack> previousPacks, int minimum) {
        if (pack.getTrueFalseQuestions() == null) {
            pack.setTrueFalseQuestions(new ArrayList<>());
        }

        if (pack.getTrueFalseQuestions().size() >= minimum) {
            return;
        }

        Set<String> existing = pack.getTrueFalseQuestions().stream()
                .filter(Objects::nonNull)
                .map(StudyPack.TrueFalseQuestion::getSourceSnippet)
                .filter(this::notBlank)
                .map(this::normalizeText)
                .collect(Collectors.toSet());

        for (StudyPack previous : previousPacks) {
            if (previous == null || previous.getTrueFalseQuestions() == null) {
                continue;
            }

            for (StudyPack.TrueFalseQuestion old : previous.getTrueFalseQuestions()) {
                if (pack.getTrueFalseQuestions().size() >= minimum) {
                    return;
                }

                if (old == null || !notBlank(old.getStatement())
                        || !notBlank(old.getExplanation())
                        || !notBlank(old.getSourceSnippet())) {
                    continue;
                }

                String key = normalizeText(old.getSourceSnippet());
                if (existing.add(key)) {
                    pack.getTrueFalseQuestions().add(old);
                }
            }
        }
    }

    private <T> List<T> keepUniqueSourceSnippets(
            List<T> items,
            Function<T, String> getSourceSnippet,
            int maxCount) {

        List<T> out = new ArrayList<>();
        Set<String> seenSnippets = new HashSet<>();

        if (items == null) {
            return out;
        }

        for (T item : items) {
            if (item == null) {
                continue;
            }

            String snippet = getSourceSnippet.apply(item);
            if (!notBlank(snippet)) {
                continue;
            }

            String key = normalizeText(snippet);

            if (!seenSnippets.add(key)) {
                continue;
            }

            out.add(item);

            if (out.size() >= maxCount) {
                break;
            }
        }

        return out;
    }

    private void enforceFinalStudyPackQuality(StudyPack pack) {
        if (pack == null) {
            return;
        }

        pack.setFlashcards(keepUniqueSourceSnippets(
                pack.getFlashcards(),
                StudyPack.Flashcard::getSourceSnippet,
                FLASHCARDS_COUNT));

        pack.setClozeQuestions(keepUniqueSourceSnippets(
                pack.getClozeQuestions(),
                StudyPack.ClozeQuestion::getSourceSnippet,
                CLOZE_COUNT));

        pack.setMcqQuestions(keepUniqueSourceSnippets(
                pack.getMcqQuestions(),
                StudyPack.McqQuestion::getSourceSnippet,
                MCQ_COUNT));

        pack.setTrueFalseQuestions(keepUniqueSourceSnippets(
                pack.getTrueFalseQuestions(),
                StudyPack.TrueFalseQuestion::getSourceSnippet,
                TF_COUNT));

        // Matching is derived from final flashcards, so rebuild it after cleaning
        // flashcards
        pack.setMatchingPairs(generateMatchingPairs(pack.getFlashcards(), MATCHING_COUNT));
    }

    private StudyPack.CandidateUsage buildCandidateUsage(StudyPack pack) {
        StudyPack.CandidateUsage usage = new StudyPack.CandidateUsage();

        usage.setFlashcardIds(collectFlashcardIds(pack.getFlashcards()));
        usage.setMatchingIds(collectMatchingIds(pack.getMatchingPairs()));
        usage.setClozeIds(collectClozeIds(pack.getClozeQuestions()));
        usage.setTrueFalseIds(collectTrueFalseIds(pack.getTrueFalseQuestions()));
        usage.setMcqIds(collectMcqIds(pack.getMcqQuestions()));

        return usage;
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