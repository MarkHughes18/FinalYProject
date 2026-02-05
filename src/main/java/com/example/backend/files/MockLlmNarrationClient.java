package com.example.backend.files;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockLlmNarrationClient implements LlmNarrationClient {

    @Override
    public String explainChunks(List<String> chunks) {
        // Return a JSON ARRAY (string) so your NarrationService pipeline still works
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            sb.append("  {\n");
            sb.append("    \"chunk\": ").append(i + 1).append(",\n");
            sb.append("    \"title\": \"Mock section ").append(i + 1).append("\",\n");
            sb.append("    \"key_points\": [\"").append(escape(chunk)).append("\"],\n");
            sb.append("    \"definitions\": [],\n");
            sb.append("    \"examples\": [],\n");
            sb.append("    \"common_mistakes\": [],\n");
            sb.append("    \"quick_check_questions\": []\n");
            sb.append("  }");

            if (i < chunks.size() - 1)
                sb.append(",");
            sb.append("\n");
        }

        sb.append("]\n");
        return sb.toString();
    }

    @Override
    public String smoothNarration(String combined) {
        // No-op for now
        return combined;
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
