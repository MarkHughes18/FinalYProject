package com.example.backend.study;

import java.util.List;

import com.example.backend.study.dto.ClozeQuestionDto;
import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.FlashcardDto;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.model.StudyPack;

public interface StudyPackLlmClient {

        // generates a concept pack (flashcards, cloze questions, mcqs)
        ConceptPackResponse generateConceptPack(
                        List<String> definitionPool,
                        List<String> processPool,
                        List<String> topicLabels,
                        int flashcardCount,
                        int clozeCount,
                        int mcqCount) throws Exception;

        // generates true/false questions based on the process and detail pools
        TrueFalsePackResponse generateTrueFalsePack(
                        List<String> processPool,
                        List<String> detailPool,
                        int trueFalseCount) throws Exception;

        // generates flashcards based on the selected snippets
        List<FlashcardDto> generateFlashcardsFromSnippets(List<String> selectedSnippets) throws Exception;

        // generates cloze questions based on the selected snippets
        List<ClozeQuestionDto> generateClozeQuestionsFromSnippets(List<String> selectedSnippets) throws Exception;
}