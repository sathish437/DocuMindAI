package com.example.DocuMindAI.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "documents",
        indexes = {
                @Index(name = "idx_doc_upload_timestamp", columnList = "upload_timestamp"),
                @Index(name = "idx_doc_filename", columnList = "file_name")
        }
)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "upload_timestamp", nullable = false)
    private Instant uploadTimestamp;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "ocr_required", nullable = false)
    private boolean ocrRequired;

    @Column(name = "ocr_message", length = 500)
    private String ocrMessage;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "paragraph_count")
    private Integer paragraphCount;

    @OneToMany(
            mappedBy = "document",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("segmentOrder ASC")
    private List<DocumentSegment> segments = new ArrayList<>();

    public Document() {
    }

    public Document(
            String fileName,
            String documentType,
            long fileSize,
            Instant uploadTimestamp,
            String status,
            boolean ocrRequired,
            String ocrMessage,
            Integer pageCount,
            Integer paragraphCount) {
        this.fileName = fileName;
        this.documentType = documentType;
        this.fileSize = fileSize;
        this.uploadTimestamp = uploadTimestamp;
        this.status = status;
        this.ocrRequired = ocrRequired;
        this.ocrMessage = ocrMessage;
        this.pageCount = pageCount;
        this.paragraphCount = paragraphCount;
    }

    public void addSegment(DocumentSegment segment) {
        this.segments.add(segment);
        segment.setDocument(this);
    }

    public void removeSegment(DocumentSegment segment) {
        this.segments.remove(segment);
        segment.setDocument(null);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(Instant uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOcrRequired() {
        return ocrRequired;
    }

    public void setOcrRequired(boolean ocrRequired) {
        this.ocrRequired = ocrRequired;
    }

    public String getOcrMessage() {
        return ocrMessage;
    }

    public void setOcrMessage(String ocrMessage) {
        this.ocrMessage = ocrMessage;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getParagraphCount() {
        return paragraphCount;
    }

    public void setParagraphCount(Integer paragraphCount) {
        this.paragraphCount = paragraphCount;
    }

    public List<DocumentSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<DocumentSegment> segments) {
        this.segments = segments;
    }
}
