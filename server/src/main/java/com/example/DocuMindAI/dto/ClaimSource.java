package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Source reference for an extracted claim or policy rule")
public record ClaimSource(
        @Schema(description = "Document ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
        UUID documentId,

        @Schema(description = "Document filename", example = "Policy.pdf")
        String documentName,

        @Schema(description = "Page number (1-based) if PDF", example = "2", nullable = true)
        Integer pageNumber,

        @Schema(description = "Paragraph index (1-based) if DOCX", example = "4", nullable = true)
        Integer paragraphIndex,

        @Schema(description = "Exact verbatim excerpt from the document text", example = "Payment is due within 30 days.")
        String excerpt
) {
}
