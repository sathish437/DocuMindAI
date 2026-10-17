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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentQaIntegrationTest {

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
    @DisplayName("Should successfully answer question grounded in uploaded document with verifiable source citation")
    void testAskQuestionSuccess() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("The annual budget is 50 million dollars."));
        MockMultipartFile file = new MockMultipartFile("files", "budget.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("According to the budget report, the annual budget is 50 million dollars.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the annual budget?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What is the annual budget?")))
                .andExpect(jsonPath("$.answer", containsString("50 million dollars")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("budget.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[budget.pdf, Page 1]")));
    }

    @Test
    @DisplayName("Should return 400 when question is empty or blank")
    void testAskBlankQuestionReturnsBadRequest() throws Exception {
        DocumentAskRequest askRequest = new DocumentAskRequest("   ");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Question must not be blank")));
    }

    @Test
    @DisplayName("Should report insufficient evidence when uploaded documents do not contain matching information")
    void testInsufficientEvidenceReported() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Company policy for travel expenses."));
        MockMultipartFile file = new MockMultipartFile("files", "policy.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("The provided document excerpts do not contain sufficient information to answer this question.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the recipe for chocolate cake?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));
    }

    @Test
    @DisplayName("Should return insufficient evidence immediately when repository has no documents")
    void testEmptyRepositoryReturnsInsufficientEvidence() throws Exception {
        DocumentAskRequest askRequest = new DocumentAskRequest("What is the capital of France?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));
    }

    @Test
    @DisplayName("Should handle Gemini LLM failure gracefully without exposing sensitive details")
    void testLlmFailureHandledGracefully() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Some valid document text"));
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Simulated upstream Gemini quota exceeded error"));

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the document about?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("AI service error")));
    }

    @Test
    @DisplayName("Should compare multiple uploaded resumes and return real source citations for 'Which is the best resume?'")
    void testWhichIsTheBestResumeComparesMultipleResumes() throws Exception {
        byte[] aliceResume = TestDocumentFactory.createPdf(List.of(
                "Alice Smith. Senior Software Engineer. Experience: 8 years in Java, Spring Boot, Microservices, Cloud Architecture. Education: BS Computer Science. Skills: Java, Spring Boot, AWS, Kubernetes, PostgreSQL. Projects: High-throughput payment gateway."
        ));
        byte[] bobResume = TestDocumentFactory.createPdf(List.of(
                "Bob Jones. Full Stack Developer. Experience: 5 years in Python, React, PostgreSQL, Docker. Education: MS Software Engineering. Skills: Python, React, TypeScript, Docker, CI/CD. Projects: Real-time team collaboration platform."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "resume_alice.pdf", MediaType.APPLICATION_PDF_VALUE, aliceResume))
                        .file(new MockMultipartFile("files", "resume_bob.pdf", MediaType.APPLICATION_PDF_VALUE, bobResume)))
                .andExpect(status().isOk());

        mockLlmResponse("Based on the provided resumes, Alice has 8 years of Java and cloud experience, while Bob has 5 years in Python and React. Without a specific job description, Alice is stronger for senior backend systems and Bob is well-suited for full-stack web roles.");

        DocumentAskRequest askRequest = new DocumentAskRequest("Which is the best resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Which is the best resume?")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'resume_alice.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'resume_bob.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.answer", containsString("Alice has 8 years")))
                .andExpect(jsonPath("$.answer", containsString("Bob has 5 years")));

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should retrieve resume context even when comparison question words differ from resume content")
    void testSemanticComparisonDoesNotFailWhenWordsDifferFromResumeContent() throws Exception {
        byte[] resumeBytes = TestDocumentFactory.createPdf(List.of(
                "Jane Doe. Principal Architect. 12 years of experience leading engineering teams and building high-scale distributed systems."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "jane_doe_cv.pdf", MediaType.APPLICATION_PDF_VALUE, resumeBytes)))
                .andExpect(status().isOk());

        mockLlmResponse("Jane Doe is a Principal Architect with 12 years of experience leading distributed systems teams.");

        DocumentAskRequest askRequest = new DocumentAskRequest("Who is the most qualified applicant for our tech lead role?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("jane_doe_cv.pdf")))
                .andExpect(jsonPath("$.sources[0].citation", is("[jane_doe_cv.pdf, Page 1]")));

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should return insufficient evidence without calling LLM when documents contain no extractable text")
    void testInsufficientEvidenceWhenUploadedDocumentsContainNoUsableText() throws Exception {
        byte[] emptyPdf = TestDocumentFactory.createPdf(List.of(""));
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "scanned_empty.pdf", MediaType.APPLICATION_PDF_VALUE, emptyPdf)))
                .andExpect(status().isOk());

        DocumentAskRequest askRequest = new DocumentAskRequest("Which is the best resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should exclude unrelated document when comparing resumes")
    void testUnrelatedDocumentExcludedFromResumeComparison() throws Exception {
        byte[] resumeBytes = TestDocumentFactory.createPdf(List.of(
                "Carol Danvers. Data Scientist. Experience: 6 years in Machine Learning and Python."
        ));
        byte[] policyBytes = TestDocumentFactory.createPdf(List.of(
                "Corporate Expense Policy. Meal reimbursements must not exceed 50 dollars per day."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "resume_carol.pdf", MediaType.APPLICATION_PDF_VALUE, resumeBytes))
                        .file(new MockMultipartFile("files", "expense_policy.pdf", MediaType.APPLICATION_PDF_VALUE, policyBytes)))
                .andExpect(status().isOk());

        mockLlmResponse("Carol Danvers has 6 years of Data Science and Machine Learning experience.");

        DocumentAskRequest askRequest = new DocumentAskRequest("Which is the best resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("resume_carol.pdf")));

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should deduplicate re-uploaded documents in database lexical fallback path")
    void testReuploadedDuplicateResumeDeduplicationInDbFallback() throws Exception {
        byte[] sathishResume = TestDocumentFactory.createPdf(List.of(
                "Sathish D. Backend Developer. Experience: 6 years in Java, Spring Boot, PostgreSQL, Docker."
        ));

        // Upload same file twice to create two separate Document records in database
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, sathishResume)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, sathishResume)))
                .andExpect(status().isOk());

        assertEquals(2, documentRepository.count(), "Database should have two document entities from separate uploads");

        mockLlmResponse("Sathish D is an experienced Backend Developer with 6 years in Java and Spring Boot.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is Sathish's backend experience?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1))) // Deduplicated to 1 source, NOT 2 duplicate sources!
                .andExpect(jsonPath("$.sources[0].documentName", is("sathish_resume.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[sathish_resume.pdf, Page 1]")));
    }

    @Test
    @DisplayName("Should accurately cite page number and paragraph for multi-page document")
    void testMultiPagePdfAccuratePageCitations() throws Exception {
        byte[] multiPagePdf = TestDocumentFactory.createPdf(List.of(
                "Page 1: Architecture Overview of Cloud Infrastructure.",
                "Page 2: Redis cluster partition tolerance is achieved using master-replica failover and Raft quorum."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "cloud_infrastructure.pdf", MediaType.APPLICATION_PDF_VALUE, multiPagePdf)))
                .andExpect(status().isOk());

        mockLlmResponse("Redis cluster partition tolerance is achieved using master-replica failover and Raft quorum as stated on Page 2.");

        DocumentAskRequest askRequest = new DocumentAskRequest("How does the Redis cluster achieve partition tolerance?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(2)))
                .andExpect(jsonPath("$.sources[0].citation", is("[cloud_infrastructure.pdf, Page 2]")))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("master-replica failover")));
    }
}
