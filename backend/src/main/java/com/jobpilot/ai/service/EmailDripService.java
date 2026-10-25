package com.jobpilot.ai.service;

import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.entity.EmailSequence;
import com.jobpilot.ai.entity.EmailSequenceStep;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.ai.repository.EmailSequenceRepository;
import com.jobpilot.ai.repository.EmailSequenceStepRepository;
import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.common.util.JsonExtractor;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Email Drip Sequence Service — Generates full multi-step outreach
 * campaigns with optimal timing, personalization, and follow-up cadence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDripService {

    private final ChatGenerationService chatGenerationService;
    private final ResumeRepository resumeRepository;
    private final EmailSequenceRepository emailSequenceRepository;
    private final EmailSequenceStepRepository emailSequenceStepRepository;
    private final AiLogRepository aiLogRepository;

    public AgentDtos.OutreachResponse generateSequence(AgentDtos.OutreachRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        String resumeContext = "";
        if (request.getResumeId() != null) {
            Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId).orElse(null);
            if (resume != null && resume.getRawText() != null) {
                resumeContext = resume.getRawText().substring(0, Math.min(1200, resume.getRawText().length()));
            }
        }

        String prompt = String.format("""
                You are an expert networking strategist. Generate a complete 5-step email drip sequence.
                
                Target Company: %s
                Target Role: %s
                Contact Name: %s
                Contact Title: %s
                Candidate Resume Context: %s
                
                Return a JSON object with:
                {
                  "subject": "Initial email subject line",
                  "body": "Initial cold email (under 150 words, warm, specific, professional)",
                  "linkedInMessage": "LinkedIn connection request message (under 300 chars)",
                  "followUpSequence": [
                    {"dayAfter": 3, "subject": "Follow-up 1 subject", "body": "Follow-up 1 body (80 words)", "channel": "EMAIL"},
                    {"dayAfter": 7, "subject": "Value-add subject", "body": "Share an article or insight (80 words)", "channel": "EMAIL"},
                    {"dayAfter": 10, "subject": "LinkedIn nudge", "body": "Brief LinkedIn message", "channel": "LINKEDIN"},
                    {"dayAfter": 14, "subject": "Final follow-up", "body": "Graceful close (60 words)", "channel": "EMAIL"}
                  ],
                  "tips": ["tip1", "tip2", "tip3"]
                }
                
                Rules:
                - Each email should reference something specific about the company
                - Day 7 should provide genuine VALUE (article, insight, congratulation)
                - Day 14 should leave the door open without being pushy
                - LinkedIn messages should be under 300 characters
                """,
                request.getTargetCompany(), request.getTargetRole(),
                request.getContactName() != null ? request.getContactName() : "Hiring Manager",
                request.getContactTitle() != null ? request.getContactTitle() : "Head of " + request.getTargetRole(),
                resumeContext);

        long startTime = System.currentTimeMillis();
        UUID aiLogId = null;

        try {
            var generation = chatGenerationService.generateFeature("email_drip", AgentPersonality.OUTREACH.getSystemPrompt(), prompt, 2500, 0.7);
            String response = generation.content();

            Map<String, Object> parsed = JsonExtractor.extractRequiredJson(response);

            // Log AI usage
            long latency = System.currentTimeMillis() - startTime;
            AiLog aiLog = AiLog.builder()
                    .userId(userId)
                    .provider("Groq")
                    .model(generation.model())
                    .feature("email_drip")
                    .promptTemplate("email-drip-sequence")
                    .requestContent(prompt)
                    .responseContent(response)
                    .latencyMs(latency)
                    .status("SUCCESS")
                    .build();
            aiLog = aiLogRepository.save(aiLog);
            aiLogId = aiLog.getId();

            // Parse follow-up sequence
            List<AgentDtos.FollowUp> followUps = new ArrayList<>();
            Object seqObj = parsed.get("followUpSequence");
            if (seqObj instanceof List<?> seqList) {
                for (Object item : seqList) {
                    if (item instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) item;
                        followUps.add(AgentDtos.FollowUp.builder()
                                .dayAfter(m.get("dayAfter") instanceof Number n ? n.intValue() : 3)
                                .subject((String) m.getOrDefault("subject", ""))
                                .body((String) m.getOrDefault("body", ""))
                                .channel((String) m.getOrDefault("channel", "EMAIL"))
                                .build());
                    }
                }
            }

            // Persist sequence to DB
            try {
                EmailSequence sequence = EmailSequence.builder()
                        .userId(userId)
                        .targetCompany(request.getTargetCompany())
                        .targetRole(request.getTargetRole())
                        .contactName(request.getContactName())
                        .contactEmail(request.getContactEmail())
                        .status("DRAFT")
                        .build();

                sequence = emailSequenceRepository.save(sequence);

                // Save step 1 (Initial step)
                EmailSequenceStep step1 = EmailSequenceStep.builder()
                        .sequenceId(sequence.getId())
                        .stepNumber(1)
                        .dayOffset(0)
                        .channel("EMAIL")
                        .subject((String) parsed.getOrDefault("subject", ""))
                        .body((String) parsed.getOrDefault("body", ""))
                        .status("PENDING")
                        .scheduledAt(LocalDateTime.now())
                        .build();
                emailSequenceStepRepository.save(step1);

                // Save other steps
                int stepNum = 2;
                for (AgentDtos.FollowUp fu : followUps) {
                    EmailSequenceStep step = EmailSequenceStep.builder()
                            .sequenceId(sequence.getId())
                            .stepNumber(stepNum++)
                            .dayOffset(fu.getDayAfter())
                            .channel(fu.getChannel())
                            .subject(fu.getSubject())
                            .body(fu.getBody())
                            .status("PENDING")
                            .scheduledAt(LocalDateTime.now().plusDays(fu.getDayAfter()))
                            .build();
                    emailSequenceStepRepository.save(step);
                }

                log.info("Persisted email outreach sequence for company {}", request.getTargetCompany());
            } catch (Exception persistenceEx) {
                log.error("Failed to persist outreach sequence: {}", persistenceEx.getMessage());
            }

            return AgentDtos.OutreachResponse.builder()
                    .subject((String) parsed.getOrDefault("subject", ""))
                    .body((String) parsed.getOrDefault("body", ""))
                    .linkedInMessage((String) parsed.getOrDefault("linkedInMessage", ""))
                    .followUpSequence(followUps)
                    .tips(toStringList(parsed.get("tips")))
                    .aiLogId(aiLogId)
                    .build();
        } catch (com.jobpilot.common.exception.CreditExhaustedException | com.jobpilot.common.exception.CreditConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Email drip generation failed: {}", e.getMessage());
            throw new com.jobpilot.common.exception.AiServiceException("AI generation failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }
}
