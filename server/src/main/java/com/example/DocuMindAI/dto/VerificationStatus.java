package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Verification status of a finding's evidence excerpts against original document text")
public enum VerificationStatus {
    @Schema(description = "Supporting evidence excerpts have been verified to exist in the original extracted document text")
    VERIFIED,

    @Schema(description = "Evidence excerpt could not be verified or is ambiguous")
    UNCERTAIN,

    @Schema(description = "Evidence has not been verified against original text")
    UNVERIFIED
}
