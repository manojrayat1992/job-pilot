package com.jobpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.entity.SalaryNegotiation;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.ai.repository.SalaryNegotiationRepository;
import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.common.util.JsonExtractor;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Salary Negotiation Agent — Analyzes offer letters and provides
 * data-driven counter-offer strategies with specific talking points.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryNegotiationService {

    private final ChatClient.Builder chatClientBuilder;
    private final ResumeRepository resumeRepository;
    private final CreditService creditService;
    private final SalaryNegotiationRepository salaryNegotiationRepository;
    private final AiLogRepository aiLogRepository;
    private final ObjectMapper objectMapper;

    public AgentDtos.SalaryAnalysisResponse analyzeOffer(AgentDtos.SalaryAnalysisRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        creditService.requireCredits();

        String resumeContext = "";
        if (request.getResumeId() != null) {
            Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId).orElse(null);
            if (resume != null && resume.getRawText() != null) {
                resumeContext = resume.getRawText().substring(0, Math.min(1500, resume.getRawText().length()));
            }
        }

        String prompt = String.format("""
                You are an expert salary negotiation coach with deep knowledge of tech compensation.
                
                Analyze this offer and provide a data-driven counter-offer strategy.
                
                Role: %s
                Company: %s
                Location: %s
                Years of Experience: %d
                Resume Context: %s
                
                Return a JSON object with:
                {
                  "estimatedMin": <number>,
                  "estimatedMax": <number>,
                  "estimatedMedian": <number>,
                  "currency": "USD",
                  "confidence": "HIGH/MEDIUM/LOW",
                  "negotiationTips": ["tip1", "tip2", "tip3", "tip4", "tip5"],
                  "dataPoints": ["source1", "source2", "source3"],
                  "analysis": "Detailed 2-3 paragraph analysis with specific negotiation language"
                }
                
                Be specific with numbers. Reference Levels.fyi, Glassdoor, Blind ranges.
                Include exact phrases the candidate should use during negotiation.
                """,
                request.getRole(), request.getCompany(), request.getLocation(),
                request.getYearsExperience(), resumeContext);

        long startTime = System.currentTimeMillis();
        UUID aiLogId = null;

        try {
            ChatClient client = chatClientBuilder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .withMaxTokens(2000)
                            .withTemperature(0.5)
                            .build())
                    .build();

            String response = client.prompt()
                    .system(AgentPersonality.ANALYST.getSystemPrompt())
                    .user(prompt)
                    .call().content();

            creditService.consumeCredit();

            Map<String, Object> parsed = JsonExtractor.extractJson(response);

            // Log AI usage
            long latency = System.currentTimeMillis() - startTime;
            AiLog aiLog = AiLog.builder()
                    .userId(userId)
                    .provider("Managed")
                    .model("llama-3.1-8b-instant")
                    .feature("salary_negotiation")
                    .promptTemplate("salary-negotiation")
                    .requestContent(prompt)
                    .responseContent(response)
                    .latencyMs(latency)
                    .status("SUCCESS")
                    .build();
            aiLog = aiLogRepository.save(aiLog);
            aiLogId = aiLog.getId();

            // Persist the salary negotiation analysis to the database
            try {
                int estimatedMedian = getInt(parsed, "estimatedMedian");
                BigDecimal offerAmount = request.getOfferAmount() != null 
                        ? request.getOfferAmount() 
                        : BigDecimal.valueOf(estimatedMedian);

                SalaryNegotiation negotiation = SalaryNegotiation.builder()
                        .userId(userId)
                        .companyName(request.getCompany())
                        .role(request.getRole())
                        .offerAmount(offerAmount)
                        .currency((String) parsed.getOrDefault("currency", "USD"))
                        .analysis(objectMapper.valueToTree(parsed))
                        .counterOffer(objectMapper.valueToTree(Map.of(
                                "suggestedCounter", estimatedMedian > 0 ? (estimatedMedian * 1.1) : 0,
                                "estimatedMin", parsed.getOrDefault("estimatedMin", 0),
                                "estimatedMax", parsed.getOrDefault("estimatedMax", 0),
                                "tips", parsed.getOrDefault("negotiationTips", List.of())
                        )))
                        .build();

                salaryNegotiationRepository.save(negotiation);
                log.info("Saved salary negotiation strategy for user {} and company {}", userId, request.getCompany());
            } catch (Exception persistenceEx) {
                log.error("Failed to persist salary negotiation strategy: {}", persistenceEx.getMessage());
            }

            return AgentDtos.SalaryAnalysisResponse.builder()
                    .estimatedMin(getInt(parsed, "estimatedMin"))
                    .estimatedMax(getInt(parsed, "estimatedMax"))
                    .estimatedMedian(getInt(parsed, "estimatedMedian"))
                    .currency((String) parsed.getOrDefault("currency", "USD"))
                    .confidence((String) parsed.getOrDefault("confidence", "MEDIUM"))
                    .negotiationTips(toStringList(parsed.get("negotiationTips")))
                    .dataPoints(toStringList(parsed.get("dataPoints")))
                    .analysis((String) parsed.getOrDefault("analysis", response))
                    .aiLogId(aiLogId)
                    .build();
        } catch (Exception e) {
            log.error("Salary analysis failed: {}", e.getMessage());
            return AgentDtos.SalaryAnalysisResponse.builder()
                    .analysis("Unable to complete salary analysis. Please try again.")
                    .negotiationTips(List.of("Research market rates on Levels.fyi and Glassdoor"))
                    .build();
        }
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
