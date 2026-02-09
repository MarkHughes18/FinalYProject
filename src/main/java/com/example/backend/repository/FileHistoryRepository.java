package com.example.backend.repository;

import com.example.backend.model.FileHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FileHistoryRepository extends MongoRepository<FileHistory, String> {
    List<FileHistory> findByUserEmailOrderByUploadedAtDesc(String userEmail);

    void deleteByUserEmail(String userEmail);

    List<FileHistory> findByUserEmail(String userEmail);
}
