package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.DocumentMetadataResponse;
import com.example.DocuMindAI.dto.DocumentUploadResponse;
import com.example.DocuMindAI.dto.EvidenceSegment;
import com.example.DocuMindAI.dto.TextSegmentResponse;
import com.example.DocuMindAI.entity.Document;
import com.example.DocuMindAI.entity.DocumentSegment;
import com.example.DocuMindAI.exception.DocumentNotFoundException;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.DocuMindAI.event.DocumentChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentSegmentRepository segmentRepository;
    private final FileValidationService fileValidationService;
    private final DocumentExtractionService documentExtractionService;
    private final DocumentVectorService documentVectorService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentSegmentRepository segmentRepository,
            FileValidationService fileValidationService,
            DocumentExtractionService documentExtractionService,
            DocumentVectorService documentVectorService) {
        this(documentRepository, segmentRepository, fileValidationService, documentExtractionService, documentVectorService, null);
    }

    @Autowired
    public DocumentService(
            DocumentRepository documentRepository,
            DocumentSegmentRepository segmentRepository,
            FileValidationService fileValidationService,
            DocumentExtractionService documentExtractionService,
            DocumentVectorService documentVectorService,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.fileValidationService = fileValidationService;
        this.documentExtractionService = documentExtractionService;
        this.documentVectorService = documentVectorService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Validates, extracts, and transactionally persists document metadata and text segments.
     */
    @Transactional
    public DocumentUploadResponse uploadAndProcessDocuments(List<MultipartFile> files) {
        fileValidationService.validateFileList(files);

        List<DocumentMetadataResponse> processedResponses = new ArrayList<>();

        for (MultipartFile file : files) {
            byte[] bytes = fileValidationService.validateAndReadBytes(file);
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();

            // Extract document text segments
            DocumentMetadataResponse extractionResult = documentExtractionService.extractDocument(
                    fileName, bytes, contentType);

            String documentType = fileName != null && fileName.toLowerCase().endsWith(".docx")
                    ? "DOCX"
                    : "PDF";

            String status = extractionResult.ocrRequired() ? "PROCESSED_OCR_REQUIRED" : "PROCESSED";

            // Create JPA Document entity
            Document document = new Document(
                    fileName,
                    documentType,
                    bytes.length,
                    Instant.now(),
                    status,
                    extractionResult.ocrRequired(),
                    extractionResult.ocrMessage(),
                    extractionResult.pageCount(),
                    extractionResult.paragraphCount()
            );

            // Create and associate DocumentSegment entities
            int order = 1;
            for (TextSegmentResponse seg : extractionResult.segments()) {
                DocumentSegment segment = new DocumentSegment(
                        seg.segmentType(),
                        seg.pageNumber(),
                        seg.paragraphIndex(),
                        seg.heading(),
                        order++,
                        seg.text()
                );
                document.addSegment(segment);
            }

            // Persist document and cascade persist segments
            Document savedDocument = documentRepository.save(document);

            // Index document segments into vector store for semantic RAG search
            documentVectorService.indexDocumentSegments(savedDocument);

            // Map to response DTO with generated UUID and timestamp
            processedResponses.add(mapToDetailResponse(savedDocument));
        }

        if (eventPublisher != null && !processedResponses.isEmpty()) {
            eventPublisher.publishEvent(new DocumentChangedEvent(null, "UPLOAD"));
        }

        return new DocumentUploadResponse(
                "Documents processed and persisted successfully.",
                processedResponses.size(),
                processedResponses
        );
    }

    // =========================================================================
    // Internal Service Methods Prepared for AI Document Question-Answering
    // =========================================================================

    /**
     * Internal retrieval of complete document with its segments.
     */
    @Transactional(readOnly = true)
    public DocumentMetadataResponse getDocumentById(UUID id) {
        Document document = documentRepository.findByIdWithSegments(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return mapToDetailResponse(document);
    }

    /**
     * Internal cleanup/management of document and its cascaded segments.
     */
    @Transactional
    public void deleteDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        documentVectorService.deleteDocumentVectors(id);
        documentRepository.delete(document);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DocumentChangedEvent(id, "DELETE"));
        }
    }

    /**
     * Retrieves all extracted segments across all uploaded documents, joined with parent
     * document metadata, to facilitate multi-document synthesis and contradiction analysis.
     */
    @Transactional(readOnly = true)
    public List<EvidenceSegment> getAllEvidence() {
        return segmentRepository.findAllWithDocument().stream()
                .map(this::mapToEvidence)
                .toList();
    }

    /**
     * Retrieves evidence segments scoped to a specific list of documents for targeted comparative analysis.
     */
    @Transactional(readOnly = true)
    public List<EvidenceSegment> getEvidenceForDocuments(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return segmentRepository.findByDocumentIdsWithDocument(documentIds).stream()
                .map(this::mapToEvidence)
                .toList();
    }

    /**
     * Searches extracted segments containing the given query string, retaining source document
     * and page/paragraph locators for accurate citations.
     */
    @Transactional(readOnly = true)
    public List<EvidenceSegment> searchEvidence(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return segmentRepository.searchSegmentsByQuery(query.trim()).stream()
                .map(this::mapToEvidence)
                .toList();
    }

    /**
     * Helper to determine whether retrieved evidence is sufficient to ground an answer,
     * preventing hallucination when evidence is absent or insufficient.
     */
    public boolean hasSufficientEvidence(List<EvidenceSegment> evidence) {
        return evidence != null && !evidence.isEmpty() && evidence.stream().anyMatch(e -> !e.text().isBlank());
    }

    private EvidenceSegment mapToEvidence(DocumentSegment seg) {
        Document doc = seg.getDocument();
        return new EvidenceSegment(
                seg.getId(),
                doc != null ? doc.getId() : null,
                doc != null ? doc.getFileName() : "Unknown",
                doc != null ? doc.getDocumentType() : "Unknown",
                seg.getSegmentType(),
                seg.getPageNumber(),
                seg.getParagraphIndex(),
                seg.getHeading(),
                seg.getSegmentOrder(),
                seg.getText()
        );
    }

    private DocumentMetadataResponse mapToDetailResponse(Document document) {
        List<TextSegmentResponse> segmentResponses = document.getSegments().stream()
                .map(seg -> new TextSegmentResponse(
                        document.getFileName(),
                        seg.getSegmentType(),
                        seg.getPageNumber(),
                        seg.getParagraphIndex(),
                        seg.getHeading(),
                        seg.getText()
                ))
                .toList();

        String mimeType = "DOCX".equalsIgnoreCase(document.getDocumentType())
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/pdf";

        return new DocumentMetadataResponse(
                document.getId(),
                document.getFileName(),
                document.getDocumentType(),
                document.getFileSize(),
                mimeType,
                document.getStatus(),
                document.getUploadTimestamp(),
                document.isOcrRequired(),
                document.getOcrMessage(),
                document.getPageCount(),
                document.getParagraphCount(),
                segmentResponses.size(),
                segmentResponses
        );
    }
}
