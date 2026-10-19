package com.example.DocuMindAI.service;

import com.example.DocuMindAI.exception.FileValidationException;
import com.example.DocuMindAI.util.TestDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileValidationServiceTest {

    private FileValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new FileValidationService(DataSize.ofMegabytes(20), 10);
    }

    @Test
    @DisplayName("Should accept valid PDF file with %PDF magic bytes")
    void testValidPdfAccepted() throws IOException {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Hello PDF"));
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", pdfBytes);

        byte[] result = validationService.validateAndReadBytes(file);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Should accept valid DOCX file with ZIP header and OpenXML structure")
    void testValidDocxAccepted() throws IOException {
        byte[] docxBytes = TestDocumentFactory.createDocx("Title", List.of("First paragraph"));
        MockMultipartFile file = new MockMultipartFile("files", "doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        byte[] result = validationService.validateAndReadBytes(file);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Should reject empty file")
    void testEmptyFileRejected() {
        MockMultipartFile file = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("is empty"));
    }

    @Test
    @DisplayName("Should reject unsupported file extensions (e.g., .txt, .exe, .doc)")
    void testUnsupportedExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("files", "document.txt", "text/plain", "Hello".getBytes());
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("unsupported extension"));
    }

    @Test
    @DisplayName("Should reject mismatched file: extension .pdf but content is not %PDF")
    void testMismatchedPdfContentRejected() {
        MockMultipartFile file = new MockMultipartFile("files", "fake.pdf", "application/pdf", "Not a real PDF".getBytes());
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("content does not match PDF signature"));
    }

    @Test
    @DisplayName("Should reject mismatched file: extension .docx but content is PDF")
    void testMismatchedDocxContentRejected() throws IOException {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("Hello"));
        MockMultipartFile file = new MockMultipartFile("files", "fake.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pdfBytes);
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("content does not match DOCX signature"));
    }

    @Test
    @DisplayName("Should reject ZIP archive disguised as .docx if OpenXML structure is missing")
    void testNonDocxZipRejected() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("notes.txt"));
            zos.write("Some text".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("files", "archive.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("is a ZIP archive but does not contain valid DOCX"));
    }

    @Test
    @DisplayName("Should reject file exceeding maximum allowed size")
    void testOversizedFileRejected() {
        FileValidationService strictService = new FileValidationService(DataSize.ofBytes(100), 5);
        byte[] largeBytes = new byte[150];
        MockMultipartFile file = new MockMultipartFile("files", "large.pdf", "application/pdf", largeBytes);
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> strictService.validateAndReadBytes(file));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    @DisplayName("Should reject request when file count exceeds max-files-per-request")
    void testExceededFileCountRejected() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(new MockMultipartFile("files", "doc" + i + ".pdf", "application/pdf", new byte[]{1}));
        }
        FileValidationException ex = assertThrows(FileValidationException.class,
                () -> validationService.validateFileList(files));
        assertTrue(ex.getMessage().contains("Maximum files per request (10) exceeded"));
    }

    @Test
    @DisplayName("Should pass valid file list")
    void testValidFileListPassed() {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[]{1}),
                new MockMultipartFile("files", "b.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1})
        );
        assertDoesNotThrow(() -> validationService.validateFileList(files));
    }
}
