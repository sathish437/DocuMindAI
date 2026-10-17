package com.example.DocuMindAI;

import com.example.DocuMindAI.dto.DocumentAskRequest;
import com.example.DocuMindAI.entity.Document;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.service.DocumentService;
import com.example.DocuMindAI.service.DocumentVectorService;
import com.example.DocuMindAI.util.TestDocumentFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentRagIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSegmentRepository segmentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentVectorService documentVectorService;

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        segmentRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should index document segments into VectorStore upon upload")
    void testUploadIndexesSegmentsIntoVectorStore() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Page 1 financial results", "Page 2 revenue breakdown"));
        MockMultipartFile file = new MockMultipartFile("files", "financial_report.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(captor.capture());

        List<org.springframework.ai.document.Document> indexedDocs = captor.getValue();
        assertEquals(2, indexedDocs.size(), "Should have indexed 2 pages into vector store");

        org.springframework.ai.document.Document firstPage = indexedDocs.get(0);
        assertEquals("financial_report.pdf", firstPage.getMetadata().get("fileName"));
        assertEquals(1, firstPage.getMetadata().get("pageNumber"));
        assertNotNull(firstPage.getMetadata().get("documentId"));
    }

    @Test
    @DisplayName("Should use vector similarity search to answer question across all documents")
    void testAskQuestionUsesVectorSimilaritySearch() throws Exception {
        // 1. Upload a document to persist it in DB
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Net revenue grew by 18% year-over-year in Q3."));
        MockMultipartFile file = new MockMultipartFile("files", "earnings.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        Document persistedDoc = documentRepository.findAll().get(0);

        // 2. Mock vector similarity search result
        org.springframework.ai.document.Document vectorHit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Net revenue grew by 18% year-over-year in Q3.",
                Map.of(
                        "documentId", persistedDoc.getId().toString(),
                        "fileName", "earnings.pdf",
                        "documentType", "PDF",
                        "segmentType", "PAGE",
                        "pageNumber", 1
                )
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorHit));

        // 3. Mock Gemini ChatModel response
        String expectedAnswer = "Net revenue grew by 18% year-over-year in Q3 according to the report [Source: earnings.pdf (Page 1)].";
        ChatResponse mockChatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(expectedAnswer))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        // 4. Perform Ask API request
        DocumentAskRequest askRequest = new DocumentAskRequest("What were the financial results in Q3?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What were the financial results in Q3?")))
                .andExpect(jsonPath("$.answer", containsString("Net revenue grew by 18%")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("earnings.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("earnings.pdf (Page 1)")));

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("Should search across all uploaded documents when answering a question")
    void testAskQuestionSearchesAcrossAllUploadedDocuments() throws Exception {
        byte[] pdf1 = TestDocumentFactory.createPdf(List.of("Alice has 10 years of Java experience."));
        byte[] pdf2 = TestDocumentFactory.createPdf(List.of("Bob has 8 years of Python experience."));
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "resume_alice.pdf", MediaType.APPLICATION_PDF_VALUE, pdf1))
                        .file(new MockMultipartFile("files", "resume_bob.pdf", MediaType.APPLICATION_PDF_VALUE, pdf2)))
                .andExpect(status().isOk());

        List<Document> docs = documentRepository.findAll();
        Document docAlice = docs.get(0);

        org.springframework.ai.document.Document vectorHit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Alice has 10 years of Java experience.",
                Map.of(
                        "documentId", docAlice.getId().toString(),
                        "fileName", docAlice.getFileName(),
                        "pageNumber", 1
                )
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorHit));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Alice's resume shows 10 years of experience [Source: " + docAlice.getFileName() + " (Page 1)]."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Which is the best resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources[0].documentName", is(docAlice.getFileName())));

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertEquals("Which is the best resume?", searchCaptor.getValue().getQuery());
    }

    @Test
    @DisplayName("Should return insufficient evidence when vector similarity search yields no results")
    void testAskQuestionReturnsInsufficientEvidenceWhenVectorSearchIsEmpty() throws Exception {
        byte[] pdf = TestDocumentFactory.createPdf(List.of("Unrelated document text"));
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "unrelated.pdf", MediaType.APPLICATION_PDF_VALUE, pdf)))
                .andExpect(status().isOk());

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        DocumentAskRequest askRequest = new DocumentAskRequest("What is quantum computing?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));
    }

    @Test
    @DisplayName("Should delete document vector records when document is deleted")
    void testDeleteDocumentRemovesVectors() {
        Document doc = new Document("temp.pdf", "PDF", 100L, java.time.Instant.now(), "PROCESSED", false, null, 1, null);
        Document saved = documentRepository.save(doc);

        documentService.deleteDocument(saved.getId());

        verify(vectorStore, atLeastOnce()).delete(any(Filter.Expression.class));
    }

    @Test
    @DisplayName("Should answer semantic questions whose vocabulary completely differs from document text via VectorStore")
    void testSemanticQuestionWhoseWordsDifferFromDocumentTextUsingVectorSearch() throws Exception {
        byte[] docBytes = TestDocumentFactory.createPdf(List.of(
                "Distributed consensus protocol utilizes the Raft algorithm to guarantee strong consistency across cluster nodes."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "consensus.pdf", MediaType.APPLICATION_PDF_VALUE, docBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        Document persistedDoc = documentRepository.findAll().get(0);

        org.springframework.ai.document.Document vectorHit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Distributed consensus protocol utilizes the Raft algorithm to guarantee strong consistency across cluster nodes.",
                Map.of(
                        "documentId", persistedDoc.getId().toString(),
                        "fileName", "consensus.pdf",
                        "documentType", "PDF",
                        "segmentType", "PAGE",
                        "pageNumber", 1
                )
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorHit));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("The system maintains node synchronization and cluster agreement using the Raft distributed consensus protocol."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        // Notice: Question words differ completely from "Raft", "consensus", "cluster nodes"
        DocumentAskRequest askRequest = new DocumentAskRequest("How does the platform ensure fault-tolerant state agreement?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("consensus.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.answer", containsString("Raft distributed consensus")));
    }

    @Test
    @DisplayName("Should compare multiple resumes using vector similarity search matches")
    void testMultipleResumeComparisonViaVectorSearch() throws Exception {
        byte[] pdf1 = TestDocumentFactory.createPdf(List.of("Carol Danvers. Senior Backend Engineer with 8 years of Go experience."));
        byte[] pdf2 = TestDocumentFactory.createPdf(List.of("Dave Bowman. Staff SRE with 10 years of Kubernetes experience."));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "resume_carol.pdf", MediaType.APPLICATION_PDF_VALUE, pdf1))
                        .file(new MockMultipartFile("files", "resume_dave.pdf", MediaType.APPLICATION_PDF_VALUE, pdf2)))
                .andExpect(status().isOk());

        List<Document> docs = documentRepository.findAll();
        Document docCarol = docs.stream().filter(d -> d.getFileName().equals("resume_carol.pdf")).findFirst().orElseThrow();
        Document docDave = docs.stream().filter(d -> d.getFileName().equals("resume_dave.pdf")).findFirst().orElseThrow();

        org.springframework.ai.document.Document hit1 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Carol Danvers. Senior Backend Engineer with 8 years of Go experience.",
                Map.of("documentId", docCarol.getId().toString(), "fileName", "resume_carol.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hit2 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Dave Bowman. Staff SRE with 10 years of Kubernetes experience.",
                Map.of("documentId", docDave.getId().toString(), "fileName", "resume_dave.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit1, hit2));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Carol has 8 years in Go backend development, while Dave has 10 years as a Staff SRE in Kubernetes."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Which is the best resume?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'resume_carol.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'resume_dave.pdf')]", notNullValue()))
                .andExpect(jsonPath("$.answer", containsString("Carol has 8 years")))
                .andExpect(jsonPath("$.answer", containsString("Dave has 10 years")));
    }

    @Test
    @DisplayName("Should gracefully fall back to database lexical retrieval when VectorStore throws an exception")
    void testVectorSearchFallbackToDatabaseWhenVectorStoreThrowsException() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Total corporate profit reached 45 million in 2025."));
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "profit_report.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes)))
                .andExpect(status().isOk());

        // Simulate upstream pgvector connection failure or embedding rate limit error
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("Simulated pgvector timeout"));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("According to the report, total corporate profit reached 45 million in 2025."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("What was the corporate profit in 2025?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("profit_report.pdf")))
                .andExpect(jsonPath("$.answer", containsString("45 million in 2025")));
    }

    @Test
    @DisplayName("Should avoid reprocessing unchanged document segments unnecessarily")
    void testAvoidReprocessingUnchangedDocumentSegments() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Content that will not change."));
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "static_doc.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes)))
                .andExpect(status().isOk());

        Document savedDoc = documentRepository.findByIdWithSegments(documentRepository.findAll().get(0).getId()).orElseThrow();

        // First index happened during upload
        verify(vectorStore, atLeastOnce()).add(any());

        // Re-invoking index on unchanged document
        documentVectorService.indexDocumentSegments(savedDoc);

        // vectorStore.add should still only have been called once for this document
        verify(vectorStore, times(1)).add(any());
    }

    @Test
    @DisplayName("Should safely handle empty text segments and not index them into VectorStore")
    void testEmptySegmentsAreNotIndexedIntoVectorStore() throws Exception {
        byte[] emptyPdf = TestDocumentFactory.createPdf(List.of(""));
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("files", "scanned_blank.pdf", MediaType.APPLICATION_PDF_VALUE, emptyPdf)))
                .andExpect(status().isOk());

        // Scanned blank PDF has no non-empty segments, so vectorStore.add should not be called with empty documents
        verify(vectorStore, times(0)).add(any());
    }

    @Test
    @DisplayName("Should deduplicate vector hits when the same resume appears under different document IDs")
    void testDuplicateVectorHitsWithDifferentDocumentIdsAreDeduplicated() throws Exception {
        byte[] pdf1 = TestDocumentFactory.createPdf(List.of("Sathish D. Backend Developer. Skills: Java, Spring Boot, PostgreSQL."));
        byte[] pdf2 = TestDocumentFactory.createPdf(List.of("Saravana K. Frontend Developer. Skills: React, TypeScript, CSS."));
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf1))
                .file(new MockMultipartFile("files", "saravana_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf2)))
                .andExpect(status().isOk());

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        UUID docId3 = UUID.randomUUID();
        UUID docId4 = UUID.randomUUID();

        // Simulate pgvector returning duplicate records of the same resumes from re-uploads
        org.springframework.ai.document.Document hitSathish1 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Sathish D. Backend Developer. Skills: Java, Spring Boot, PostgreSQL.",
                Map.of("documentId", docId1.toString(), "fileName", "sathish_resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitSathish2 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Sathish D. Backend Developer. Skills: Java, Spring Boot, PostgreSQL.",
                Map.of("documentId", docId2.toString(), "fileName", "sathish_resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitSaravana1 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Saravana K. Frontend Developer. Skills: React, TypeScript, CSS.",
                Map.of("documentId", docId3.toString(), "fileName", "saravana_resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitSaravana2 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Saravana K. Frontend Developer. Skills: React, TypeScript, CSS.",
                Map.of("documentId", docId4.toString(), "fileName", "saravana_resume.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hitSathish1, hitSathish2, hitSaravana1, hitSaravana2));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Sathish D is suitable for a backend developer role with Java and Spring Boot experience."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Which resume is suitable for a backend developer and tell the name?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2))) // Exactly 2 unique resumes, NOT 4 duplicates!
                .andExpect(jsonPath("$.sources[?(@.documentName == 'sathish_resume.pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'saravana_resume.pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.answer", containsString("Sathish D")));
    }

    @Test
    @DisplayName("Should exclude unrelated document sources when comparing resumes for a role")
    void testMultipleResumeComparisonExcludesUnrelatedDocumentSources() throws Exception {
        byte[] pdf1 = TestDocumentFactory.createPdf(List.of("Sathish D. Backend Developer with 5 years in Java and Spring Boot microservices."));
        byte[] pdf2 = TestDocumentFactory.createPdf(List.of("Saravana K. Frontend Developer with 4 years in React and Angular."));
        byte[] pdf3 = TestDocumentFactory.createPdf(List.of("Quarterly Corporate Tax Depreciation Guidelines and Schedule."));
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf1))
                .file(new MockMultipartFile("files", "saravana_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf2))
                .file(new MockMultipartFile("files", "corporate_tax_policy.pdf", MediaType.APPLICATION_PDF_VALUE, pdf3)))
                .andExpect(status().isOk());

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        UUID docId3 = UUID.randomUUID();

        org.springframework.ai.document.Document hitBackend = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Sathish D. Backend Developer with 5 years in Java and Spring Boot microservices.",
                Map.of("documentId", docId1.toString(), "fileName", "sathish_resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitFrontend = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Saravana K. Frontend Developer with 4 years in React and Angular.",
                Map.of("documentId", docId2.toString(), "fileName", "saravana_resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitPolicy = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Quarterly Corporate Tax Depreciation Guidelines and Schedule.",
                Map.of("documentId", docId3.toString(), "fileName", "corporate_tax_policy.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hitBackend, hitFrontend, hitPolicy));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Sathish D is the most suitable backend developer based on his 5 years in Java and Spring Boot."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Which resume is suitable for a backend developer and tell the name?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(2))) // Only the 2 candidate resumes, tax policy excluded
                .andExpect(jsonPath("$.sources[?(@.documentName == 'corporate_tax_policy.pdf')]", hasSize(0)))
                .andExpect(jsonPath("$.answer", containsString("Sathish D")));
    }

    @Test
    @DisplayName("Should return generous excerpt visibly demonstrating projects, technologies, and certifications")
    void testSourceExcerptsPreserveDetailedProjectsTechnologiesAndCertifications() throws Exception {
        String richResumeText = "Sathish D. Senior Java Backend Developer. " +
                "Summary: Experienced software engineer specializing in distributed architectures. " +
                "Skills: Java 21, Spring Boot, Microservices, PostgreSQL, Apache Kafka, Redis, Docker, Kubernetes. " +
                "Projects: Architected an ultra-low latency Payment Gateway processing 50,000 transactions per second. " +
                "Certifications: AWS Certified Solutions Architect Professional, Oracle Certified Professional Java SE 17 Developer.";

        byte[] pdf = TestDocumentFactory.createPdf(List.of(richResumeText));
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_d_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf)))
                .andExpect(status().isOk());

        org.springframework.ai.document.Document hit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                richResumeText,
                Map.of("documentId", UUID.randomUUID().toString(), "fileName", "sathish_d_resume.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Sathish D built a Payment Gateway handling 50k TPS and holds AWS Solutions Architect and Oracle Java 17 certifications."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("What backend projects and certifications does Sathish have?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("Payment Gateway")))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("AWS Certified Solutions Architect")))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("Oracle Certified Professional")));
    }

    @Test
    @DisplayName("Should report insufficient evidence when requested credentials are not in retrieved context")
    void testMissingEvidenceReturnsInsufficientEvidence() throws Exception {
        byte[] pdf = TestDocumentFactory.createPdf(List.of("Sathish D. Backend Developer. Skills: Java, Spring Boot."));
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "sathish_resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdf)))
                .andExpect(status().isOk());

        org.springframework.ai.document.Document hit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Sathish D. Backend Developer. Skills: Java, Spring Boot.",
                Map.of("documentId", UUID.randomUUID().toString(), "fileName", "sathish_resume.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("The provided document excerpts do not contain sufficient information to determine if Sathish has CISSP cybersecurity certification."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Does Sathish hold a CISSP cybersecurity certification?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.answer", containsString("do not contain sufficient information")));
    }

    @Test
    @DisplayName("Should resolve duplicate uploads with copy filenames like '(1)' to the canonical document")
    void testDuplicateResumeUploadWithCopyFilenameResolvedToCanonicalDocument() throws Exception {
        String sathishContent = "Sathish D. Senior Java Backend Developer. Experience: 6 years in Java, Spring Boot, PostgreSQL, Microservices. " +
                "Projects: Distributed high-throughput payment processing engine. Certifications: AWS Certified Solutions Architect.";
        String saravanaContent = "Saravana Kumar. Frontend Developer. Experience: 4 years in React, TypeScript, Angular, Redux. " +
                "Projects: Modern responsive dashboard for fintech clients. Certifications: Meta Certified Frontend Developer.";

        byte[] sathishBytes = TestDocumentFactory.createPdf(List.of(sathishContent));
        byte[] saravanaBytes = TestDocumentFactory.createPdf(List.of(saravanaContent));

        // Upload original Sathish resume, duplicate copy upload, and Saravana resume
        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "Sathish-dev-Resume.pdf", MediaType.APPLICATION_PDF_VALUE, sathishBytes))
                .file(new MockMultipartFile("files", "Sathish-dev-Resume (1).pdf", MediaType.APPLICATION_PDF_VALUE, sathishBytes))
                .file(new MockMultipartFile("files", "Saravana's Resume (1).pdf", MediaType.APPLICATION_PDF_VALUE, saravanaBytes)))
                .andExpect(status().isOk());

        List<Document> allDocs = documentRepository.findAll();
        assertEquals(3, allDocs.size(), "Three documents uploaded and stored in DB");

        Document sathishCanonicalDoc = allDocs.stream()
                .filter(d -> "Sathish-dev-Resume.pdf".equals(d.getFileName()))
                .findFirst().orElseThrow();
        Document sathishCopyDoc = allDocs.stream()
                .filter(d -> "Sathish-dev-Resume (1).pdf".equals(d.getFileName()))
                .findFirst().orElseThrow();
        Document saravanaDoc = allDocs.stream()
                .filter(d -> "Saravana's Resume (1).pdf".equals(d.getFileName()))
                .findFirst().orElseThrow();

        // Simulate pgvector returning matches from both Sathish uploads and Saravana upload
        org.springframework.ai.document.Document hitSathishOriginal = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                sathishContent,
                Map.of("documentId", sathishCanonicalDoc.getId().toString(), "fileName", "Sathish-dev-Resume.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitSathishCopy = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                sathishContent,
                Map.of("documentId", sathishCopyDoc.getId().toString(), "fileName", "Sathish-dev-Resume (1).pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitSaravana = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                saravanaContent,
                Map.of("documentId", saravanaDoc.getId().toString(), "fileName", "Saravana's Resume (1).pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hitSathishOriginal, hitSathishCopy, hitSaravana));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Between the candidates, Sathish D is suitable for a backend developer role because he has 6 years of experience in Java, Spring Boot, and PostgreSQL."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Which resume is suitable for a backend developer and tell the name?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2))) // Exactly 2 sources (Sathish & Saravana), NO duplicate Sathish-dev-Resume (1).pdf!
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Sathish-dev-Resume.pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Saravana\\'s Resume (1).pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Sathish-dev-Resume (1).pdf')]", hasSize(0)))
                .andExpect(jsonPath("$.sources[?(@.documentId == '" + sathishCanonicalDoc.getId() + "')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentId == '" + saravanaDoc.getId() + "')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'Sathish-dev-Resume.pdf')].excerpt", notNullValue()))
                .andExpect(jsonPath("$.answer", containsString("Sathish D")));
    }

    @Test
    @DisplayName("Should preserve genuinely distinct resume versions and keep them distinguishable")
    void testGenuinelyDistinctResumeVersionsRemainDistinguishable() throws Exception {
        String v1Content = "John Doe. Junior Java Developer. 1 year experience with Spring framework and MySQL. Education: BS Computer Science 2024.";
        String v2Content = "John Doe. Senior Java Backend Engineer. 5 years experience with Java, Spring Boot, Kafka, Kubernetes. Certifications: AWS Certified Solutions Architect.";

        byte[] v1Bytes = TestDocumentFactory.createPdf(List.of(v1Content));
        byte[] v2Bytes = TestDocumentFactory.createPdf(List.of(v2Content));

        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "candidate_v1.pdf", MediaType.APPLICATION_PDF_VALUE, v1Bytes))
                .file(new MockMultipartFile("files", "candidate_v2.pdf", MediaType.APPLICATION_PDF_VALUE, v2Bytes)))
                .andExpect(status().isOk());

        List<Document> docs = documentRepository.findAll();
        assertEquals(2, docs.size(), "Both versions uploaded");

        Document docV1 = docs.stream().filter(d -> "candidate_v1.pdf".equals(d.getFileName())).findFirst().orElseThrow();
        Document docV2 = docs.stream().filter(d -> "candidate_v2.pdf".equals(d.getFileName())).findFirst().orElseThrow();

        org.springframework.ai.document.Document hitV1 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                v1Content,
                Map.of("documentId", docV1.getId().toString(), "fileName", "candidate_v1.pdf", "pageNumber", 1)
        );
        org.springframework.ai.document.Document hitV2 = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                v2Content,
                Map.of("documentId", docV2.getId().toString(), "fileName", "candidate_v2.pdf", "pageNumber", 1)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hitV1, hitV2));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("Candidate v1 reflects junior experience with 1 year in Spring, whereas candidate v2 reflects 5 years of senior experience with AWS and Kafka."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("Compare candidate_v1 and candidate_v2 qualifications");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(2))) // Both versions remain completely distinct
                .andExpect(jsonPath("$.sources[?(@.documentName == 'candidate_v1.pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentName == 'candidate_v2.pdf')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentId == '" + docV1.getId() + "')]", hasSize(1)))
                .andExpect(jsonPath("$.sources[?(@.documentId == '" + docV2.getId() + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("Should ensure source document ID and page numbers precisely match the retrieved evidence")
    void testSourceDocumentIdAndPageNumbersMatchRetrievedEvidence() throws Exception {
        byte[] multiPagePdf = TestDocumentFactory.createPdf(List.of(
                "Page 1: General company information.",
                "Page 2: Security audit report confirms zero critical vulnerabilities found in API services."
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                .file(new MockMultipartFile("files", "audit_report.pdf", MediaType.APPLICATION_PDF_VALUE, multiPagePdf)))
                .andExpect(status().isOk());

        Document doc = documentRepository.findAll().get(0);

        org.springframework.ai.document.Document hit = new org.springframework.ai.document.Document(
                UUID.randomUUID().toString(),
                "Page 2: Security audit report confirms zero critical vulnerabilities found in API services.",
                Map.of(
                        "documentId", doc.getId().toString(),
                        "fileName", "audit_report.pdf",
                        "documentType", "PDF",
                        "segmentType", "PAGE",
                        "pageNumber", 2
                )
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        ChatResponse mockChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage("The security audit report confirmed zero critical vulnerabilities in API services on Page 2."))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        DocumentAskRequest askRequest = new DocumentAskRequest("What did the security audit conclude?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentId", is(doc.getId().toString())))
                .andExpect(jsonPath("$.sources[0].documentName", is("audit_report.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(2)))
                .andExpect(jsonPath("$.sources[0].citation", is("audit_report.pdf (Page 2)")))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("zero critical vulnerabilities")));
    }
}

