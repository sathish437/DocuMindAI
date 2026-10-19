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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SourceCitationVerificationIntegrationTest {

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

    private void mockLlmResponse(String answerText) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(answerText))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("Scenario 1: Workflow question about Hostel Mess Reduction System.pdf excludes unrelated sample.pdf and specification.pdf")
    void testExplicitDocumentExcludesUnrelatedDocuments() throws Exception {
        byte[] messPdf = TestDocumentFactory.createPdf(List.of(
                "Hostel Mess Reduction System. Workflow: Students submit mess reduction requests online at least 2 days before absence. "
                        + "The hostel warden reviews and approves the request. Approved days are automatically credited back to the student account during end-of-month mess bill calculations."
        ));
        byte[] samplePdf = TestDocumentFactory.createPdf(List.of(
                "Sample PDF Document. This is a generic sample file used for demonstration purposes. It contains general sample notes and guidelines."
        ));
        byte[] specPdf = TestDocumentFactory.createPdf(List.of(
                "Technical Specification Document. The database architecture requires PostgreSQL 16 with JSONB indexing and Redis for distributed session caching."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "Hostel Mess Reduction System.pdf", MediaType.APPLICATION_PDF_VALUE, messPdf))
                        .file(new MockMultipartFile("files", "sample.pdf", MediaType.APPLICATION_PDF_VALUE, samplePdf))
                        .file(new MockMultipartFile("files", "specification.pdf", MediaType.APPLICATION_PDF_VALUE, specPdf)))
                .andExpect(status().isOk());

        mockLlmResponse("In the Hostel Mess Reduction System, the workflow begins when students submit a mess reduction request online at least 2 days before absence. "
                + "The hostel warden then reviews and approves the request, and approved days are credited back during end-of-month mess bill calculations. [Hostel Mess Reduction System.pdf, Page 1]");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the workflow in Hostel Mess Reduction System.pdf?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What is the workflow in Hostel Mess Reduction System.pdf?")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("Hostel Mess Reduction System.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[Hostel Mess Reduction System.pdf, Page 1]")))
                .andExpect(jsonPath("$.sources[*].documentName", not(org.hamcrest.Matchers.hasItem("sample.pdf"))))
                .andExpect(jsonPath("$.sources[*].documentName", not(org.hamcrest.Matchers.hasItem("specification.pdf"))));
    }

    @Test
    @DisplayName("Scenario 1b: Base name without extension also targets correct document and excludes unrelated files")
    void testBaseNameWithoutExtensionTargetsCorrectDocument() throws Exception {
        byte[] messPdf = TestDocumentFactory.createPdf(List.of(
                "Hostel Mess Reduction System. Students submit reduction requests online. Warden approval is mandatory."
        ));
        byte[] samplePdf = TestDocumentFactory.createPdf(List.of(
                "Sample demonstration file with guidelines."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "Hostel Mess Reduction System.pdf", MediaType.APPLICATION_PDF_VALUE, messPdf))
                        .file(new MockMultipartFile("files", "sample.pdf", MediaType.APPLICATION_PDF_VALUE, samplePdf)))
                .andExpect(status().isOk());

        mockLlmResponse("The Hostel Mess Reduction System requires online student requests and warden approval.");

        DocumentAskRequest askRequest = new DocumentAskRequest("Explain the process in Hostel Mess Reduction System");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("Hostel Mess Reduction System.pdf")))
                .andExpect(jsonPath("$.sources[*].documentName", not(org.hamcrest.Matchers.hasItem("sample.pdf"))));
    }

    @Test
    @DisplayName("Scenario 2: Question requiring evidence from two documents retains sources from both documents")
    void testQuestionRequiringEvidenceFromTwoDocumentsRetainsBoth() throws Exception {
        byte[] policyAlpha = TestDocumentFactory.createPdf(List.of(
                "Policy Alpha stipulates that annual vacation leave is capped at 15 working days. Core working hours are 9 AM to 5 PM."
        ));
        byte[] policyBeta = TestDocumentFactory.createPdf(List.of(
                "Policy Beta stipulates that annual vacation leave is capped at 25 working days. Remote work is permitted up to 3 days weekly."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "Policy_Alpha.pdf", MediaType.APPLICATION_PDF_VALUE, policyAlpha))
                        .file(new MockMultipartFile("files", "Policy_Beta.pdf", MediaType.APPLICATION_PDF_VALUE, policyBeta)))
                .andExpect(status().isOk());

        mockLlmResponse("Comparing the two policies: Policy Alpha caps annual vacation leave at 15 working days, while Policy Beta offers 25 working days. [Policy_Alpha.pdf, Page 1] [Policy_Beta.pdf, Page 1]");

        DocumentAskRequest askRequest = new DocumentAskRequest("Compare vacation leave between Policy Alpha and Policy Beta");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Policy_Alpha.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Policy_Beta.pdf')]", notNullValue()));
    }

    @Test
    @DisplayName("Scenario 3: Question whose answer is not in documents returns 0 sources and sets insufficientEvidence=true")
    void testAnswerNotInDocumentsReturnsZeroSources() throws Exception {
        byte[] messPdf = TestDocumentFactory.createPdf(List.of(
                "Hostel Mess Reduction System guidelines and submission deadlines."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "Hostel Mess Reduction System.pdf", MediaType.APPLICATION_PDF_VALUE, messPdf)))
                .andExpect(status().isOk());

        mockLlmResponse("I could not find sufficient information in the uploaded documents regarding quantum cryptography algorithms.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the quantum key distribution protocol used here?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", empty()))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));
    }

    @Test
    @DisplayName("Scenario 4: Multiple similar or duplicate chunks from the same document locator are deduplicated")
    void testDuplicateOrSimilarChunksAreDeduplicated() throws Exception {
        byte[] docBytes = TestDocumentFactory.createPdf(List.of(
                "Travel Reimbursement Policy. Employees must submit flight boarding passes and original hotel receipts within 30 days of travel completion."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "travel_policy.pdf", MediaType.APPLICATION_PDF_VALUE, docBytes)))
                .andExpect(status().isOk());

        mockLlmResponse("Employees must submit flight boarding passes and original hotel receipts within 30 days of travel completion. [travel_policy.pdf, Page 1]");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the reimbursement submission deadline for travel receipts?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                // Must be exactly 1 source reference, no duplicate citations for the same page/paragraph
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("travel_policy.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)));
    }

    @Test
    @DisplayName("Scenario 5: Removed document is no longer available as evidence in subsequent questions")
    void testRemovedDocumentIsNotAvailableAsEvidence() throws Exception {
        byte[] doc1Bytes = TestDocumentFactory.createPdf(List.of(
                "Confidential Financial Audit 2025: Net operating margin increased to 24 percent."
        ));
        byte[] doc2Bytes = TestDocumentFactory.createPdf(List.of(
                "Public Annual Report: Our company values include integrity and transparency."
        ));

        org.springframework.test.web.servlet.MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "audit_2025.pdf", MediaType.APPLICATION_PDF_VALUE, doc1Bytes))
                        .file(new MockMultipartFile("files", "public_report.pdf", MediaType.APPLICATION_PDF_VALUE, doc2Bytes)))
                .andExpect(status().isOk())
                .andReturn();

        String doc1IdStr = com.jayway.jsonpath.JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.files[0].id");
        java.util.UUID doc1Id = java.util.UUID.fromString(doc1IdStr);

        // Delete audit_2025.pdf
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/documents/" + doc1Id))
                .andExpect(status().isOk());

        // Ask a question about the removed document
        DocumentAskRequest askRequest = new DocumentAskRequest("What is the net operating margin in audit_2025.pdf?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", empty()))
                .andExpect(jsonPath("$.answer", containsString("was not found among the uploaded documents")));
    }
}
