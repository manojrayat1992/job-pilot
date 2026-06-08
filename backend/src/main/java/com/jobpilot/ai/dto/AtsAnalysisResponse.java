package com.jobpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsAnalysisResponse {
    private int score;
    private List<AtsIssue> issues;
    private List<String> suggestions;
    private Map<String, Object> structuralFlags; // e.g., { "multiColumn": true, "hasGraphics": false }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtsIssue {
        private String category; // 'Structural', 'Content', 'Formatting'
        private String description;
        private String severity; // 'High', 'Medium', 'Low'
    }
}
