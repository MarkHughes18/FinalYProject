package com.example.backend.files;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class FileTextExtractService {
    private final Tika tika = new Tika();

    public String extractText(Path filePath) throws IOException, TikaException {
        String text = tika.parseToString(filePath);
        // basic cleanup
        return text == null ? "" : text.trim();
    }
}
