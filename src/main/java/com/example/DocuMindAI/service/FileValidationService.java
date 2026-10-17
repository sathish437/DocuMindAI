package com.example.DocuMindAI.service;

import com.example.DocuMindAI.exception.FileValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileValidationService {

    private final DataSize maxFileSize;
    private final int maxFilesPerRequest;

    public FileValidationService(
            @Value("${app.upload.max-file-size:20MB}") DataSize maxFileSize,
            @Value("${app.upload.max-files-per-request:10}") int maxFilesPerRequest) {
        this.maxFileSize = maxFileSize;
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    public void validateFileList(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new FileValidationException("No files were provided for upload.");
        }
        if (files.size() > maxFilesPerRequest) {
            throw new FileValidationException("Maximum files per request (" + maxFilesPerRequest
                    + ") exceeded. Received: " + files.size());
        }
    }

    public byte[] validateAndReadBytes(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            String fileName = (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                    ? file.getOriginalFilename()
                    : "unnamed";
            throw new FileValidationException("Uploaded file '" + fileName + "' is empty.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new FileValidationException("Uploaded file must have a valid filename.");
        }

        if (file.getSize() > maxFileSize.toBytes()) {
            throw new FileValidationException("File '" + fileName + "' (" + file.getSize()
                    + " bytes) exceeds maximum allowed size of " + maxFileSize.toMegabytes() + "MB.");
        }

        String lowerName = fileName.toLowerCase();
        boolean isPdf = lowerName.endsWith(".pdf");
        boolean isDocx = lowerName.endsWith(".docx");

        if (!isPdf && !isDocx) {
            throw new FileValidationException("File '" + fileName
                    + "' has an unsupported extension. Only PDF (.pdf) and DOCX (.docx) documents are supported.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new FileValidationException("Failed to read bytes from file '" + fileName + "': " + ex.getMessage());
        }

        if (isPdf) {
            validatePdfSignature(fileName, bytes);
        } else {
            validateDocxSignature(fileName, bytes);
        }

        return bytes;
    }

    private void validatePdfSignature(String fileName, byte[] bytes) {
        if (bytes.length < 4) {
            throw new FileValidationException("File '" + fileName + "' is too small to be a valid PDF document.");
        }

        // Detect if a ZIP/DOCX was renamed to .pdf (ZIP signature: PK\x03\x04)
        if (bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04) {
            throw new FileValidationException("File '" + fileName
                    + "' content does not match PDF signature. The file appears to be a ZIP/DOCX archive.");
        }

        // Standard PDF signature: %PDF (0x25, 0x50, 0x44, 0x46)
        if (bytes[0] != 0x25 || bytes[1] != 0x50 || bytes[2] != 0x44 || bytes[3] != 0x46) {
            throw new FileValidationException("File '" + fileName
                    + "' content does not match PDF signature. Expected '%PDF' header.");
        }
    }

    private void validateDocxSignature(String fileName, byte[] bytes) {
        if (bytes.length < 4) {
            throw new FileValidationException("File '" + fileName + "' is too small to be a valid DOCX document.");
        }

        // Detect if a PDF was renamed to .docx (%PDF)
        if (bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            throw new FileValidationException("File '" + fileName
                    + "' content does not match DOCX signature. The file appears to be a PDF document.");
        }

        // DOCX must be a ZIP archive starting with PK\x03\x04
        if (bytes[0] != 0x50 || bytes[1] != 0x4B || bytes[2] != 0x03 || bytes[3] != 0x04) {
            throw new FileValidationException("File '" + fileName
                    + "' content does not match DOCX signature. Expected valid ZIP/OpenXML header.");
        }

        // Inspect internal ZIP entries to confirm OpenXML structure ([Content_Types].xml or word/)
        boolean hasOoxmlStructure = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("[Content_Types].xml") || name.startsWith("word/")) {
                    hasOoxmlStructure = true;
                    break;
                }
            }
        } catch (Exception ex) {
            throw new FileValidationException("File '" + fileName
                    + "' has a corrupted ZIP/DOCX structure: " + ex.getMessage());
        }

        if (!hasOoxmlStructure) {
            throw new FileValidationException("File '" + fileName
                    + "' is a ZIP archive but does not contain valid DOCX (OpenXML) structure.");
        }
    }
}
