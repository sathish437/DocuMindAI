package com.example.DocuMindAI.repository;

import com.example.DocuMindAI.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByUploadTimestampDesc();

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.segments WHERE d.id = :id")
    Optional<Document> findByIdWithSegments(@Param("id") UUID id);
}
