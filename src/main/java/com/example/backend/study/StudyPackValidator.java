package com.example.backend.study;

import com.example.backend.study.dto.*;
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

        Set<String> seen = new HashSet<>();

        return items.stream()
                .filter(i -> i != null && notBlank(i.getFront()) && notBlank(i.getBack()))
                .filter(i -> seen.add(i.getFront().toLowerCase()))
                .limit(max)
                .collect(Collectors.toList());
    }

    private List<ClozeQuestionDto> cleanCloze(List<ClozeQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        Set<String> seen = new HashSet<>();

        return items.stream()
                .filter(i -> i != null && notBlank(i.getSentenceWithBlank()) && notBlank(i.getAnswer()))
                .filter(i -> seen.add(i.getSentenceWithBlank().toLowerCase()))
                .limit(max)
                .collect(Collectors.toList());
    }

    private List<McqQuestionDto> cleanMcq(List<McqQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        Set<String> seen = new HashSet<>();

        return items.stream()
                .filter(i -> i != null
                        && notBlank(i.getQuestion())
                        && i.getOptions() != null
                        && i.getOptions().size() == 4
                        && i.getCorrectIndex() != null)
                .filter(i -> seen.add(i.getQuestion().toLowerCase()))
                .limit(max)
                .collect(Collectors.toList());
    }

    private List<TrueFalseQuestionDto> cleanTf(List<TrueFalseQuestionDto> items, int max) {
        if (items == null)
            return new ArrayList<>();

        Set<String> seen = new HashSet<>();

        return items.stream()
                .filter(i -> i != null && notBlank(i.getStatement()))
                .filter(i -> seen.add(i.getStatement().toLowerCase()))
                .limit(max)
                .collect(Collectors.toList());
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}