package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study")
public class StudyPackController {

    private final StudyPackGenerationService generationService;
    private final FileHistoryRepository fileHistoryRepository;

    public StudyPackController(StudyPackGenerationService generationService,
            FileHistoryRepository fileHistoryRepository) {
        this.generationService = generationService;
        this.fileHistoryRepository = fileHistoryRepository;
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
}