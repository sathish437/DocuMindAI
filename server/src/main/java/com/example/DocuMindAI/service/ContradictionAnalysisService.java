package com.example.DocuMindAI.service;

import com.example.DocuMindAI.dto.ClaimSource;
import com.example.DocuMindAI.dto.ContradictionAnalysisRequest;
import com.example.DocuMindAI.dto.ContradictionAnalysisResponse;
import com.example.DocuMindAI.dto.ContradictionFinding;
import com.example.DocuMindAI.dto.DocumentClaim;
import com.example.DocuMindAI.dto.FindingType;
import com.example.DocuMindAI.dto.VerificationStatus;
import com.example.DocuMindAI.entity.Document;
import com.example.DocuMindAI.entity.DocumentSegment;
import com.example.DocuMindAI.exception.DocumentNotFoundException;
import com.example.DocuMindAI.repository.DocumentRepository;
import com.example.DocuMindAI.repository.DocumentSegmentRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.DocuMindAI.dto.SourceReferenceResponse;
import org.springframework.ai.chat.model.ChatModel;

@Service
public class ContradictionAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ContradictionAnalysisService.class);

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```");
    private static final Pattern BRACE_BLOCK_PATTERN = Pattern.compile("(?s)(\\{.*\\})");

    private final DocumentRepository documentRepository;
    private final DocumentSegmentRepository segmentRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ContradictionAnalysisService(
            DocumentRepository documentRepository,
            DocumentSegmentRepository segmentRepository,
            ChatModel chatModel) {
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static final String CONTRADICTION_SYSTEM_PROMPT = """
        You are an Evidence-Based Document Contradiction & Policy Rule Analysis Engine.
        Your task is to detect contradictions, policy conflicts, agreements, and missing information between statements and policy rules found in the provided document excerpts, returning structured findings.

        STRICT ANALYSIS & NORMALIZATION PIPELINE:
        1. EXTRACT CLAIMS & POLICY RULES:
           - Extract factual claims, requirements, limits, deadlines, conditions, fees, and rules from the provided excerpts.
           - Record the exact originalText and verbatim excerpt for every claim.

        2. NORMALIZE VALUES:
           - Normalize dates (e.g. "2026-12-31" or ISO standard), monetary amounts (e.g. "$50 USD", "100 EUR"), units/durations (e.g. "30 days", "15 days", "40 hours/week"), and conditions.
           - Preserve the originalText alongside the normalizedValue.
           - CRITICAL: Never treat different currencies (e.g. USD vs EUR), different units, or different time frames (e.g. annual vs monthly) as equivalent unless an explicit conversion rule is stated.
           - Mark ambiguous values as uncertain rather than guessing.

        3. COMPARE RELATED CLAIMS:
           - Compare claims that refer to the EXACT SAME subject, attribute, or policy rule.
           - Direct contradiction (CONFLICTING) occurs ONLY when reliable source statements directly disagree UNDER THE SAME OR COMPARABLE APPLICABLE CONDITIONS.
           - CRITICAL RULE ON CONDITIONAL VARIATIONS: Do NOT label two statements as contradictory merely because they discuss different dates, contexts, versions, scopes, tiers, or conditions! (e.g. "Full-time employees receive 20 days PTO" vs "Part-time employees receive 10 days PTO" are differing conditions, NOT a contradiction).

        4. CLASSIFY EACH FINDING:
           Use EXACTLY one of these findingType classifications:
           - "CONFLICTING": Reliable source statements directly disagree or conflict under comparable conditions.
           - "SUPPORTED": Reliable source statements agree with or corroborate each other under comparable conditions.
           - "INCOMPLETE": Required information is missing from one or both sources to make a complete comparison.
           - "UNCERTAIN": Evidence is ambiguous, insufficient, or cannot be reliably compared.

        5. GROUNDING AND VERIFIABILITY:
           - Every finding must include exact verbatim excerpts from the provided text.
           - Never invent citations, quotations, document names, or page numbers.
           - If no contradictions exist across the documents, report findings as SUPPORTED or state in summary that no conflicts were detected. Do NOT fabricate findings.

        6. MULTILINGUAL OUTPUT:
           - If the user's focus/question is in Tanglish (Tamil in Latin script), formulate the "summary" and "explanation" fields in Tanglish using ONLY the Latin alphabet. Never use Tamil script characters for Tanglish.
           - If the user's focus/question is in Tamil script, formulate the "summary" and "explanation" in Tamil script.
           - Always keep document names, page citations, and exact claim originalText faithful to the original documents without translating them.

        OUTPUT FORMAT:
        Output ONLY a valid JSON object strictly matching this schema with NO outside text or markdown:
        {
          "summary": "High-level summary of the comparison and findings",
          "findings": [
            {
              "findingType": "CONFLICTING",
              "subject": "Payment deadline",
              "claimA": {
                "originalText": "Payment is due within 30 days.",
                "normalizedValue": "30 days",
                "source": {
                  "documentName": "Policy.pdf",
                  "pageNumber": 2,
                  "paragraphIndex": null,
                  "excerpt": "Payment is due within 30 days."
                }
              },
              "claimB": {
                "originalText": "Payment is due within 15 days.",
                "normalizedValue": "15 days",
                "source": {
                  "documentName": "Agreement.pdf",
                  "pageNumber": 4,
                  "paragraphIndex": null,
                  "excerpt": "Payment is due within 15 days."
                }
              },
              "explanation": "The stated payment deadlines differ under the same applicable conditions.",
              "confidence": 0.95
            }
          ]
        }
        """;

    @Transactional(readOnly = true)
    public ContradictionAnalysisResponse analyzeContradictions(ContradictionAnalysisRequest request) {
        List<UUID> requestedDocIds = request != null ? request.documentIds() : null;
        String focusQuery = (request != null && request.focusQuery() != null && !request.focusQuery().isBlank())
                ? request.focusQuery().trim()
                : "policy requirements, limits, deadlines, rules, specifications, and factual claims";

        // Step 1: Validate explicitly requested document IDs
        if (requestedDocIds != null && !requestedDocIds.isEmpty()) {
            for (UUID docId : requestedDocIds) {
                if (!documentRepository.existsById(docId)) {
                    throw new DocumentNotFoundException("Document not found with ID: " + docId);
                }
            }
        }

        // Step 2: Retrieve candidate segments
        List<DocumentSegment> usableSegments;
        if (requestedDocIds != null && !requestedDocIds.isEmpty()) {
            usableSegments = segmentRepository.findByDocumentIdsWithDocument(requestedDocIds).stream()
                    .filter(s -> s.getText() != null && !s.getText().isBlank())
                    .toList();
        } else {
            if (documentRepository.count() == 0) {
                return new ContradictionAnalysisResponse(
                        List.of(),
                        "No documents are currently available for contradiction analysis.",
                        List.of(),
                        true
                );
            }
            usableSegments = segmentRepository.findAllWithDocument().stream()
                    .filter(s -> s.getText() != null && !s.getText().isBlank())
                    .toList();
        }

        // Step 3: Handle insufficient evidence
        if (usableSegments.isEmpty()) {
            return new ContradictionAnalysisResponse(
                    requestedDocIds != null ? requestedDocIds : List.of(),
                    "The requested documents do not contain extractable text to perform contradiction analysis.",
                    List.of(),
                    true
            );
        }

        // Map segments for verification and document ID lookups
        Map<UUID, List<DocumentSegment>> segmentsByDocId = usableSegments.stream()
                .filter(s -> s.getDocument() != null && s.getDocument().getId() != null)
                .collect(Collectors.groupingBy(s -> s.getDocument().getId()));

        Map<String, List<DocumentSegment>> segmentsByDocName = usableSegments.stream()
                .filter(s -> s.getDocument() != null && s.getDocument().getFileName() != null)
                .collect(Collectors.groupingBy(s -> s.getDocument().getFileName().toLowerCase(Locale.ROOT)));

        List<UUID> resolvedDocIds = usableSegments.stream()
                .map(s -> s.getDocument() != null ? s.getDocument().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Step 4: Build context text from segments
        String contextText = buildComparisonContextText(usableSegments);
        List<String> docNames = usableSegments.stream()
                .map(s -> s.getDocument() != null ? s.getDocument().getFileName() : "Unknown")
                .distinct()
                .toList();

        // Step 5: Construct LLM prompt requesting structured output
        String userPromptText = String.format("""
                DOCUMENTS TO COMPARE:
                %s

                FOCUS / TOPIC:
                %s

                DOCUMENT EXCERPTS:
                %s

                INSTRUCTIONS:
                Extract all relevant claims and policy rules, normalize values, compare claims under comparable conditions, classify each finding (SUPPORTED, CONFLICTING, INCOMPLETE, UNCERTAIN), and return the structured JSON object.
                """, String.join(", ", docNames), focusQuery, contextText);

        GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                .temperature(0.1)
                .maxOutputTokens(3000)
                .responseMimeType("application/json")
                .build();

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(CONTRADICTION_SYSTEM_PROMPT),
                new UserMessage(userPromptText)
        ), chatOptions);

        // Step 6: Invoke LLM
        ChatResponse chatResponse = chatModel.call(prompt);
        String rawOutput = (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null)
                ? chatResponse.getResult().getOutput().getText()
                : "{}";

        // Step 7 & 8: Parse structured response and verify
        return parseAndVerify(rawOutput, usableSegments);
    }

    public boolean isStructuredJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) return false;
        String trimmed = rawOutput.trim();
        return (trimmed.startsWith("{") && trimmed.contains("\"findings\""))
                || (trimmed.startsWith("```") && trimmed.contains("\"findings\""));
    }

    public ContradictionAnalysisResponse parseAndVerify(String rawOutput, List<DocumentSegment> usableSegments) {
        RawContradictionResponse rawResponse = parseStructuredResponse(rawOutput);

        Map<UUID, List<DocumentSegment>> segmentsByDocId = usableSegments != null
                ? usableSegments.stream()
                .filter(s -> s.getDocument() != null && s.getDocument().getId() != null)
                .collect(Collectors.groupingBy(s -> s.getDocument().getId()))
                : Map.of();

        Map<String, List<DocumentSegment>> segmentsByDocName = usableSegments != null
                ? usableSegments.stream()
                .filter(s -> s.getDocument() != null && s.getDocument().getFileName() != null)
                .collect(Collectors.groupingBy(s -> s.getDocument().getFileName().toLowerCase(Locale.ROOT)))
                : Map.of();

        List<UUID> resolvedDocIds = usableSegments != null
                ? usableSegments.stream()
                .map(s -> s.getDocument() != null ? s.getDocument().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList()
                : List.of();

        List<ContradictionFinding> verifiedFindings = new ArrayList<>();
        if (rawResponse.findings() != null) {
            for (RawFinding rf : rawResponse.findings()) {
                ContradictionFinding verified = verifyFinding(rf, segmentsByDocId, segmentsByDocName);
                if (verified != null) {
                    verifiedFindings.add(verified);
                }
            }
        }

        String summary = rawResponse.summary() != null && !rawResponse.summary().isBlank()
                ? rawResponse.summary()
                : (verifiedFindings.isEmpty()
                ? "No contradictions or conflicting claims were detected across the analyzed documents."
                : "Contradiction analysis completed with " + verifiedFindings.size() + " structured finding(s).");

        boolean insufficient = Boolean.TRUE.equals(rawResponse.insufficientEvidence());

        return new ContradictionAnalysisResponse(
                resolvedDocIds,
                summary,
                verifiedFindings,
                insufficient
        );
    }

    public String formatFindingsAsAnswer(ContradictionAnalysisResponse response) {
        return formatFindingsAsAnswer(response, DocumentQaService.QueryLanguage.ENGLISH);
    }

    public String formatFindingsAsAnswer(ContradictionAnalysisResponse response, DocumentQaService.QueryLanguage language) {
        if (response == null) {
            return switch (language) {
                case TANGLISH, MIXED_TANGLISH -> "Contradiction analysis findings edhuvum kedaikala.";
                case TAMIL_SCRIPT -> "முரண்பாட்டு பகுப்பாய்வு முடிவுகள் எதுவும் கிடைக்கவில்லை.";
                case ENGLISH -> "No contradiction analysis findings available.";
            };
        }
        if (response.insufficientEvidence()) {
            return switch (language) {
                case TANGLISH -> "Contradiction irukkannu kandupidikka kuduthuruka document excerpts-la podhumana information illa.";
                case MIXED_TANGLISH -> "Contradiction irukkannu determine panna provided document excerpts-la sufficient information illa.";
                case TAMIL_SCRIPT -> "முரண்பாடுகள் உள்ளனவா என்பதைத் தீர்மானிக்க வழங்கப்பட்ட ஆவணப் பகுதிகளில் போதுமான தகவல்கள் இல்லை.";
                case ENGLISH -> "The provided document excerpts do not contain sufficient information to determine whether contradictions exist.";
            };
        }

        StringBuilder sb = new StringBuilder();
        if (response.summary() != null && !response.summary().isBlank()) {
            sb.append(response.summary().trim()).append("\n\n");
        }

        if (response.findings() == null || response.findings().isEmpty()) {
            if (response.summary() == null || response.summary().isBlank()) {
                String noConflictMsg = switch (language) {
                    case TANGLISH -> "Reviewed document excerpts-la endha contradiction-um kedaikala.";
                    case MIXED_TANGLISH -> "Reviewed document excerpts-la no contradictions were detected.";
                    case TAMIL_SCRIPT -> "ஆய்வு செய்யப்பட்ட ஆவணப் பகுதிகளில் எந்த முரண்பாடுகளும் கண்டறியப்படவில்லை.";
                    case ENGLISH -> "No contradictions were detected across the reviewed document excerpts.";
                };
                sb.append(noConflictMsg);
            }
            return sb.toString().trim();
        }

        String heading = switch (language) {
            case TANGLISH -> "### Mukkiyamaana Findings (Key Findings):\n";
            case MIXED_TANGLISH -> "### Key Findings:\n";
            case TAMIL_SCRIPT -> "### முக்கிய கண்டுபிடிப்புகள் (Key Findings):\n";
            case ENGLISH -> "### Key Findings:\n";
        };
        sb.append(heading);

        for (ContradictionFinding f : response.findings()) {
            sb.append("• [").append(f.findingType()).append("] ").append(f.subject()).append(":\n");
            if (f.claimA() != null) {
                sb.append("  - Claim A: \"").append(f.claimA().originalText()).append("\"");
                if (f.claimA().normalizedValue() != null && !f.claimA().normalizedValue().isBlank()) {
                    sb.append(" (Normalized: ").append(f.claimA().normalizedValue()).append(")");
                }
                if (f.claimA().source() != null && f.claimA().source().documentName() != null) {
                    sb.append(" [").append(f.claimA().source().documentName());
                    if (f.claimA().source().pageNumber() != null) {
                        sb.append(", Page ").append(f.claimA().source().pageNumber());
                    } else if (f.claimA().source().paragraphIndex() != null) {
                        sb.append(", Para ").append(f.claimA().source().paragraphIndex());
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
            if (f.claimB() != null) {
                sb.append("  - Claim B: \"").append(f.claimB().originalText()).append("\"");
                if (f.claimB().normalizedValue() != null && !f.claimB().normalizedValue().isBlank()) {
                    sb.append(" (Normalized: ").append(f.claimB().normalizedValue()).append(")");
                }
                if (f.claimB().source() != null && f.claimB().source().documentName() != null) {
                    sb.append(" [").append(f.claimB().source().documentName());
                    if (f.claimB().source().pageNumber() != null) {
                        sb.append(", Page ").append(f.claimB().source().pageNumber());
                    } else if (f.claimB().source().paragraphIndex() != null) {
                        sb.append(", Para ").append(f.claimB().source().paragraphIndex());
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
            if (f.explanation() != null && !f.explanation().isBlank()) {
                sb.append("  Explanation: ").append(f.explanation()).append("\n");
            }
            sb.append("  Verification: ").append(f.verificationStatus())
              .append(" (Confidence: ").append(String.format(Locale.ROOT, "%.2f", f.confidence() != null ? f.confidence() : 1.0))
              .append(")\n\n");
        }

        return sb.toString().trim();
    }

    public List<SourceReferenceResponse> mapFindingsToSources(
            ContradictionAnalysisResponse response,
            List<SourceReferenceResponse> fallbackSources) {
        if (response == null || response.findings() == null || response.findings().isEmpty()) {
            return fallbackSources != null ? fallbackSources : List.of();
        }

        Map<String, SourceReferenceResponse> uniqueSources = new LinkedHashMap<>();

        // Add verified sources from claims
        for (ContradictionFinding f : response.findings()) {
            addSourceFromClaim(f.claimA(), uniqueSources);
            addSourceFromClaim(f.claimB(), uniqueSources);
        }

        // Add any remaining fallback sources ONLY IF no verified claim sources were found
        if (uniqueSources.isEmpty() && fallbackSources != null) {
            for (SourceReferenceResponse s : fallbackSources) {
                String key = (s.documentName() != null ? s.documentName() : "") + ":" + s.pageNumber() + ":" + s.paragraphIndex();
                uniqueSources.putIfAbsent(key, s);
            }
        }

        return new ArrayList<>(uniqueSources.values());
    }

    private void addSourceFromClaim(DocumentClaim claim, Map<String, SourceReferenceResponse> uniqueSources) {
        if (claim == null || claim.source() == null) return;
        ClaimSource cs = claim.source();
        String docName = cs.documentName() != null ? cs.documentName() : "Unknown Document";
        String citation = buildSourceCitation(docName, cs.pageNumber(), cs.paragraphIndex());
        String key = docName + ":" + cs.pageNumber() + ":" + cs.paragraphIndex();

        String docType = docName.toLowerCase(Locale.ROOT).endsWith(".docx") ? "DOCX" : "PDF";
        String segType = cs.pageNumber() != null ? "PAGE" : "PARAGRAPH";
        SourceReferenceResponse srr = new SourceReferenceResponse(
                cs.documentId(),
                docName,
                docType,
                segType,
                cs.pageNumber(),
                cs.paragraphIndex(),
                null,
                citation,
                cs.excerpt() != null ? cs.excerpt() : claim.originalText()
        );
        uniqueSources.putIfAbsent(key, srr);
    }

    private String buildSourceCitation(String docName, Integer pageNumber, Integer paragraphIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(docName);
        if (pageNumber != null) {
            sb.append(", Page ").append(pageNumber);
        } else if (paragraphIndex != null) {
            sb.append(", Para ").append(paragraphIndex);
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildComparisonContextText(List<DocumentSegment> segments) {
        StringBuilder sb = new StringBuilder();
        int currentChars = 0;
        int maxChars = 14000;

        for (DocumentSegment seg : segments) {
            String text = seg.getText();
            if (text == null || text.isBlank()) continue;

            String docName = seg.getDocument() != null ? seg.getDocument().getFileName() : "Unknown";
            String location = seg.getPageNumber() != null ? "Page " + seg.getPageNumber() : (seg.getParagraphIndex() != null ? "Paragraph " + seg.getParagraphIndex() : "General");

            String block = String.format("--- EXCERPT [%s, %s] ---\n%s\n--- END EXCERPT ---\n\n", docName, location, text.trim());
            if (currentChars + block.length() > maxChars && sb.length() > 0) {
                break;
            }
            sb.append(block);
            currentChars += block.length();
        }
        return sb.toString();
    }

    private RawContradictionResponse parseStructuredResponse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new RawContradictionResponse(List.of(), "Empty response received from AI service.", List.of(), false);
        }

        String jsonCandidate = rawOutput.trim();

        // Check for markdown code fences
        Matcher fenceMatcher = JSON_BLOCK_PATTERN.matcher(jsonCandidate);
        if (fenceMatcher.find()) {
            jsonCandidate = fenceMatcher.group(1).trim();
        } else {
            Matcher braceMatcher = BRACE_BLOCK_PATTERN.matcher(jsonCandidate);
            if (braceMatcher.find()) {
                jsonCandidate = braceMatcher.group(1).trim();
            }
        }

        try {
            return objectMapper.readValue(jsonCandidate, RawContradictionResponse.class);
        } catch (Exception ex) {
            logger.warn("Failed to parse structured model response into RawContradictionResponse: {}", ex.getMessage());
            return new RawContradictionResponse(
                    List.of(),
                    "Unable to parse structured model response. Please verify document contents or retry.",
                    List.of(),
                    false
            );
        }
    }

    private ContradictionFinding verifyFinding(
            RawFinding rf,
            Map<UUID, List<DocumentSegment>> segmentsByDocId,
            Map<String, List<DocumentSegment>> segmentsByDocName) {
        if (rf == null) return null;

        FindingType findingType = parseFindingType(rf.findingType());
        String subject = (rf.subject() != null && !rf.subject().isBlank()) ? rf.subject().trim() : "General Policy Topic";

        DocumentClaim claimA = buildAndVerifyClaim(rf.claimA(), segmentsByDocId, segmentsByDocName);
        DocumentClaim claimB = buildAndVerifyClaim(rf.claimB(), segmentsByDocId, segmentsByDocName);

        boolean claimAVerified = claimA != null && isClaimExcerptVerified(claimA, segmentsByDocId, segmentsByDocName);
        boolean claimBVerified = claimB != null && isClaimExcerptVerified(claimB, segmentsByDocId, segmentsByDocName);

        VerificationStatus status;
        String explanation = rf.explanation() != null ? rf.explanation().trim() : "";
        Double confidence = rf.confidence() != null ? Math.max(0.0, Math.min(1.0, rf.confidence())) : 0.85;

        // Deterministic verification rule
        if (claimA != null && claimB != null) {
            if (claimAVerified && claimBVerified) {
                status = VerificationStatus.VERIFIED;
            } else {
                status = VerificationStatus.UNCERTAIN;
                if (!claimAVerified || !claimBVerified) {
                    explanation += " [Verification Note: One or more evidence excerpts could not be verified in the original extracted document text.]";
                    confidence = Math.min(confidence, 0.45);
                    if (findingType == FindingType.CONFLICTING) {
                        findingType = FindingType.UNCERTAIN;
                    }
                }
            }
        } else if (claimA != null) {
            if (claimAVerified) {
                status = VerificationStatus.VERIFIED;
            } else {
                status = VerificationStatus.UNCERTAIN;
                explanation += " [Verification Note: Evidence excerpt could not be verified in the original extracted document text.]";
                confidence = Math.min(confidence, 0.45);
            }
        } else {
            status = VerificationStatus.UNCERTAIN;
        }

        // Validate that CONFLICTING findings have both claims
        if (findingType == FindingType.CONFLICTING && (claimA == null || claimB == null)) {
            findingType = FindingType.INCOMPLETE;
            explanation += " [Missing second claim for complete conflict determination.]";
        }

        return new ContradictionFinding(
                findingType,
                subject,
                claimA,
                claimB,
                explanation,
                status,
                confidence
        );
    }

    private DocumentClaim buildAndVerifyClaim(
            RawClaim rc,
            Map<UUID, List<DocumentSegment>> segmentsByDocId,
            Map<String, List<DocumentSegment>> segmentsByDocName) {
        if (rc == null) return null;

        String originalText = rc.originalText() != null ? rc.originalText().trim() : "";
        String normalizedValue = rc.normalizedValue() != null ? rc.normalizedValue().trim() : null;

        ClaimSource source = null;
        if (rc.source() != null) {
            RawSource rs = rc.source();
            UUID docId = resolveDocumentId(rs.documentId(), rs.documentName(), segmentsByDocId, segmentsByDocName);
            String docName = (rs.documentName() != null && !rs.documentName().isBlank())
                    ? rs.documentName().trim()
                    : (docId != null && segmentsByDocId.containsKey(docId) && !segmentsByDocId.get(docId).isEmpty()
                    ? segmentsByDocId.get(docId).get(0).getDocument().getFileName()
                    : "Unknown Document");

            source = new ClaimSource(
                    docId,
                    docName,
                    rs.pageNumber(),
                    rs.paragraphIndex(),
                    rs.excerpt() != null ? rs.excerpt().trim() : originalText
            );
        }

        return new DocumentClaim(originalText, normalizedValue, source);
    }

    private boolean isClaimExcerptVerified(
            DocumentClaim claim,
            Map<UUID, List<DocumentSegment>> segmentsByDocId,
            Map<String, List<DocumentSegment>> segmentsByDocName) {
        if (claim == null || claim.source() == null) {
            return false;
        }
        String excerpt = claim.source().excerpt();
        if (excerpt == null || excerpt.isBlank()) {
            return false;
        }

        List<DocumentSegment> candidates = new ArrayList<>();
        if (claim.source().documentId() != null && segmentsByDocId.containsKey(claim.source().documentId())) {
            candidates.addAll(segmentsByDocId.get(claim.source().documentId()));
        } else if (claim.source().documentName() != null) {
            String lower = claim.source().documentName().toLowerCase(Locale.ROOT);
            if (segmentsByDocName.containsKey(lower)) {
                candidates.addAll(segmentsByDocName.get(lower));
            }
        }

        if (candidates.isEmpty()) {
            // Check across all available segments if doc name or id was ambiguous
            for (List<DocumentSegment> segs : segmentsByDocId.values()) {
                candidates.addAll(segs);
            }
        }

        return verifyExcerptInSegments(excerpt, candidates);
    }

    public boolean verifyExcerptInSegments(String excerpt, List<DocumentSegment> docSegments) {
        if (excerpt == null || excerpt.isBlank() || docSegments == null || docSegments.isEmpty()) {
            return false;
        }
        String normalizedExcerpt = normalizeForSearch(excerpt);
        if (normalizedExcerpt.isBlank()) {
            return false;
        }

        for (DocumentSegment seg : docSegments) {
            String segText = seg.getText();
            if (segText == null || segText.isBlank()) {
                continue;
            }
            String normalizedSegText = normalizeForSearch(segText);

            // 1. Direct normalized substring match
            if (normalizedSegText.contains(normalizedExcerpt)) {
                return true;
            }

            // 2. Continuous word sequence match for long excerpts (tolerates minor whitespace/quote differences)
            String[] words = normalizedExcerpt.split("\\s+");
            if (words.length >= 4) {
                int checkLen = Math.min(words.length, 6);
                String subExcerpt = String.join(" ", java.util.Arrays.copyOfRange(words, 0, checkLen));
                if (normalizedSegText.contains(subExcerpt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeForSearch(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\"'`“”‘’«»]", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private UUID resolveDocumentId(
            String idString,
            String nameString,
            Map<UUID, List<DocumentSegment>> segmentsByDocId,
            Map<String, List<DocumentSegment>> segmentsByDocName) {
        if (idString != null && !idString.isBlank()) {
            try {
                UUID parsed = UUID.fromString(idString.trim());
                if (segmentsByDocId.containsKey(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (nameString != null && !nameString.isBlank()) {
            String lower = nameString.trim().toLowerCase(Locale.ROOT);
            if (segmentsByDocName.containsKey(lower)) {
                List<DocumentSegment> segs = segmentsByDocName.get(lower);
                if (!segs.isEmpty() && segs.get(0).getDocument() != null) {
                    return segs.get(0).getDocument().getId();
                }
            }
        }
        return null;
    }

    private FindingType parseFindingType(String raw) {
        if (raw == null) return FindingType.UNCERTAIN;
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CONFLICTING", "CONFLICT", "CONTRADICTION", "CONTRADICTING" -> FindingType.CONFLICTING;
            case "SUPPORTED", "AGREED", "CONSISTENT", "COMPATIBLE" -> FindingType.SUPPORTED;
            case "INCOMPLETE", "MISSING", "PARTIAL" -> FindingType.INCOMPLETE;
            default -> FindingType.UNCERTAIN;
        };
    }

    // Lenient Raw DTOs for structured JSON parsing from LLM output
    record RawContradictionResponse(
            List<String> documentIds,
            String summary,
            List<RawFinding> findings,
            Boolean insufficientEvidence
    ) {}

    record RawFinding(
            String findingType,
            String subject,
            RawClaim claimA,
            RawClaim claimB,
            String explanation,
            String verificationStatus,
            Double confidence
    ) {}

    record RawClaim(
            String originalText,
            String normalizedValue,
            RawSource source
    ) {}

    record RawSource(
            String documentId,
            String documentName,
            Integer pageNumber,
            Integer paragraphIndex,
            String excerpt
    ) {}
}
