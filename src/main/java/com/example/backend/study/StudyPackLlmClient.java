package com.example.backend.study;

import java.util.List;

import com.example.backend.study.dto.ClozeQuestionDto;
import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.FlashcardDto;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.model.StudyPack;

public interface StudyPackLlmClient {

        ConceptPackResponse generateConceptPack(
                        List<String> definitionPool,
                        List<String> processPool,
                        List<String> topicLabels,
                        int flashcardCount,
                        int clozeCount,
                        int mcqCount) throws Exception;

        TrueFalsePackResponse generateTrueFalsePack(
                        List<String> processPool,
                        List<String> detailPool,
                        int trueFalseCount) throws Exception;

        List<FlashcardDto> generateFlashcardsFromSnippets(List<String> selectedSnippets) throws Exception;

        List<ClozeQuestionDto> generateClozeQuestionsFromSnippets(List<String> selectedSnippets) throws Exception;
}