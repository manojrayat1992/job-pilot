package com.jobpilot.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponse {
    private UUID sessionId;
    private int currentTurn;
    private int maxTurns;
    private String status;
    private InterviewMessage latestMessage;
    private List<InterviewMessage> history;
    private FeedbackReport feedback;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewMessage {
        private String role; // 'user', 'assistant'
        private String persona; // 'TECH_LEAD', 'HIRING_MANAGER', 'HR'
        private String content;
        private String timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackReport {
        private int overallScore;
        private String executiveSummary;
        private List<PersonaScore> personaScores;
        private List<String> strengths;
        private List<String> weaknesses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonaScore {
        private String persona;
        private int score;
        private String comment;
    }
}
