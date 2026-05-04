package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkService {

    // rough character budget per chunk
    private static final int CHUNK_SIZE = 2000;
    private static final int OVERLAP = 200;
    private static final int MAX_CHUNKS = 30;

    // breaks cleaned text into chunks for LLM processing, trying to split on
    // sentence boundaries
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank())
            return chunks;

        int start = 0;
        int guard = 0; // safety

        while (start < text.length() && guard++ < 10000) {

            int end = Math.min(text.length(), start + CHUNK_SIZE);
            int cut = end;
            int lastPeriod = text.lastIndexOf('.', end);
            if (lastPeriod > start + 100) {
                cut = lastPeriod + 1;
            }
            if (cut <= start) {
                cut = end; // safety if period logic fails
            }
            String part = text.substring(start, cut).trim();
            if (!part.isBlank()) {
                chunks.add(part);
            }
            if (chunks.size() >= MAX_CHUNKS) {
                break;
            }
            // overlap correctly
            if (cut >= text.length()) {
                break;
            }
            int nextStart = cut - OVERLAP;
            if (nextStart <= start) {
                nextStart = cut; // no overlap possible, force forward progress
            }
            start = nextStart;
        }
        return chunks;
    }
}
