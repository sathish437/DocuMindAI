package com.example.DocuMindAI.controller;

import com.example.DocuMindAI.dto.DocumentMetadataResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document Ingestion", description = "Endpoints for ingesting and managing documents in the AI Document Assistant")
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

    @Operation(
            summary = "List all uploaded documents",
            description = "Retrieves persisted document metadata for workspace and sidebar initialization."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of all persisted documents retrieved successfully"
            )
    })
    @GetMapping
    public ResponseEntity<List<DocumentMetadataResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @Operation(
            summary = "Remove an uploaded document",
            description = "Permanently removes an uploaded document, its extracted segments, and vector embeddings by unique document ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Document successfully removed",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id:[a-fA-F0-9\\-]+}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "Unique ID of the document to remove")
            @PathVariable("id") UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of(
                "message", "Document removed successfully.",
                "documentId", id.toString()
        ));
    }
}
