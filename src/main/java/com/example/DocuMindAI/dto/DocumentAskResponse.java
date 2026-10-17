package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response containing the grounded AI answer and verifiable source citations")
public record DocumentAskResponse(
        @Schema(description = "The original user question", example = "What were the main financial results in Q3?")
        String question,

        @Schema(description = "Grounded answer synthesized strictly from document evidence", example = "According to the financial report, revenue increased by 15% to $15.2M.")
        String answer,

        @Schema(description = "List of verifiable document source references supporting the answer")
        List<SourceReferenceResponse> sources,

        @Schema(description = "True if the uploaded documents did not contain sufficient information to answer the question", example = "false")
        boolean insufficientEvidence
) {
}
