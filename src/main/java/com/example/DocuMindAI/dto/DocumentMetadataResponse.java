package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Document processing and extraction metadata with structured segments")
public record DocumentMetadataResponse(
        @Schema(description = "Unique document ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Original filename", example = "report.pdf")
        String fileName,

        @Schema(description = "Document type (PDF or DOCX)", example = "PDF")
        String documentType,

        @Schema(description = "File size in bytes", example = "104857")
        long size,

        @Schema(description = "MIME content type", example = "application/pdf")
        String contentType,

        @Schema(description = "Processing status", example = "PROCESSED")
        String status,

        @Schema(description = "Timestamp when the document was uploaded", example = "2026-10-17T12:00:00Z")
        Instant uploadTimestamp,

        @Schema(description = "True if PDF has no extractable text and may require OCR", example = "false")
        boolean ocrRequired,

        @Schema(description = "OCR advisory message if applicable", example = "No extractable text detected in PDF. Scanned document may require OCR.", nullable = true)
        String ocrMessage,

        @Schema(description = "Total pages for PDF documents. Null for DOCX.", example = "5", nullable = true)
        Integer pageCount,

        @Schema(description = "Total paragraphs extracted for DOCX. Null for PDF.", example = "24", nullable = true)
        Integer paragraphCount,

        @Schema(description = "Total extracted segments count", example = "5")
        int totalSegments,

        @Schema(description = "Extracted structured text segments with source tracking")
        List<TextSegmentResponse> segments
) {
    public DocumentMetadataResponse(String fileName, long size, String contentType, String status) {
        this(null, fileName, fileName != null && fileName.toLowerCase().endsWith(".docx") ? "DOCX" : "PDF",
                size, contentType, status, Instant.now(), false, null, null, null, 0, List.of());
    }

    public DocumentMetadataResponse(
            String fileName,
            long size,
            String contentType,
            String status,
            boolean ocrRequired,
            String ocrMessage,
            Integer pageCount,
            Integer paragraphCount,
            int totalSegments,
            List<TextSegmentResponse> segments) {
        this(null, fileName, fileName != null && fileName.toLowerCase().endsWith(".docx") ? "DOCX" : "PDF",
                size, contentType, status, Instant.now(), ocrRequired, ocrMessage, pageCount, paragraphCount, totalSegments, segments);
    }
}
