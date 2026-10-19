package com.example.DocuMindAI.controller;

import com.example.DocuMindAI.dto.DocumentAskRequest;
import com.example.DocuMindAI.dto.DocumentAskResponse;
import com.example.DocuMindAI.dto.ErrorResponse;
import com.example.DocuMindAI.service.DocumentQaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document Question Answering", description = "Endpoints for evidence-based AI question answering and automatic cross-document analysis")
public class DocumentQaController {

    private final DocumentQaService documentQaService;

    public DocumentQaController(DocumentQaService documentQaService) {
        this.documentQaService = documentQaService;
    }

    @Operation(
            summary = "Ask a question or request cross-document analysis about uploaded documents",
            description = "Synthesizes an answer grounded strictly in uploaded document segments across all eligible documents using Google Gemini via Spring AI. Automatically detects question intent to support normal Q&A, candidate/document comparisons, potential contradiction detection, and qualification gap / missing information analysis with verifiable source citations."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Answer or analysis successfully generated with source citations",
                    content = @Content(schema = @Schema(implementation = DocumentAskResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request (e.g., blank question)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "AI service or internal error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(value = "/ask", consumes = "application/json", produces = "application/json")
    public ResponseEntity<DocumentAskResponse> askQuestion(@RequestBody DocumentAskRequest request) {
        DocumentAskResponse response = documentQaService.askQuestion(request);
        return ResponseEntity.ok(response);
    }
}
