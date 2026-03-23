package com.example.backend.repository;

import com.example.backend.model.StudyPack;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StudyPackRepository extends MongoRepository<StudyPack, String> {

    Optional<StudyPack> findByUserEmailAndHistoryId(String userEmail, String historyId);

    boolean existsByUserEmailAndHistoryId(String userEmail, String historyId);

    List<StudyPack> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    void deleteByUserEmail(String userEmail);

    void deleteByUserEmailAndHistoryId(String userEmail, String historyId);
}