package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Detailed finding comparing claims or evaluating policy rules")
public record ContradictionFinding(
        @Schema(description = "Classification of the finding", example = "CONFLICTING")
        FindingType findingType,

        @Schema(description = "Subject, attribute, or policy rule under comparison", example = "Payment deadline")
        String subject,

        @Schema(description = "First claim or policy rule", nullable = true)
        DocumentClaim claimA,

        @Schema(description = "Second claim or comparison rule", nullable = true)
        DocumentClaim claimB,

        @Schema(description = "Detailed explanation of whether and why the claims agree, conflict, or lack information", example = "The stated deadlines differ under the same applicable conditions.")
        String explanation,

        @Schema(description = "Verification status of the evidence excerpts against original extracted document text", example = "VERIFIED")
        VerificationStatus verificationStatus,

        @Schema(description = "Confidence score between 0.0 and 1.0", example = "0.95")
        Double confidence
) {
}
