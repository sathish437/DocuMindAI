package com.example.DocuMindAI.controller;

import com.example.DocuMindAI.dto.DocumentUploadResponse;
import com.example.DocuMindAI.dto.ErrorResponse;
import com.example.DocuMindAI.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document Ingestion", description = "Endpoints for ingesting documents into the AI Document Assistant")
public class DocumentUploadController {

    private final DocumentService documentService;

    public DocumentUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(
            summary = "Upload and process document files",
            description = "Validates, extracts text segments, and persists document metadata and segments into PostgreSQL for evidence-based AI question answering."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Documents successfully processed and persisted",
                    content = @Content(schema = @Schema(implementation = DocumentUploadResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error (unsupported file type, empty file, mismatched signature, or size/count exceeded)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Extraction failure (corrupted document or encrypted PDF)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocuments(
            @Parameter(description = "One or more PDF or DOCX files to upload (max 10 files per request, max 20MB per file)")
            @RequestParam("files") List<MultipartFile> files) {

        DocumentUploadResponse response = documentService.uploadAndProcessDocuments(files);
        return ResponseEntity.ok(response);
    }
}
