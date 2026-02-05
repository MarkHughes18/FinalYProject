package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

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

    public String buildNarration(String extractedText) throws Exception {
        String cleaned = cleanupService.clean(extractedText);
        if (cleaned == null || cleaned.isBlank()) {
            return "I couldn't find readable text in this document. Try a clearer file or a different format.";
        }

        List<String> chunks = chunkService.chunk(cleaned);

        // LLM returns JSON per chunk
        List<String> jsonObjects = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            // explainChunk should return a JSON object string
            String json = llmClient.explainChunk(chunk, i + 1, chunks.size());

            if (json != null && !json.isBlank()) {
                jsonObjects.add(json.trim());
            }
        }
        if (jsonObjects.isEmpty()) {
            return "I couldn't generate a narration summary from this file. Try a different document.";
        }

        // combine into ONE JSON array for smoothing
        String combinedJsonArray = "[\n" + String.join(",\n", jsonObjects) + "\n]";

        // smoothing pass converts JSON summaries -> ONE spoken script
        return llmClient.smoothNarration(combinedJsonArray);
    }
}
