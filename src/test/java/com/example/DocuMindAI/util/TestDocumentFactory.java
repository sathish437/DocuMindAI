package com.example.DocuMindAI.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class TestDocumentFactory {

    public static byte[] createPdf(List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 0; i < pageTexts.size(); i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                String text = pageTexts.get(i);
                if (text != null && !text.isBlank()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
                        contentStream.newLineAtOffset(50, 700);
                        contentStream.showText(text);
                        contentStream.endText();
                    }
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    public static byte[] createDocx(String heading, List<String> paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            if (heading != null && !heading.isBlank()) {
                XWPFParagraph p = document.createParagraph();
                p.setStyle("Heading 1");
                XWPFRun r = p.createRun();
                r.setText(heading);
            }
            if (paragraphs != null) {
                for (String text : paragraphs) {
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(text);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();
        }
    }
}
