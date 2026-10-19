package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Classification of a cross-document contradiction finding")
public enum FindingType {
    @Schema(description = "The available evidence supports the claim")
    SUPPORTED,

    @Schema(description = "Reliable source statements directly disagree under comparable conditions")
    CONFLICTING,

    @Schema(description = "Required information is missing to make a complete determination")
    INCOMPLETE,

    @Schema(description = "Evidence is ambiguous, insufficient, or cannot be reliably compared")
    UNCERTAIN
}
