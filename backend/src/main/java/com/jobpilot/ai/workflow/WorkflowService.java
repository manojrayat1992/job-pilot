package com.jobpilot.ai.workflow;

import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.service.ChatGenerationService;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.CreditExhaustedException;
import com.jobpilot.common.util.SecurityUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class WorkflowService {
    private final WorkflowStore store;
    private final WorkflowDefinition definition;
    private final WorkflowActivities activities;
    private final ChatGenerationService chat;
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(2);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(jobContext|emailDraft|outputs)}}");

    public WorkflowService(WorkflowStore store, WorkflowDefinition definition, WorkflowActivities activities,
                           ChatGenerationService chat) {
        this.store = store; this.definition = definition; this.activities = activities; this.chat = chat;
    }
    @PreDestroy public void shutdown() { heartbeats.shutdownNow(); }

    public WorkflowStore.Run prepare(AgentDtos.OrchestrationRequest request) {
        if (request.getGoal() == null || request.getGoal().isBlank() || request.getGoal().length() > 4000)
            throw new BadRequestException("A goal between 1 and 4000 characters is required");
        UUID user = SecurityUtils.getCurrentUserId();
        UUID requestId = request.getRequestId() == null ? UUID.randomUUID() : request.getRequestId();
        var serialized = store.requestMap(request);
        var existing = store.findRequest(user, requestId);
        if (existing.isPresent()) { store.checkRequest(existing.get(), serialized); return existing.get(); }
        return store.create(user, requestId, serialized, definition.plan(request, user));
    }
    public AgentDtos.OrchestrationResponse start(AgentDtos.OrchestrationRequest request, BiConsumer<String, Object> events) {
        var run = prepare(request);
        return execute(run.id(), false, events);
    }
    public AgentDtos.OrchestrationResponse retry(UUID id, BiConsumer<String, Object> events) { return execute(id, true, events); }
    public AgentDtos.OrchestrationResponse get(UUID id) { return store.response(id, SecurityUtils.getCurrentUserId()); }
    public List<WorkflowStore.Summary> history(int page) { return store.list(SecurityUtils.getCurrentUserId(), page); }

    public AgentDtos.OrchestrationResponse execute(UUID id, boolean retry, BiConsumer<String, Object> events) {
        UUID user = SecurityUtils.getCurrentUserId();
        var run = store.load(id, user); // Ownership precedes lease acquisition or any billable operation.
        if (run.status().equals("COMPLETED") || (!retry && !run.status().equals("PENDING"))) return store.response(id, user);
        UUID token;
        try { token = store.claim(id, user); }
        catch (WorkflowConflictException e) {
            if (retry) throw e;
            return store.response(id, user); // A repeated start observes the existing execution.
        }
        if (token == null) return store.response(id, user);
        var leaseLost = new AtomicBoolean(false);
        var heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            try { if (!store.heartbeat(id, token)) leaseLost.set(true); }
            catch (Exception e) { leaseLost.set(true); log.warn("Workflow heartbeat failed for {}", id); }
        }, 20, 20, TimeUnit.SECONDS);
        String currentStep = null;
        try {
            emit(events, "workflow_start", Map.of("workflowId", id.toString(), "workflowType", run.type()));
            for (var planned : run.steps()) {
                if (planned.status().equals("COMPLETED")) continue;
                if (leaseLost.get()) throw new WorkflowConflictException("Execution lease was lost");
                currentStep = planned.key();
                var state = store.load(id, user);
                var step = store.begin(id, user, token, currentStep, resolvePrompt(planned.template(), state));
                if (step.status().equals("COMPLETED")) continue;
                emit(events, "thought", Map.of("agent", step.agent(), "thought", step.action()));
                if (step.kind().equals("SEARCH")) {
                    var output = activities.search(state);
                    store.completeStep(id, user, token, step.key(), output, null, () -> activities.saveJobs(user, output));
                } else {
                    var result = chat.generateWorkflow(id, step.key(), step.system(), step.prompt(), 1500, 0.7);
                    store.completeStep(id, user, token, step.key(), Map.of("text", result.content()), result.model(), () -> {});
                }
                emit(events, "step_complete", Map.of("workflowId", id.toString(), "stepId", step.key()));
            }
            store.finish(id, user, token);
        } catch (Exception e) {
            log.warn("Workflow {} stopped at step {} ({})", id, currentStep, e.getClass().getSimpleName());
            String message = e instanceof com.jobpilot.common.exception.CreditConflictException ? e.getMessage() : e instanceof CreditExhaustedException ? "Insufficient credits to continue this step." :
                    "This step could not finish. Saved outputs are preserved; you can retry from workflow history.";
            try { store.fail(id, user, token, currentStep, message); }
            catch (Exception saveError) { log.error("Could not persist failure for workflow {}; lease expiry will allow recovery", id); }
        } finally { heartbeat.cancel(false); }
        return store.response(id, user);
    }
    private static void emit(BiConsumer<String, Object> events, String name, Object value) {
        try { events.accept(name, value); }
        catch (RuntimeException ignored) { /* Transport loss must not discard durable outputs or stop checkpointing. */ }
    }
    private String resolvePrompt(String template, WorkflowStore.Run run) {
        var values = new HashMap<String, String>();
        var outputs = new StringBuilder();
        for (var step : run.steps()) if (step.status().equals("COMPLETED")) {
            String text = step.output().getOrDefault("text", step.output()).toString();
            values.put(step.artifact(), text);
            if (step.kind().equals("SEARCH")) values.put("jobContext", text);
            outputs.append(step.action()).append(": ").append(text).append("\n");
        }
        values.put("outputs", WorkflowDefinition.clip(outputs.toString(), 24000));
        values.putIfAbsent("jobContext", run.context().getOrDefault("job", "No jobs found").toString());
        // Single-pass replacement prevents generated content from injecting new template substitutions.
        Matcher matcher = PLACEHOLDER.matcher(template);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(values.getOrDefault(match.group(1), "")));
    }
}
