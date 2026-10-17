package com.example.DocuMindAI.dto;

import java.util.UUID;

/**
 * Structured evidence representation designed for AI document question-answering.
 * Encapsulates content text alongside strict source attribution metadata
 * (document name, page number, or paragraph locator).
 */
public record EvidenceSegment(
        UUID segmentId,
        UUID documentId,
        String documentName,
        String documentType,
        String segmentType,
        Integer pageNumber,
        Integer paragraphIndex,
        String heading,
        int segmentOrder,
        String text
) {
    /**
     * Formats an exact, human-readable source citation string.
     * Examples: "[financial_report.pdf, Page 3]" or "[spec.docx, Para 5 (System Architecture)]"
     */
    public String getCitation() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(documentName);
        if (pageNumber != null) {
            sb.append(", Page ").append(pageNumber);
        } else if (paragraphIndex != null) {
            sb.append(", Para ").append(paragraphIndex);
            if (heading != null && !heading.isBlank()) {
                sb.append(" (").append(heading).append(")");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
