package com.jobpilot.ai.workflow;

import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.agent.StreamingAgentOrchestrator;
import com.jobpilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflows;
    private final StreamingAgentOrchestrator streaming;
    @GetMapping public ApiResponse<List<WorkflowStore.Summary>> list(@RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(workflows.history(page));
    }
    @GetMapping("/{id}") public ApiResponse<AgentDtos.OrchestrationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(workflows.get(id));
    }
    @PostMapping("/{id}/retry") public ApiResponse<AgentDtos.OrchestrationResponse> retry(@PathVariable UUID id) {
        return ApiResponse.ok(workflows.retry(id, (event, data) -> {}));
    }
    @PostMapping(value = "/{id}/retry/stream", produces = "text/event-stream")
    public SseEmitter retryStream(@PathVariable UUID id) { return streaming.retryStream(id); }
}
