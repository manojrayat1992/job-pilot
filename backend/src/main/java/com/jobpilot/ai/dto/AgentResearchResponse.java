package com.jobpilot.ai.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentResearchResponse {
    private String report;
    private List<RankedJob> jobs;
    private List<String> thoughts; // To show the agent's process in the UI

    @Data
    @Builder
    public static class RankedJob {
        private String title;
        private String company;
        private String location;
        private String url;
        private Integer matchScore;
        private String matchReason;
        private String source; // "Internal" or "Web"
        private String description;
    }
}
