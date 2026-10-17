package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.DocumentMetadataResponse;
import com.example.DocuMindAI.dto.TextSegmentResponse;
import com.example.DocuMindAI.exception.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentExtractionService {

    public DocumentMetadataResponse extractDocument(String fileName, byte[] bytes, String contentType) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return extractPdf(fileName, bytes);
        } else if (lowerName.endsWith(".docx")) {
            return extractDocx(fileName, bytes);
        } else {
            throw new DocumentExtractionException("Unsupported file type for extraction: " + fileName);
        }
    }

    private DocumentMetadataResponse extractPdf(String fileName, byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new DocumentExtractionException("PDF file '" + fileName + "' is encrypted and cannot be processed.");
            }

            int numberOfPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            List<TextSegmentResponse> segments = new ArrayList<>();
            boolean hasAnyText = false;

            for (int page = 1; page <= numberOfPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    hasAnyText = true;
                }

                segments.add(new TextSegmentResponse(
                        fileName,
                        "PAGE",
                        page,
                        null,
                        null,
                        pageText
                ));
            }

            boolean ocrRequired = !hasAnyText;
            String ocrMessage = ocrRequired
                    ? "No extractable text detected in PDF. Scanned document may require OCR."
                    : null;

            return new DocumentMetadataResponse(
                    fileName,
                    bytes.length,
                    "application/pdf",
                    "SUCCESS",
                    ocrRequired,
                    ocrMessage,
                    numberOfPages,
                    null,
                    segments.size(),
                    segments
            );
        } catch (DocumentExtractionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract content from PDF '" + fileName + "': " + ex.getMessage(), ex);
        }
    }

    private DocumentMetadataResponse extractDocx(String fileName, byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(bais)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            List<TextSegmentResponse> segments = new ArrayList<>();
            int paragraphIndex = 0;

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText() != null ? paragraph.getText().trim() : "";
                if (text.isEmpty()) {
                    continue;
                }
                paragraphIndex++;
                String style = paragraph.getStyle();
                boolean isHeading = style != null && style.toLowerCase().contains("heading");
                String segmentType = isHeading ? "HEADING" : "PARAGRAPH";
                String heading = isHeading ? style : null;

                segments.add(new TextSegmentResponse(
                        fileName,
                        segmentType,
                        null,
                        paragraphIndex,
                        heading,
                        text
                ));
            }

            return new DocumentMetadataResponse(
                    fileName,
                    bytes.length,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "SUCCESS",
                    false,
                    null,
                    null,
                    paragraphIndex,
                    segments.size(),
                    segments
            );
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract content from DOCX '" + fileName + "': " + ex.getMessage(), ex);
        }
    }
}
