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
            System.out.println("PROCESS start id=" + historyId);
            // mark processing started
            fh.setTextStatus("PROCESSING");
            fh.setAudioStatus("PROCESSING");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            // text extraction
            Path source = Paths.get(fh.getSourcePath()); // ✅ declare BEFORE using/logging
            System.out.println("TEXT extract start id=" + historyId + " source=" + source);
            String extracted = textExtractService.extractText(source);
            final int MAX_EXTRACTED_CHARS = 50_000;
            if (extracted != null && extracted.length() > MAX_EXTRACTED_CHARS) {
                extracted = extracted.substring(0, MAX_EXTRACTED_CHARS);
            }

            System.out.println(
                    "TEXT extract done id=" + historyId + " len=" + (extracted == null ? 0 : extracted.length()));
            fh.setExtractedText(extracted);
            fh.setTextStatus("READY");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            // narration build
            fh.setNarrationStatus("PROCESSING");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);
            System.out.println("NARRATION build start id=" + historyId);

            String narration = narrationService.buildNarration(extracted);

            System.out.println(
                    "NARRATION build done id=" + historyId + " len=" + (narration == null ? 0 : narration.length()));
            fh.setNarrationText(narration);
            fh.setNarrationStatus("READY");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            String toSpeak = narration; // trim to fit TTS limits
            if (toSpeak == null)
                toSpeak = "";

            if (toSpeak.length() > 4500) {
                toSpeak = toSpeak.substring(0, 4500);
            }
            System.out.println("TTS start id=" + historyId + " speakLen=" + toSpeak.length());

            byte[] mp3Bytes = ttsService.synthesizeMp3(toSpeak, "en-GB", fh.getTtsVoiceName(), fh.getTtsGender());
            System.out.println("TTS done id=" + historyId + " bytes=" + (mp3Bytes == null ? 0 : mp3Bytes.length));

            Path audioDir = Paths.get("audio");
            Files.createDirectories(audioDir);

            Path outMp3 = audioDir.resolve(fh.getId() + ".mp3");
            Files.write(outMp3, mp3Bytes);

            fh.setAudioPath(outMp3.toAbsolutePath().toString());
            fh.setAudioStatus("READY");
            fh.setAudioUrl("/api/files/history/" + fh.getId() + "/audio");
            fh.setUpdatedAt(Instant.now());
            repo.save(fh);

            System.out.println("PROCESS done id=" + historyId);
        } catch (Exception ex) {
            ex.printStackTrace(); // important so to see why it failed
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
