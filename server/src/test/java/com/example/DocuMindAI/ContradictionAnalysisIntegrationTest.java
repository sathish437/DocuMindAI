package com.example.DocuMindAI;

import com.example.DocuMindAI.dto.DocumentAskRequest;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.util.TestDocumentFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContradictionAnalysisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSegmentRepository segmentRepository;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        segmentRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private void mockLlmResponse(String jsonResponse) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(jsonResponse))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("Verification: /api/documents/contradictions should no longer be mapped (returns 404)")
    void testContradictionsEndpointIsNoLongerMapped() throws Exception {
        mockMvc.perform(post("/api/documents/contradictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should detect direct contradiction between two documents through /api/documents/ask with verified source citations")
    void testDirectContradictionThroughAskEndpoint() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "Vendor Payment Policy: All vendor invoices must be paid within 30 days of receipt."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Vendor_Policy.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] docBBytes = TestDocumentFactory.createPdf(List.of(
                "Master Service Agreement: The client agrees that all invoices shall be paid within 15 days of invoice receipt."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "MSA_Agreement.pdf", MediaType.APPLICATION_PDF_VALUE, docBBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        String structuredLlmOutput = """
            {
              "summary": "Direct contradiction detected in invoice payment deadlines between Vendor Policy and MSA Agreement.",
              "findings": [
                {
                  "findingType": "CONFLICTING",
                  "subject": "Invoice payment deadline",
                  "claimA": {
                    "originalText": "All vendor invoices must be paid within 30 days of receipt.",
                    "normalizedValue": "30 days",
                    "source": {
                      "documentName": "Vendor_Policy.pdf",
                      "pageNumber": 1,
                      "excerpt": "All vendor invoices must be paid within 30 days of receipt."
                    }
                  },
                  "claimB": {
                    "originalText": "The client agrees that all invoices shall be paid within 15 days of invoice receipt.",
                    "normalizedValue": "15 days",
                    "source": {
                      "documentName": "MSA_Agreement.pdf",
                      "pageNumber": 1,
                      "excerpt": "all invoices shall be paid within 15 days of invoice receipt."
                    }
                  },
                  "explanation": "Vendor policy permits a 30-day payment term, but the Master Service Agreement specifies 15 days under the same invoicing condition.",
                  "confidence": 0.95
                }
              ]
            }
            """;
        mockLlmResponse(structuredLlmOutput);

        DocumentAskRequest request = new DocumentAskRequest("Are there any contradictions regarding payment deadlines?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Are there any contradictions regarding payment deadlines?")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.answer", containsString("Direct contradiction detected")))
                .andExpect(jsonPath("$.answer", containsString("[CONFLICTING] Invoice payment deadline")))
                .andExpect(jsonPath("$.answer", containsString("30 days")))
                .andExpect(jsonPath("$.answer", containsString("15 days")))
                .andExpect(jsonPath("$.answer", containsString("Verification: VERIFIED")))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Vendor_Policy.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'MSA_Agreement.pdf')]", notNullValue()));
    }

    @Test
    @DisplayName("Should classify agreeing claims as SUPPORTED with verified excerpts through /api/documents/ask")
    void testClaimsThatAgreeClassifiedAsSupportedThroughAsk() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "Employment Agreement: Either party may terminate employment by providing 30 days written notice."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Employment_Agreement.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] docBBytes = TestDocumentFactory.createPdf(List.of(
                "Company Handbook: Resignations and terminations require 30 days written notice to HR."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Company_Handbook.pdf", MediaType.APPLICATION_PDF_VALUE, docBBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        String structuredLlmOutput = """
            {
              "summary": "Both documents are consistent regarding the 30-day termination notice requirement.",
              "findings": [
                {
                  "findingType": "SUPPORTED",
                  "subject": "Termination notice period",
                  "claimA": {
                    "originalText": "Either party may terminate employment by providing 30 days written notice.",
                    "normalizedValue": "30 days",
                    "source": {
                      "documentName": "Employment_Agreement.pdf",
                      "pageNumber": 1,
                      "excerpt": "Either party may terminate employment by providing 30 days written notice."
                    }
                  },
                  "claimB": {
                    "originalText": "Resignations and terminations require 30 days written notice to HR.",
                    "normalizedValue": "30 days",
                    "source": {
                      "documentName": "Company_Handbook.pdf",
                      "pageNumber": 1,
                      "excerpt": "Resignations and terminations require 30 days written notice"
                    }
                  },
                  "explanation": "Both documents establish a harmonious 30-day notice requirement.",
                  "confidence": 0.98
                }
              ]
            }
            """;
        mockLlmResponse(structuredLlmOutput);

        DocumentAskRequest request = new DocumentAskRequest("Do these documents contradict each other regarding termination notice?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("[SUPPORTED] Termination notice period")))
                .andExpect(jsonPath("$.answer", containsString("Verification: VERIFIED")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)));
    }

    @Test
    @DisplayName("Should classify different conditions as non-conflicting through /api/documents/ask")
    void testDifferentConditionsNotClassifiedAsContradictionsThroughAsk() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "Benefits Guide: Full-time employees receive 20 days of paid time off per calendar year."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Benefits_FullTime.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] docBBytes = TestDocumentFactory.createPdf(List.of(
                "Benefits Guide: Part-time employees receive 10 days of paid time off per calendar year."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Benefits_PartTime.pdf", MediaType.APPLICATION_PDF_VALUE, docBBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        String structuredLlmOutput = """
            {
              "summary": "Comparison of paid time off allowances across different employment classifications.",
              "findings": [
                {
                  "findingType": "SUPPORTED",
                  "subject": "Paid time off by employment status",
                  "claimA": {
                    "originalText": "Full-time employees receive 20 days of paid time off per calendar year.",
                    "normalizedValue": "20 days/year (full-time)",
                    "source": {
                      "documentName": "Benefits_FullTime.pdf",
                      "pageNumber": 1,
                      "excerpt": "Full-time employees receive 20 days of paid time off per calendar year."
                    }
                  },
                  "claimB": {
                    "originalText": "Part-time employees receive 10 days of paid time off per calendar year.",
                    "normalizedValue": "10 days/year (part-time)",
                    "source": {
                      "documentName": "Benefits_PartTime.pdf",
                      "pageNumber": 1,
                      "excerpt": "Part-time employees receive 10 days of paid time off per calendar year."
                    }
                  },
                  "explanation": "These rules reflect different conditions (full-time vs part-time) and are complementary rather than conflicting.",
                  "confidence": 0.92
                }
              ]
            }
            """;
        mockLlmResponse(structuredLlmOutput);

        DocumentAskRequest request = new DocumentAskRequest("Find conflicts in the vacation policy between full-time and part-time workers.");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("[SUPPORTED] Paid time off by employment status")))
                .andExpect(jsonPath("$.answer", containsString("different conditions")))
                .andExpect(jsonPath("$.answer", containsString("Verification: VERIFIED")));
    }

    @Test
    @DisplayName("Should mark finding as UNCERTAIN when evidence excerpt cannot be verified in original text")
    void testUnverifiedEvidenceExcerptBecomesUncertainThroughAsk() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "Server Security Policy: Passwords must be at least 12 characters and changed every 90 days."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Security_Policy.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        // LLM hallucinates an excerpt that does not exist in Security_Policy.pdf
        String structuredLlmOutput = """
            {
              "summary": "Password policy rules analysis.",
              "findings": [
                {
                  "findingType": "CONFLICTING",
                  "subject": "Password expiration",
                  "claimA": {
                    "originalText": "Passwords must be changed every 90 days.",
                    "normalizedValue": "90 days",
                    "source": {
                      "documentName": "Security_Policy.pdf",
                      "pageNumber": 1,
                      "excerpt": "Passwords must be at least 12 characters and changed every 90 days."
                    }
                  },
                  "claimB": {
                    "originalText": "Passwords never expire for administrative accounts.",
                    "normalizedValue": "never",
                    "source": {
                      "documentName": "Security_Policy.pdf",
                      "pageNumber": 1,
                      "excerpt": "Passwords never expire for administrative accounts under any circumstance."
                    }
                  },
                  "explanation": "Conflicting rules regarding password expiration.",
                  "confidence": 0.90
                }
              ]
            }
            """;
        mockLlmResponse(structuredLlmOutput);

        DocumentAskRequest request = new DocumentAskRequest("Identify contradictions in the password policy.");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("[UNCERTAIN] Password expiration")))
                .andExpect(jsonPath("$.answer", containsString("Verification: UNCERTAIN")))
                .andExpect(jsonPath("$.answer", containsString("Verification Note: One or more evidence excerpts could not be verified")));
    }

    @Test
    @DisplayName("Should handle missing named document gracefully with clear explanation")
    void testMissingNamedDocumentReturnsClearExplanation() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of("Existing document content."));
        MockMultipartFile fileA = new MockMultipartFile("files", "Existing_Doc.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        DocumentAskRequest request = new DocumentAskRequest("What does Non_Existent_Doc.pdf say about payment terms?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.answer", containsString("The requested document 'Non_Existent_Doc.pdf' was not found among the uploaded documents")))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }
}
