package com.example.backend.files;

import com.example.backend.model.FileHistory;
import com.example.backend.repository.FileHistoryRepository;
import com.example.backend.files.FileProcessingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
                        long fileSize,
                        String ttsLanguageCode,
                        String ttsVoice,
                        String label) {
        }

        public record HistoryResponse(
                        String id,
                        String fileName,
                        String fileType,
                        long fileSize,
                        String uploadedAt,
                        String textStatus,
                        String narrationStatus,
                        String audioStatus,
                        String audioUrl,
                        String updatedAt,
                        String errorMessage,
                        String ttsLanguageCode,
                        String ttsVoice,
                        String label) {
        }

        @PostMapping("/history")
        public HistoryResponse createHistory(@RequestBody CreateHistoryRequest req) {
                FileHistory fh = new FileHistory();
                fh.setUserEmail(req.userEmail());
                fh.setFileName(req.fileName());
                fh.setFileType(req.fileType());
                fh.setFileSize(req.fileSize());
                fh.setUploadedAt(Instant.now());
                // inintial status
                fh.setAudioStatus("PENDING");
                fh.setAudioUrl(null);
                fh.setTextStatus("PENDING");
                fh.setExtractedText(null);
                fh.setSourcePath(null);
                fh.setAudioPath(null);
                fh.setErrorMessage(null);
                fh.setUpdatedAt(Instant.now());
                fh.setNarrationStatus("PENDING");
                fh.setNarrationText(null);
                fh.setTtsLanguageCode((req.ttsLanguageCode() == null || req.ttsLanguageCode().isBlank()) ? "en-GB"
                                : req.ttsLanguageCode().trim());
                fh.setTtsVoice((req.ttsVoice() == null || req.ttsVoice().isBlank()) ? "female" : req.ttsVoice().trim());
                fh.setLabel(req.label());
                fh = repo.save(fh);

                return new HistoryResponse(
                                fh.getId(),
                                fh.getFileName(),
                                fh.getFileType(),
                                fh.getFileSize(),
                                fh.getUploadedAt() != null ? fh.getUploadedAt().toString() : null,
                                fh.getTextStatus(),
                                fh.getNarrationStatus(),
                                fh.getAudioStatus(),
                                fh.getAudioUrl(),
                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                fh.getErrorMessage(),
                                fh.getTtsLanguageCode(),
                                fh.getTtsVoice(),
                                fh.getLabel());
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
                                                fh.getTextStatus(),
                                                fh.getNarrationStatus(),
                                                fh.getAudioStatus(),
                                                fh.getAudioUrl(),
                                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                                fh.getErrorMessage(),
                                                fh.getTtsLanguageCode(),
                                                fh.getTtsVoice(),
                                                fh.getLabel()))
                                .toList();
        }

        @DeleteMapping("/history")
        public ResponseEntity<Void> clearHistory(@RequestParam("email") String email) {
                List<FileHistory> items = repo.findByUserEmail(email);
                for (FileHistory fh : items) {
                        try {
                                // delete uploaded source file
                                if (fh.getSourcePath() != null) {
                                        Files.deleteIfExists(Paths.get(fh.getSourcePath()));
                                }
                                // delete generated audio file
                                if (fh.getAudioPath() != null) {
                                        Files.deleteIfExists(Paths.get(fh.getAudioPath()));
                                }
                        } catch (Exception ex) {
                                System.out.println("Failed deleting file: " + ex.getMessage());
                        }
                }
                // delete records from db
                repo.deleteByUserEmail(email);

                return ResponseEntity.noContent().build();
        }

        @DeleteMapping("/history/{id}")
        public ResponseEntity<Void> deleteHistoryItem(@PathVariable String id) {

                FileHistory fh = repo.findById(id).orElse(null);
                if (fh == null) {
                        return ResponseEntity.notFound().build();
                }
                // delete files
                try {
                        if (fh.getSourcePath() != null) {
                                Files.deleteIfExists(Paths.get(fh.getSourcePath()));
                        }
                } catch (Exception ex) {
                        System.out.println("Failed deleting source file: " + ex.getMessage());
                }
                try {
                        if (fh.getAudioPath() != null) {
                                Files.deleteIfExists(Paths.get(fh.getAudioPath()));
                        }
                } catch (Exception ex) {
                        System.out.println("Failed deleting audio file: " + ex.getMessage());
                }
                // delete DB record
                repo.deleteById(id);

                return ResponseEntity.noContent().build();
        }

        @PostMapping("/upload")
        public ResponseEntity<HistoryResponse> uploadFile(@RequestParam("historyId") String historyId,
                        @RequestParam("file") MultipartFile file) throws IOException {

                if (file.isEmpty()) {
                        return ResponseEntity.badRequest().build();
                }
                FileHistory fh = repo.findById(historyId).orElse(null);
                if (fh == null) {
                        return ResponseEntity.notFound().build();
                }

                // choose a storage directory
                Path uploadRoot = Paths.get("uploads");
                Files.createDirectories(uploadRoot);

                // save file to disk with a unique name
                String original = file.getOriginalFilename();
                String safeOriginalName = (original != null && !original.isBlank())
                                ? Paths.get(original).getFileName().toString()
                                : "upload.bin";
                String storedName = System.currentTimeMillis() + "_" + safeOriginalName;
                Path storedPath = uploadRoot.resolve(storedName);
                Files.copy(file.getInputStream(), storedPath);

                // update metedata on existing history record
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
                fh.setAudioStatus("PENDING");
                fh.setNarrationStatus("PENDING");
                fh.setExtractedText(null);
                fh.setNarrationText(null);
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
                                fh.getTextStatus(),
                                fh.getNarrationStatus(),
                                fh.getAudioStatus(),
                                fh.getAudioUrl(),
                                fh.getUpdatedAt() != null ? fh.getUpdatedAt().toString() : null,
                                fh.getErrorMessage(),
                                fh.getTtsLanguageCode(),
                                fh.getTtsVoice(),
                                fh.getLabel());

                return ResponseEntity.ok(resp);
        }

        @GetMapping("/history/{id}/narration")
        public ResponseEntity<String> getNarration(@PathVariable String id) {

                FileHistory fh = repo.findById(id).orElse(null);
                if (fh == null) {
                        return ResponseEntity.notFound().build();
                }

                // Not ready yet
                if (!"READY".equalsIgnoreCase(fh.getNarrationStatus()) || fh.getNarrationText() == null) {
                        return ResponseEntity.status(202) // Accepted (processing)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body("Narration is not ready yet. Status = " + fh.getNarrationStatus());
                }

                return ResponseEntity.ok()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(fh.getNarrationText());
        }

        @GetMapping("/history/{id}/audio")
        public ResponseEntity<Resource> streamAudio(@PathVariable String id) throws IOException {
                FileHistory fh = repo.findById(id).orElse(null);
                if (fh == null)
                        return ResponseEntity.notFound().build();

                if (fh.getAudioPath() == null || !"READY".equalsIgnoreCase(fh.getAudioStatus())) {
                        return ResponseEntity.notFound().build(); // not ready yet
                }

                Path audioPath = Paths.get(fh.getAudioPath());
                if (!Files.exists(audioPath)) {
                        return ResponseEntity.notFound().build();
                }

                Resource resource = new UrlResource(audioPath.toUri());
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + ".mp3\"")
                                .contentType(MediaType.parseMediaType("audio/mpeg"))
                                .body(resource);
        }
}
