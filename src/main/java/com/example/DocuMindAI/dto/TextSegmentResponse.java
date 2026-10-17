package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Structured text segment extracted from a document")
public record TextSegmentResponse(
        @Schema(description = "Source file name", example = "report.pdf")
        String fileName,

        @Schema(description = "Segment type (PAGE for PDF, PARAGRAPH or HEADING for DOCX)", example = "PAGE")
        String segmentType,

        @Schema(description = "PDF page number (1-based). Null for DOCX.", example = "1", nullable = true)
        Integer pageNumber,

        @Schema(description = "DOCX paragraph index (1-based). Null for PDF.", example = "1", nullable = true)
        Integer paragraphIndex,

        @Schema(description = "Heading style or text if segment is a heading", example = "Heading 1", nullable = true)
        String heading,

        @Schema(description = "Extracted text content from this segment", example = "Project Introduction and Goals...")
        String text
) {
}
