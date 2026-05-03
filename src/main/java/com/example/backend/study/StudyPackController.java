package com.example.backend.study;

import com.example.backend.model.FileHistory;
import com.example.backend.model.StudyPack;
import com.example.backend.repository.FileHistoryRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.backend.repository.StudyPackRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.backend.study.dto.ConceptPackResponse;
import com.example.backend.study.dto.TrueFalsePackResponse;
import com.example.backend.study.dto.CustomStudyPackRequest;

@RestController
@RequestMapping("/api/study")
public class StudyPackController {

        private final StudyPackGenerationService generationService;
        private final FileHistoryRepository fileHistoryRepository;
        private final StudyPackRepository studyPackRepository;
        private final StudyPackLlmService studyPackLlmService;

        public StudyPackController(StudyPackGenerationService generationService,
                        FileHistoryRepository fileHistoryRepository, StudyPackRepository studyPackRepository,
                        StudyPackLlmService studyPackLlmService) {
                this.generationService = generationService;
                this.fileHistoryRepository = fileHistoryRepository;
                this.studyPackRepository = studyPackRepository;
                this.studyPackLlmService = studyPackLlmService;
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
                        Integer versionNumber,
                        Boolean active,
                        String createdAt,
                        String updatedAt) {
        }

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
                                        .body("Study pack cannot be generated yet. Narration status = "
                                                        + fh.getNarrationStatus());
                }

                // Generate/fetch cached pack
                StudyPack pack = generationService.getOrGenerate(email, historyId);
                return ResponseEntity.ok(pack);
        }

        // Regenerate a StudyPack for a given historyId
        @PostMapping(value = "/packs/{historyId}/regenerate", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<?> regeneratePack(@PathVariable String historyId,
                        @RequestParam("email") String email) {

                FileHistory fh = fileHistoryRepository.findById(historyId).orElse(null);
                if (fh == null) {
                        return ResponseEntity.notFound().build();
                }

                // Ownership check
                if (fh.getUserEmail() == null || !fh.getUserEmail().equalsIgnoreCase(email)) {
                        return ResponseEntity.status(403).body("Not allowed");
                }

                // Have narration/extracted source ready before regeneration
                if (!"READY".equalsIgnoreCase(fh.getNarrationStatus()) || fh.getNarrationText() == null
                                || fh.getNarrationText().isBlank()) {
                        return ResponseEntity.status(202)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body("Study pack cannot be regenerated yet. Narration status = "
                                                        + fh.getNarrationStatus());
                }

                StudyPack regenerated = generationService.regenerate(email, historyId);
                return ResponseEntity.ok(regenerated);
        }

        // Summary endpoint, counts + status
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
                                        null,
                                        null,
                                        null);

                        return ResponseEntity.status(202).body(resp);
                }

                // If pack exists, return counts. If not, return exists=false.
                return studyPackRepository.findByUserEmailAndHistoryIdAndActiveTrue(email, historyId)
                                .<ResponseEntity<?>>map(pack -> {
                                        StudyPackSummaryResponse resp = new StudyPackSummaryResponse(
                                                        historyId,
                                                        true,
                                                        fh.getNarrationStatus(),
                                                        "OK",
                                                        pack.getFlashcards() != null ? pack.getFlashcards().size() : 0,
                                                        pack.getMatchingPairs() != null ? pack.getMatchingPairs().size()
                                                                        : 0,
                                                        pack.getClozeQuestions() != null
                                                                        ? pack.getClozeQuestions().size()
                                                                        : 0,
                                                        pack.getTrueFalseQuestions() != null
                                                                        ? pack.getTrueFalseQuestions().size()
                                                                        : 0,
                                                        pack.getMcqQuestions() != null ? pack.getMcqQuestions().size()
                                                                        : 0,
                                                        pack.getVersionNumber(),
                                                        pack.getActive(),
                                                        pack.getCreatedAt() != null ? pack.getCreatedAt().toString()
                                                                        : null,
                                                        pack.getUpdatedAt() != null ? pack.getUpdatedAt().toString()
                                                                        : null);
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
                                                        null,
                                                        null,
                                                        null);
                                        return ResponseEntity.ok(resp);
                                });
        }

        @PostMapping(value = "/custom-pack", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<?> createCustomPack(@RequestBody CustomStudyPackRequest request) {
                if (request == null || request.getUserEmail() == null || request.getUserEmail().isBlank()) {
                        return ResponseEntity.badRequest().body("Missing user email");
                }

                StudyPack pack = generationService.createCustomStudyPack(request);
                return ResponseEntity.ok(pack);
        }

        @GetMapping("/test-llm")
        public Map<String, Object> testLlm() throws Exception {
                List<String> definitionPool = List.of(
                                "Project management is the application of knowledge, skills, tools, and techniques to meet project requirements.",
                                "A project is a temporary endeavor undertaken to create a unique product, service, or result.",
                                "Planning involves defining the scope, schedule, resources, and risks.",
                                "Execution is where the project plan is put into action.",
                                "Control involves monitoring progress and making adjustments as needed.",
                                "Closeout occurs when the project is formally completed.");

                List<String> processPool = List.of(
                                "Planning involves defining the scope, schedule, resources, and risks.",
                                "Execution is where the project plan is put into action.",
                                "Control involves monitoring progress and making adjustments as needed.",
                                "Closeout occurs when the project is formally completed.");

                List<String> detailPool = List.of(
                                "Stakeholders include anyone affected by the project, such as team members, customers, suppliers, and management.",
                                "Key skills in project management include communication, leadership, problem-solving, negotiation, and risk management.");

                List<String> topicLabels = List.of(
                                "Project Management",
                                "Planning",
                                "Execution",
                                "Control");

                StudyPack.StudyPackSettings settings = new StudyPack.StudyPackSettings(
                                5, 3, 5, 5, 5, "EASY");

                ConceptPackResponse conceptPack = studyPackLlmService.generateConceptPack(
                                definitionPool,
                                processPool,
                                topicLabels,
                                settings);

                TrueFalsePackResponse tfPack = studyPackLlmService.generateTrueFalsePack(
                                processPool,
                                detailPool,
                                settings);

                Map<String, Object> out = new HashMap<>();
                out.put("conceptPack", conceptPack);
                out.put("trueFalsePack", tfPack);
                return out;
        }
}