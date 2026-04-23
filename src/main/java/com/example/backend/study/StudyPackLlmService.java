package com.example.backend.study;

import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.study.dto.FlashcardDto;
import com.example.backend.study.dto.ClozeQuestionDto;
import org.springframework.stereotype.Service;
import com.example.backend.model.StudyPack;

import java.util.*;

@Service
public class StudyPackLlmService {

    private final StudyPackLlmClient client;
    private final StudyPackValidator validator;

    public StudyPackLlmService(StudyPackLlmClient client,
            StudyPackValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    public ConceptPackResponse generateConceptPack(
            List<String> definitionPool,
            List<String> processPool,
            List<String> topicLabels,
            StudyPack.StudyPackSettings settings) throws Exception {

        definitionPool = limitList(definitionPool, 30);
        processPool = limitList(processPool, 20);
        topicLabels = limitList(topicLabels, 15);

        System.out.println("STUDYPACK LLM request:");
        System.out.println("definitionPool size=" + definitionPool.size());
        System.out.println("processPool size=" + processPool.size());
        System.out.println("topicLabels size=" + topicLabels.size());
        System.out.println("flashcardCount=" + settings.getFlashcardCount());
        System.out.println("clozeCount=" + settings.getClozeCount());
        System.out.println("mcqCount=" + settings.getMcqCount());

        ConceptPackResponse response = client.generateConceptPack(
                definitionPool,
                processPool,
                topicLabels,
                settings.getFlashcardCount(),
                settings.getClozeCount(),
                settings.getMcqCount());

        return validator.validateConceptPack(response, settings);
    }

    private <T> List<T> limitList(List<T> items, int max) {
        if (items == null) {
            return new ArrayList<>();
        }
        if (items.size() <= max) {
            return items;
        }
        return new ArrayList<>(items.subList(0, max));
    }

    public TrueFalsePackResponse generateTrueFalsePack(
            List<String> processPool,
            List<String> detailPool,
            StudyPack.StudyPackSettings settings) throws Exception {

        TrueFalsePackResponse response = client.generateTrueFalsePack(
                processPool,
                detailPool,
                settings.getTrueFalseCount());

        return validator.validateTrueFalsePack(response, settings);
    }

    public List<FlashcardDto> generateFlashcardsFromSnippets(List<String> selectedSnippets) throws Exception {

        selectedSnippets = limitList(selectedSnippets, 12);

        System.out.println("STUDYPACK LLM flashcard snippet request:");
        System.out.println("selectedSnippets size=" + selectedSnippets.size());

        return client.generateFlashcardsFromSnippets(selectedSnippets);
    }

    public List<ClozeQuestionDto> generateClozeQuestionsFromSnippets(List<String> selectedSnippets) throws Exception {

        selectedSnippets = limitList(selectedSnippets, 12);

        System.out.println("STUDYPACK LLM cloze snippet request:");
        System.out.println("selectedSnippets size=" + selectedSnippets.size());

        return client.generateClozeQuestionsFromSnippets(selectedSnippets);
    }
}