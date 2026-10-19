package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Structured contradiction and policy comparison analysis response")
public record ContradictionAnalysisResponse(
        @Schema(description = "IDs of documents included in this comparison")
        List<UUID> documentIds,

        @Schema(description = "High-level summary of the comparison and findings")
        String summary,

        @Schema(description = "List of structured findings (conflicts, supported claims, incomplete, uncertain)")
        List<ContradictionFinding> findings,

        @Schema(description = "True if insufficient document evidence was available to perform the analysis", example = "false")
        boolean insufficientEvidence
) {
}
