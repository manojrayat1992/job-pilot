package com.jobpilot.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.dto.*;
import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.entity.ConversationHistory;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.ai.repository.ConversationHistoryRepository;
import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.dto.MatchScoreResponse;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core AI service — orchestrates all LLM interactions for resume parsing,
 * optimization, cover letter generation, match scoring, and Q&A.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingService embeddingService;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final ConversationHistoryRepository historyRepository;
    private final AiLogRepository aiLogRepository;
    private final ModelRoutingService routingService;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    private record ModelResult(String content, String modelName) {}

    // ================================================================
    // Resume Parsing
    // ================================================================

    public Map<String, Object> parseResumeText(String rawText) {
        String prompt = loadPrompt("parse-resume.st", Map.of("resumeText", rawText));
        ModelResult result = callAi(prompt);
        return parseJsonResponse(result.content());
    }

    // ================================================================
    // Job Description Parsing
    // ================================================================

    public Map<String, Object> parseJobDescription(String jobText) {
        String prompt = loadPrompt("parse-job.st", Map.of("jobText", jobText));
        ModelResult result = callAi(prompt);
        return parseJsonResponse(result.content());
    }

    // ================================================================
    // Resume Optimization
    // ================================================================

    public OptimizeResponse optimizeResume(OptimizeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        long startTime = System.currentTimeMillis();

        // 1. Fetch Resume & Job
        Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId()));

        String jobText = request.getJobText();
        UUID jobId = request.getJobId();
        if (jobId != null) {
            Job job = jobRepository.findByIdAndUserId(jobId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId));
            jobText = job.getRawText();
        }

        // 2. Fetch Memory (Last 3 interactions)
        String pastMemory = "No past interactions for this job.";
        if (jobId != null) {
            List<ConversationHistory> history = historyRepository.findLastInteractions(userId, jobId, 3);
            if (!history.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConversationHistory h : history) {
                    sb.append(h.getRole()).append(": ").append(h.getContent()).append("\n");
                }
                pastMemory = sb.toString();
            }
        }

        // 3. RAG: Retrieve 2 similar resumes
        String ragContext = "No additional context available.";
        if (jobText != null) {
            ragContext = embeddingService.buildRagContext(userId, jobText);
        }

        // 4. Build Prompt
        String prompt = loadPrompt("optimize-resume.st", Map.of(
                "resumeText", resume.getRawText() != null ? resume.getRawText() : "",
                "jobText", jobText != null ? jobText : "",
                "ragContext", ragContext != null ? ragContext : "",
                "pastMemory", pastMemory
        ));

        // 5. Call AI
        ModelResult aiResult = callAi(prompt);
        Map<String, Object> parsed = parseJsonResponse(aiResult.content());

        // 6. Log Interaction & Usage
        long latency = System.currentTimeMillis() - startTime;
        
        // Calculate score jump from result (e.g., "+15%" -> 15)
        Integer scoreJump = 0;
        try {
            String sj = (String) parsed.get("scoreImprovement");
            if (sj != null) {
                scoreJump = Integer.parseInt(sj.replaceAll("[^0-9]", ""));
            }
        } catch (Exception e) {
            log.warn("Failed to parse score jump: {}", parsed.get("scoreImprovement"));
        }

        AiLog aiLog = logAiUsage(userId, "optimizer", "optimize-resume.st", prompt, aiResult.content(), scoreJump, aiResult.modelName(), latency, 0, 0); 
        
        if (jobId != null) {
            saveInteraction(userId, jobId, request.getResumeId(), "user", "Optimize my resume for this job.");
            saveInteraction(userId, jobId, request.getResumeId(), "assistant", "Optimized your resume. Score improvement: " + parsed.get("scoreImprovement"));
        }

        return OptimizeResponse.builder()
                .optimizedResume(parsed.get("optimizedResume"))
                .changes(parsed.get("changes"))
                .keywordsAdded(toStringList(parsed.get("keywordsAdded")))
                .missingKeywords(toStringList(parsed.get("missingKeywords")))
                .matchScore(getInt(parsed, "matchScore"))
                .scoreImprovement((String) parsed.get("scoreImprovement"))
                .aiLogId(aiLog.getId())
                .build();
    }

    private void saveInteraction(UUID userId, UUID jobId, UUID resumeId, String role, String content) {
        ConversationHistory history = ConversationHistory.builder()
                .userId(userId)
                .jobId(jobId)
                .resumeId(resumeId)
                .role(role)
                .content(content)
                .build();
        historyRepository.save(history);
    }

    private AiLog logAiUsage(UUID userId, String feature, String promptTemplate, String request, String response, Integer scoreJump, String modelVersion, long latency, int promptTokens, int completionTokens) {
        AiLog logEntry = AiLog.builder()
                .userId(userId)
                .feature(feature)
                .promptTemplate(promptTemplate)
                .requestContent(request)
                .responseContent(response)
                .scoreJump(scoreJump)
                .provider("Managed")
                .model(modelVersion)
                .latencyMs(latency)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .status("SUCCESS")
                .build();
        return aiLogRepository.save(logEntry);
    }

    // ================================================================
    // Cover Letter Generation
    // ================================================================

    public CoverLetterResponse generateCoverLetter(CoverLetterRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId()));

        String prompt = loadPrompt("cover-letter.st", Map.of(
                "resumeText", resume.getRawText() != null ? resume.getRawText() : "",
                "jobText", request.getJobText(),
                "tone", request.getTone() != null ? request.getTone() : "confident",
                "length", request.getLength() != null ? request.getLength() : "medium"
        ));

        ModelResult aiResult = callAi(prompt);
        Map<String, Object> parsed = parseJsonResponse(aiResult.content());

        AiLog aiLog = logAiUsage(userId, "cover_letter", "cover-letter.st", prompt, aiResult.content(), 0, aiResult.modelName(), 0, 0, 0);

        return CoverLetterResponse.builder()
                .coverLetter((String) parsed.get("coverLetter"))
                .highlights(parsed.get("highlights"))
                .wordCount(parsed.get("wordCount") != null
                        ? ((Number) parsed.get("wordCount")).intValue() : 0)
                .aiLogId(aiLog.getId())
                .build();
    }

    // ================================================================
    // Tailored Resume for PDF
    // ================================================================

    public Map<String, Object> tailorResume(Map<String, Object> baseJson, String jobText) {
        try {
            String jsonStr = objectMapper.writeValueAsString(baseJson);
            String prompt = loadPrompt("tailor-resume.st", Map.of(
                    "resumeJson", jsonStr,
                    "jobText", jobText != null ? jobText : ""
            ));
            ModelResult aiResult = callAi(prompt);
            return parseJsonResponse(aiResult.content());
        } catch (Exception e) {
            log.error("Failed to tailor resume: {}", e.getMessage(), e);
            throw new AiServiceException("Failed to tailor resume for the job.", e);
        }
    }

    // ================================================================
    // Ghost Auto-Apply: Semantic Field Mapping
    // ================================================================

    public Map<String, Object> mapFormFields(String formFieldsJson, String resumeJson, String jobUrl) {
        try {
            String prompt = loadPrompt("ghost-apply-mapper.st", Map.of(
                    "formFields", formFieldsJson,
                    "resumeJson", resumeJson,
                    "jobUrl", jobUrl != null ? jobUrl : ""
            ));
            ModelResult aiResult = callAi(prompt);
            return parseJsonResponse(aiResult.content());
        } catch (Exception e) {
            log.error("AI form field mapping failed: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    // ================================================================
    // Match Score
    // ================================================================

    public MatchScoreResponse calculateMatchScore(UUID resumeId, Job job) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", resumeId));

        String prompt = loadPrompt("match-score.st", Map.of(
                "resumeText", resume.getRawText() != null ? resume.getRawText() : "",
                "jobText", job.getRawText()
        ));

        ModelResult aiResult = callAi(prompt);
        Map<String, Object> parsed = parseJsonResponse(aiResult.content());

        return MatchScoreResponse.builder()
                .overallScore(getInt(parsed, "overallScore"))
                .keywordMatchScore(getInt(parsed, "keywordMatchScore"))
                .experienceMatchScore(getInt(parsed, "experienceMatchScore"))
                .skillsMatchScore(getInt(parsed, "skillsMatchScore"))
                .matchedSkills(toStringList(parsed.get("matchedSkills")))
                .missingSkills(toStringList(parsed.get("missingSkills")))
                .suggestions(toStringList(parsed.get("suggestions")))
                .summary((String) parsed.get("summary"))
                .build();
    }

    // ================================================================
    // Interview Q&A
    // ================================================================

    public AnswerResponse answerQuestion(AnswerRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId()));

        String prompt = loadPrompt("answer-question.st", Map.of(
                "resumeText", resume.getRawText() != null ? resume.getRawText() : "",
                "jobText", request.getJobText() != null ? request.getJobText() : "",
                "question", request.getQuestion()
        ));

        ModelResult aiResult = callAi(prompt);
        Map<String, Object> parsed = parseJsonResponse(aiResult.content());

        AiLog aiLog = logAiUsage(userId, "interview_qa", "answer-question.st", prompt, aiResult.content(), 0, aiResult.modelName(), 0, 0, 0);

        return AnswerResponse.builder()
                .answer((String) parsed.get("answer"))
                .tips(parsed.get("tips"))
                .followUpQuestions(parsed.get("followUpQuestions"))
                .aiLogId(aiLog.getId())
                .build();
    }

    // ================================================================
    // Audio Processing (Whisper / TTS)
    // ================================================================

    public String transcribeAudio(MultipartFile audioFile) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.warn("OpenAI API key not configured, returning mock transcription.");
            return "This is a simulated transcription because the OpenAI API key is missing.";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(openAiApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", audioFile.getResource());
            body.add("model", "whisper-1");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("text");
            }
        } catch (Exception e) {
            log.error("Failed to transcribe audio: {}", e.getMessage());
        }
        return "I could not hear that clearly. Could you repeat?";
    }

    public byte[] generateSpeech(String text) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.warn("OpenAI API key not configured, returning null for speech.");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> body = Map.of(
                    "model", "tts-1",
                    "input", text,
                    "voice", "alloy"
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/speech",
                    requestEntity,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to generate speech: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Performs a deep analysis of resume text for ATS compatibility.
     */
    public AtsAnalysisResponse analyzeAtsCompatibility(UUID userId, String resumeText) {
        String prompt = loadPrompt("ats-analysis.st", Map.of("resumeText", resumeText));
        ModelResult aiResult = callAi(prompt);
        Map<String, Object> parsed = parseJsonResponse(aiResult.content());

        AiLog aiLog = logAiUsage(userId, "ats_analyzer", "ats-analysis.st", prompt, aiResult.content(), 0, aiResult.modelName(), 0, 0, 0);

        // Map issues list
        List<Map<String, String>> issuesRaw = (List<Map<String, String>>) parsed.get("issues");
        List<AtsAnalysisResponse.AtsIssue> issues = issuesRaw != null ? issuesRaw.stream()
                .map(i -> AtsAnalysisResponse.AtsIssue.builder()
                        .category(i.get("category"))
                        .description(i.get("description"))
                        .severity(i.get("severity"))
                        .build())
                .toList() : java.util.List.of();

        return AtsAnalysisResponse.builder()
                .score(getInt(parsed, "score"))
                .issues(issues)
                .suggestions(toStringList(parsed.get("suggestions")))
                .structuralFlags((Map<String, Object>) parsed.get("structuralFlags"))
                .build();
    }

    public void submitFeedback(AiFeedbackRequest request) {
        AiLog logEntry = aiLogRepository.findById(request.getAiLogId())
                .orElseThrow(() -> new ResourceNotFoundException("AiLog", "id", request.getAiLogId()));
        
        logEntry.setRating(request.getRating());
        logEntry.setFeedbackComment(request.getComment());
        
        aiLogRepository.save(logEntry);
        log.info("Feedback received for AI Log {}: Rating {}", request.getAiLogId(), request.getRating());
    }

    // ================================================================
    // Async Embedding Operations
    // ================================================================

    @Async
    public void embedResumeAsync(UUID resumeId, String text) {
        try {
            embeddingService.embedAndStoreResume(resumeId, text);
            log.info("Resume embedding completed: {}", resumeId);
        } catch (Exception e) {
            log.error("Failed to embed resume {}: {}", resumeId, e.getMessage(), e);
        }
    }

    @Async
    public void embedJobAsync(UUID jobId, String text) {
        try {
            embeddingService.embedAndStoreJob(jobId, text);
            log.info("Job embedding completed: {}", jobId);
        } catch (Exception e) {
            log.error("Failed to embed job {}: {}", jobId, e.getMessage(), e);
        }
    }

    // ================================================================
    // Internal Helpers
    // ================================================================

    private ModelResult callAi(String promptText) {
        // Enforce credit quota before making the LLM call
        creditService.requireCredits();

        try {
            com.jobpilot.ai.entity.ModelVersion targetModel = routingService.getTargetModel();
            log.debug("Using AI Model: {}", targetModel.getVersionName());

            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            
            if (response == null || response.isBlank()) {
                throw new AiServiceException("AI returned an empty response");
            }

            // Consume one credit on success
            creditService.consumeCredit();

            return new ModelResult(response, targetModel.getVersionName());
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI service call failed: {}", e.getMessage(), e);
            throw new AiServiceException("AI service call failed", e);
        }
    }

    private String loadPrompt(String templateFile, Map<String, Object> variables) {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/" + templateFile);
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            // Simple variable substitution using {varName} pattern
            String result = template;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }
            return result;
        } catch (IOException e) {
            throw new AiServiceException("Failed to load prompt template: " + templateFile, e);
        }
    }

    private Map<String, Object> parseJsonResponse(String response) {
        return com.jobpilot.common.util.JsonExtractor.extractJson(response);
    }

    private String escapeInternalNewlines(String json) {
        if (json == null || json.isEmpty()) return json;
        
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                sb.append(c);
                escaped = true;
            } else if (c == '"') {
                sb.append(c);
                inString = !inString;
            } else if (c == '\n' || c == '\r') {
                if (inString) {
                    sb.append(c == '\n' ? "\\n" : "\\r");
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> toStringList(Object obj) {
        if (obj instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return java.util.List.of();
    }
}
