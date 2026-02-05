package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkService {

    // Rough character budget per chunk
    private static final int CHUNK_SIZE = 3500;
    private static final int OVERLAP = 200;

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
            // overlap correctly
            start = Math.max(cut - OVERLAP, 0);
            if (cut >= text.length()) {
                break;
            }
        }
        return chunks;
    }
}
