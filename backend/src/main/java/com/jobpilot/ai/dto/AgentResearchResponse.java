package com.jobpilot.ai.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AgentResearchResponse {
    private String report;
    private List<RankedJob> jobs;
    private List<String> thoughts; // To show the agent's process in the UI

    @Data
    @Builder
    public static class RankedJob {
        private UUID id;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        private String title;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        private String company;
        private String location;
        @jakarta.validation.constraints.Size(max = 1000)
        private String url;
        private Integer matchScore;
        private String matchReason;
        private String source; // "Internal" or "Web"
        private String description;
    }
}
