package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

@Service
public class FileProcessingService {

    private final FileHistoryRepository repo;
    private final FileTextExtractService textExtractService;
    private final CloudTtsService ttsService;
    private final NarrationService narrationService;

    public FileProcessingService(FileHistoryRepository repo, FileTextExtractService textExtractService,
            CloudTtsService ttsService, NarrationService narrationService) {
        this.repo = repo;
        this.textExtractService = textExtractService;
        this.ttsService = ttsService;
        this.narrationService = narrationService;
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

            // Generate narration
            fh.setNarrationStatus("PROCESSING");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            String narration = narrationService.buildNarration(extracted);

            fh.setNarrationText(narration);
            fh.setNarrationStatus("READY");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            // TTS uses narration
            String toSpeak = narration;
            if (toSpeak.length() > 4500) {
                toSpeak = toSpeak.substring(0, 4500);
            }

            byte[] mp3Bytes = ttsService.synthesizeMp3(toSpeak);

            Path audioDir = Paths.get("audio");
            Files.createDirectories(audioDir);

            Path outMp3 = audioDir.resolve(fh.getId() + ".mp3");
            Files.write(outMp3, mp3Bytes);

            fh.setAudioPath(outMp3.toAbsolutePath().toString());
            fh.setAudioStatus("READY");
            fh.setAudioUrl("/api/files/history/" + fh.getId() + "/audio");

            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

        } catch (Exception ex) {
            FileHistory fail = repo.findById(historyId).orElse(null);
            if (fail != null) {
                fail.setTextStatus("FAILED");
                fail.setNarrationStatus("FAILED");
                fail.setAudioStatus("FAILED");
                fail.setErrorMessage(ex.getMessage());
                fail.setUpdatedAt(Instant.now());
                repo.save(fail);
            }
        }
    }
}
