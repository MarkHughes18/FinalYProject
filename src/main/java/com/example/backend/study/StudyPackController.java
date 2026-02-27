package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.backend.repository.StudyPackRepository;

@RestController
@RequestMapping("/api/study")
public class StudyPackController {

    private final StudyPackGenerationService generationService;
    private final FileHistoryRepository fileHistoryRepository;
    private final StudyPackRepository studyPackRepository;

    public StudyPackController(StudyPackGenerationService generationService,
            FileHistoryRepository fileHistoryRepository, StudyPackRepository studyPackRepository) {
        this.generationService = generationService;
        this.fileHistoryRepository = fileHistoryRepository;
        this.studyPackRepository = studyPackRepository;
    }

    public record StudyPackSummaryResponse(
            String historyId,
            boolean exists,
            String narrationStatus,
            String message,
            int flashcards,
            int matchingPairs,
            int cloze,
            int trueFalse,
            int mcq,
            String createdAt,
            String updatedAt) {
    }

    // Get a StudyPack for a given historyId.
    // Same study pack will be returned each time , will add regenerate later
    @GetMapping(value = "/packs/{historyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrGeneratePack(@PathVariable String historyId,
            @RequestParam("email") String email) {

        FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
        if (fh == null) {
            return ResponseEntity.notFound().build();
        }

        // Ownership check
        if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(403).body("Not allowed");
        }

        // If narration not ready, return 202 like narration endpoint
        if (!"READY".equalsIgnoreCase(fh.getNarrationStatus()) || fh.getNarrationText() == null
                || fh.getNarrationText().isBlank()) {
            return ResponseEntity.status(202)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Study pack cannot be generated yet. Narration status = " + fh.getNarrationStatus());
        }

        // Generate/fetch cached pack
        StudyPack pack = generationService.getOrGenerate(email, historyId);
        return ResponseEntity.ok(pack);
    }

    // Summary endpoint, counts + status
    // GET /api/study/packs/{historyId}/summary?email=...
    @GetMapping(value = "/packs/{historyId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPackSummary(@PathVariable String historyId,
            @RequestParam("email") String email) {

        FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
        if (fh == null) {
            return ResponseEntity.notFound().build();
        }

        // Ownership check
        if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(403).body("Not allowed");
        }

        // If narration isn't ready, return 202 but still JSON
        if (!"READY".equalsIgnoreCase(fh.getNarrationStatus()) || fh.getNarrationText() == null
                || fh.getNarrationText().isBlank()) {

            StudyPackSummaryResponse resp = new StudyPackSummaryResponse(
                    historyId,
                    false,
                    fh.getNarrationStatus(),
                    "Narration not ready yet; study pack cannot be generated.",
                    0, 0, 0, 0, 0,
                    null,
                    null);

            return ResponseEntity.status(202).body(resp);
        }

        // If pack exists, return counts. If not, return exists=false.
        return studyPackRepository.findByUserEmailAndHistoryId(email, historyId)
                .<ResponseEntity<?>>map(pack -> {
                    StudyPackSummaryResponse resp = new StudyPackSummaryResponse(
                            historyId,
                            true,
                            fh.getNarrationStatus(),
                            "OK",
                            pack.getFlashcards() != null ? pack.getFlashcards().size() : 0,
                            pack.getMatchingPairs() != null ? pack.getMatchingPairs().size() : 0,
                            pack.getClozeQuestions() != null ? pack.getClozeQuestions().size() : 0,
                            pack.getTrueFalseQuestions() != null ? pack.getTrueFalseQuestions().size() : 0,
                            pack.getMcqQuestions() != null ? pack.getMcqQuestions().size() : 0,
                            pack.getCreatedAt() != null ? pack.getCreatedAt().toString() : null,
                            pack.getUpdatedAt() != null ? pack.getUpdatedAt().toString() : null);
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> {
                    StudyPackSummaryResponse resp = new StudyPackSummaryResponse(
                            historyId,
                            false,
                            fh.getNarrationStatus(),
                            "No study pack generated yet.",
                            0, 0, 0, 0, 0,
                            null,
                            null);
                    return ResponseEntity.ok(resp);
                });
    }
}