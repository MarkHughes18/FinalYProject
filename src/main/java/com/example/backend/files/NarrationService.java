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
        return llmClient.smoothNarrationFromNotes(chunks);
    }
}
