package com.jobpilot.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete multi-agent workflow execution with its steps,
 * status tracking, and aggregated results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkflow {

    @Builder.Default
    private String workflowId = UUID.randomUUID().toString();

    private String workflowType;  // AUTO_PILOT, INTERVIEW_BLITZ, STEALTH_NETWORKING, CUSTOM
    private String userGoal;
    private UUID userId;

    @Builder.Default
    private List<AgentStep> steps = new ArrayList<>();

    @Builder.Default
    private List<String> thoughts = new ArrayList<>();

    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.PLANNING;

    @Builder.Default
    private java.util.Map<String, Object> artifacts = new java.util.HashMap<>();

    private Instant startedAt;
    private Instant completedAt;
    private String finalReport;

    public enum WorkflowStatus {
        PLANNING,
        EXECUTING,
        COMPLETED,
        FAILED,
        PARTIAL
    }

    public void addThought(AgentPersonality agent, String thought) {
        thoughts.add(String.format("[%s %s] %s", agent.getEmoji(), agent.getDisplayName(), thought));
    }

    public void addStep(AgentStep step) {
        step.setId("step-" + steps.size());
        steps.add(step);
    }

    public long getTotalDurationMs() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }
}
