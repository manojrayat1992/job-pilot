package com.jobpilot.ai.service;

import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.common.util.JsonExtractor;
import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * LinkedIn Profile Optimizer — Analyzes LinkedIn profile content
 * and generates keyword-optimized rewrites for maximum recruiter visibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkedInOptimizerService {

    private final ChatClient.Builder chatClientBuilder;
    private final CreditService creditService;
    private final AiLogRepository aiLogRepository;

    public Map<String, Object> optimizeProfile(String headline, String summary,
                                                String experience, String targetRole) {
        UUID userId = SecurityUtils.getCurrentUserId();
        creditService.requireCredits();

        String prompt = String.format("""
                You are a LinkedIn optimization expert who helps professionals maximize recruiter visibility.
                
                Analyze this LinkedIn profile and provide optimized versions:
                
                Current Headline: %s
                Current Summary: %s
                Experience Snippet: %s
                Target Role: %s
                
                Return a JSON object with:
                {
                  "optimizedHeadline": "New keyword-rich headline (max 220 chars)",
                  "optimizedSummary": "Rewritten summary with SEO keywords (300-500 words)",
                  "keywordDensityScore": <1-100>,
                  "recruiterVisibilityScore": <1-100>,
                  "missingKeywords": ["keyword1", "keyword2"],
                  "addedKeywords": ["keyword1", "keyword2"],
                  "sectionTips": [
                    {"section": "Headline", "tip": "specific improvement"},
                    {"section": "About", "tip": "specific improvement"},
                    {"section": "Experience", "tip": "specific improvement"},
                    {"section": "Skills", "tip": "specific improvement"}
                  ],
                  "competitiveAnalysis": "2-3 sentences comparing to top profiles in the target role"
                }
                
                Focus on ATS-compatible keywords that recruiters actually search for.
                """,
                headline != null ? headline : "Not provided",
                summary != null ? summary : "Not provided",
                experience != null ? experience.substring(0, Math.min(1000, experience.length())) : "Not provided",
                targetRole != null ? targetRole : "Not specified");

        long startTime = System.currentTimeMillis();
        try {
            ChatClient client = chatClientBuilder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .withMaxTokens(2000)
                            .withTemperature(0.6)
                            .build())
                    .build();

            String response = client.prompt()
                    .system(AgentPersonality.GHOSTWRITER.getSystemPrompt())
                    .user(prompt)
                    .call().content();

            creditService.consumeCredit();
            Map<String, Object> result = JsonExtractor.extractJson(response);

            // Log AI Usage & Persist Optimization
            try {
                AiLog aiLog = AiLog.builder()
                        .userId(userId)
                        .feature("linkedin_optimizer")
                        .promptTemplate("linkedin-optimize")
                        .requestContent(prompt)
                        .responseContent(response)
                        .provider("OpenAI")
                        .model(AgentPersonality.GHOSTWRITER.getPreferredModel())
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .status("SUCCESS")
                        .build();
                aiLogRepository.save(aiLog);
                log.info("Saved LinkedIn profile optimization logs in database for user {}", userId);
            } catch (Exception logEx) {
                log.error("Failed to log LinkedIn profile optimization: {}", logEx.getMessage());
            }

            return result;
        } catch (Exception e) {
            log.error("LinkedIn optimization failed: {}", e.getMessage());
            return Map.of("error", "Unable to optimize profile. Please try again.",
                    "rawResponse", true);
        }
    }
}
