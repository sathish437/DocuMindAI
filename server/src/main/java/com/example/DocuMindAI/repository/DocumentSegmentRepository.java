package com.example.DocuMindAI.repository;

import com.example.DocuMindAI.entity.DocumentSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, UUID> {

    List<DocumentSegment> findByDocumentIdOrderBySegmentOrderAsc(UUID documentId);

    /**
     * Eagerly loads all segments joined with their parent document metadata in order,
     * enabling cross-document multi-evidence synthesis and contradiction analysis.
     */
    @Query("SELECT s FROM DocumentSegment s JOIN FETCH s.document d ORDER BY d.uploadTimestamp DESC, s.segmentOrder ASC")
    List<DocumentSegment> findAllWithDocument();

    /**
     * Retrieves segments for a specific subset of documents with parent metadata fetched.
     * Supports scoped cross-document comparative analysis.
     */
    @Query("SELECT s FROM DocumentSegment s JOIN FETCH s.document d WHERE d.id IN :documentIds ORDER BY d.id, s.segmentOrder ASC")
    List<DocumentSegment> findByDocumentIdsWithDocument(@Param("documentIds") List<UUID> documentIds);

    /**
     * Searches extracted segments containing the specified text query (case-insensitive),
     * eagerly fetching parent document details for source citation.
     */
    @Query("SELECT s FROM DocumentSegment s JOIN FETCH s.document d WHERE LOWER(s.text) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY d.uploadTimestamp DESC, s.segmentOrder ASC")
    List<DocumentSegment> searchSegmentsByQuery(@Param("query") String query);
}
