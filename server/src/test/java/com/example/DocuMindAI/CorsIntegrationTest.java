package com.example.DocuMindAI;

import com.example.DocuMindAI.dto.DocumentAskRequest;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.util.TestDocumentFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CorsIntegrationTest {

    private static final String PROD_ORIGIN = "https://documindai-1d1v.onrender.com";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @DisplayName("Preflight OPTIONS /api/documents/upload should return 200 OK with CORS headers for production domain")
    void testUploadOptionsPreflightAllowsProductionOrigin() throws Exception {
        mockMvc.perform(options("/api/documents/upload")
                        .header("Origin", PROD_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Preflight OPTIONS /api/documents/ask should return 200 OK with CORS headers for production domain")
    void testAskOptionsPreflightAllowsProductionOrigin() throws Exception {
        mockMvc.perform(options("/api/documents/ask")
                        .header("Origin", PROD_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Preflight OPTIONS /api/documents/{id} should return 200 OK with CORS headers for production domain")
    void testDeleteOptionsPreflightAllowsProductionOrigin() throws Exception {
        mockMvc.perform(options("/api/documents/00000000-0000-0000-0000-000000000000")
                        .header("Origin", PROD_ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("POST /api/documents/upload with production Origin should not return 403 Forbidden")
    void testUploadWithProductionOriginSucceeds() throws Exception {
        byte[] pdfContent = TestDocumentFactory.createPdf(List.of("Introductory content for Document Assistant."));
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .header("Origin", PROD_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN));
    }

    @Test
    @DisplayName("POST /api/documents/ask with production Origin should not return 403 Forbidden")
    void testAskWithProductionOriginSucceeds() throws Exception {
        DocumentAskRequest askRequest = new DocumentAskRequest("What is this document about?");
        String requestJson = objectMapper.writeValueAsString(askRequest);

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Origin", PROD_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN));
    }

    @Test
    @DisplayName("DELETE /api/documents/{id} with production Origin should not return 403 Forbidden")
    void testDeleteWithProductionOriginDoesNotReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/documents/00000000-0000-0000-0000-000000000000")
                        .header("Origin", PROD_ORIGIN))
                .andExpect(status().is(not(403)))
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN));
    }

    @Test
    @DisplayName("GET /api/documents with production Origin should return 200 OK with CORS headers")
    void testGetDocumentsWithProductionOriginSucceeds() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header("Origin", PROD_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PROD_ORIGIN));
    }
}
