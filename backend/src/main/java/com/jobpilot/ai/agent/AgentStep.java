package com.jobpilot.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a single step in an agent workflow execution plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {

    private String id;
    private AgentPersonality agent;
    private String action;
    private Map<String, Object> params;
    private List<String> dependsOn;
    private StepStatus status;
    private String result;
    private String error;
    private long durationMs;

    public enum StepStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}
