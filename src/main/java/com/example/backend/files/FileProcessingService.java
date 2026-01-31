package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

@Service
public class FileProcessingService {

    private final FileHistoryRepository repo;
    private final FileTextExtractService textExtractService;

    public FileProcessingService(FileHistoryRepository repo, FileTextExtractService textExtractService) {
        this.repo = repo;
        this.textExtractService = textExtractService;
    }

    @Async
    public void processHistoryAsync(String historyId) {
        FileHistory fh = repo.findById(historyId).orElse(null);
        if (fh == null)
            return;

        try {
            // mark processing started
            fh.setTextStatus("PROCESSING");
            fh.setAudioStatus("PROCESSING");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            // Extract text from uploaded file
            Path source = Paths.get(fh.getSourcePath());
            String extracted = textExtractService.extractText(source);

            fh.setExtractedText(extracted);
            fh.setTextStatus("READY");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            // Smoke-test MP3 generation step:
            // copy a real MP3 from resources to disk proves streaming + playback
            Path audioDir = Paths.get("audio");
            Files.createDirectories(audioDir);

            Path outMp3 = audioDir.resolve(fh.getId() + ".mp3");
            ClassPathResource sample = new ClassPathResource("sample.mp3");
            Files.copy(sample.getInputStream(), outMp3, StandardCopyOption.REPLACE_EXISTING);

            fh.setAudioPath(outMp3.toAbsolutePath().toString());
            fh.setAudioStatus("READY");

            // use relative URL for streaming endpoint
            fh.setAudioUrl("/api/files/history/" + fh.getId() + "/audio");

            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

        } catch (Exception ex) {
            FileHistory fail = repo.findById(historyId).orElse(null);
            if (fail != null) {
                fail.setTextStatus("FAILED");
                fail.setAudioStatus("FAILED");
                fail.setErrorMessage(ex.getMessage());
                fail.setUpdatedAt(Instant.now());
                repo.save(fail);
            }
        }
    }
}
