package com.jobpilot.ai.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class AgentAutoApplyRequest {
    private List<AgentResearchResponse.RankedJob> jobs;
    private UUID resumeId;
}
