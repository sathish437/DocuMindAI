package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.DocumentMetadataResponse;
import com.example.DocuMindAI.dto.TextSegmentResponse;
import com.example.DocuMindAI.exception.DocumentExtractionException;
import com.example.DocuMindAI.util.TestDocumentFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentExtractionServiceTest {

    private DocumentExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new DocumentExtractionService();
    }

    @Test
    @DisplayName("Should extract PDF text page-by-page preserving 1-based page numbers")
    void testExtractPdfPageByPage() throws IOException {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of("First Page Text", "Second Page Text"));

        DocumentMetadataResponse response = extractionService.extractDocument("sample.pdf", pdfBytes, "application/pdf");

        assertEquals("sample.pdf", response.fileName());
        assertEquals("SUCCESS", response.status());
        assertFalse(response.ocrRequired());
        assertNull(response.ocrMessage());
        assertEquals(2, response.pageCount());
        assertNull(response.paragraphCount());
        assertEquals(2, response.totalSegments());

        List<TextSegmentResponse> segments = response.segments();
        assertEquals(2, segments.size());

        TextSegmentResponse page1 = segments.get(0);
        assertEquals("sample.pdf", page1.fileName());
        assertEquals("PAGE", page1.segmentType());
        assertEquals(1, page1.pageNumber());
        assertNull(page1.paragraphIndex());
        assertNull(page1.heading());
        assertTrue(page1.text().contains("First Page Text"));

        TextSegmentResponse page2 = segments.get(1);
        assertEquals(2, page2.pageNumber());
        assertTrue(page2.text().contains("Second Page Text"));
    }

    @Test
    @DisplayName("Should detect PDF with no extractable text and flag ocrRequired = true")
    void testExtractPdfNoTextFlagsOcr() throws IOException {
        byte[] blankPdf = TestDocumentFactory.createPdf(List.of(""));

        DocumentMetadataResponse response = extractionService.extractDocument("scanned.pdf", blankPdf, "application/pdf");

        assertTrue(response.ocrRequired());
        assertNotNull(response.ocrMessage());
        assertTrue(response.ocrMessage().contains("OCR"));
        assertEquals(1, response.pageCount());
    }

    @Test
    @DisplayName("Should extract DOCX paragraphs in order preserving paragraph index and headings without claiming page numbers")
    void testExtractDocxParagraphsAndHeadings() throws IOException {
        byte[] docxBytes = TestDocumentFactory.createDocx("Chapter 1: Overview", List.of("Body paragraph one", "Body paragraph two"));

        DocumentMetadataResponse response = extractionService.extractDocument("manual.docx", docxBytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertEquals("manual.docx", response.fileName());
        assertEquals("SUCCESS", response.status());
        assertFalse(response.ocrRequired());
        assertNull(response.pageCount());
        assertEquals(3, response.paragraphCount());
        assertEquals(3, response.totalSegments());

        List<TextSegmentResponse> segments = response.segments();

        // Heading segment
        TextSegmentResponse seg1 = segments.get(0);
        assertEquals("HEADING", seg1.segmentType());
        assertEquals(1, seg1.paragraphIndex());
        assertNull(seg1.pageNumber()); // Must not claim to be rendered page number
        assertTrue(seg1.heading().toLowerCase().contains("heading"));
        assertEquals("Chapter 1: Overview", seg1.text());

        // Body paragraph 1
        TextSegmentResponse seg2 = segments.get(1);
        assertEquals("PARAGRAPH", seg2.segmentType());
        assertEquals(2, seg2.paragraphIndex());
        assertNull(seg2.pageNumber());
        assertNull(seg2.heading());
        assertEquals("Body paragraph one", seg2.text());

        // Body paragraph 2
        TextSegmentResponse seg3 = segments.get(2);
        assertEquals("PARAGRAPH", seg3.segmentType());
        assertEquals(3, seg3.paragraphIndex());
        assertNull(seg3.pageNumber());
        assertNull(seg3.heading());
        assertEquals("Body paragraph two", seg3.text());
    }

    @Test
    @DisplayName("Should throw DocumentExtractionException when PDF is corrupted")
    void testCorruptPdfThrowsExtractionException() {
        byte[] corruptPdf = "%PDF-1.4 corrupt data and invalid tables \n%%EOF".getBytes();

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractionService.extractDocument("corrupt.pdf", corruptPdf, "application/pdf"));

        assertTrue(ex.getMessage().contains("Failed to extract content from PDF"));
    }

    @Test
    @DisplayName("Should throw DocumentExtractionException when DOCX is corrupted")
    void testCorruptDocxThrowsExtractionException() {
        byte[] corruptDocx = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractionService.extractDocument("corrupt.docx", corruptDocx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        assertTrue(ex.getMessage().contains("Failed to extract content from DOCX"));
    }
}
