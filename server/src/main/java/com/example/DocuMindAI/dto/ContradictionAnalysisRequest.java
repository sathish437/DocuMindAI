package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request to analyze potential contradictions and policy compliance across documents")
public record ContradictionAnalysisRequest(
        @Schema(description = "Optional list of document IDs to specifically compare. If omitted, documents are retrieved based on focus query or recent uploads.", nullable = true)
        List<UUID> documentIds,

        @Schema(description = "Optional focus query or topic for the contradiction analysis (e.g., 'payment deadlines', 'termination policy'). If omitted, compares all available retrieved rules.", nullable = true)
        String focusQuery
) {
}
