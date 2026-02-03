package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
        if (cleaned.isBlank()) {
            return "I couldn't find readable text in this document. Try a clearer file or a different format.";
        }

        List<String> chunks = chunkService.chunk(cleaned);

        // Generate explained narration per chunk
        List<String> explainedParts = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String explained = llmClient.explainChunk(chunk, i + 1, chunks.size());
            explainedParts.add(explained);
        }

        // Stitch into one narration script
        String stitched = explainedParts.stream()
                .map(String::trim)
                .collect(Collectors.joining("\n\n"));

        // Optional: final smoothing pass (makes it sound like one consistent talk)
        // If you want to save cost, skip this and go straight to TTS.
        return llmClient.smoothNarration(stitched);
    }
}
