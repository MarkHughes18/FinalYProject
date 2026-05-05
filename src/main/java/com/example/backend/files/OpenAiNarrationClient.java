package com.example.backend.files;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Callable;

@Service
@Primary
public class OpenAiNarrationClient implements LlmNarrationClient {

    private final WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();
    // only one request is sent at a time
    private static final Semaphore OPENAI_LOCK = new Semaphore(1);
    // tracks last call to space out requests
    private static volatile long lastCallMs = 0;
    // minimum delay between calls to avoid hitting rate limits
    private static final long MIN_GAP_MS = 2500; // 2.5 seconds between calls

    // api key
    public OpenAiNarrationClient() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }

        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE);

        this.web = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // explain a chunk into structured JSON for narration building, returns JSON
    @Override
    public String explainChunks(List<String> chunks) throws Exception {
        if (chunks == null || chunks.isEmpty()) {
            return "[]";
        }
        String system = """
                You are an expert tutor. Output MUST be valid JSON only.
                No markdown. No extra text.
                """;

        // format chunks with headers for clarity in the prompt
        int totalChunks = chunks.size();
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            notes.append("=== CHUNK ").append(i + 1).append(" of ").append(totalChunks).append(" ===\n");
            notes.append(chunks.get(i)).append("\n\n");
        }
        String user = """
                    Convert the following lecture notes into structured JSON for narration building.


                    Return JSON EXACTLY in this schema (top-level object):
                {
                  "chunks": [
                    {
                      "chunk": 1,
                      "title": "short title",
                      "key_points": ["...","..."],
                      "definitions": [{"term":"...","meaning":"..."}],
                      "examples": ["..."],
                      "common_mistakes": ["..."],
                      "quick_check_questions": ["..."]
                    }
                  ]
                }

                Notes:
                %s
                """.formatted(notes.toString());

        Map<String, Object> payload = Map.of(
                "model", "gpt-4.1-mini",
                "temperature", 0.2,
                "max_tokens", 2200,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        String content = withRateLimit(() -> callAndExtractContent(payload));

        // extract and return JUST the JSON ARRAY string
        JsonNode root = mapper.readTree(content);
        JsonNode arr = root.get("chunks");
        if (arr == null || !arr.isArray()) {
            throw new RuntimeException("OpenAI JSON missing 'chunks' array:\n" + content);
        }

        return mapper.writeValueAsString(arr);
    }

    // take combined JSON summaries and produce 1 final spoken script
    @Override
    public String smoothNarration(String combinedJsonSummaries) throws Exception {
        String system = """
                You are an expert tutor creating a spoken narration script.
                Output plain text only.
                Do not mention JSON or chunks.
                """;

        String user = """
                Create ONE final spoken narration script using the JSON summaries below.

                Requirements:
                - Start with a 1–2 sentence overview
                - Explain concepts clearly with transitions ("Now let's move on to...")
                - Include key definitions
                - Include 1–2 short examples if available
                - End with 3 recap questions
                - Output narration as plain text only (no JSON)

                JSON summaries:
                %s
                """.formatted(combinedJsonSummaries);

        Map<String, Object> payload = Map.of(
                "model", "gpt-4.1-mini",
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        return withRateLimit(() -> callAndExtractContent(payload));
    }

    // reads message from api response and handles errors, retries, and rate limits
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionsResponse(java.util.List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String content) {
        }
    }

    // spaces out calls and prevents multiple simultaneous calls
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

    // makes the API call and extracts the content, with error handling and retries
    // for rate limits
    private String callAndExtractContent(Map<String, Object> payload) {

        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String raw = web.post()
                        .uri("/chat/completions")
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(60));

                if (raw == null || raw.isBlank()) {
                    throw new RuntimeException("Empty response from OpenAI");
                }

                ChatCompletionsResponse parsed = mapper.readValue(raw, ChatCompletionsResponse.class);
                if (parsed.choices() == null || parsed.choices().isEmpty()) {
                    throw new RuntimeException("OpenAI returned no choices: " + raw);
                }
                ChatCompletionsResponse.Choice c0 = parsed.choices().get(0);
                if (c0.message() == null || c0.message().content() == null) {
                    throw new RuntimeException("OpenAI returned empty message content: " + raw);
                }
                String content = c0.message().content().trim();
                // Validate JSON ONLY when response_format.type == "json_object"
                boolean expectsJson = false;
                Object rf = payload.get("response_format");
                if (rf instanceof Map<?, ?> rfMap) {
                    Object type = rfMap.get("type");
                    expectsJson = "json_object".equals(String.valueOf(type));
                }
                if (expectsJson) {
                    try {
                        mapper.readTree(content); // throws if invalid JSON
                    } catch (Exception jsonEx) {
                        throw new RuntimeException("OpenAI returned invalid JSON:\n" + content);
                    }
                }
                return content;
            } catch (WebClientResponseException.TooManyRequests e) {
                String body = e.getResponseBodyAsString();
                System.out.println("OPENAI 429 BODY: " + e.getResponseBodyAsString());
                System.out.println("OPENAI 429 RETRY-AFTER: " + e.getHeaders().getFirst("Retry-After"));

                if (body != null && body.contains("\"insufficient_quota\"")) {
                    throw new RuntimeException(
                            "OpenAI quota exceeded (insufficient_quota). Fix billing/limits for this API key. Body: "
                                    + body,
                            e);
                }
                // 429 retry with backoff
                long retryMs = 0;

                String retryAfter = e.getHeaders().getFirst("Retry-After");
                if (retryAfter != null) {
                    try {
                        retryMs = Long.parseLong(retryAfter.trim()) * 1000L;
                    } catch (NumberFormatException ignore) {
                    }
                }
                if (retryMs <= 0) {
                    retryMs = Math.max(MIN_GAP_MS, (long) Math.pow(2, attempt - 1) * 10_000L);
                }
                System.out
                        .println("OPENAI 429 (attempt " + attempt + "/" + maxAttempts + ") waiting " + retryMs + "ms");
                try {
                    Thread.sleep(retryMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while backing off after 429", ie);
                }
                // last attempt -> fail
                if (attempt == maxAttempts) {
                    throw new RuntimeException("OpenAI rate limit (429) after retries: " + e.getMessage(), e);
                }

            } catch (WebClientResponseException e) {
                // non-429 HTTP errors
                throw new RuntimeException(
                        "OpenAI HTTP error: " + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
            } catch (Exception e) {
                throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("OpenAI call failed after retries");
    }

    // helper that does both steps in one call
    @Override
    public String smoothNarrationFromNotes(List<String> chunks) throws Exception {
        String system = """
                You are an expert tutor creating a spoken narration script.
                Output plain text only.
                """;

        String user = """
                Create ONE final spoken narration script from these notes.

                Requirements:
                - Start with a 1–2 sentence overview
                - Explain clearly with transitions
                - Include key definitions
                - Include 1–2 short examples
                - End with 3 recap questions

                Notes:
                %s
                """.formatted(String.join("\n\n", chunks));

        Map<String, Object> payload = Map.of(
                "model", "gpt-4.1-mini",
                "temperature", 0.3,
                "max_tokens", 1200,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        return withRateLimit(() -> callAndExtractContent(payload));
    }

}
