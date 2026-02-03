package com.example.backend.files;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
public class MockLlmNarrationClient implements LlmNarrationClient {

    @Override
    public String explainChunk(String chunk, int chunkIndex, int totalChunks) {
        return """
                Section %d of %d:
                Here’s an explanation of the content:

                %s
                """.formatted(chunkIndex, totalChunks, chunk);
    }

    @Override
    public String smoothNarration(String combined) {
        // No-op for now
        return combined;
    }
}
