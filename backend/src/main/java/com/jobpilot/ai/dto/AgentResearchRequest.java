package com.jobpilot.ai.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class AgentResearchRequest {
    private String query;
    private UUID resumeId;
}
