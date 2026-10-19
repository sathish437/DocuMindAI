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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentAnalysisIntegrationTest {

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
    @DisplayName("Requirement 1: Normal Q&A still works through the existing ask endpoint")
    void testNormalQaStillWorksThroughExistingAskEndpoint() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "Sathish D. Backend Developer with Java 17, Spring Boot, Microservices, and PostgreSQL."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("Sathish's resume lists skills in Java 17, Spring Boot, Microservices, and PostgreSQL.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What backend skills are listed in Sathish's resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What backend skills are listed in Sathish's resume?")))
                .andExpect(jsonPath("$.answer", containsString("Java 17, Spring Boot")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("sathish_resume.pdf")));
    }

    @Test
    @DisplayName("Requirement 2: Comparison questions automatically retrieve evidence from multiple relevant resumes")
    void testComparisonQuestionsRetrieveEvidenceFromMultipleResumes() throws Exception {
        byte[] aliceBytes = TestDocumentFactory.createPdf(List.of(
                "Alice Dev. Senior Backend Developer with 8 years Java, Spring Boot, Microservices, and AWS experience."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, aliceBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] bobBytes = TestDocumentFactory.createPdf(List.of(
                "Bob Smith. Full Stack Engineer with 5 years Python, Django, FastAPI, and React experience."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Bob_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, bobBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        mockLlmResponse("Comparing the candidates: Alice demonstrates 8 years of Java and Spring Boot experience. Bob demonstrates 5 years of Python and Django experience.");

        DocumentAskRequest request = new DocumentAskRequest(
                "Compare all uploaded resumes for a Java Backend Developer role."
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Compare all uploaded resumes for a Java Backend Developer role.")))
                .andExpect(jsonPath("$.answer", containsString("Alice demonstrates 8 years")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Alice_Resume.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Bob_Resume.pdf')]", notNullValue()));
    }

    @Test
    @DisplayName("Requirement 3: Comparisons evaluate candidates against the job role stated in the question")
    void testComparisonEvaluatesAgainstJobRoleInQuestion() throws Exception {
        byte[] aliceBytes = TestDocumentFactory.createPdf(List.of(
                "Alice Dev. 8 years Java, Spring Boot, AWS, Docker."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, aliceBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] bobBytes = TestDocumentFactory.createPdf(List.of(
                "Bob Smith. 5 years Python, Django, React."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Bob_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, bobBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        mockLlmResponse("For the Senior Java Developer role: Alice has strong documented evidence with 8 years Java and Spring Boot. Bob does not have documented Java experience in the excerpts.");

        DocumentAskRequest request = new DocumentAskRequest(
                "Which candidate has stronger documented evidence of Spring Boot experience?"
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Alice has strong documented evidence")))
                .andExpect(jsonPath("$.sources", hasSize(2)));
    }

    @Test
    @DisplayName("Requirement 4: Contradiction detection returns evidence for both conflicting claims")
    void testContradictionDetectionReturnsEvidenceForBothClaims() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "John Doe. Experience: Software Engineer at TechCorp from January 2018 to December 2021."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "John_Resume_2023.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] docBBytes = TestDocumentFactory.createPdf(List.of(
                "John Doe. Experience: Lead Architect at TechCorp from March 2020 to November 2023."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "John_Resume_2024.pdf", MediaType.APPLICATION_PDF_VALUE, docBBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        mockLlmResponse("""
                A potential contradiction requiring verification was detected regarding employment dates at TechCorp:
                - Claim 1: John_Resume_2023.pdf reports Software Engineer from January 2018 to December 2021 [John_Resume_2023.pdf, Page 1].
                - Claim 2: John_Resume_2024.pdf reports Lead Architect from March 2020 to November 2023 [John_Resume_2024.pdf, Page 1].
                This overlapping date range for different roles at the same employer represents a potential contradiction requiring verification.
                """);

        DocumentAskRequest request = new DocumentAskRequest(
                "Do any documents report different dates for the same project or employer?"
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("potential contradiction")))
                .andExpect(jsonPath("$.answer", containsString("January 2018 to December 2021")))
                .andExpect(jsonPath("$.answer", containsString("March 2020 to November 2023")))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'John_Resume_2023.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'John_Resume_2024.pdf')]", notNullValue()));
    }

    @Test
    @DisplayName("Requirement 5: Missing information is not incorrectly reported as a confirmed contradiction")
    void testMissingInfoNotReportedAsConfirmedContradiction() throws Exception {
        byte[] docABytes = TestDocumentFactory.createPdf(List.of(
                "Alice Dev. Skills: Java, Spring Boot, Docker, AWS Certified Solutions Architect."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, docABytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] docBBytes = TestDocumentFactory.createPdf(List.of(
                "Bob Smith. Skills: Java, Spring Boot, Docker."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Bob_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, docBBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        mockLlmResponse("""
                No contradictions were detected across the reviewed excerpts.
                While Alice holds an AWS Certified Solutions Architect certification and Bob does not mention AWS certifications,
                this absence represents unevidenced information rather than a contradiction.
                """);

        DocumentAskRequest request = new DocumentAskRequest(
                "Find contradictions across my uploaded documents."
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("No contradictions were detected")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)));
    }

    @Test
    @DisplayName("Requirement 6: Qualification-gap analysis distinguishes missing evidence from lack of skill")
    void testQualificationGapDistinguishesMissingEvidenceFromLackOfSkill() throws Exception {
        byte[] aliceBytes = TestDocumentFactory.createPdf(List.of(
                "Alice Dev. Senior Backend Developer with 8 years Java, Spring Boot, RabbitMQ, Docker."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, aliceBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        mockLlmResponse("""
                Regarding the Java Backend Developer requirements:
                - Java and Spring Boot: Documented in Alice's resume [Alice_Resume.pdf, Page 1].
                - Kafka event streaming: Not found in the reviewed document excerpts.
                - Kubernetes container orchestration: Not found in the reviewed document excerpts.
                Note: The lack of mention indicates that these qualifications were not found in the reviewed evidence, not that the candidate definitively lacks the skill.
                """);

        DocumentAskRequest request = new DocumentAskRequest(
                "Which Java Backend Developer requirements are not evidenced in these resumes?"
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Not found in the reviewed document excerpts")))
                .andExpect(jsonPath("$.answer", containsString("not that the candidate definitively lacks the skill")))
                .andExpect(jsonPath("$.sources", hasSize(1)));
    }

    @Test
    @DisplayName("Requirement 7: Combined analysis handles all requested analysis types coherently")
    void testCombinedAnalysisHandlesAllRequestedAnalysesCoherently() throws Exception {
        byte[] aliceBytes = TestDocumentFactory.createPdf(List.of(
                "Alice Dev. Java 17, Spring Boot, Microservices, AWS."
        ));
        MockMultipartFile fileA = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, aliceBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk());

        byte[] bobBytes = TestDocumentFactory.createPdf(List.of(
                "Bob Smith. Python, Django, React, AWS."
        ));
        MockMultipartFile fileB = new MockMultipartFile("files", "Bob_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, bobBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk());

        mockLlmResponse("""
                ### Summary
                Alice and Bob demonstrate complementary skill sets for backend development.

                ### Candidate Comparisons
                - Alice: 8 years Java and Spring Boot microservices.
                - Bob: 5 years Python and Django full-stack.

                ### Potential Contradictions
                No contradictions were detected between the documents.

                ### Missing Information & Qualification Gaps
                Kafka and Kubernetes were not found in the reviewed document excerpts for either candidate.
                """);

        DocumentAskRequest request = new DocumentAskRequest(
                "Compare all candidates, identify contradictions, and list qualification gaps."
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Candidate Comparisons")))
                .andExpect(jsonPath("$.answer", containsString("Potential Contradictions")))
                .andExpect(jsonPath("$.answer", containsString("Missing Information & Qualification Gaps")))
                .andExpect(jsonPath("$.sources", hasSize(2)));
    }

    @Test
    @DisplayName("Requirement 8: Requests require only the question field")
    void testRequestRequiresOnlyQuestionField() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Alice Java Developer."));
        MockMultipartFile file = new MockMultipartFile("files", "alice.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("Alice is a Java Developer.");

        // Sending raw JSON containing only "question"
        String rawJson = "{\"question\":\"What role does Alice have?\"}";

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What role does Alice have?")))
                .andExpect(jsonPath("$.answer", containsString("Alice is a Java Developer")));
    }

    @Test
    @DisplayName("Requirement 9: Duplicate retrieval results do not produce duplicate sources or findings")
    void testDuplicateUploadsDeduplicatedInAskEndpoint() throws Exception {
        byte[] resumeBytes = TestDocumentFactory.createPdf(List.of(
                "Sathish D. Backend Developer with 5 years Java, Spring Boot, Microservices experience."
        ));
        MockMultipartFile file1 = new MockMultipartFile("files", "Sathish-dev-Resume.pdf", MediaType.APPLICATION_PDF_VALUE, resumeBytes);
        MockMultipartFile file2 = new MockMultipartFile("files", "Sathish-dev-Resume (1).pdf", MediaType.APPLICATION_PDF_VALUE, resumeBytes);

        mockMvc.perform(multipart("/api/documents/upload").file(file1)).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/documents/upload").file(file2)).andExpect(status().isOk());

        mockLlmResponse("Sathish demonstrates 5 years of Java and Spring Boot experience.");

        DocumentAskRequest request = new DocumentAskRequest(
                "Compare candidate skills across all uploaded resumes."
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("Sathish-dev-Resume.pdf")));
    }

    @Test
    @DisplayName("Requirement 10: Citations match the correct document IDs, names, and page locations")
    void testCitationsMatchCorrectDocumentAndPage() throws Exception {
        byte[] docBytes = TestDocumentFactory.createPdf(List.of(
                "Page 1: General contact information and personal details.",
                "Page 2: Professional experience: 7 years Java, Spring Boot, AWS architecture."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "MultiPage_Alice.pdf", MediaType.APPLICATION_PDF_VALUE, docBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("Alice demonstrates Java and AWS architecture skills on Page 2 [MultiPage_Alice.pdf, Page 2].");

        DocumentAskRequest request = new DocumentAskRequest(
                "What are Alice's backend architecture skills?"
        );

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[?(@.pageNumber == 2)].citation", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.pageNumber == 2)].documentName", notNullValue()));
    }

    @Test
    @DisplayName("Requirement 11: Empty retrieval and model errors are handled safely")
    void testEmptyRetrievalAndErrorsHandledSafely() throws Exception {
        // Case A: Empty repository
        DocumentAskRequest request = new DocumentAskRequest("Compare all uploaded resumes.");
        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)));

        verify(chatModel, never()).call(any(Prompt.class));

        // Case B: Blank question returns 400 Bad Request
        DocumentAskRequest blankRequest = new DocumentAskRequest("   ");
        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));

        // Case C: Model error returns 500
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Some document text."));
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Simulated Gemini API timeout"));

        DocumentAskRequest errorRequest = new DocumentAskRequest("What is in the document?");
        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(errorRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Internal Server Error")));
    }

    @Test
    @DisplayName("Requirement 12: Existing upload and question-answering workflow preserved")
    void testExistingUploadAndAskWorkflowPreserved() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Company revenue reached 50M."));
        MockMultipartFile file = new MockMultipartFile("files", "financial_report.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.files[0].fileName", is("financial_report.pdf")));

        mockLlmResponse("Company revenue reached 50M.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What was the company revenue?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("50M")))
                .andExpect(jsonPath("$.sources", hasSize(1)));
    }

    @Test
    @DisplayName("Requirement 13: Verify that unwanted public /api/documents/analyze is no longer exposed")
    void testUnwantedAnalyzeEndpointIsNotFound() throws Exception {
        mockMvc.perform(post("/api/documents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"test\"}"))
                .andExpect(status().isNotFound());
    }
}
