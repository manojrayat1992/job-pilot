package com.jobpilot.ai.dto;

import lombok.Data;
import com.jobpilot.ai.dto.AgentResearchResponse.RankedJob;
import java.util.List;
import java.util.UUID;

@Data
public class AgentAutoApplyRequest {
    @jakarta.validation.constraints.NotEmpty
    @jakarta.validation.constraints.Size(max = 25)
    private List<@jakarta.validation.Valid @jakarta.validation.constraints.NotNull RankedJob> jobs;
    private UUID resumeId;
}
