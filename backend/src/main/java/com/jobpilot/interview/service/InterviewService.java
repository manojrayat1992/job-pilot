package com.jobpilot.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.entity.ConversationHistory;
import com.jobpilot.ai.repository.ConversationHistoryRepository;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.interview.dto.InterviewResponse;
import com.jobpilot.interview.entity.InterviewSession;
import com.jobpilot.interview.repository.InterviewSessionRepository;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final ConversationHistoryRepository historyRepository;
    private final ResumeRepository resumeRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder;
    private final org.springframework.core.io.ResourceLoader resourceLoader;

    private static final Map<String, String> PERSONA_DESCRIPTIONS = Map.of(
        "TECH_LEAD", "A senior engineer who cares about technical depth, architecture, clean code, and trade-offs. You are direct and analytical.",
        "HIRING_MANAGER", "A manager focused on delivery, team impact, leadership, and how you solve business problems. You look for ownership and results.",
        "HR_AGENT", "A recruiter focused on cultural fit, behavioral indicators (STAR method), communication skills, and professional maturity."
    );

    @Transactional
    public InterviewResponse startInterview(String role, UUID resumeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        
        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .resumeId(resumeId)
                .targetRole(role)
                .status(InterviewSession.InterviewStatus.ACTIVE)
                .currentTurn(1)
                .maxTurns(10)
                .build();
        
        session = sessionRepository.save(session);
        
        // Initial agent turn
        return processAgentTurn(session, "Start the interview by introducing yourself and asking an icebreaker or initial question.");
    }

    public InterviewSession getSessionById(UUID sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewSession", "id", sessionId));
    }

    @Transactional
    public InterviewResponse processUserAnswer(UUID sessionId, String answer) {
        UUID userId = SecurityUtils.getCurrentUserId();
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewSession", "id", sessionId));
        
        if (session.getStatus() == InterviewSession.InterviewStatus.COMPLETED) {
            return getSessionSummary(session);
        }

        // 1. Save User Answer
        saveHistory(session, "user", null, answer, null);
        
        // 2. Increment Turn
        session.setCurrentTurn(session.getCurrentTurn() + 1);
        
        if (session.getCurrentTurn() > session.getMaxTurns()) {
            return finalizeInterview(session);
        }

        // 3. Supervisor decides next persona
        Map<String, Object> supervisorDecision = getSupervisorDecision(session);
        String nextPersona = (String) supervisorDecision.get("nextPersona");
        String instruction = (String) supervisorDecision.get("instruction");

        // 4. Agent speaks
        return processAgentTurn(session, instruction, nextPersona);
    }

    @Transactional
    public Map<String, Object> processUserAudio(UUID sessionId, MultipartFile audioFile) {
        // 1. Transcribe audio to text
        String transcribedText = aiService.transcribeAudio(audioFile);
        
        // 2. Process the text answer
        InterviewResponse textResponse = processUserAnswer(sessionId, transcribedText);
        
        // 3. Generate Speech (TTS) for the agent's response
        String audioBase64 = null;
        if (textResponse.getLatestMessage() != null) {
            byte[] speechBytes = aiService.generateSpeech(textResponse.getLatestMessage().getContent());
            if (speechBytes != null) {
                audioBase64 = Base64.getEncoder().encodeToString(speechBytes);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("transcribedText", transcribedText);
        result.put("interviewResponse", textResponse);
        result.put("audioBase64", audioBase64);
        
        return result;
    }

    private InterviewResponse processAgentTurn(InterviewSession session, String instruction) {
        return processAgentTurn(session, instruction, "HIRING_MANAGER"); // Default starter
    }

    private InterviewResponse processAgentTurn(InterviewSession session, String instruction, String persona) {
        Resume resume = null;
        if (session.getResumeId() != null) {
            resume = resumeRepository.findById(session.getResumeId()).orElse(null);
        }
        
        String resumeText = resume != null ? resume.getRawText() : "No resume provided.";
        String history = getHistoryString(session.getId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("persona", persona);
        variables.put("personaDescription", PERSONA_DESCRIPTIONS.get(persona));
        variables.put("targetRole", session.getTargetRole());
        variables.put("resumeText", resumeText);
        variables.put("instruction", instruction);
        variables.put("history", history);

        // Real LLM call to the sub-agent
        Map<String, Object> agentResult = callAiWithTemplate("interview-agent.st", variables);
        
        String content = (String) agentResult.get("content");
        Integer shadowScore = getInt(agentResult, "shadowScore");
        
        saveHistory(session, "assistant", persona, content, Map.of("shadowScore", shadowScore));
        
        sessionRepository.save(session);

        return buildResponse(session, content, persona);
    }

    private Map<String, Object> callAiWithTemplate(String templateFile, Map<String, Object> variables) {
        try {
            // 1. Load and parse template
            org.springframework.core.io.Resource resource = resourceLoader.getResource("classpath:prompts/" + templateFile);
            String template = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                template = template.replace("{" + entry.getKey() + "}", 
                    entry.getValue() != null ? entry.getValue().toString() : "");
            }

            // 2. Call LLM
            String response = chatClientBuilder.build().prompt().user(template).call().content();
            
            // 3. Clean and parse JSON using robust extractor
            return com.jobpilot.common.util.JsonExtractor.extractJson(response);
        } catch (Exception e) {
            log.error("AI Interview call failed for {}: {}", templateFile, e.getMessage());
            return Map.of("content", "I'm having a bit of trouble connecting to the panel. Could you repeat that?", "shadowScore", 0);
        }
    }

    private Map<String, Object> getSupervisorDecision(InterviewSession session) {
        String history = getHistoryString(session.getId());
        Map<String, Object> variables = Map.of(
            "targetRole", session.getTargetRole(),
            "resumeContext", "Context extracted from resume...",
            "currentTurn", session.getCurrentTurn(),
            "maxTurns", session.getMaxTurns(),
            "history", history
        );
        return callAiWithTemplate("interview-supervisor.st", variables);
    }

    private void saveHistory(InterviewSession session, String role, String persona, String content, Map<String, Object> shadowScores) {
        ConversationHistory history = ConversationHistory.builder()
                .userId(session.getUserId())
                .interviewSessionId(session.getId())
                .role(role)
                .agentPersona(persona)
                .content(content)
                .shadowScores(shadowScores)
                .build();
        historyRepository.save(history);
    }

    private String getHistoryString(UUID sessionId) {
        List<ConversationHistory> histories = historyRepository.findByInterviewSessionIdOrderByCreatedAtAsc(sessionId);
        StringBuilder sb = new StringBuilder();
        for (ConversationHistory h : histories) {
            String speaker = h.getAgentPersona() != null ? h.getAgentPersona() : "USER";
            sb.append(speaker).append(": ").append(h.getContent()).append("\n");
        }
        return sb.toString();
    }

    private InterviewResponse buildResponse(InterviewSession session, String content, String persona) {
        return InterviewResponse.builder()
                .sessionId(session.getId())
                .currentTurn(session.getCurrentTurn())
                .maxTurns(session.getMaxTurns())
                .status(session.getStatus().name())
                .latestMessage(InterviewResponse.InterviewMessage.builder()
                        .role("assistant")
                        .persona(persona)
                        .content(content)
                        .build())
                .build();
    }

    private InterviewResponse finalizeInterview(InterviewSession session) {
        session.setStatus(InterviewSession.InterviewStatus.COMPLETED);
        
        // 1. Generate Report
        Map<String, Object> variables = Map.of(
            "targetRole", session.getTargetRole(),
            "history", getHistoryString(session.getId())
        );
        
        Map<String, Object> reportData = callAiWithTemplate("interview-report.st", variables);
        
        // 2. Save Report to Session
        try {
            session.setFeedbackReport(objectMapper.valueToTree(reportData));
            session.setOverallScore(getInt(reportData, "overallScore"));
        } catch (Exception e) {
            log.error("Failed to save feedback report: {}", e.getMessage());
        }
        
        sessionRepository.save(session);
        return getSessionSummary(session);
    }

    public InterviewResponse getSessionSummary(InterviewSession session) {
        // Convert JSONNode feedback to DTO
        InterviewResponse.FeedbackReport feedback = null;
        if (session.getFeedbackReport() != null) {
            try {
                feedback = objectMapper.treeToValue(session.getFeedbackReport(), InterviewResponse.FeedbackReport.class);
            } catch (Exception e) {
                log.error("Failed to parse stored feedback: {}", e.getMessage());
            }
        }

        return InterviewResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .currentTurn(session.getCurrentTurn())
                .maxTurns(session.getMaxTurns())
                .feedback(feedback)
                .build();
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}
