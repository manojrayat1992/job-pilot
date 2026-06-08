package com.jobpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.entity.CultureFitAssessment;
import com.jobpilot.ai.repository.CultureFitAssessmentRepository;
import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.common.util.JsonExtractor;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Company Culture Fit Score — Analyzes a company's culture signals
 * against the user's stated values, work preferences, and career goals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CultureFitService {

    private final ChatClient.Builder chatClientBuilder;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final CultureFitAssessmentRepository cultureFitAssessmentRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> assessCultureFit(String companyName, String userValues) {
        UUID userId = SecurityUtils.getCurrentUserId();
        creditService.requireCredits();

        // Pull user onboarding data for context
        User user = userRepository.findById(userId).orElse(null);
        String workMode = user != null && user.getWorkMode() != null ? user.getWorkMode() : "Not specified";
        String targetRole = user != null && user.getTargetRole() != null ? user.getTargetRole() : "Not specified";

        String prompt = String.format("""
                You are a career culture analyst. Assess the culture fit between this candidate and company.
                
                Company: %s
                Candidate's Preferred Work Mode: %s
                Candidate's Target Role: %s
                Candidate's Values: %s
                
                Research what is publicly known about %s's culture (Glassdoor reviews, LinkedIn signals,
                company values page, recent news) and return a JSON object:
                
                {
                  "overallScore": <1-100>,
                  "companyProfile": {
                    "values": ["value1", "value2", "value3"],
                    "workStyle": "Remote/Hybrid/In-Office",
                    "growthRate": "description",
                    "teamSize": "description",
                    "techStack": ["tech1", "tech2"]
                  },
                  "fitBreakdown": [
                    {"dimension": "Work-Life Balance", "score": <1-10>, "explanation": "..."},
                    {"dimension": "Growth Opportunities", "score": <1-10>, "explanation": "..."},
                    {"dimension": "Innovation Culture", "score": <1-10>, "explanation": "..."},
                    {"dimension": "Management Style", "score": <1-10>, "explanation": "..."},
                    {"dimension": "Compensation Philosophy", "score": <1-10>, "explanation": "..."},
                    {"dimension": "Diversity & Inclusion", "score": <1-10>, "explanation": "..."}
                  ],
                  "greenFlags": ["positive signal 1", "positive signal 2"],
                  "redFlags": ["concern 1", "concern 2"],
                  "interviewQuestions": ["Question to ask them about culture", "Another question"],
                  "verdict": "2-3 sentence overall assessment"
                }
                """,
                companyName, workMode, targetRole,
                userValues != null ? userValues : "Growth, impact, work-life balance",
                companyName);

        try {
            ChatClient client = chatClientBuilder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .withMaxTokens(2500)
                            .withTemperature(0.5)
                            .build())
                    .build();

            String response = client.prompt()
                    .system(AgentPersonality.ANALYST.getSystemPrompt())
                    .user(prompt)
                    .call().content();

            creditService.consumeCredit();
            Map<String, Object> resultMap = JsonExtractor.extractJson(response);

            // Persist the assessment results to database
            try {
                Object scoreObj = resultMap.get("overallScore");
                Integer score = 0;
                if (scoreObj instanceof Number n) {
                    score = n.intValue();
                } else if (scoreObj instanceof String s) {
                    score = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                }

                CultureFitAssessment assessment = CultureFitAssessment.builder()
                        .userId(userId)
                        .companyName(companyName)
                        .overallScore(score)
                        .analysis(objectMapper.valueToTree(resultMap))
                        .build();

                cultureFitAssessmentRepository.save(assessment);
                log.info("Saved culture fit assessment for user {} and company {}", userId, companyName);
            } catch (Exception persistenceEx) {
                log.error("Failed to persist culture fit assessment: {}", persistenceEx.getMessage());
            }

            return resultMap;
        } catch (Exception e) {
            log.error("Culture fit assessment failed: {}", e.getMessage());
            return Map.of("error", "Unable to assess culture fit. Please try again.",
                    "overallScore", 0, "rawResponse", true);
        }
    }
}
