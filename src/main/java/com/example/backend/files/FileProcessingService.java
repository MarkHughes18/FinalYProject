package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FileProcessingService {

    private final FileHistoryRepository repo;

    public FileProcessingService(FileHistoryRepository repo) {
        this.repo = repo;
    }

    @Async
    public void processHistoryAsync(String historyId) {
        // mark text/audio as READY so can prove async wiring works.
        // replace this with real extraction + cloud TTS + mp3 saving.
        try {
            FileHistory fh = repo.findById(historyId).orElse(null);
            if (fh == null)
                return;

            fh.setTextStatus("READY");
            fh.setExtractedText("Placeholder extracted text.");

            fh.setAudioStatus("READY");
            fh.setAudioUrl("http://10.0.2.2:8080/api/files/history/" + fh.getId() + "/audio"); // build this endpoint
                                                                                               // later

            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

        } catch (Exception ex) {
            FileHistory fh = repo.findById(historyId).orElse(null);
            if (fh != null) {
                fh.setTextStatus("FAILED");
                fh.setAudioStatus("FAILED");
                fh.setErrorMessage(ex.getMessage());
                fh.setUpdatedAt(Instant.now());
                repo.save(fh);
            }
        }
    }
}
