package com.example.backend.study;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StudyPackPromptFactory {

    public String buildConceptSystemPrompt() {
        return """
                You generate educational study-pack content.
                Use only the provided source sentences.
                Do not invent facts.
                Return valid JSON only.
                No markdown. No commentary.
                Use natural educational wording.
                Use "Who was ..." for a person when appropriate.
                Avoid vague answers like "they", "this", "during", "throughout".
                Avoid repeating the same exact concept across outputs when possible.
                """;
    }

    public String buildConceptUserPrompt(
            List<String> definitionPool,
            List<String> processPool,
            List<String> topicLabels,
            int flashcardCount,
            int clozeCount,
            int mcqCount) {

        return """
                Generate study-pack content from the source material below.

                Requested counts:
                - flashcards: %d
                - clozeQuestions: %d
                - mcqQuestions: %d

                Topic labels:
                %s

                Definition pool:
                %s

                Process pool:
                %s

                Return JSON matching this structure:
                {
                  "flashcards": [
                    {"front":"...","back":"...","sourceSnippet":"..."}
                  ],
                  "clozeQuestions": [
                    {"sentenceWithBlank":"...","answer":"...","sourceSnippet":"..."}
                  ],
                  "mcqQuestions": [
                    {
                      "question":"...",
                      "options":["...","...","...","..."],
                      "correctIndex":0,
                      "explanation":"...",
                      "sourceSnippet":"..."
                    }
                  ]
                }
                """.formatted(
                flashcardCount,
                clozeCount,
                mcqCount,
                joinLines(topicLabels),
                joinLines(definitionPool),
                joinLines(processPool));
    }

    public String buildTrueFalseSystemPrompt() {
        return """
                You generate educational true/false questions.
                Use only the provided source sentences.
                Do not invent facts.
                Return valid JSON only.
                No markdown. No commentary.
                Make false statements believable, not nonsense.
                Keep explanations concise.
                """;
    }

    public String buildTrueFalseUserPrompt(
            List<String> processPool,
            List<String> detailPool,
            int trueFalseCount) {

        return """
                Generate true/false questions from the source material below.

                Requested count:
                - trueFalseQuestions: %d

                Aim for a balanced mix of true and false if possible.

                Process pool:
                %s

                Detail pool:
                %s

                Return JSON matching this structure:
                {
                  "trueFalseQuestions": [
                    {
                      "statement":"...",
                      "answer":true,
                      "explanation":"...",
                      "sourceSnippet":"..."
                    }
                  ]
                }
                """.formatted(
                trueFalseCount,
                joinLines(processPool),
                joinLines(detailPool));
    }

    private String joinLines(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "(none)";
        }
        return items.stream()
                .limit(20)
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));
    }
}