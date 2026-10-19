package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An extracted factual claim or policy rule")
public record DocumentClaim(
        @Schema(description = "Original verbatim text of the claim or policy rule", example = "Payment is due within 30 days.")
        String originalText,

        @Schema(description = "Normalized representation of value, amount, date, or condition", example = "30 days", nullable = true)
        String normalizedValue,

        @Schema(description = "Source reference where the claim was found", nullable = true)
        ClaimSource source
) {
}
