package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to ask a question grounded in uploaded documents")
public record DocumentAskRequest(
        @Schema(description = "The question to be answered from document evidence", example = "Which is the best resume?", requiredMode = Schema.RequiredMode.REQUIRED)
        String question
) {
}
