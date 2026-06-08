package com.jobpilot.ai.agent;

import com.jobpilot.ai.dto.AgentAutoApplyRequest;
import com.jobpilot.ai.dto.AgentResearchRequest;
import com.jobpilot.ai.dto.AgentResearchResponse;
import com.jobpilot.ai.service.AgentService;
import com.jobpilot.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Multi-Agent System", description = "Orchestrate specialized AI agents for complex career goals")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final StreamingAgentOrchestrator streamingOrchestrator;
    private final AgentService agentService;
    private final com.jobpilot.ai.service.CreditService creditService;

    // ─── Multi-Agent Orchestration (Synchronous) ───

    @PostMapping("/orchestrate")
    @Operation(summary = "Execute a multi-agent workflow based on a user's career goal")
    public ResponseEntity<ApiResponse<AgentDtos.OrchestrationResponse>> orchestrate(
            @Valid @RequestBody AgentDtos.OrchestrationRequest request) {
        AgentDtos.OrchestrationResponse response = orchestrator.orchestrate(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Workflow completed"));
    }

    // ─── Multi-Agent Orchestration (SSE Streaming) ───

    @PostMapping(value = "/orchestrate/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Execute a multi-agent workflow with real-time SSE streaming of agent thoughts")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter orchestrateStream(
            @Valid @RequestBody AgentDtos.OrchestrationRequest request) {
        return streamingOrchestrator.orchestrateStream(request);
    }

    @GetMapping("/personalities")
    @Operation(summary = "Get all available agent personalities and their capabilities")
    public ResponseEntity<ApiResponse<AgentPersonality[]>> getPersonalities() {
        return ResponseEntity.ok(ApiResponse.ok(AgentPersonality.values(), "Agent roster loaded"));
    }

    @GetMapping("/credits")
    @Operation(summary = "Get remaining AI credits for the current user")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getCredits() {
        int remaining = creditService.getRemainingCredits();
        return ResponseEntity.ok(ApiResponse.ok(
                java.util.Map.of("remaining", remaining, "unlimited", remaining == Integer.MAX_VALUE),
                "Credits loaded"));
    }

    // ─── Existing Legacy Endpoints (backward compatible) ───

    @PostMapping("/job-research")
    @Operation(summary = "Start a job research agent task")
    public ResponseEntity<ApiResponse<AgentResearchResponse>> researchJobs(
            @RequestBody AgentResearchRequest request) {
        AgentResearchResponse response = agentService.researchJobs(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Job research completed"));
    }

    @PostMapping("/auto-apply")
    @Operation(summary = "Auto-apply to selected jobs")
    public ResponseEntity<ApiResponse<Void>> autoApply(
            @RequestBody AgentAutoApplyRequest request) {
        agentService.autoApply(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Auto-apply process completed"));
    }
}
