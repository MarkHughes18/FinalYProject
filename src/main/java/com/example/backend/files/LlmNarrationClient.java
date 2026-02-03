package com.example.backend.files;

public interface LlmNarrationClient {
    String explainChunk(String chunk, int chunkIndex, int totalChunks) throws Exception;

    String smoothNarration(String combined) throws Exception;
}
