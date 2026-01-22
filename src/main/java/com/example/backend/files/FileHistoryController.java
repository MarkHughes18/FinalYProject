package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileHistoryController {

    private final FileHistoryRepository repo;

    public FileHistoryController(FileHistoryRepository repo) {
        this.repo = repo;
    }

    public record CreateHistoryRequest(
            String userEmail,
            String fileName,
            String fileType,
            long fileSize) {
    }

    public record HistoryResponse(
            String id,
            String fileName,
            String fileType,
            long fileSize,
            String uploadedAt,
            String audioStatus,
            String audioUrl) {
    }

    @PostMapping("/history")
    public HistoryResponse createHistory(@RequestBody CreateHistoryRequest req) {
        FileHistory fh = new FileHistory();
        fh.setUserEmail(req.userEmail());
        fh.setFileName(req.fileName());
        fh.setFileType(req.fileType());
        fh.setFileSize(req.fileSize());
        fh.setUploadedAt(Instant.now());
        fh.setAudioStatus("PENDING");
        fh.setAudioUrl(null);

        fh = repo.save(fh);

        return new HistoryResponse(
                fh.getId(),
                fh.getFileName(),
                fh.getFileType(),
                fh.getFileSize(),
                fh.getUploadedAt().toString(),
                fh.getAudioStatus(),
                fh.getAudioUrl());
    }

    @GetMapping("/history")
    public List<HistoryResponse> getHistory(@RequestParam String userEmail) {
        return repo.findByUserEmailOrderByUploadedAtDesc(userEmail)
                .stream()
                .map(fh -> new HistoryResponse(
                        fh.getId(),
                        fh.getFileName(),
                        fh.getFileType(),
                        fh.getFileSize(),
                        fh.getUploadedAt().toString(),
                        fh.getAudioStatus(),
                        fh.getAudioUrl()))
                .toList();
    }
}
