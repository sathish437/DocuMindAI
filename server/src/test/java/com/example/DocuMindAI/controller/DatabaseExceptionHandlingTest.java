package com.example.DocuMindAI.controller;

import com.example.DocuMindAI.exception.GlobalExceptionHandler;
import com.example.DocuMindAI.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DocumentUploadController.class, GlobalExceptionHandler.class})
class DatabaseExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("Should return HTTP 503 Service Unavailable when database connection fails")
    void testDatabaseFailureReturns503() throws Exception {
        when(documentService.getAllDocuments())
                .thenThrow(new DataAccessResourceFailureException("Unable to connect to Neon PostgreSQL server"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.error", is("Service Unavailable")))
                .andExpect(jsonPath("$.message", containsString("database service is temporarily unavailable")));
    }
}
