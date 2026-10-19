package com.example.DocuMindAI.service;

import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DocumentQaServiceIntentTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentSegmentRepository segmentRepository;

    @Mock
    private DocumentVectorService documentVectorService;

    @Mock
    private ChatModel chatModel;

    private DocumentQaService qaService;

    @BeforeEach
    void setUp() {
        qaService = new DocumentQaService(
                documentRepository,
                segmentRepository,
                documentVectorService,
                chatModel
        );
    }

    @Test
    @DisplayName("Should detect NORMAL_QA intent for standard factual questions")
    void testDetectNormalQaIntent() {
        assertEquals(DocumentQaService.QuestionIntent.NORMAL_QA,
                qaService.detectIntent("What backend skills are listed in Sathish's resume?"));
        assertEquals(DocumentQaService.QuestionIntent.NORMAL_QA,
                qaService.detectIntent("What is the annual budget?"));
        assertEquals(DocumentQaService.QuestionIntent.NORMAL_QA,
                qaService.detectIntent("Summarize the financial performance of Q3."));
        assertEquals(DocumentQaService.QuestionIntent.NORMAL_QA,
                qaService.detectIntent(""));
        assertEquals(DocumentQaService.QuestionIntent.NORMAL_QA,
                qaService.detectIntent(null));
    }

    @Test
    @DisplayName("Should detect COMPARISON intent for candidate/document comparison questions")
    void testDetectComparisonIntent() {
        assertEquals(DocumentQaService.QuestionIntent.COMPARISON,
                qaService.detectIntent("Which resume is most suitable for a backend developer?"));
        assertEquals(DocumentQaService.QuestionIntent.COMPARISON,
                qaService.detectIntent("Compare all uploaded resumes for a Java Backend Developer role."));
        assertEquals(DocumentQaService.QuestionIntent.COMPARISON,
                qaService.detectIntent("Compare the candidates' skills, projects, and experience."));
        assertEquals(DocumentQaService.QuestionIntent.COMPARISON,
                qaService.detectIntent("Which candidate has stronger documented evidence of Spring Boot experience?"));
        assertEquals(DocumentQaService.QuestionIntent.COMPARISON,
                qaService.detectIntent("What are the key differences between these documents?"));
    }

    @Test
    @DisplayName("Should detect CONTRADICTION intent for conflict detection questions")
    void testDetectContradictionIntent() {
        assertEquals(DocumentQaService.QuestionIntent.CONTRADICTION,
                qaService.detectIntent("Find contradictions across my uploaded documents."));
        assertEquals(DocumentQaService.QuestionIntent.CONTRADICTION,
                qaService.detectIntent("Do any documents report different dates for the same project?"));
        assertEquals(DocumentQaService.QuestionIntent.CONTRADICTION,
                qaService.detectIntent("Identify conflicting information about the same person."));
        assertEquals(DocumentQaService.QuestionIntent.CONTRADICTION,
                qaService.detectIntent("Are there any discrepancies in the reported employment dates?"));
    }

    @Test
    @DisplayName("Should detect MISSING_INFO intent for qualification gap questions")
    void testDetectMissingInfoIntent() {
        assertEquals(DocumentQaService.QuestionIntent.MISSING_INFO,
                qaService.detectIntent("Which Java Backend Developer requirements are not evidenced in these resumes?"));
        assertEquals(DocumentQaService.QuestionIntent.MISSING_INFO,
                qaService.detectIntent("What skills are missing from each resume compared with this job description?"));
        assertEquals(DocumentQaService.QuestionIntent.MISSING_INFO,
                qaService.detectIntent("Which required qualifications cannot be verified from the uploaded documents?"));
        assertEquals(DocumentQaService.QuestionIntent.MISSING_INFO,
                qaService.detectIntent("Analyze the qualification gaps in Alice's resume."));
    }

    @Test
    @DisplayName("Should detect COMBINED intent when question asks for comparison, contradictions, and/or gaps")
    void testDetectCombinedIntent() {
        assertEquals(DocumentQaService.QuestionIntent.COMBINED,
                qaService.detectIntent("Compare all candidates, identify contradictions, and list qualification gaps."));
        assertEquals(DocumentQaService.QuestionIntent.COMBINED,
                qaService.detectIntent("Compare all uploaded resumes for a Java Backend Developer role and identify qualification gaps."));
        assertEquals(DocumentQaService.QuestionIntent.COMBINED,
                qaService.detectIntent("Compare the resumes and find any conflicting information."));
    }

    @Test
    @DisplayName("Should verify keyword helper methods correctly classify questions")
    void testKeywordHelpers() {
        assertTrue(qaService.isComparisonQuestion("Compare Alice and Bob"));
        assertTrue(qaService.isComparisonQuestion("Which resume is more suitable?"));
        assertFalse(qaService.isComparisonQuestion("What is Java?"));

        assertTrue(qaService.isContradictionQuestion("Find contradictions"));
        assertTrue(qaService.isContradictionQuestion("Are there conflicting dates?"));
        assertFalse(qaService.isContradictionQuestion("Tell me about Alice"));

        assertTrue(qaService.isMissingInfoQuestion("Which skills are missing?"));
        assertTrue(qaService.isMissingInfoQuestion("What qualifications are not evidenced?"));
        assertFalse(qaService.isMissingInfoQuestion("List all projects"));
    }
}
