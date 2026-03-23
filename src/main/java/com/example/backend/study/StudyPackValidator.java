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

        response.setFlashcards(cleanFlashcards(response.getFlashcards(), settings.getFlashcardCount()));
        response.setClozeQuestions(cleanCloze(response.getClozeQuestions(), settings.getClozeCount()));
        response.setMcqQuestions(cleanMcq(response.getMcqQuestions(), settings.getMcqCount()));

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

            // Reject similar flashcard fronts
            if (isTooSimilarToExisting(frontKey, seenFronts, 0.85)) {
                continue;
            }

            snippetUsage.put(snippetKey, snippetUsage.getOrDefault(snippetKey, 0) + 1);
            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private List<ClozeQuestionDto> cleanCloze(List<ClozeQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        List<ClozeQuestionDto> out = new ArrayList<>();
        Set<String> seenSentences = new HashSet<>();
        Set<String> seenAnswers = new HashSet<>();
        Map<String, Integer> snippetUsage = new HashMap<>();

        for (ClozeQuestionDto i : items) {
            if (i == null
                    || !notBlank(i.getSentenceWithBlank())
                    || !notBlank(i.getAnswer())
                    || !notBlank(i.getSourceSnippet())) {
                continue;
            }

            String sentenceKey = normalizeText(i.getSentenceWithBlank());
            String answerKey = normalizeText(i.getAnswer());
            String snippetKey = normalizeText(i.getSourceSnippet());

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

            seenAnswers.add(answerKey);
            snippetUsage.put(snippetKey, snippetUsage.getOrDefault(snippetKey, 0) + 1);
            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
    }

    private List<McqQuestionDto> cleanMcq(List<McqQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        List<McqQuestionDto> out = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        Map<String, Integer> snippetUsage = new HashMap<>();

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

            if (!seenQuestions.add(questionKey)) {
                continue;
            }

            if (snippetUsage.getOrDefault(snippetKey, 0) >= 1) {
                continue;
            }

            // Reject MCQs with bad options
            if (!hasValidMcqOptions(i.getOptions())) {
                continue;
            }

            // Reject near duplicate questions
            if (isTooSimilarToExisting(questionKey, seenQuestions, 0.85)) {
                continue;
            }

            snippetUsage.put(snippetKey, snippetUsage.getOrDefault(snippetKey, 0) + 1);
            out.add(i);

            if (out.size() >= max) {
                break;
            }
        }

        return out;
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
        }

        return true;
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

    private boolean isTooSimilarToExisting(String candidate, Set<String> existing, double threshold) {
        for (String e : existing) {
            if (e.equals(candidate)) {
                continue;
            }
            if (similarity(candidate, e) >= threshold) {
                return true;
            }
        }
        return false;
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

    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }

        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}