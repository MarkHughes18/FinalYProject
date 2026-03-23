package com.example.backend.study;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StudyPackPromptFactory {

  public String buildConceptSystemPrompt() {
    return """
        You generate educational study-pack content from source sentences.

        Rules:
        - Use only the provided source sentences.
        - Do not invent facts.
        - Return valid JSON only.
        - No markdown. No commentary.
        - Keep outputs educational, concise, and natural.
        - Prefer direct study-style wording over creative wording.
        - Avoid vague terms such as "they", "this", "that", "during", "throughout".
        - Avoid repeating the same exact concept too many times across sections.
        - If the source sentence defines a concept, preserve that concept clearly.
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

                        Flashcard rules:
                        - Prefer direct definition-style cards.
                        - For a person, use "Who was ...?"
                        - For a singular concept, use "What is ...?"
                        - For a plural concept, use "What are ...?"
                        - Keep the front short and natural.
                        - Do NOT create indirect questions like "Who undertakes a project?"
                        - Do NOT ask about generic actions if the sentence is defining a concept.
                        - The back should be a concise definition or explanation based on the source sentence.

                        Cloze rules:
                        - Blank exactly one important concept or term.
                        - Prefer blanking the main defined concept, named term, phase, or key topic.
                        - Do NOT blank generic nouns like "thing", "product", "result", "people", "way" unless they are the actual target concept.
                        - Keep the sentence grammatical after blanking.
                        - The answer should be short and meaningful.

                        MCQ rules:
                        - Write exactly 4 options.
                        - Exactly 1 option must be correct.
                        - The question must be a normal multiple-choice question.
                        - The options must be short answer choices, not full questions.
                        - Do NOT start any option with "What", "Who", "Which", "When", or "Where".
                        - Prefer concept terms or short noun phrases as options.
                        - Each option should usually be between 1 and 5 words where possible.
                        - Distractors must be plausible and same-topic.
                        - Do NOT use "all of the above" or "none of the above".
                        - Do NOT use joke or obviously wrong distractors.
                        - Do NOT make the options long sentence definitions.
                        - Prefer concept-based questions where the answer options are terms such as phases, concepts, roles, or definitions in short form.

                        Example good MCQ:
        question: "Which phase involves defining scope, schedule, resources, and risks?"
        options: ["Planning", "Execution", "Control", "Closeout"]

        Example bad MCQ:
        options: ["What is planning?", "What is execution?", "What is control?", "What is closeout?"]

                        For mcqQuestions, "options" must contain answer choices only, not question sentences.

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
                        """
        .formatted(
            flashcardCount,
            clozeCount,
            mcqCount,
            joinLines(topicLabels),
            joinLines(definitionPool),
            joinLines(processPool));
  }

  public String buildTrueFalseSystemPrompt() {
    return """
        You generate educational true/false questions from source sentences.

        Rules:
        - Use only the provided source sentences.
        - Do not invent facts.
        - Return valid JSON only.
        - No markdown. No commentary.
        - False statements must be believable, not nonsense.
        - Keep explanations concise and clear.
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

        True/False rules:
        - Statements should sound natural and educational.
        - False statements must be close to the source material but clearly incorrect.
        - Do NOT create nonsense grammar.
        - Do NOT use trivial changes that make the false statement too obvious.
        - Explanation should briefly state why the statement is true or false.

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