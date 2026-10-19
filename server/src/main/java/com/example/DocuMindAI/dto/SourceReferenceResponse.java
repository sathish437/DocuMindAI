package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Source reference citation verifying where document evidence was found")
public record SourceReferenceResponse(
        @Schema(description = "Document ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID documentId,

        @Schema(description = "Original filename", example = "q3_report.pdf")
        String documentName,

        @Schema(description = "Document type (PDF or DOCX)", example = "PDF")
        String documentType,

        @Schema(description = "Segment type (PAGE, HEADING, PARAGRAPH)", example = "PAGE")
        String segmentType,

        @Schema(description = "PDF page number (1-based), null for DOCX", example = "1", nullable = true)
        Integer pageNumber,

        @Schema(description = "DOCX paragraph index (1-based), null for PDF", example = "2", nullable = true)
        Integer paragraphIndex,

        @Schema(description = "Heading title if applicable", example = "Financial Summary", nullable = true)
        String heading,

        @Schema(description = "Formatted citation reference string", example = "[q3_report.pdf, Page 1]")
        String citation,

        @Schema(description = "Extracted text snippet used as evidence")
        String excerpt
) {
}
