package com.example.DocuMindAI;

import com.example.DocuMindAI.dto.DocumentAskRequest;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.example.DocuMindAI.service.DocumentQaService;
import com.example.DocuMindAI.service.DocumentQaService.QueryLanguage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultilingualQaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSegmentRepository segmentRepository;

    @Autowired
    private DocumentQaService documentQaService;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        segmentRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private void mockLlmResponse(String answerText) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(answerText))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    @DisplayName("Should detect query languages accurately: English, Tanglish, Mixed, and Tamil script")
    void testLanguageDetection() {
        assertEquals(QueryLanguage.ENGLISH, documentQaService.detectLanguage("What is the eligibility criteria?"));
        assertEquals(QueryLanguage.ENGLISH, documentQaService.detectLanguage("Explain the annual budget allocation for 2026."));

        assertEquals(QueryLanguage.TANGLISH, documentQaService.detectLanguage("Eligibility criteria enna?"));
        assertEquals(QueryLanguage.TANGLISH, documentQaService.detectLanguage("Thalaivar yaar?"));
        assertEquals(QueryLanguage.TANGLISH, documentQaService.detectLanguage("Indha velai ku enna theva?"));

        assertEquals(QueryLanguage.MIXED_TANGLISH, documentQaService.detectLanguage("Indha document-la assessment duration evlo?"));
        assertEquals(QueryLanguage.MIXED_TANGLISH, documentQaService.detectLanguage("Candidate A oda experience paththi sollunga."));

        assertEquals(QueryLanguage.TAMIL_SCRIPT, documentQaService.detectLanguage("தகுதி வரம்புகள் என்ன?"));
        assertEquals(QueryLanguage.TAMIL_SCRIPT, documentQaService.detectLanguage("மதிப்பீட்டு காலம் எவ்வளவு?"));
    }

    @Test
    @DisplayName("Requirement 1 & 3: English question yields English answer with faithful citations")
    void testEnglishQuestionGroundedAnswer() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The eligibility criteria requires a bachelor's degree in Computer Science and 3 years of software engineering experience."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "eligibility_policy.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("The eligibility criteria requires a bachelor's degree in Computer Science and 3 years of software engineering experience.");

        DocumentAskRequest askRequest = new DocumentAskRequest("What is the eligibility criteria?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("What is the eligibility criteria?")))
                .andExpect(jsonPath("$.answer", containsString("bachelor's degree in Computer Science")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("eligibility_policy.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[eligibility_policy.pdf, Page 1]")));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemMsg = promptCaptor.getValue().getInstructions().get(0).getText();
        assertTrue(systemMsg.contains("The user asked in English"));
    }

    @Test
    @DisplayName("Requirement 1, 3, 4: Tanglish question retrieves English document and returns Tanglish answer in Latin script")
    void testTanglishQuestionRetrievalAndAnswer() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The eligibility criteria requires a bachelor's degree in Computer Science and 3 years of software engineering experience."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "eligibility_policy.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        String tanglishAnswer = "Eligibility criteria-la enna sollirukanga-na, Computer Science-la bachelor's degree irukanum and 3 years software engineering experience theva [eligibility_policy.pdf, Page 1].";
        mockLlmResponse(tanglishAnswer);

        DocumentAskRequest askRequest = new DocumentAskRequest("Eligibility criteria enna?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Eligibility criteria enna?")))
                .andExpect(jsonPath("$.answer", containsString("Eligibility criteria-la enna sollirukanga-na")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("eligibility_policy.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[eligibility_policy.pdf, Page 1]")));

        // Verify that prompt instructed Tanglish Latin script only
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemMsg = promptCaptor.getValue().getInstructions().get(0).getText();
        assertTrue(systemMsg.contains("The user asked in Tanglish"));
        assertTrue(systemMsg.contains("NEVER use Tamil script characters"));
    }

    @Test
    @DisplayName("Requirement 1, 3, 4: Mixed English + Tanglish question retrieves English document and returns Mixed answer")
    void testMixedLanguageQuestionRetrievalAndAnswer() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The assessment duration is 60 minutes. Candidates must complete all tasks within this window."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "assessment_rules.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        String mixedAnswer = "Indha document-la assessment duration 60 minutes nu explicitly mention pannirukanga [assessment_rules.pdf, Page 1].";
        mockLlmResponse(mixedAnswer);

        DocumentAskRequest askRequest = new DocumentAskRequest("Indha document-la assessment duration evlo?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Indha document-la assessment duration evlo?")))
                .andExpect(jsonPath("$.answer", containsString("assessment duration 60 minutes nu")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("assessment_rules.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[assessment_rules.pdf, Page 1]")));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemMsg = promptCaptor.getValue().getInstructions().get(0).getText();
        assertTrue(systemMsg.contains("Mixed English + Tanglish"));
    }

    @Test
    @DisplayName("Requirement 1, 3, 4: Tamil script question retrieves English document and returns Tamil script answer")
    void testTamilScriptQuestionRetrievalAndAnswer() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The eligibility criteria requires a bachelor's degree in Computer Science and 3 years of software engineering experience."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "eligibility_policy.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        String tamilAnswer = "தகுதி வரம்புகள்: கணினி அறிவியலில் இளங்கலை பட்டம் மற்றும் 3 ஆண்டுகள் மென்பொருள் பொறியியல் அனுபவம் பெற்றிருக்க வேண்டும் [eligibility_policy.pdf, Page 1].";
        mockLlmResponse(tamilAnswer);

        DocumentAskRequest askRequest = new DocumentAskRequest("தகுதி வரம்புகள் என்ன?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("தகுதி வரம்புகள் என்ன?")))
                .andExpect(jsonPath("$.answer", containsString("தகுதி வரம்புகள்")))
                .andExpect(jsonPath("$.insufficientEvidence", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].documentName", is("eligibility_policy.pdf")))
                .andExpect(jsonPath("$.sources[0].pageNumber", is(1)))
                .andExpect(jsonPath("$.sources[0].citation", is("[eligibility_policy.pdf, Page 1]")));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemMsg = promptCaptor.getValue().getInstructions().get(0).getText();
        assertTrue(systemMsg.contains("The user asked in Tamil script"));
    }

    @Test
    @DisplayName("Requirement 4 & 5: Multilingual query retrieval extracts English document segments for Tanglish queries")
    void testCrossLingualRetrievalDirect() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The assessment duration is 90 minutes. Eligible candidates must have valid certifications."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "guide.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);

        // Upload document
        try {
            mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Test retrieval with Tanglish query
        DocumentQaService.ContextBundle bundle = documentQaService.retrieveContextBundle(
                "Indha document-la assessment duration evlo?", null, false
        );
        assertNotNull(bundle);
        assertFalse(bundle.sourceReferences().isEmpty());
        assertEquals("guide.pdf", bundle.sourceReferences().get(0).documentName());
        assertTrue(bundle.contextText().contains("assessment duration is 90 minutes"));

        // Test retrieval with Tamil script query
        DocumentQaService.ContextBundle tamilBundle = documentQaService.retrieveContextBundle(
                "தகுதி வரம்புகள் என்ன?", null, false
        );
        assertNotNull(tamilBundle);
        assertFalse(tamilBundle.sourceReferences().isEmpty());
        assertEquals("guide.pdf", tamilBundle.sourceReferences().get(0).documentName());
        assertTrue(tamilBundle.contextText().contains("Eligible candidates"));
    }

    @Test
    @DisplayName("Requirement 7: Insufficient evidence response matches the user's language (Tanglish)")
    void testInsufficientEvidenceTanglish() throws Exception {
        // No documents uploaded
        DocumentAskRequest askRequest = new DocumentAskRequest("Eligibility criteria enna?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("Eligibility criteria enna?")))
                .andExpect(jsonPath("$.answer", is("Kuduthuruka document excerpts-la indha kelvikku badhil solla podhumana information illa.")))
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    @DisplayName("Requirement 7: Insufficient evidence response matches the user's language (Tamil script)")
    void testInsufficientEvidenceTamilScript() throws Exception {
        // No documents uploaded
        DocumentAskRequest askRequest = new DocumentAskRequest("தகுதி வரம்புகள் என்ன?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question", is("தகுதி வரம்புகள் என்ன?")))
                .andExpect(jsonPath("$.answer", is("வழங்கப்பட்ட ஆவணப் பகுதிகளில் இந்தக் கேள்விக்கு பதிலளிக்க போதுமான தகவல்கள் இல்லை.")))
                .andExpect(jsonPath("$.insufficientEvidence", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    @DisplayName("Requirement 6: Faithful source citations and document names are preserved in Tanglish answer")
    void testFaithfulCitationsPreserved() throws Exception {
        byte[] pdfBytes = TestDocumentFactory.createPdf(List.of(
                "The candidate Alice has 5 years of Spring Boot experience."
        ));
        MockMultipartFile file = new MockMultipartFile("files", "Alice_Resume.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes);
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());

        mockLlmResponse("Alice-ku 5 years Spring Boot experience irukku [Alice_Resume.pdf, Page 1].");

        DocumentAskRequest askRequest = new DocumentAskRequest("Alice experience evlo?");

        mockMvc.perform(post("/api/documents/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("[Alice_Resume.pdf, Page 1]")))
                .andExpect(jsonPath("$.sources[0].documentName", is("Alice_Resume.pdf")))
                .andExpect(jsonPath("$.sources[0].citation", is("[Alice_Resume.pdf, Page 1]")))
                .andExpect(jsonPath("$.sources[0].excerpt", containsString("Alice has 5 years of Spring Boot experience")));
    }
}
