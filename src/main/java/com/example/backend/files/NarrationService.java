package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NarrationService {

    private final TextCleanupService cleanupService;
    private final TextChunkService chunkService;
    private final LlmNarrationClient llmClient;

    public NarrationService(TextCleanupService cleanupService,
            TextChunkService chunkService,
            LlmNarrationClient llmClient) {
        this.cleanupService = cleanupService;
        this.chunkService = chunkService;
        this.llmClient = llmClient;
    }

    // builds a narration from the extracted text, then sends it to TTS for
    // generation
    public String buildNarration(String extractedText) throws Exception {
        // clean it up and chunk it for the LLM
        String cleaned = cleanupService.clean(extractedText);

        // limit narration length to keep llm calls manageable
        final int MAX_CLEANED_CHARS = 50_000;
        if (cleaned != null && cleaned.length() > MAX_CLEANED_CHARS) {
            cleaned = cleaned.substring(0, MAX_CLEANED_CHARS);
        }
        if (cleaned == null || cleaned.isBlank()) {
            return "I couldn't find readable text in this document. Try a clearer file or a different format.";
        }

        // chunk the cleaned text before sending it for narration
        List<String> chunks = chunkService.chunk(cleaned);
        System.out.println("CLEANED length=" + cleaned.length());
        System.out.println("CHUNKS count=" + chunks.size());
        if (chunks.isEmpty()) {
            return "I couldn't break this document into readable sections. Try a different file or format.";
        }
        // now smooth it into a final narration
        try {
            return llmClient.smoothNarrationFromNotes(chunks);
        } catch (Exception ex) {
            System.out.println("OpenAI narration failed, using extracted-text fallback: " + ex.getMessage());
            return buildFallbackNarration(chunks);
        }
    }

    private String buildFallbackNarration(List<String> chunks) {
        StringBuilder fallback = new StringBuilder();
        fallback.append("Here is an audio overview of your uploaded notes.\n\n");

        int included = 0;
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            String trimmed = chunk.replaceAll("\\s+", " ").trim();
            if (trimmed.length() > 900) {
                trimmed = trimmed.substring(0, 900).trim() + ".";
            }

            fallback.append(trimmed).append("\n\n");
            included++;

            if (included >= 4 || fallback.length() >= 3600) {
                break;
            }
        }

        fallback.append(
                "To recap, review the key definitions, examples, and any steps or patterns described in these notes.");
        return fallback.toString();
    }
}
