package com.example.backend.files;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@Service
@Primary
public class OpenAiNarrationClient implements LlmNarrationClient {

    private final WebClient web;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiNarrationClient() {
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

    // Explain a chunk into STRUCTURED JSON for narration building, returns JSON
    // string
    @Override
    public String explainChunk(String chunk, int chunkIndex, int totalChunks) {
        String system = """
                You are an expert tutor. Output MUST be valid JSON only.
                No markdown. No extra text.
                """;

        String user = """
                Convert this chunk of lecture notes into JSON for narration building.

                Chunk %d of %d

                Return JSON EXACTLY in this schema:
                {
                  "chunk": %d,
                  "title": "short title",
                  "key_points": ["...","..."],
                  "definitions": [{"term":"...","meaning":"..."}],
                  "examples": ["..."],
                  "common_mistakes": ["..."],
                  "quick_check_questions": ["..."]
                }

                Notes chunk:
                %s
                """.formatted(chunkIndex, totalChunks, chunkIndex, chunk);

        Map<String, Object> payload = Map.of(
                "model", "gpt-4.1-mini",
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        return callAndExtractContent(payload);
    }

    // Take combined JSON summaries and produce 1 final spoken script
    @Override
    public String smoothNarration(String combinedJsonSummaries) {
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

        return callAndExtractContent(payload);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionsResponse(java.util.List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(Message message) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String content) {
        }
    }

    private String callAndExtractContent(Map<String, Object> payload) {
        String raw = web.post()
                .uri("/chat/completions")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Empty response from OpenAI");
        }

        try {
            ChatCompletionsResponse parsed = mapper.readValue(raw, ChatCompletionsResponse.class);
            if (parsed.choices() == null || parsed.choices().isEmpty()) {
                throw new RuntimeException("OpenAI returned no choices: " + raw);
            }
            ChatCompletionsResponse.Choice c0 = parsed.choices().get(0);
            if (c0.message() == null || c0.message().content() == null) {
                throw new RuntimeException("OpenAI returned empty message content: " + raw);
            }
            String content = c0.message().content().trim();
            if (content.startsWith("```")) {
                int firstNewline = content.indexOf('\n');
                int lastFence = content.lastIndexOf("```");
                if (firstNewline > 0 && lastFence > firstNewline) {
                    content = content.substring(firstNewline + 1, lastFence).trim();
                }
            }

            return content;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response JSON: " + e.getMessage() + "\nRAW:\n" + raw, e);
        }
    }
}
