package com.jobpilot.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.workflow.WorkflowService;
import com.jobpilot.common.exception.FeatureUnavailableException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streaming is a view of persisted work; disconnecting does not discard the run. */
@Service
@RequiredArgsConstructor
public class StreamingAgentOrchestrator {
    private final WorkflowService workflows;
    private final ObjectMapper mapper;
    private final ExecutorService executor = new DelegatingSecurityContextExecutorService(
            new ThreadPoolExecutor(4, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32)));
    @PreDestroy public void shutdown() { executor.shutdownNow(); }
    public SseEmitter orchestrateStream(AgentDtos.OrchestrationRequest request) {
        var run = workflows.prepare(request); // Persist before dispatch, including when the executor queue is full.
        return stream(run.id(), false);
    }
    public SseEmitter retryStream(UUID id) {
        workflows.get(id); // Reject foreign IDs before asynchronous execution.
        return stream(id, true);
    }
    private SseEmitter stream(UUID id, boolean retry) {
        var emitter = new SseEmitter(120_000L);
        var disconnected = new AtomicBoolean();
        emitter.onTimeout(() -> { disconnected.set(true); emitter.complete(); });
        emitter.onError(e -> disconnected.set(true));
        emitter.onCompletion(() -> disconnected.set(true));
        try {
            executor.execute(() -> {
                try {
                    var result = workflows.execute(id, retry, (name, data) -> send(emitter, disconnected, name, data));
                    send(emitter, disconnected, "workflow_complete", result);
                    emitter.complete();
                } catch (Exception e) {
                    send(emitter, disconnected, "error", Map.of("workflowId", id.toString(), "message", "Open saved workflow history to check progress or retry."));
                    emitter.complete();
                }
            });
        } catch (RejectedExecutionException e) {
            throw new FeatureUnavailableException("Workflow queue is full. Your run is saved in workflow history and can be resumed there.");
        }
        return emitter;
    }
    private void send(SseEmitter emitter, AtomicBoolean disconnected, String name, Object data) {
        if (disconnected.get()) return;
        Map<String, Object> payload = mapper.convertValue(data, new TypeReference<>() {});
        payload.put("type", name);
        try { emitter.send(SseEmitter.event().name(name).data(payload)); }
        catch (IOException | IllegalStateException e) { disconnected.set(true); }
    }
}
