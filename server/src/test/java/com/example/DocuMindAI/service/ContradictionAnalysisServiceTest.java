package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.ContradictionAnalysisRequest;
import com.example.DocuMindAI.dto.ContradictionAnalysisResponse;
import com.example.DocuMindAI.dto.ContradictionFinding;
import com.example.DocuMindAI.dto.FindingType;
import com.example.DocuMindAI.dto.VerificationStatus;
import com.example.DocuMindAI.entity.Document;
import com.example.DocuMindAI.entity.DocumentSegment;
import com.example.DocuMindAI.exception.DocumentNotFoundException;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.springframework.ai.chat.model.ChatModel;

@ExtendWith(MockitoExtension.class)
class ContradictionAnalysisServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentSegmentRepository segmentRepository;

    @Mock
    private ChatModel chatModel;

    private ContradictionAnalysisService contradictionService;

    @BeforeEach
    void setUp() {
        contradictionService = new ContradictionAnalysisService(
                documentRepository,
                segmentRepository,
                chatModel
        );
    }

    @Test
    @DisplayName("Deterministic verification should verify exact and normalized excerpts")
    void testVerifyExcerptInSegments() {
        DocumentSegment seg1 = new DocumentSegment();
        seg1.setText("Invoices must be settled within 30 days of receipt by the accounts payable department.");

        DocumentSegment seg2 = new DocumentSegment();
        seg2.setText("Employees working full-time receive 20 days paid leave annually.");

        List<DocumentSegment> segments = List.of(seg1, seg2);

        // Exact match
        assertTrue(contradictionService.verifyExcerptInSegments("within 30 days of receipt", segments));

        // Punctuation and case insensitive match
        assertTrue(contradictionService.verifyExcerptInSegments("INVOICES MUST BE SETTLED WITHIN 30 DAYS", segments));

        // Quoted text variation
        assertTrue(contradictionService.verifyExcerptInSegments("\"within 30 days of receipt\"", segments));

        // Substring across multiple words
        assertTrue(contradictionService.verifyExcerptInSegments("receive 20 days paid leave", segments));

        // Hallucinated excerpt not present in documents
        assertFalse(contradictionService.verifyExcerptInSegments("Invoices must be paid within 10 days immediately", segments));
        assertFalse(contradictionService.verifyExcerptInSegments("Employees receive 50 days leave", segments));

        // Blank or null excerpt
        assertFalse(contradictionService.verifyExcerptInSegments("", segments));
        assertFalse(contradictionService.verifyExcerptInSegments(null, segments));
        assertFalse(contradictionService.verifyExcerptInSegments("within 30 days", List.of()));
    }

    @Test
    @DisplayName("Should throw DocumentNotFoundException when requested document ID does not exist")
    void testMissingDocumentIdThrowsException() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.existsById(docId)).thenReturn(false);

        ContradictionAnalysisRequest request = new ContradictionAnalysisRequest(List.of(docId), "policies");

        assertThrows(DocumentNotFoundException.class, () -> contradictionService.analyzeContradictions(request));
    }

    @Test
    @DisplayName("Should return insufficientEvidence when database has no documents")
    void testNoDocumentsReturnsInsufficientEvidence() {
        when(documentRepository.count()).thenReturn(0L);

        ContradictionAnalysisResponse response = contradictionService.analyzeContradictions(null);

        assertTrue(response.insufficientEvidence());
        assertTrue(response.findings().isEmpty());
    }

    @Test
    @DisplayName("Should downgrade CONFLICTING to UNCERTAIN when evidence excerpt fails deterministic verification")
    void testDowngradeConflictingWhenExcerptNotVerified() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(docId);
        doc.setFileName("Policy.pdf");

        DocumentSegment seg = new DocumentSegment();
        seg.setDocument(doc);
        seg.setPageNumber(1);
        seg.setText("The official work hours are 9:00 AM to 5:00 PM Monday through Friday.");

        when(documentRepository.count()).thenReturn(1L);
        when(segmentRepository.findAllWithDocument()).thenReturn(List.of(seg));

        // Model hallucinates an excerpt that is not in the text
        String llmOutput = """
            {
              "summary": "Conflict detected in working hours.",
              "findings": [
                {
                  "findingType": "CONFLICTING",
                  "subject": "Working hours",
                  "claimA": {
                    "originalText": "Work hours are 9:00 AM to 5:00 PM",
                    "normalizedValue": "09:00 - 17:00",
                    "source": {
                      "documentName": "Policy.pdf",
                      "pageNumber": 1,
                      "excerpt": "Official work hours are 9:00 AM to 5:00 PM"
                    }
                  },
                  "claimB": {
                    "originalText": "Work hours are 8:00 AM to 4:00 PM",
                    "normalizedValue": "08:00 - 16:00",
                    "source": {
                      "documentName": "Policy.pdf",
                      "pageNumber": 1,
                      "excerpt": "Mandatory working shift starts at 08:00 AM sharp without exception"
                    }
                  },
                  "explanation": "Conflicting hours stated.",
                  "confidence": 0.95
                }
              ]
            }
            """;

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(llmOutput))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ContradictionAnalysisResponse response = contradictionService.analyzeContradictions(null);

        assertNotNull(response);
        assertFalse(response.findings().isEmpty());
        ContradictionFinding finding = response.findings().get(0);

        // Verification status must be UNCERTAIN because claimB excerpt was hallucinated
        assertEquals(VerificationStatus.UNCERTAIN, finding.verificationStatus());
        // Finding type must be downgraded from CONFLICTING to UNCERTAIN to prevent false alarm
        assertEquals(FindingType.UNCERTAIN, finding.findingType());
        assertTrue(finding.confidence() <= 0.45);
        assertTrue(finding.explanation().contains("Verification Note"));
    }
}
