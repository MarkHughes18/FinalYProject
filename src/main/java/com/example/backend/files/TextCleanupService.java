package com.example.backend.files;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TextCleanupService {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PAGE_MARKERS = Pattern.compile("(?i)page\\s+\\d+\\s+(of\\s+\\d+)?");
    private static final Pattern SLIDE_MARKERS = Pattern.compile("(?i)slide\\s+\\d+");
    private static final int MIN_LINE_LEN = 3;

    public String clean(String raw) {
        if (raw == null)
            return "";

        // Normalize whitespace
        String norm = MULTI_SPACE.matcher(raw).replaceAll(" ").trim();

        // Break into lines using punctuation
        // (Tika sometimes returns messy blocks; line splitting helps de-dup)
        String[] lines = norm.split("(?<=[.!?])\\s+");

        // Remove boilerplate + de-duplicate
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : lines) {
            String s = line.trim();
            if (s.length() < MIN_LINE_LEN)
                continue;
            if (PAGE_MARKERS.matcher(s).find())
                continue;
            if (SLIDE_MARKERS.matcher(s).find())
                continue;
            unique.add(s);
        }

        // Join back into a clean paragraph block
        return String.join(" ", unique).trim();
    }
}
