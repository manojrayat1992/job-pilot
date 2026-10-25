package com.jobpilot.ai.agent;

import com.jobpilot.ai.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Synchronous facade over the same persisted execution path used by streaming. */
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {
    private final WorkflowService workflows;
    public AgentDtos.OrchestrationResponse orchestrate(AgentDtos.OrchestrationRequest request) {
        return workflows.start(request, (event, data) -> {});
    }
}
