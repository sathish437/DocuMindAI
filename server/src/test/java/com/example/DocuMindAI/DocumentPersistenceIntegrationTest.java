package com.example.DocuMindAI;

import com.example.DocuMindAI.dto.EvidenceSegment;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.service.DocumentService;
import com.example.DocuMindAI.util.TestDocumentFactory;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSegmentRepository segmentRepository;

    @Autowired
    private DocumentService documentService;

    @BeforeEach
    void cleanDatabase() {
        segmentRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should upload PDF, extract text, and persist metadata and segments into database")
    void testUploadAndPersistPdf() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Neon PostgreSQL integration test page"));
        MockMultipartFile file = new MockMultipartFile(
                "files", "neon_test.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        MvcResult result = mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles", is(1)))
                .andExpect(jsonPath("$.files[0].id", notNullValue()))
                .andExpect(jsonPath("$.files[0].status", is("PROCESSED")))
                .andExpect(jsonPath("$.files[0].uploadTimestamp", notNullValue()))
                .andExpect(jsonPath("$.files[0].segments", hasSize(1)))
                .andReturn();

        String idStr = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].id");
        UUID docId = UUID.fromString(idStr);

        assertTrue(documentRepository.findById(docId).isPresent());
        assertEquals(1, segmentRepository.findByDocumentIdOrderBySegmentOrderAsc(docId).size());
    }

    @Test
    @DisplayName("Should upload DOCX and persist paragraphs and headings with locators")
    void testUploadAndPersistDocx() throws Exception {
        byte[] docxBytes = TestDocumentFactory.createDocx("Doc Title", List.of("First paragraph body text"));
        MockMultipartFile file = new MockMultipartFile(
                "files", "doc_test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        MvcResult result = mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].documentType", is("DOCX")))
                .andExpect(jsonPath("$.files[0].paragraphCount", is(2)))
                .andReturn();

        String idStr = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].id");
        UUID docId = UUID.fromString(idStr);

        assertTrue(documentRepository.findById(docId).isPresent());
        assertEquals(2, segmentRepository.findByDocumentIdOrderBySegmentOrderAsc(docId).size());
    }

    @Test
    @DisplayName("Should support duplicate filenames with distinct IDs")
    void testDuplicateFilenamesSupported() throws Exception {
        byte[] pdf1 = TestDocumentFactory.createPdf(List.of("Version 1 content"));
        byte[] pdf2 = TestDocumentFactory.createPdf(List.of("Version 2 content"));

        MockMultipartFile file1 = new MockMultipartFile("files", "same_name.pdf", MediaType.APPLICATION_PDF_VALUE, pdf1);
        MockMultipartFile file2 = new MockMultipartFile("files", "same_name.pdf", MediaType.APPLICATION_PDF_VALUE, pdf2);

        MvcResult res1 = mockMvc.perform(multipart("/api/documents/upload").file(file1))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult res2 = mockMvc.perform(multipart("/api/documents/upload").file(file2))
                .andExpect(status().isOk())
                .andReturn();

        String id1 = JsonPath.read(res1.getResponse().getContentAsString(), "$.files[0].id");
        String id2 = JsonPath.read(res2.getResponse().getContentAsString(), "$.files[0].id");

        assertNotEquals(id1, id2);
        assertEquals(2, documentRepository.count());
    }

    @Test
    @DisplayName("Should persist empty-text PDF with PROCESSED_OCR_REQUIRED status")
    void testEmptyTextPdfPersistedWithOcrRequired() throws Exception {
        byte[] blankPdf = TestDocumentFactory.createPdf(List.of(""));
        MockMultipartFile file = new MockMultipartFile("files", "scanned.pdf", MediaType.APPLICATION_PDF_VALUE, blankPdf);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].status", is("PROCESSED_OCR_REQUIRED")))
                .andExpect(jsonPath("$.files[0].ocrRequired", is(true)))
                .andExpect(jsonPath("$.files[0].ocrMessage", containsString("OCR")));

        assertEquals(1, documentRepository.count());
        assertTrue(documentRepository.findAll().get(0).isOcrRequired());
    }

    @Test
    @DisplayName("Should rollback transaction and leave no partial records when extraction fails")
    void testExtractionFailureRollsBackTransaction() throws Exception {
        MockMultipartFile corruptFile = new MockMultipartFile(
                "files", "corrupted.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-corrupted-binary-noise".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(corruptFile))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, documentRepository.count());
        assertEquals(0, segmentRepository.count());
    }

    // =========================================================================
    // Verification That Public CRUD Endpoints Are Removed
    // =========================================================================

    @Test
    @DisplayName("Should verify that public GET /api/documents listing endpoint is no longer exposed")
    void testPublicGetDocumentsEndpointIsRemoved() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should verify that public GET /api/documents/{id} endpoint is no longer exposed")
    void testPublicGetDocumentByIdEndpointIsRemoved() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should verify that public DELETE /api/documents/{id} endpoint is no longer exposed")
    void testPublicDeleteDocumentEndpointIsRemoved() throws Exception {
        mockMvc.perform(delete("/api/documents/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // AI Question-Answering Evidence Retrieval Tests
    // =========================================================================

    @Test
    @DisplayName("Should retrieve all evidence segments across documents with accurate citations")
    void testGetAllEvidenceWithCitations() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("The revenue increased by 15 percent."));
        byte[] docxBytes = TestDocumentFactory.createDocx("Risk Factors", List.of("Inflation risk may impact operational costs."));

        MockMultipartFile pdfFile = new MockMultipartFile("files", "q3_report.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        MockMultipartFile docxFile = new MockMultipartFile("files", "risks.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        mockMvc.perform(multipart("/api/documents/upload").file(pdfFile).file(docxFile))
                .andExpect(status().isOk());

        List<EvidenceSegment> allEvidence = documentService.getAllEvidence();
        assertEquals(3, allEvidence.size()); // 1 PDF page + 1 DOCX heading + 1 DOCX paragraph

        // Verify PDF evidence citation
        EvidenceSegment pdfSeg = allEvidence.stream()
                .filter(e -> "q3_report.pdf".equals(e.documentName()))
                .findFirst()
                .orElseThrow();
        assertEquals("[q3_report.pdf, Page 1]", pdfSeg.getCitation());
        assertTrue(pdfSeg.text().contains("revenue increased"));

        // Verify DOCX heading citation
        EvidenceSegment docxHeading = allEvidence.stream()
                .filter(e -> "risks.docx".equals(e.documentName()) && "HEADING".equals(e.segmentType()))
                .findFirst()
                .orElseThrow();
        assertEquals("[risks.docx, Para 1 (Heading 1)]", docxHeading.getCitation());

        // Verify DOCX body paragraph citation
        EvidenceSegment docxPara = allEvidence.stream()
                .filter(e -> "risks.docx".equals(e.documentName()) && "PARAGRAPH".equals(e.segmentType()))
                .findFirst()
                .orElseThrow();
        assertEquals("[risks.docx, Para 2]", docxPara.getCitation());
    }

    @Test
    @DisplayName("Should search evidence by query text and return matching segments with citations")
    void testSearchEvidenceByQuery() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Net profit reached 5 million dollars in 2025."));
        MockMultipartFile file = new MockMultipartFile("files", "annual.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk());

        List<EvidenceSegment> matching = documentService.searchEvidence("profit");
        assertEquals(1, matching.size());
        assertEquals("[annual.pdf, Page 1]", matching.get(0).getCitation());
        assertTrue(matching.get(0).text().contains("Net profit reached"));

        List<EvidenceSegment> nonMatching = documentService.searchEvidence("unmentioned topic");
        assertTrue(nonMatching.isEmpty());
        assertFalse(documentService.hasSufficientEvidence(nonMatching));
    }

    @Test
    @DisplayName("Should compare and retrieve evidence scoped to specific documents")
    void testGetEvidenceForSpecificDocuments() throws Exception {
        byte[] docA = TestDocumentFactory.createPdf(List.of("Policy A specifies deadline as June 30."));
        byte[] docB = TestDocumentFactory.createPdf(List.of("Policy B specifies deadline as July 15.")); // Potential contradiction!

        MockMultipartFile fileA = new MockMultipartFile("files", "policyA.pdf", MediaType.APPLICATION_PDF_VALUE, docA);
        MockMultipartFile fileB = new MockMultipartFile("files", "policyB.pdf", MediaType.APPLICATION_PDF_VALUE, docB);

        MvcResult resA = mockMvc.perform(multipart("/api/documents/upload").file(fileA)).andExpect(status().isOk()).andReturn();
        MvcResult resB = mockMvc.perform(multipart("/api/documents/upload").file(fileB)).andExpect(status().isOk()).andReturn();

        String idAStr = JsonPath.read(resA.getResponse().getContentAsString(), "$.files[0].id");
        UUID idA = UUID.fromString(idAStr);

        // Retrieve only doc A evidence
        List<EvidenceSegment> docAEvidence = documentService.getEvidenceForDocuments(List.of(idA));
        assertEquals(1, docAEvidence.size());
        assertEquals("[policyA.pdf, Page 1]", docAEvidence.get(0).getCitation());

        // Retrieve evidence across both documents for contradiction/comparison analysis
        String idBStr = JsonPath.read(resB.getResponse().getContentAsString(), "$.files[0].id");
        UUID idB = UUID.fromString(idBStr);

        List<EvidenceSegment> multiDocEvidence = documentService.getEvidenceForDocuments(List.of(idA, idB));
        assertEquals(2, multiDocEvidence.size());
        assertTrue(documentService.hasSufficientEvidence(multiDocEvidence));
    }
}
