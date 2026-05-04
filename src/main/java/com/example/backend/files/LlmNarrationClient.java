package com.example.backend.files;

import java.util.*;

public interface LlmNarrationClient {
    String explainChunks(List<String> chunks) throws Exception; // explain all chunks in one call, returns JSON array

    String smoothNarration(String combinedJson) throws Exception; // returns combined JSON & final plain narration

    String smoothNarrationFromNotes(List<String> chunks) throws Exception; // helper that does both steps in one call
}
