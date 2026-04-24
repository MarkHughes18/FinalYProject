package com.example.backend.study;

import com.example.backend.study.dto.*;
import com.example.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StudyPackValidator {

    public ConceptPackResponse validateConceptPack(
            ConceptPackResponse response,
            StudyPack.StudyPackSettings settings) {

        if (response == null) {
            response = new ConceptPackResponse();
        }

        Map<String, Integer> snippetUsage = new HashMap<>();

        // Flashcards first
        List<FlashcardDto> flashcards = cleanFlashcards(
                response.getFlashcards(),
                Math.min(settings.getFlashcardCount(), 6));
        response.setFlashcards(flashcards);
        addFlashcardSnippetsToUsage(flashcards, snippetUsage);

        // Cloze next, avoiding flashcard snippets
        List<ClozeQuestionDto> clozeQuestions = cleanCloze(
                response.getClozeQuestions(),
                settings.getClozeCount(),
                snippetUsage, 1);

        if (clozeQuestions.size() < 3) {
            clozeQuestions = cleanCloze(
                    response.getClozeQuestions(),
                    settings.getClozeCount(),
                    snippetUsage, 2);
        }

        if (clozeQuestions.size() < 2) {
            clozeQuestions = cleanCloze(
                    response.getClozeQuestions(),
                    settings.getClozeCount(),
                    new HashMap<>(), // ignore usage
                    Integer.MAX_VALUE);
        }
        response.setClozeQuestions(clozeQuestions);
        addClozeSnippetsToUsage(clozeQuestions, snippetUsage);

        // MCQ last, avoiding flashcard + cloze snippets
        List<McqQuestionDto> mcqQuestions = cleanMcq(
                response.getMcqQuestions(),
                settings.getMcqCount(),
                snippetUsage, 1);

        if (mcqQuestions.size() < 3) {
            mcqQuestions = cleanMcq(
                    response.getMcqQuestions(),
                    settings.getMcqCount(),
                    snippetUsage,
                    2);
        }

        if (mcqQuestions.size() < 2) {
            mcqQuestions = cleanMcq(
                    response.getMcqQuestions(),
                    settings.getMcqCount(),
                    new HashMap<>(),
                    Integer.MAX_VALUE);
        }

        // This guarantees correctIndex & correctAnswer
        mcqQuestions.removeIf(q -> {
            if (q == null)
                return true;
            if (isBlank(q.getQuestion()))
                return true;
            if (q.getOptions() == null || q.getOptions().size() != 4)
                return true;
            if (isBlank(q.getCorrectAnswer()))
                return true;
            if (isBlank(q.getExplanation()))
                return true;
            if (isBlank(q.getSourceSnippet()))
                return true;

            int repairedIndex = findCorrectIndex(q.getOptions(), q.getCorrectAnswer());

            if (repairedIndex < 0) {
                return true;
            }

            q.setCorrectIndex(repairedIndex);
            shuffleMcqOptionsAndRepairIndex(q);
            return q.getCorrectIndex() < 0;
        });
        response.setMcqQuestions(mcqQuestions);

        return response;
    }

    public TrueFalsePackResponse validateTrueFalsePack(
            TrueFalsePackResponse response,
            StudyPack.StudyPackSettings settings) {

        if (response == null) {
            response = new TrueFalsePackResponse();
        }

        response.setTrueFalseQuestions(cleanTf(response.getTrueFalseQuestions(), settings.getTrueFalseCount()));

        return response;
    }

    private List<FlashcardDto> cleanFlashcards(List<FlashcardDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        List<FlashcardDto> out = new ArrayList<>();
        Set<String> seenFronts = new HashSet<>();
        Set<String> seenBacks = new HashSet<>();
        Map<String, Integer> snippetUsage = new HashMap<>();

        for (FlashcardDto i : items) {
            if (i == null || !notBlank(i.getFront()) || !notBlank(i.getBack()) || !notBlank(i.getSourceSnippet())) {
                continue;
            }

            String frontKey = normalizeText(i.getFront());
            String backKey = normalizeText(i.getBack());
            String snippetKey = normalizeText(i.getSourceSnippet());

            // Exact/ near exact duplicates
            if (!seenFronts.add(frontKey)) {
                continue;
            }
            if (!seenBacks.add(backKey)) {
                continue;
            }

            // Avoid using the same source sentence
            if (snippetUsage.getOrDefault(snippetKey, 0) >= 1) {
                continue;
            }

            /**
             * if (isTooSimilarToExisting(frontKey, seenFronts, 0.85)) {
             * continue;
             * }
             */

            snippetUsage.put(snippetKey, snippetUsage.getOrDefault(snippetKey, 0) + 1);
            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private void addFlashcardSnippetsToUsage(List<FlashcardDto> items, Map<String, Integer> snippetUsage) {
        if (items == null) {
            return;
        }

        for (FlashcardDto item : items) {
            if (item == null || !notBlank(item.getSourceSnippet())) {
                continue;
            }

            String key = normalizeText(item.getSourceSnippet());
            snippetUsage.put(key, snippetUsage.getOrDefault(key, 0) + 1);
        }
    }

    private List<ClozeQuestionDto> cleanCloze(List<ClozeQuestionDto> items, int max, Map<String, Integer> snippetUsage,
            int maxReuse) {
        if (items == null)
            return new ArrayList<>();

        List<ClozeQuestionDto> out = new ArrayList<>();
        Set<String> seenSentences = new HashSet<>();
        Set<String> seenAnswers = new HashSet<>();

        for (ClozeQuestionDto i : items) {
            if (i == null
                    || !notBlank(i.getSentenceWithBlank())
                    || !notBlank(i.getAnswer())
                    || !notBlank(i.getSourceSnippet())) {
                continue;
            }

            if (!hasSingleBlank(i.getSentenceWithBlank())) {
                continue;
            }

            if (!isReasonableClozeAnswer(i.getAnswer())) {
                continue;
            }

            if (!hasValidClozeChoices(i)) {
                continue;
            }

            String sentenceKey = normalizeText(i.getSentenceWithBlank());
            String answerKey = normalizeText(i.getAnswer());
            String snippetKey = normalizeText(i.getSourceSnippet());

            if (snippetUsage.getOrDefault(snippetKey, 0) >= maxReuse) {
                continue;
            }
            if (!seenSentences.add(sentenceKey)) {
                continue;
            }

            // Avoid repeated answers
            if (seenAnswers.contains(answerKey)) {
                continue;
            }

            if (snippetUsage.getOrDefault(snippetKey, 0) >= 1) {
                continue;
            }

            // Reject weak cloze answers
            if (isWeakClozeAnswer(i.getAnswer())) {
                continue;
            }

            if (!hasValidClozeChoices(i)) {
                continue;
            }

            seenAnswers.add(answerKey);
            snippetUsage.put(snippetKey, snippetUsage.getOrDefault(snippetKey, 0) + 1);
            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private void addClozeSnippetsToUsage(List<ClozeQuestionDto> items, Map<String, Integer> snippetUsage) {
        if (items == null) {
            return;
        }

        for (ClozeQuestionDto item : items) {
            if (item == null || !notBlank(item.getSourceSnippet())) {
                continue;
            }

            String key = normalizeText(item.getSourceSnippet());
            snippetUsage.put(key, snippetUsage.getOrDefault(key, 0) + 1);
        }
    }

    private List<McqQuestionDto> cleanMcq(List<McqQuestionDto> items, int max, Map<String, Integer> snippetUsage,
            int maxReuse) {
        if (items == null)
            return new ArrayList<>();

        List<McqQuestionDto> out = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        Set<String> seenSnippets = new HashSet<>();

        for (McqQuestionDto i : items) {
            if (i == null
                    || !notBlank(i.getQuestion())
                    || i.getOptions() == null
                    || i.getOptions().size() != 4
                    || i.getCorrectIndex() == null
                    || i.getCorrectIndex() < 0
                    || i.getCorrectIndex() > 3
                    || !notBlank(i.getExplanation())
                    || !notBlank(i.getSourceSnippet())) {
                continue;
            }

            String questionKey = normalizeText(i.getQuestion());
            String snippetKey = normalizeText(i.getSourceSnippet());

            if (snippetUsage.getOrDefault(snippetKey, 0) >= maxReuse) {
                continue;
            }

            if (!seenQuestions.add(questionKey)) {
                continue;
            }

            if (!seenSnippets.add(snippetKey)) {
                continue;
            }

            if (i.getCorrectIndex() < 0 || i.getCorrectIndex() >= i.getOptions().size()) {
                continue;
            }

            // Reject MCQs with bad options
            if (!hasValidMcqOptions(i.getOptions())) {
                continue;
            }

            if (isWeakMcq(i)) {
                continue;
            }

            if (!isMcqCorrectOptionConsistent(i)) {
                continue;
            }

            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private void shuffleMcqOptionsAndRepairIndex(McqQuestionDto q) {
        if (q == null || q.getOptions() == null || q.getCorrectAnswer() == null) {
            return;
        }

        List<String> shuffled = new ArrayList<>(q.getOptions());
        Collections.shuffle(shuffled);

        q.setOptions(shuffled);

        int repairedIndex = findCorrectIndex(shuffled, q.getCorrectAnswer());
        q.setCorrectIndex(repairedIndex);
    }

    private List<TrueFalseQuestionDto> cleanTf(List<TrueFalseQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        List<TrueFalseQuestionDto> out = new ArrayList<>();
        Set<String> seenStatements = new HashSet<>();

        for (TrueFalseQuestionDto i : items) {
            if (i == null
                    || !notBlank(i.getStatement())
                    || i.getAnswer() == null
                    || !notBlank(i.getExplanation())
                    || !notBlank(i.getSourceSnippet())) {
                continue;
            }

            String statementKey = normalizeText(i.getStatement());
            if (!seenStatements.add(statementKey)) {
                continue;
            }

            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private int findCorrectIndex(List<String> options, String correctAnswer) {
        if (options == null || correctAnswer == null) {
            return -1;
        }

        String target = correctAnswer.trim();

        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);

            if (option != null && option.trim().equalsIgnoreCase(target)) {
                return i;
            }
        }

        return -1;
    }

    private void addFlashcardSnippetsToUsed(List<FlashcardDto> items, Set<String> usedSnippets) {
        if (items == null) {
            return;
        }

        for (FlashcardDto item : items) {
            if (item == null || !notBlank(item.getSourceSnippet())) {
                continue;
            }
            usedSnippets.add(normalizeText(item.getSourceSnippet()));
        }
    }

    private void addClozeSnippetsToUsed(List<ClozeQuestionDto> items, Set<String> usedSnippets) {
        if (items == null) {
            return;
        }

        for (ClozeQuestionDto item : items) {
            if (item == null || !notBlank(item.getSourceSnippet())) {
                continue;
            }
            usedSnippets.add(normalizeText(item.getSourceSnippet()));
        }
    }

    private boolean hasValidMcqOptions(List<String> options) {
        if (options == null || options.size() != 4) {
            return false;
        }

        Set<String> seen = new HashSet<>();
        for (String opt : options) {
            if (!notBlank(opt)) {
                return false;
            }

            String key = normalizeText(opt);

            // Reject question like options
            if (key.startsWith("what ")
                    || key.startsWith("who ")
                    || key.startsWith("which ")
                    || key.startsWith("when ")
                    || key.startsWith("where ")) {
                return false;
            }

            if (!seen.add(key)) {
                return false;
            }

            String normalized = normalizeText(opt);
            if (looksLikeQuestion(normalized)) {
                return false;
            }

            if (normalized.equals("all of the above") || normalized.equals("none of the above")) {
                return false;
            }
        }

        return true;
    }

    private boolean hasValidClozeChoices(ClozeQuestionDto i) {
        if (i == null || i.getChoices() == null || i.getChoices().size() != 4) {
            return false;
        }

        String answer = normalizeText(i.getAnswer());
        if (!notBlank(answer)) {
            return false;
        }

        Set<String> seen = new HashSet<>();
        boolean containsAnswer = false;

        for (String choice : i.getChoices()) {
            if (!notBlank(choice)) {
                return false;
            }

            String normalizedChoice = normalizeText(choice);

            if (!seen.add(normalizedChoice)) {
                return false;
            }

            if (normalizedChoice.equals(answer)) {
                containsAnswer = true;
            }
        }

        return containsAnswer;
    }

    private boolean isReasonableClozeAnswer(String answer) {
        if (!notBlank(answer)) {
            return false;
        }

        String trimmed = answer.trim();

        if (trimmed.length() > 40) {
            return false;
        }

        return trimmed.split("\\s+").length <= 6;
    }

    private boolean isWeakClozeAnswer(String answer) {
        if (!notBlank(answer)) {
            return true;
        }

        String a = normalizeText(answer);

        Set<String> weak = Set.of(
                "management", "people", "education", "healthcare", "effectiveness",
                "organizational", "business", "product", "result", "service");

        return weak.contains(a);
    }

    private boolean isWeakMcq(McqQuestionDto i) {
        if (i == null) {
            return true;
        }

        String question = normalizeText(i.getQuestion());
        String explanation = normalizeText(i.getExplanation());
        String correct = normalizeText(i.getOptions().get(i.getCorrectIndex()));

        if (question.length() < 12) {
            return true;
        }

        if (!notBlank(explanation)) {
            return true;
        }

        if (looksLikeQuestionOptionSet(i.getOptions())) {
            return true;
        }

        if (allOptionsTooSimilar(i.getOptions())) {
            return true;
        }

        if (question.contains(correct)) {
            return true;
        }

        if (question.startsWith("what is") && correct.length() < 10) {
            return true;
        }

        return false;
    }

    private boolean isMcqCorrectOptionConsistent(McqQuestionDto i) {
        if (i == null || i.getOptions() == null || i.getCorrectIndex() == null) {
            return false;
        }

        if (i.getCorrectIndex() < 0 || i.getCorrectIndex() >= i.getOptions().size()) {
            return false;
        }

        String correctOption = i.getOptions().get(i.getCorrectIndex());
        if (!notBlank(correctOption)) {
            return false;
        }

        String explanation = normalizeText(i.getExplanation());
        String sourceSnippet = normalizeText(i.getSourceSnippet());
        String correctNorm = normalizeText(correctOption);

        // At least one meaningful token from the correct option should appear in the
        // explanation or source snippet
        List<String> tokens = extractMeaningfulTokens(correctNorm);
        if (tokens.isEmpty()) {
            return false;
        }

        for (String token : tokens) {
            if (explanation.contains(token) || sourceSnippet.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private List<String> extractMeaningfulTokens(String text) {
        List<String> out = new ArrayList<>();
        if (!notBlank(text)) {
            return out;
        }

        for (String part : text.split("\\s+")) {
            String token = normalizeText(part);
            if (token.length() < 4) {
                continue;
            }
            if (isStopWord(token)) {
                continue;
            }
            out.add(token);
        }

        return out;
    }

    private boolean looksLikeQuestion(String s) {
        return s.startsWith("what ")
                || s.startsWith("who ")
                || s.startsWith("which ")
                || s.startsWith("when ")
                || s.startsWith("where ")
                || s.endsWith("?");
    }

    private boolean looksLikeQuestionOptionSet(List<String> options) {
        if (options == null) {
            return true;
        }

        for (String option : options) {
            if (!notBlank(option)) {
                return true;
            }
            if (looksLikeQuestion(normalizeText(option))) {
                return true;
            }
        }

        return false;
    }

    private boolean allOptionsTooSimilar(List<String> options) {
        if (options == null || options.size() != 4) {
            return true;
        }

        Set<String> normalized = new HashSet<>();
        for (String option : options) {
            normalized.add(normalizeText(option));
        }

        return normalized.size() < 4;
    }

    private String safeOptionAt(List<String> options, int index) {
        if (options == null || index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index);
    }

    private boolean isTooSimilarToExisting(List<String> options) {
        if (options == null || options.size() < 4) {
            return true;
        }

        Set<String> normalized = new HashSet<>();
        for (String opt : options) {
            normalized.add(normalizeText(opt));
        }
        return normalized.size() < 4;
    }

    private double similarity(String a, String b) {
        Set<String> aWords = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> bWords = new HashSet<>(Arrays.asList(b.split("\\s+")));

        if (aWords.isEmpty() || bWords.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(aWords);
        intersection.retainAll(bWords);

        Set<String> union = new HashSet<>(aWords);
        union.addAll(bWords);

        return (double) intersection.size() / union.size();
    }

    private boolean hasSingleBlank(String sentence) {
        if (!notBlank(sentence)) {
            return false;
        }

        int count = 0;
        boolean inBlank = false;

        for (int i = 0; i < sentence.length(); i++) {
            char ch = sentence.charAt(i);

            if (ch == '_') {
                if (!inBlank) {
                    count++;
                    inBlank = true;
                }
            } else {
                inBlank = false;
            }
        }

        return count == 1;
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

    private boolean isStopWord(String s) {
        return Set.of(
                "the", "and", "with", "from", "that", "this", "into", "their", "than",
                "then", "have", "has", "been", "were", "will", "would", "could",
                "should", "about", "only", "also", "such", "both", "more", "most",
                "some", "many", "much", "management").contains(s);
    }
}