package com.example.backend.study;

import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.study.dto.FlashcardDto;
import com.example.backend.study.dto.ClozeQuestionDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

@Service
@Primary
public class OpenAiStudyPackClient implements StudyPackLlmClient {

        private final WebClient web;
        private final ObjectMapper mapper = new ObjectMapper();
        private final StudyPackPromptFactory promptFactory;

        private static final Semaphore OPENAI_LOCK = new Semaphore(1);
        private static volatile long lastCallMs = 0;
        private static final long MIN_GAP_MS = 2500;

        public OpenAiStudyPackClient(StudyPackPromptFactory promptFactory) {
                this.promptFactory = promptFactory;

                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                        throw new IllegalStateException("OPENAI_API_KEY is not set");
                }

                this.web = WebClient.builder()
                                .baseUrl("https://api.openai.com/v1")
                                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .build();
        }

        @Override
        public ConceptPackResponse generateConceptPack(
                        List<String> definitionPool,
                        List<String> processPool,
                        List<String> topicLabels,
                        int flashcardCount,
                        int clozeCount,
                        int mcqCount) throws Exception {

                String system = promptFactory.buildConceptSystemPrompt();
                String user = promptFactory.buildConceptUserPrompt(
                                definitionPool, processPool, topicLabels, flashcardCount, clozeCount, mcqCount);

                Map<String, Object> payload = Map.of(
                                "model", "gpt-4.1-mini",
                                "temperature", 0.2,
                                "messages", List.of(
                                                Map.of("role", "system", "content", system),
                                                Map.of("role", "user", "content", user)),
                                "response_format", buildConceptJsonSchema());

                String content = withRateLimit(() -> callAndExtractContent(payload));
                return mapper.readValue(content, ConceptPackResponse.class);
        }

        @Override
        public TrueFalsePackResponse generateTrueFalsePack(
                        List<String> processPool,
                        List<String> detailPool,
                        int trueFalseCount) throws Exception {

                String system = promptFactory.buildTrueFalseSystemPrompt();
                String user = promptFactory.buildTrueFalseUserPrompt(processPool, detailPool, trueFalseCount);

                Map<String, Object> payload = Map.of(
                                "model", "gpt-4.1-mini",
                                "temperature", 0.2,
                                "max_completion_tokens", 2500,
                                "messages", List.of(
                                                Map.of("role", "system", "content", system),
                                                Map.of("role", "user", "content", user)),
                                "response_format", buildTrueFalseJsonSchema());

                String content = withRateLimit(() -> callAndExtractContent(payload));
                return mapper.readValue(content, TrueFalsePackResponse.class);
        }

        @Override
        public List<FlashcardDto> generateFlashcardsFromSnippets(
                        List<String> selectedSnippets) throws Exception {

                String system = promptFactory.buildFlashcardSnippetSystemPrompt();
                String user = promptFactory.buildFlashcardSnippetUserPrompt(selectedSnippets);

                Map<String, Object> payload = Map.of(
                                "model", "gpt-4.1-mini",
                                "temperature", 0.2,
                                "messages", List.of(
                                                Map.of("role", "system", "content", system),
                                                Map.of("role", "user", "content", user)),
                                "response_format", buildFlashcardJsonSchema());

                String content = withRateLimit(() -> callAndExtractContent(payload));

                JsonNode root = mapper.readTree(content);
                JsonNode itemsNode = root.get("flashcards");
                if (itemsNode == null || !itemsNode.isArray()) {
                        throw new RuntimeException("Flashcard response missing flashcards array");
                }

                return mapper.readerForListOf(FlashcardDto.class).readValue(itemsNode);
        }

        @Override
        public List<ClozeQuestionDto> generateClozeQuestionsFromSnippets(
                        List<String> selectedSnippets) throws Exception {

                String system = promptFactory.buildClozeSnippetSystemPrompt();
                String user = promptFactory.buildClozeSnippetUserPrompt(selectedSnippets);

                Map<String, Object> payload = Map.of(
                                "model", "gpt-4.1-mini",
                                "temperature", 0.2,
                                "messages", List.of(
                                                Map.of("role", "system", "content", system),
                                                Map.of("role", "user", "content", user)),
                                "response_format", buildClozeJsonSchema());

                String content = withRateLimit(() -> callAndExtractContent(payload));

                JsonNode root = mapper.readTree(content);
                JsonNode itemsNode = root.get("clozeQuestions");
                if (itemsNode == null || !itemsNode.isArray()) {
                        throw new RuntimeException("Cloze response missing clozeQuestions array");
                }

                return mapper.readerForListOf(ClozeQuestionDto.class).readValue(itemsNode);
        }

        private Map<String, Object> buildConceptJsonSchema() {
                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "concept_pack",
                                                "strict", true,
                                                "schema", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "properties", Map.of(
                                                                                "flashcards", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "front",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "back",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("front", "back",
                                                                                                                                "sourceSnippet"))),
                                                                                "clozeQuestions", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "sentenceWithBlank",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "answer",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "choices",
                                                                                                                                Map.of(
                                                                                                                                                "type",
                                                                                                                                                "array",
                                                                                                                                                "items",
                                                                                                                                                Map.of("type", "string"),
                                                                                                                                                "minItems",
                                                                                                                                                4,
                                                                                                                                                "maxItems",
                                                                                                                                                4),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("sentenceWithBlank",
                                                                                                                                "answer",
                                                                                                                                "choices",
                                                                                                                                "sourceSnippet"))),
                                                                                "mcqQuestions", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "question",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "options",
                                                                                                                                Map.of(
                                                                                                                                                "type",
                                                                                                                                                "array",
                                                                                                                                                "items",
                                                                                                                                                Map.of("type", "string"),
                                                                                                                                                "minItems",
                                                                                                                                                4,
                                                                                                                                                "maxItems",
                                                                                                                                                4),
                                                                                                                                "correctIndex",
                                                                                                                                Map.of("type", "integer"),
                                                                                                                                "explanation",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("question",
                                                                                                                                "options",
                                                                                                                                "correctIndex",
                                                                                                                                "explanation",
                                                                                                                                "sourceSnippet")))),
                                                                "required", List.of("flashcards", "clozeQuestions",
                                                                                "mcqQuestions"))));
        }

        private Map<String, Object> buildTrueFalseJsonSchema() {
                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "true_false_pack",
                                                "strict", true,
                                                "schema", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "properties", Map.of(
                                                                                "trueFalseQuestions", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "statement",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "answer",
                                                                                                                                Map.of("type", "boolean"),
                                                                                                                                "explanation",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("statement",
                                                                                                                                "answer",
                                                                                                                                "explanation",
                                                                                                                                "sourceSnippet")))),
                                                                "required", List.of("trueFalseQuestions"))));
        }

        private Map<String, Object> buildFlashcardJsonSchema() {
                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "flashcard_snippet_pack",
                                                "strict", true,
                                                "schema", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "properties", Map.of(
                                                                                "flashcards", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "front",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "back",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("front", "back",
                                                                                                                                "sourceSnippet")))),
                                                                "required", List.of("flashcards"))));
        }

        private Map<String, Object> buildClozeJsonSchema() {
                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "cloze_snippet_pack",
                                                "strict", true,
                                                "schema", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "properties", Map.of(
                                                                                "clozeQuestions", Map.of(
                                                                                                "type", "array",
                                                                                                "items", Map.of(
                                                                                                                "type",
                                                                                                                "object",
                                                                                                                "additionalProperties",
                                                                                                                false,
                                                                                                                "properties",
                                                                                                                Map.of(
                                                                                                                                "sentenceWithBlank",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "answer",
                                                                                                                                Map.of("type", "string"),
                                                                                                                                "choices",
                                                                                                                                Map.of(
                                                                                                                                                "type",
                                                                                                                                                "array",
                                                                                                                                                "items",
                                                                                                                                                Map.of("type", "string"),
                                                                                                                                                "minItems",
                                                                                                                                                4,
                                                                                                                                                "maxItems",
                                                                                                                                                4),
                                                                                                                                "sourceSnippet",
                                                                                                                                Map.of("type", "string")),
                                                                                                                "required",
                                                                                                                List.of("sentenceWithBlank",
                                                                                                                                "answer",
                                                                                                                                "choices",
                                                                                                                                "sourceSnippet")))),
                                                                "required", List.of("clozeQuestions"))));
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ChatCompletionsResponse(List<Choice> choices) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                public record Choice(Message message) {
                }

                @JsonIgnoreProperties(ignoreUnknown = true)
                public record Message(String content) {
                }
        }

        private <T> T withRateLimit(Callable<T> fn) throws Exception {
                OPENAI_LOCK.acquire();
                try {
                        long now = System.currentTimeMillis();
                        long wait = MIN_GAP_MS - (now - lastCallMs);
                        if (wait > 0) {
                                Thread.sleep(wait);
                        }
                        T result = fn.call();
                        lastCallMs = System.currentTimeMillis();
                        return result;
                } finally {
                        OPENAI_LOCK.release();
                }
        }

        private String callAndExtractContent(Map<String, Object> payload) {
                int maxAttempts = 5;

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                                String raw = web.post()
                                                .uri("/chat/completions")
                                                .bodyValue(payload)
                                                .retrieve()
                                                .bodyToMono(String.class)
                                                .block(Duration.ofSeconds(180));

                                if (raw == null || raw.isBlank()) {
                                        throw new RuntimeException("Empty response from OpenAI");
                                }

                                ChatCompletionsResponse parsed = mapper.readValue(raw, ChatCompletionsResponse.class);
                                if (parsed.choices() == null || parsed.choices().isEmpty()) {
                                        throw new RuntimeException("OpenAI returned no choices: " + raw);
                                }

                                String content = parsed.choices().get(0).message().content();
                                if (content == null || content.isBlank()) {
                                        throw new RuntimeException("OpenAI returned empty message content: " + raw);
                                }

                                mapper.readTree(content); // validate JSON
                                return content.trim();

                        } catch (WebClientResponseException.TooManyRequests e) {
                                long retryMs = Math.max(MIN_GAP_MS, (long) Math.pow(2, attempt - 1) * 10_000L);
                                try {
                                        Thread.sleep(retryMs);
                                } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException("Interrupted during OpenAI backoff", ie);
                                }
                                if (attempt == maxAttempts) {
                                        throw new RuntimeException("OpenAI rate limit after retries", e);
                                }
                        } catch (WebClientResponseException e) {
                                throw new RuntimeException(
                                                "OpenAI HTTP error: " + e.getStatusCode().value() + " "
                                                                + e.getResponseBodyAsString(),
                                                e);
                        } catch (Exception e) {
                                throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
                        }
                }

                throw new RuntimeException("OpenAI call failed after retries");
        }
}