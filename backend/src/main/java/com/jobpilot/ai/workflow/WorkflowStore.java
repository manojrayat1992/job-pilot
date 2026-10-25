package com.jobpilot.ai.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Short database transactions, fenced by an expiring execution token. No provider calls here. */
@Service
public class WorkflowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public WorkflowStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.mapper = mapper; this.tx = new TransactionTemplate(manager);
    }
    public record Step(String key, String agent, String action, String kind, String artifact,
                       String system, String template, String prompt, String status, int attempts,
                       Map<String, Object> output, String model, String error, Instant started, Instant completed) {}
    public record Run(UUID id, UUID user, String type, int version, Map<String, Object> request,
                      Map<String, Object> context, String status, UUID token, boolean expired,
                      String error, Instant created, Instant completed, List<Step> steps) {}
    public record Summary(String workflowId, String workflowType, String userGoal, String status,
                          Instant createdAt, boolean retryable, String error) {}

    public Optional<Run> findRequest(UUID user, UUID requestId) {
        var ids = jdbc.queryForList("SELECT id FROM agent_workflow_runs WHERE user_id=? AND request_id=?", UUID.class, user, requestId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(load(ids.getFirst(), user));
    }
    public Run create(UUID user, UUID requestId, Map<String, Object> request, WorkflowDefinition.Plan plan) {
        return tx.execute(ignored -> {
            UUID id = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO agent_workflow_runs(id,user_id,request_id,workflow_type,request_json,context_json,status)
                    VALUES (?,?,?,?,?::jsonb,?::jsonb,'PENDING') ON CONFLICT(user_id,request_id) DO NOTHING
                    """, id, user, requestId, plan.type(), json(request), json(plan.context()));
            if (inserted == 0) {
                Run existing = findRequest(user, requestId).orElseThrow();
                checkRequest(existing, request);
                return existing;
            }
            int position = 0;
            for (var step : plan.steps()) jdbc.update("""
                    INSERT INTO agent_workflow_steps(run_id,step_key,position,agent,action,kind,artifact_key,system_prompt,prompt_template)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, id, step.key(), position++, step.agent().name(), step.action(), step.kind(), step.artifact(), step.agent().getSystemPrompt(), step.prompt());
            return load(id, user);
        });
    }
    public void checkRequest(Run run, Map<String, Object> request) {
        if (!run.request().equals(request)) throw new WorkflowConflictException("This request ID belongs to a different workflow request");
    }
    public Run load(UUID id, UUID user) { return read(id, user, false); }
    private Run read(UUID id, UUID user, boolean lock) {
        var rows = jdbc.query("SELECT *, lease_until <= clock_timestamp() AS expired FROM agent_workflow_runs WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Run(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("workflow_type"), rs.getInt("definition_version"),
                        map(rs.getString("request_json")), map(rs.getString("context_json")), rs.getString("status"), rs.getObject("execution_token", UUID.class),
                        rs.getBoolean("expired"), rs.getString("error"), instant(rs, "created_at"), instant(rs, "completed_at"), List.of()), id, user);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Workflow", "id", id);
        Run r = rows.getFirst();
        var steps = jdbc.query("SELECT * FROM agent_workflow_steps WHERE run_id=? ORDER BY position", (rs, n) -> new Step(
                rs.getString("step_key"), rs.getString("agent"), rs.getString("action"), rs.getString("kind"), rs.getString("artifact_key"),
                rs.getString("system_prompt"), rs.getString("prompt_template"), rs.getString("resolved_prompt"), rs.getString("status"), rs.getInt("attempts"),
                map(rs.getString("output_json")), rs.getString("model"), rs.getString("error"), instant(rs, "started_at"), instant(rs, "completed_at")), id);
        return new Run(r.id(), r.user(), r.type(), r.version(), r.request(), r.context(), r.status(), r.token(), r.expired(), r.error(), r.created(), r.completed(), steps);
    }
    public UUID claim(UUID id, UUID user) {
        return tx.execute(ignored -> {
            Run r = read(id, user, true);
            if (r.version() != 1) throw new WorkflowConflictException("This workflow definition requires a compatible worker");
            if (r.status().equals("COMPLETED")) return null;
            if (r.status().equals("RUNNING") && !r.expired()) throw new WorkflowConflictException("This workflow is already running. Open its saved history for progress.");
            UUID token = UUID.randomUUID();
            jdbc.update("""
                    UPDATE agent_workflow_runs SET status='RUNNING', execution_token=?, lease_until=clock_timestamp()+interval '90 seconds',
                    error=NULL,completed_at=NULL,updated_at=clock_timestamp() WHERE id=?
                    """, token, id);
            // A previously running step has an unknown external outcome; its saved outputs are never guessed.
            jdbc.update("UPDATE agent_workflow_steps SET status='FAILED',error='Previous execution was interrupted before its result was saved' WHERE run_id=? AND status='RUNNING'", id);
            return token;
        });
    }
    public boolean heartbeat(UUID id, UUID token) {
        return jdbc.update("""
                UPDATE agent_workflow_runs SET lease_until=clock_timestamp()+interval '90 seconds',updated_at=clock_timestamp()
                WHERE id=? AND execution_token=? AND status='RUNNING' AND lease_until>clock_timestamp()
                """, id, token) == 1;
    }
    public Step begin(UUID id, UUID user, UUID token, String key, String prompt) {
        return tx.execute(ignored -> {
            Run run = ownedExecution(id, user, token);
            Step step = run.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
            if (step.status().equals("COMPLETED")) return step;
            if (run.steps().stream().takeWhile(s -> !s.key().equals(key)).anyMatch(s -> !s.status().equals("COMPLETED")))
                throw new WorkflowConflictException("Previous workflow steps are not complete");
            jdbc.update("""
                    UPDATE agent_workflow_steps SET status='RUNNING',attempts=attempts+1,error=NULL,started_at=clock_timestamp(),completed_at=NULL,
                    resolved_prompt=COALESCE(resolved_prompt,?) WHERE run_id=? AND step_key=?
                    """, prompt, id, key);
            return load(id, user).steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
        });
    }
    public void completeStep(UUID id, UUID user, UUID token, String key, Map<String, Object> output, String model, Runnable localEffect) {
        tx.executeWithoutResult(ignored -> {
            Run run = ownedExecution(id, user, token);
            Step step = run.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
            if (step.status().equals("COMPLETED")) return;
            if (!step.status().equals("RUNNING")) throw new WorkflowConflictException("Workflow step is not running");
            // Job imports and credit settlement join the checkpoint transaction, preventing repeat local writes on retry.
            localEffect.run();
            jdbc.update("UPDATE agent_workflow_steps SET status='COMPLETED',output_json=?::jsonb,model=?,completed_at=clock_timestamp(),error=NULL WHERE run_id=? AND step_key=?",
                    json(output), model, id, key);
            jdbc.update("UPDATE agent_workflow_runs SET updated_at=clock_timestamp() WHERE id=?", id);
        });
    }
    public void finish(UUID id, UUID user, UUID token) {
        tx.executeWithoutResult(ignored -> {
            Run run = ownedExecution(id, user, token);
            if (run.steps().stream().anyMatch(s -> !s.status().equals("COMPLETED"))) throw new WorkflowConflictException("Workflow has unfinished steps");
            jdbc.update("UPDATE agent_workflow_runs SET status='COMPLETED',execution_token=NULL,lease_until=NULL,completed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=?", id);
        });
    }
    public void fail(UUID id, UUID user, UUID token, String key, String message) {
        tx.executeWithoutResult(ignored -> {
            Run run = read(id, user, true);
            if (!Objects.equals(run.token(), token) || !run.status().equals("RUNNING")) return; // A stale worker cannot overwrite its successor.
            if (key != null) jdbc.update("UPDATE agent_workflow_steps SET status='FAILED',error=?,completed_at=clock_timestamp() WHERE run_id=? AND step_key=? AND status='RUNNING'", message, id, key);
            jdbc.update("UPDATE agent_workflow_runs SET status='FAILED',error=?,execution_token=NULL,lease_until=NULL,completed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE id=?", message, id);
        });
    }
    private Run ownedExecution(UUID id, UUID user, UUID token) {
        Run run = read(id, user, true);
        if (!Objects.equals(run.token(), token) || !run.status().equals("RUNNING") || run.expired())
            throw new WorkflowConflictException("Workflow execution lease expired; reload saved history before retrying");
        return run;
    }
    public List<Summary> list(UUID user, int page) {
        if (page < 0 || page > 100000) throw new com.jobpilot.common.exception.BadRequestException("Invalid history page");
        return jdbc.query("SELECT *, lease_until <= clock_timestamp() AS expired FROM agent_workflow_runs WHERE user_id=? ORDER BY created_at DESC,id LIMIT 20 OFFSET ?",
                (rs, n) -> {
                    String status = effectiveStatus(rs.getString("status"), rs.getBoolean("expired"));
                    return new Summary(rs.getString("id"), rs.getString("workflow_type"), String.valueOf(map(rs.getString("request_json")).get("goal")), status,
                            instant(rs, "created_at"), retryable(status), rs.getString("error"));
                }, user, page * 20);
    }
    public AgentDtos.OrchestrationResponse response(UUID id, UUID user) {
        // Lock the parent while taking a consistent run/step snapshot; never contact a provider here.
        return tx.execute(ignored -> response(read(id, user, true)));
    }
    AgentDtos.OrchestrationResponse response(Run run) {
        var artifacts = new LinkedHashMap<String, Object>();
        var thoughts = new ArrayList<String>();
        var steps = new ArrayList<AgentDtos.StepResult>();
        for (var s : run.steps()) {
            if (s.status().equals("COMPLETED")) {
                if (s.kind().equals("SEARCH")) artifacts.put("scoutedJobs", s.output().getOrDefault("scoutedJobs", List.of()));
                else artifacts.put(s.artifact(), s.output().getOrDefault("text", ""));
            }
            String status = s.status().equals("RUNNING") && run.expired() ? "INTERRUPTED" : s.status();
            thoughts.add("[" + s.agent() + "] " + s.action() + ": " + status);
            var agent = AgentPersonality.valueOf(s.agent());
            steps.add(AgentDtos.StepResult.builder().stepId(s.key()).agent(agent.getDisplayName()).agentEmoji(agent.getEmoji())
                    .action(s.action()).status(status).attempts(s.attempts()).error(s.error()).model(s.model())
                    .durationMs(s.started() == null ? 0 : Duration.between(s.started(), s.completed() == null ? Instant.now() : s.completed()).toMillis()).build());
        }
        String status = effectiveStatus(run.status(), run.expired());
        return AgentDtos.OrchestrationResponse.builder().workflowId(run.id().toString()).workflowType(run.type()).userGoal(String.valueOf(run.request().get("goal")))
                .status(status).retryable(retryable(status)).error(run.error()).createdAt(run.created()).thoughts(thoughts).steps(steps)
                .artifacts(artifacts).finalReport((String) artifacts.get("finalReport"))
                .durationMs(Duration.between(run.created(), run.completed() == null ? Instant.now() : run.completed()).toMillis()).build();
    }
    private static String effectiveStatus(String status, boolean expired) { return status.equals("RUNNING") && expired ? "INTERRUPTED" : status; }
    private static boolean retryable(String status) { return Set.of("PENDING", "FAILED", "INTERRUPTED").contains(status); }
    private static Instant instant(ResultSet rs, String column) throws SQLException { var t = rs.getTimestamp(column); return t == null ? null : t.toInstant(); }
    public Map<String, Object> requestMap(AgentDtos.OrchestrationRequest request) {
        Map<String, Object> result = mapper.convertValue(request, new TypeReference<>() {});
        result.remove("requestId"); return result;
    }
    private Map<String, Object> map(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid saved workflow data", e); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Workflow data could not be serialized", e); }
    }
}
