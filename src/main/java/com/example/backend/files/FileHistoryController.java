package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import com.example.backend.files.FileProcessingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/files")
public class FileHistoryController {

        private final FileHistoryRepository repo;
        private final FileProcessingService processingService;

        public FileHistoryController(FileHistoryRepository repo, FileProcessingService processingService) {
                this.repo = repo;
                this.processingService = processingService;
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
                        String audioUrl,
                        String updatedAt,
                        String errorMessage) {
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
                fh.setSourcePath(null);
                fh.setAudioPath(null);
                fh.setErrorMessage(null);
                fh.setUpdatedAt(Instant.now());

                fh = repo.save(fh);

                return new HistoryResponse(
                                fh.getId(),
                                fh.getFileName(),
                                fh.getFileType(),
                                fh.getFileSize(),
                                fh.getUploadedAt().toString(),
                                fh.getAudioStatus(),
                                fh.getAudioUrl(),
                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                fh.getErrorMessage());
        }

        @GetMapping("/history")
        public List<HistoryResponse> getHistory(@RequestParam("email") String email) {
                return repo.findByUserEmailOrderByUploadedAtDesc(email)
                                .stream()
                                .map(fh -> new HistoryResponse(
                                                fh.getId(),
                                                fh.getFileName(),
                                                fh.getFileType(),
                                                fh.getFileSize(),
                                                fh.getUploadedAt() != null ? fh.getUploadedAt().toString() : null,
                                                fh.getAudioStatus(),
                                                fh.getAudioUrl(),
                                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                                fh.getErrorMessage()))
                                .toList();
        }

        @PostMapping("/upload")
        public ResponseEntity<HistoryResponse> uploadFile(@RequestParam("email") String email,
                        @RequestParam("file") MultipartFile file)
                        throws IOException {

                if (file.isEmpty()) {
                        return ResponseEntity.badRequest().build();
                }

                // choose a storage directory
                Path uploadRoot = Paths.get("uploads");
                Files.createDirectories(uploadRoot);

                // save file to disk with a unique name
                String safeOriginalName = file.getOriginalFilename() != null ? file.getOriginalFilename()
                                : "upload.bin";
                String storedName = System.currentTimeMillis() + "_" + safeOriginalName;
                Path storedPath = uploadRoot.resolve(storedName);
                Files.copy(file.getInputStream(), storedPath);

                // create a FileHistory record
                FileHistory fh = new FileHistory();
                fh.setUserEmail(email);
                fh.setFileName(safeOriginalName);
                fh.setFileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
                fh.setFileSize(file.getSize());

                fh.setUploadedAt(Instant.now());
                fh.setUpdatedAt(Instant.now());

                // disk paths
                fh.setSourcePath(storedPath.toAbsolutePath().toString());
                fh.setAudioPath(null);

                // initial status
                fh.setTextStatus("PENDING");
                fh.setExtractedText(null);

                fh.setAudioStatus("PENDING");
                fh.setAudioUrl(null);
                fh.setErrorMessage(null);

                fh = repo.save(fh);
                // trigger async processing -> extract text, tts, save mp3, update record
                processingService.processHistoryAsync(fh.getId());

                HistoryResponse resp = new HistoryResponse(
                                fh.getId(),
                                fh.getFileName(),
                                fh.getFileType(),
                                fh.getFileSize(),
                                fh.getUploadedAt() != null ? fh.getUploadedAt().toString() : null,
                                fh.getAudioStatus(),
                                fh.getAudioUrl(),
                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                fh.getErrorMessage());

                return ResponseEntity.ok(resp);
        }

}
