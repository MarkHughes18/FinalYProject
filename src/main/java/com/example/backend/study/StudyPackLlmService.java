package com.example.backend.study;

import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.TrueFalsePackResponse;
import org.springframework.stereotype.Service;
import com.example.backend.model.StudyPack;

import java.util.List;

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

        ConceptPackResponse response = client.generateConceptPack(
                definitionPool,
                processPool,
                topicLabels,
                settings.getFlashcardCount(),
                settings.getClozeCount(),
                settings.getMcqCount());

        return validator.validateConceptPack(response, settings);
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
}