package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.VectorSegmentResult;
import com.example.DocuMindAI.entity.Document;
import com.example.DocuMindAI.entity.DocumentSegment;
import com.example.DocuMindAI.event.DocumentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service managing vector embeddings and pgvector similarity retrieval for document segments.
 */
@Service
public class DocumentVectorService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVectorService.class);

    private final VectorStore vectorStore;
    private final Map<UUID, Integer> documentContentHashes = new ConcurrentHashMap<>();
    private final Map<String, CachedVectorResults> vectorQueryCache = new ConcurrentHashMap<>();
    private static final long VECTOR_CACHE_TTL_MS = 60_000;
    private static final int MAX_CACHE_ENTRIES = 200;

    private record CachedVectorResults(long timestamp, List<VectorSegmentResult> results) {}

    public DocumentVectorService(@Autowired(required = false) VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        if (vectorStore != null) {
            logger.info("DocumentVectorService initialized with active VectorStore: {}", vectorStore.getClass().getSimpleName());
        } else {
            logger.info("DocumentVectorService initialized without VectorStore (vector indexing disabled).");
        }
    }

    public void clearQueryCache() {
        vectorQueryCache.clear();
    }

    @EventListener
    public void onDocumentChanged(DocumentChangedEvent event) {
        clearQueryCache();
        if (event != null && event.documentId() != null && "DELETE".equalsIgnoreCase(event.action())) {
            documentContentHashes.remove(event.documentId());
        }
    }

    /**
     * Checks if a VectorStore bean is configured and available.
     */
    public boolean isVectorStoreAvailable() {
        return vectorStore != null;
    }

    /**
     * Generates and persists embeddings for non-empty extracted segments of a document.
     * Deletes any previous vector records for this document to avoid duplicates.
     * Avoids reprocessing unchanged segments when document content has not changed.
     */
    @Transactional
    public void indexDocumentSegments(Document document) {
        if (vectorStore == null) {
            logger.debug("VectorStore is not configured; skipping embedding generation for document ID: {}", document.getId());
            return;
        }

        if (document.getSegments() == null || document.getSegments().isEmpty()) {
            logger.debug("Document ID {} has no segments to index.", document.getId());
            return;
        }

        int contentHash = computeDocumentContentHash(document);
        if (document.getId() != null && documentContentHashes.containsKey(document.getId())
                && documentContentHashes.get(document.getId()) == contentHash) {
            logger.debug("Document ID {} segments are unchanged; skipping redundant embedding generation.", document.getId());
            return;
        }

        try {
            // Remove previous vector records for this document to prevent duplicates
            deleteDocumentVectors(document.getId());

            List<org.springframework.ai.document.Document> aiDocuments = new ArrayList<>();

            for (DocumentSegment segment : document.getSegments()) {
                if (segment.getText() == null || segment.getText().trim().isEmpty()) {
                    continue; // Skip empty segments
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", document.getId().toString());
                metadata.put("segmentId", segment.getId() != null ? segment.getId().toString() : "");
                metadata.put("fileName", document.getFileName() != null ? document.getFileName() : "Unknown");
                metadata.put("documentType", document.getDocumentType() != null ? document.getDocumentType() : "PDF");
                metadata.put("segmentType", segment.getSegmentType() != null ? segment.getSegmentType() : "PAGE");
                metadata.put("segmentOrder", segment.getSegmentOrder());

                if (segment.getPageNumber() != null) {
                    metadata.put("pageNumber", segment.getPageNumber());
                }
                if (segment.getParagraphIndex() != null) {
                    metadata.put("paragraphIndex", segment.getParagraphIndex());
                }
                if (segment.getHeading() != null && !segment.getHeading().isBlank()) {
                    metadata.put("heading", segment.getHeading());
                }

                String id = segment.getId() != null ? segment.getId().toString() : UUID.randomUUID().toString();
                aiDocuments.add(new org.springframework.ai.document.Document(id, segment.getText(), metadata));
            }

            if (!aiDocuments.isEmpty()) {
                vectorStore.add(aiDocuments);
                if (document.getId() != null) {
                    documentContentHashes.put(document.getId(), contentHash);
                }
                clearQueryCache();
                logger.info("Successfully indexed {} vector segments for document ID: {}", aiDocuments.size(), document.getId());
            }
        } catch (Exception ex) {
            if (document.getId() != null) {
                documentContentHashes.remove(document.getId());
            }
            // Log warning without exposing sensitive connection or credential details
            logger.warn("Failed to generate and store embeddings for document ID {}: {}", document.getId(), ex.getMessage());
        }
    }

    /**
     * Removes all vector records associated with a specific document ID.
     */
    public void deleteDocumentVectors(UUID documentId) {
        if (vectorStore == null || documentId == null) {
            return;
        }

        try {
            documentContentHashes.remove(documentId);
            clearQueryCache();
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            Filter.Expression filter = b.eq("documentId", documentId.toString()).build();
            vectorStore.delete(filter);
            logger.debug("Deleted existing vector records for document ID: {}", documentId);
        } catch (Exception ex) {
            logger.warn("Could not delete vector records for document ID {}: {}", documentId, ex.getMessage());
        }
    }

    /**
     * Searches for semantically similar document segments across all uploaded documents using vector cosine distance.
     * Results are cached for short durations to prevent duplicate remote embedding calls on repeated questions.
     */
    public List<VectorSegmentResult> searchSimilarSegments(String question, int topK, double similarityThreshold) {
        if (vectorStore == null || question == null || question.isBlank()) {
            return List.of();
        }

        String cacheKey = question.trim().toLowerCase(Locale.ROOT) + "::" + topK + "::" + similarityThreshold;
        CachedVectorResults cached = vectorQueryCache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp()) < VECTOR_CACHE_TTL_MS) {
            return cached.results();
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question.trim())
                    .topK(Math.max(1, topK))
                    .similarityThreshold(similarityThreshold)
                    .build();

            List<org.springframework.ai.document.Document> matches = vectorStore.similaritySearch(searchRequest);
            List<VectorSegmentResult> results = mapAiDocumentsToResults(matches);
            if (vectorQueryCache.size() < MAX_CACHE_ENTRIES) {
                vectorQueryCache.put(cacheKey, new CachedVectorResults(System.currentTimeMillis(), results));
            }
            return results;
        } catch (Exception ex) {
            logger.warn("Vector similarity search failed for query: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Searches for semantically similar segments within a specific document using vector similarity search.
     */
    public List<VectorSegmentResult> searchSimilarSegmentsForDocument(
            String question, UUID documentId, int topK, double similarityThreshold) {
        if (vectorStore == null || question == null || question.isBlank() || documentId == null) {
            return List.of();
        }

        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question.trim())
                    .filterExpression(b.eq("documentId", documentId.toString()).build())
                    .topK(Math.max(1, topK))
                    .similarityThreshold(similarityThreshold)
                    .build();

            List<org.springframework.ai.document.Document> matches = vectorStore.similaritySearch(searchRequest);
            return mapAiDocumentsToResults(matches);
        } catch (Exception ex) {
            logger.warn("Vector similarity search for document ID {} failed: {}", documentId, ex.getMessage());
            return List.of();
        }
    }

    private List<VectorSegmentResult> mapAiDocumentsToResults(List<org.springframework.ai.document.Document> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<VectorSegmentResult> results = new ArrayList<>();
        for (org.springframework.ai.document.Document doc : matches) {
            Map<String, Object> metadata = doc.getMetadata() != null ? doc.getMetadata() : Map.of();

            UUID docId = getMetadataAsUuid(metadata, "documentId");
            UUID segId = getMetadataAsUuid(metadata, "segmentId");
            String fileName = (String) metadata.getOrDefault("fileName", "Unknown Document");
            String docType = (String) metadata.getOrDefault("documentType", "PDF");
            String segType = (String) metadata.getOrDefault("segmentType", "PAGE");
            Integer pageNumber = getMetadataAsInteger(metadata, "pageNumber");
            Integer paragraphIndex = getMetadataAsInteger(metadata, "paragraphIndex");
            String heading = (String) metadata.get("heading");

            results.add(new VectorSegmentResult(
                    docId,
                    segId,
                    fileName,
                    docType,
                    segType,
                    pageNumber,
                    paragraphIndex,
                    heading,
                    doc.getText(),
                    doc.getScore()
            ));
        }

        return results;
    }

    private int computeDocumentContentHash(Document document) {
        if (document == null || document.getSegments() == null) return 0;
        int hash = 1;
        for (DocumentSegment seg : document.getSegments()) {
            if (seg.getText() != null && !seg.getText().isBlank()) {
                hash = 31 * hash + seg.getText().hashCode();
                if (seg.getHeading() != null) {
                    hash = 31 * hash + seg.getHeading().hashCode();
                }
                if (seg.getPageNumber() != null) {
                    hash = 31 * hash + seg.getPageNumber().hashCode();
                }
                if (seg.getParagraphIndex() != null) {
                    hash = 31 * hash + seg.getParagraphIndex().hashCode();
                }
            }
        }
        return hash;
    }

    private Integer getMetadataAsInteger(Map<String, Object> metadata, String key) {
        Object val = metadata.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private UUID getMetadataAsUuid(Map<String, Object> metadata, String key) {
        Object val = metadata.get(key);
        if (val instanceof UUID u) {
            return u;
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
