package com.example.DocuMindAI.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response returned after uploading and processing documents")
public record DocumentUploadResponse(
        @Schema(description = "Status summary message", example = "Documents processed and extracted successfully.")
        String message,

        @Schema(description = "Total number of documents uploaded in the request", example = "1")
        int totalFiles,

        @Schema(description = "List of processed document metadata and extracted text segments")
        List<DocumentMetadataResponse> files
) {
}
