package com.jobpilot.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the multi-agent orchestration API.
 */
public class AgentDtos {

    // ─── Request ───

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationRequest {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 4000)
        private String goal;            // Natural language goal from user
        private String workflowType;    // Optional: AUTO_PILOT, INTERVIEW_BLITZ, STEALTH_NETWORKING
        private UUID requestId;         // Client key for deduplicating repeated start requests
        private UUID resumeId;          // Optional context
        private UUID jobId;             // Optional context
        private String targetCompany;   // Optional context
        @jakarta.validation.constraints.Size(max = 200)
        private String targetRole;      // Optional context
        private Map<String, Object> extra; // Any additional parameters
    }

    // ─── Response ───

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationResponse {
        private String workflowId;
        private String workflowType;
        private String status;
        private String userGoal;
        private java.time.Instant createdAt;
        private boolean retryable;
        private String error;
        private String plan;
        private List<StepResult> steps;
        private List<String> thoughts;
        private String finalReport;
        private long durationMs;
        private Map<String, Object> artifacts;  // Generated content (resume, cover letter, emails, etc.)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private String stepId;
        private String agent;
        private String agentEmoji;
        private String action;
        private String status;
        private int attempts;
        private String error;
        private String model;
        private String summary;
        private long durationMs;
    }

    // ─── Salary Analysis ───

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryAnalysisRequest {
        private String role;
        private String company;
        private String location;
        private int yearsExperience;
        private UUID resumeId;
        private java.math.BigDecimal offerAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryAnalysisResponse {
        private int estimatedMin;
        private int estimatedMax;
        private int estimatedMedian;
        private String currency;
        private String confidence;
        private List<String> negotiationTips;
        private List<String> dataPoints;
        private String analysis;
        private UUID aiLogId;
    }

    // ─── Outreach ───

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutreachRequest {
        private String targetCompany;
        private String targetRole;
        private UUID resumeId;
        private String contactName;
        private String contactTitle;
        private String contactEmail;
        private String outreachType; // COLD_EMAIL, LINKEDIN_MESSAGE, FOLLOW_UP
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutreachResponse {
        private String subject;
        private String body;
        private String linkedInMessage;
        private List<FollowUp> followUpSequence;
        private List<String> tips;
        private UUID aiLogId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowUp {
        private int dayAfter;
        private String subject;
        private String body;
        private String channel; // EMAIL, LINKEDIN
    }

    // ─── Career Gap Analysis ───

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerGapRequest {
        private UUID resumeId;
        private UUID jobId;
        private String targetRole;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerGapResponse {
        private int overallReadiness;
        private List<SkillGap> gaps;
        private List<String> strengths;
        private String strategicAdvice;
        private List<LearningResource> suggestedResources;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillGap {
        private String skill;
        private String category; // TECHNICAL, LEADERSHIP, DOMAIN, SOFT_SKILL
        private int currentLevel;  // 0-100
        private int requiredLevel; // 0-100
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningResource {
        private String title;
        private String provider;
        private String url;
        private String type; // COURSE, CERTIFICATION, PROJECT, BOOK
        private String estimatedTime;
    }
}
