package com.example.DocuMindAI.controller;

import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.util.TestDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSegmentRepository segmentRepository;

    @BeforeEach
    void setUp() {
        segmentRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully upload a single PDF file and return structured metadata and text segments")
    void testUploadSinglePdfSuccess() throws Exception {
        byte[] pdfContent = TestDocumentFactory.createPdf(List.of("Introductory content for Document Assistant."));
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Documents processed and persisted successfully.")))
                .andExpect(jsonPath("$.totalFiles", is(1)))
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.files[0].id", notNullValue()))
                .andExpect(jsonPath("$.files[0].fileName", is("sample.pdf")))
                .andExpect(jsonPath("$.files[0].size", is(pdfContent.length)))
                .andExpect(jsonPath("$.files[0].contentType", is(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(jsonPath("$.files[0].status", is("PROCESSED")))
                .andExpect(jsonPath("$.files[0].ocrRequired", is(false)))
                .andExpect(jsonPath("$.files[0].pageCount", is(1)))
                .andExpect(jsonPath("$.files[0].totalSegments", is(1)))
                .andExpect(jsonPath("$.files[0].segments[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.files[0].segments[0].segmentType", is("PAGE")))
                .andExpect(jsonPath("$.files[0].segments[0].text", containsString("Introductory content")));
    }

    @Test
    @DisplayName("Should successfully upload multiple files (PDF and DOCX) and return segments for each")
    void testUploadMultipleFilesSuccess() throws Exception {
        byte[] pdfContent = TestDocumentFactory.createPdf(List.of("First PDF page content."));
        byte[] docxContent = TestDocumentFactory.createDocx("Doc Title", List.of("First paragraph text"));

        MockMultipartFile pdfFile = new MockMultipartFile(
                "files",
                "specification.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent
        );

        MockMultipartFile docxFile = new MockMultipartFile(
                "files",
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxContent
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(pdfFile)
                        .file(docxFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles", is(2)))
                .andExpect(jsonPath("$.files", hasSize(2)))
                .andExpect(jsonPath("$.files[0].fileName", is("specification.pdf")))
                .andExpect(jsonPath("$.files[0].status", is("PROCESSED")))
                .andExpect(jsonPath("$.files[0].pageCount", is(1)))
                .andExpect(jsonPath("$.files[1].fileName", is("report.docx")))
                .andExpect(jsonPath("$.files[1].status", is("PROCESSED")))
                .andExpect(jsonPath("$.files[1].paragraphCount", is(2)))
                .andExpect(jsonPath("$.files[1].segments[0].segmentType", is("HEADING")))
                .andExpect(jsonPath("$.files[1].segments[0].paragraphIndex", is(1)))
                .andExpect(jsonPath("$.files[1].segments[1].segmentType", is("PARAGRAPH")))
                .andExpect(jsonPath("$.files[1].segments[1].paragraphIndex", is(2)));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when an empty file is uploaded")
    void testUploadEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "files",
                "empty.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Uploaded file 'empty.pdf' is empty.")))
                .andExpect(jsonPath("$.path", is("/api/documents/upload")));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when files parameter is missing")
    void testMissingFilesParameterReturnsBadRequest() throws Exception {
        MockMultipartFile unrelatedFile = new MockMultipartFile(
                "wrongParam",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "some content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(unrelatedFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Required request parameter 'files' is missing")));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when unsupported file format is uploaded")
    void testUnsupportedFileFormatReturnsBadRequest() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
                "files",
                "notes.txt",
                "text/plain",
                "Plain text document".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(txtFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("unsupported extension")));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when file content signature does not match extension")
    void testMismatchedSignatureReturnsBadRequest() throws Exception {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "files",
                "fake.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Plain text disguised as PDF".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(fakePdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("content does not match PDF signature")));
    }

    @Test
    @DisplayName("Should return 422 Unprocessable Entity when PDF is corrupted")
    void testCorruptPdfReturnsUnprocessableEntity() throws Exception {
        MockMultipartFile corruptPdf = new MockMultipartFile(
                "files",
                "corrupt.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-corrupted-bytes-and-invalid-objects".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(corruptPdf))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.error", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.message", containsString("Failed to extract content from PDF 'corrupt.pdf'")));
    }

    @Test
    @DisplayName("Should return HTTP 200 with an empty list when no documents are persisted")
    void testGetAllDocumentsEmpty() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should return HTTP 200 with documents after successful upload")
    void testGetAllDocumentsPopulated() throws Exception {
        byte[] pdfContent = TestDocumentFactory.createPdf(List.of("Test document text"));
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "test-doc.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName", is("test-doc.pdf")))
                .andExpect(jsonPath("$[0].documentType", is("PDF")))
                .andExpect(jsonPath("$[0].status", is("PROCESSED")));
    }
}

