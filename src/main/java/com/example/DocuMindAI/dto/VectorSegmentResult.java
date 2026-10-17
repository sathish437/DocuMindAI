package com.example.DocuMindAI.dto;

import java.util.UUID;

/**
 * Represents a document segment retrieved via semantic vector similarity search.
 */
public record VectorSegmentResult(
        UUID documentId,
        UUID segmentId,
        String fileName,
        String documentType,
        String segmentType,
        Integer pageNumber,
        Integer paragraphIndex,
        String heading,
        String text,
        Double similarityScore
) {
    public String getCitation() {
        if (pageNumber != null) {
            return fileName + " (Page " + pageNumber + ")";
        } else if (paragraphIndex != null) {
            return fileName + " (Paragraph " + paragraphIndex + (heading != null && !heading.isBlank() ? " - " + heading : "") + ")";
        } else {
            return fileName != null ? fileName : "Unknown Document";
        }
    }
}
