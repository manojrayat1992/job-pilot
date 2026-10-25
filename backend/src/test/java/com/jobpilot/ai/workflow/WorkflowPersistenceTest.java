package com.jobpilot.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.service.*;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.*;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.repository.ResumeRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real transactions/concurrency, mocked providers. Use only an empty disposable database. */
@EnabledIfEnvironmentVariable(named = "JOBPILOT_REVIEW_DB_URL", matches = ".+")
class WorkflowPersistenceTest {
    private static DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private WorkflowStore store;
    private WorkflowService service;
    private WorkflowDefinition definition;
    private WorkflowActivities activities;
    private final ChatGenerationService chat = mock(ChatGenerationService.class);
    private final ResumeRepository resumes = mock(ResumeRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final SerpApiClient search = mock(SerpApiClient.class);
    private final UUID user = UUID.randomUUID(), foreign = UUID.randomUUID();
    private static final java.util.function.BiConsumer<String, Object> NO_EVENTS = (event, data) -> {};

    @BeforeAll static void migrate() {
        dataSource = new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"), "review_test", "local_review_test_only");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource);
        for (UUID id : List.of(user, foreign)) jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)", id, id+"@workflow.test", "test-only", "Workflow Test");
        store = new WorkflowStore(jdbc, new ObjectMapper().findAndRegisterModules(), new DataSourceTransactionManager(dataSource));
        definition = new WorkflowDefinition(resumes, jobs);
        activities = new WorkflowActivities(embeddings, jobs, search, jdbc, new com.jobpilot.job.service.JobWriter(jdbc, new ObjectMapper()));
        service = new WorkflowService(store, definition, activities, chat);
        authenticate(user);
    }
    @AfterEach void cleanup() {
        service.shutdown();
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", user, foreign);
    }
    private void authenticate(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"owner@workflow.test","USER"), null));
    }
    private AgentDtos.OrchestrationRequest request(String type) {
        return AgentDtos.OrchestrationRequest.builder().requestId(UUID.randomUUID()).goal("Prepare my next career step").workflowType(type).targetRole("Engineer").build();
    }
    private ChatGenerationService.Result answer(String text) { return new ChatGenerationService.Result(text, "selected-model"); }
    private void alwaysAnswer() { when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7))).thenReturn(answer("Saved output")); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, user); }

    @Test void outputsSurviveNewServiceInstancesAndRepeatedStartDoesNotRegenerate() {
        alwaysAnswer();
        var request = request("CUSTOM");
        var first = service.start(request, NO_EVENTS);
        service.shutdown();
        var freshStore = new WorkflowStore(jdbc, new ObjectMapper(), new DataSourceTransactionManager(dataSource));
        service = new WorkflowService(freshStore, definition, activities, chat);
        var restored = service.get(UUID.fromString(first.getWorkflowId()));
        assertEquals("Saved output", restored.getFinalReport());
        assertEquals("COMPLETED", restored.getStatus());
        assertEquals(first.getWorkflowId(), service.start(request, NO_EVENTS).getWorkflowId());
        assertEquals(first.getWorkflowId(), service.retry(UUID.fromString(first.getWorkflowId()), NO_EVENTS).getWorkflowId());
        assertEquals(1, count("agent_workflow_runs"));
        verify(chat).generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7));
    }
    @Test void failedStepRetryReusesSavedQuestionsAndCompletesRemainingSteps() {
        when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7)))
                .thenReturn(answer("Saved questions")).thenThrow(new AiServiceException("private provider error"))
                .thenReturn(answer("Thank you"), answer("Summary"));
        var failed = service.start(request("INTERVIEW_BLITZ"), NO_EVENTS);
        assertEquals("FAILED", failed.getStatus());
        assertEquals("Saved questions", failed.getArtifacts().get("questions"));
        assertTrue(failed.isRetryable());
        assertFalse(failed.getError().contains("private provider error"));
        assertEquals("PENDING", failed.getSteps().get(2).getStatus());
        var done = service.retry(UUID.fromString(failed.getWorkflowId()), NO_EVENTS);
        assertEquals("COMPLETED", done.getStatus());
        assertEquals("Saved questions", done.getArtifacts().get("questions"));
        assertEquals("Thank you", done.getArtifacts().get("thankYouDraft"));
        assertEquals(List.of(1,2,1), done.getSteps().stream().map(AgentDtos.StepResult::getAttempts).toList());
        verify(chat, times(4)).generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7));
    }
    @Test void reportFailureIsPersistedAndOnlyReportRunsOnRetry() {
        when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7)))
                .thenReturn(answer("Questions"), answer("Thank you"))
                .thenThrow(new AiServiceException("report failed")).thenReturn(answer("Recovered report"));
        var failed = service.start(request("INTERVIEW_BLITZ"), NO_EVENTS);
        assertEquals("FAILED", failed.getStatus());
        assertNull(failed.getFinalReport());
        var recovered = service.retry(UUID.fromString(failed.getWorkflowId()), NO_EVENTS);
        assertEquals("Recovered report", recovered.getFinalReport());
        assertEquals(List.of(1,1,2), recovered.getSteps().stream().map(AgentDtos.StepResult::getAttempts).toList());
    }
    @Test void foreignUserCannotReadRetryOrListAnotherUsersWorkflow() {
        var run = service.prepare(request("CUSTOM"));
        authenticate(foreign);
        assertThrows(ResourceNotFoundException.class, () -> service.get(run.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.retry(run.id(), NO_EVENTS));
        assertTrue(service.history(0).isEmpty());
        verifyNoInteractions(chat);
        assertEquals("PENDING", store.load(run.id(), user).status());
    }
    @Test void foreignInputReferencesAreRejectedBeforeRunCreation() {
        var request = request("CUSTOM"); request.setResumeId(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class, () -> service.prepare(request));
        request.setResumeId(null); request.setJobId(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class, () -> service.prepare(request));
        assertEquals(0, count("agent_workflow_runs"));
        verifyNoInteractions(chat);
    }
    @Test void sameRequestKeyCannotBeReusedWithChangedInputs() {
        var request = request("CUSTOM"); service.prepare(request); request.setGoal("Different goal");
        assertThrows(WorkflowConflictException.class, () -> service.prepare(request));
        assertEquals(1, count("agent_workflow_runs"));
    }
    @Test void simultaneousRetriesCannotExecuteTheSameStepTwice() throws Exception {
        var run = service.prepare(request("CUSTOM"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7))).thenAnswer(inv -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return answer("Only once");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> { authenticate(user); try { return service.retry(run.id(), NO_EVENTS); } finally { SecurityContextHolder.clearContext(); } });
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(WorkflowConflictException.class, () -> service.retry(run.id(), NO_EVENTS));
            } finally { release.countDown(); }
            assertEquals("COMPLETED", first.get(10, TimeUnit.SECONDS).getStatus());
        }
        verify(chat).generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7));
    }
    @Test void expiredExecutionCanResumeAndOldWorkerCannotCommitOrOverwriteIt() {
        var run = service.prepare(request("INTERVIEW_BLITZ"));
        UUID oldToken = store.claim(run.id(), user);
        var first = store.begin(run.id(), user, oldToken, "questions", "Original prompt");
        store.completeStep(run.id(), user, oldToken, first.key(), Map.of("text", "Durable questions"), "old-model", () -> {});
        store.begin(run.id(), user, oldToken, "thank-you", "Saved thank-you prompt");
        jdbc.update("UPDATE agent_workflow_runs SET lease_until=clock_timestamp()-interval '1 second' WHERE id=?", run.id());
        assertEquals("INTERRUPTED", service.get(run.id()).getStatus());
        assertTrue(service.get(run.id()).isRetryable());
        UUID newToken = store.claim(run.id(), user);
        assertFalse(store.heartbeat(run.id(), oldToken));
        assertThrows(WorkflowConflictException.class, () -> store.completeStep(run.id(), user, oldToken, "thank-you", Map.of("text","Stale"), "old", () -> fail("Must not write")));
        store.fail(run.id(), user, oldToken, "thank-you", "Old worker failed");
        assertEquals(newToken, store.load(run.id(), user).token());
        var retry = store.begin(run.id(), user, newToken, "thank-you", "Changed template should not replace saved prompt");
        assertEquals("Saved thank-you prompt", retry.prompt());
        assertEquals(2, retry.attempts());
        assertEquals("Durable questions", service.get(run.id()).getArtifacts().get("questions"));
    }
    @Test void disconnectDoesNotStopPersistence() {
        alwaysAnswer();
        var result = service.start(request("INTERVIEW_BLITZ"), (event, data) -> { throw new IllegalStateException("browser disconnected"); });
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(3, result.getArtifacts().size());
    }
    @Test void failedSearchCommitRollsBackJobImportsWithTheCheckpoint() {
        var run = service.prepare(request("AUTO_PILOT"));
        UUID token = store.claim(run.id(), user);
        store.begin(run.id(), user, token, "search", "query");
        var output = candidates();
        assertThrows(IllegalStateException.class, () -> store.completeStep(run.id(), user, token, "search", output, null, () -> {
            activities.saveJobs(user, output); throw new IllegalStateException("Checkpoint write failed");
        }));
        assertEquals(0, count("jobs"));
        assertEquals("RUNNING", store.load(run.id(), user).steps().getFirst().status());
        store.completeStep(run.id(), user, token, "search", output, null, () -> activities.saveJobs(user, output));
        store.completeStep(run.id(), user, token, "search", output, null, () -> fail("Completed side effect must not repeat"));
        assertEquals(1, count("jobs"));
        assertEquals(1, ((List<?>) service.get(run.id()).getArtifacts().get("scoutedJobs")).size());
    }
    @Test void autopilotRetryDoesNotRepeatSearchOrCreateDuplicateJobs() {
        when(jobs.findTop5ByUserIdOrderByEmbedding(eq(user), any())).thenReturn(List.of());
        when(embeddings.getEmbedding(anyString())).thenReturn(new float[]{1});
        when(search.searchJobs(anyString())).thenReturn(List.of(Map.of("title","Engineer","company","Test","url","https://example.test/job","description","Actual job")));
        when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7))).thenThrow(new AiServiceException("failure"))
                .thenReturn(answer("Resume suggestions"), answer("Salary guidance"), answer("Report"));
        var result = service.start(request("AUTO_PILOT"), NO_EVENTS);
        assertEquals("FAILED", result.getStatus()); assertEquals(1, count("jobs"));
        assertEquals("COMPLETED", service.retry(UUID.fromString(result.getWorkflowId()), NO_EVENTS).getStatus());
        assertEquals(1, count("jobs"));
        verify(search).searchJobs(anyString()); verify(embeddings).getEmbedding(anyString());
    }
    @Test void historyIsBoundedAndStableAndUserDeletionCascades() {
        for (int i=0; i<21; i++) service.prepare(request("CUSTOM"));
        assertEquals(20, service.history(0).size()); assertEquals(1, service.history(1).size());
        assertThrows(BadRequestException.class, () -> service.history(-1));
        jdbc.update("DELETE FROM users WHERE id=?", user);
        assertEquals(0, count("agent_workflow_runs"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agent_workflow_steps s LEFT JOIN agent_workflow_runs r ON r.id=s.run_id WHERE r.id IS NULL", Integer.class));
    }
    @Test void unsupportedWorkflowDefinitionFailsBeforeProviderWork() {
        var request = request("UNKNOWN");
        assertThrows(BadRequestException.class, () -> service.prepare(request));
        assertEquals(0, count("agent_workflow_runs")); verifyNoInteractions(chat);
    }
    @Test void restartedRunnerResumesExpiredRunUsingPreviouslySavedOutputs() {
        var run = service.prepare(request("INTERVIEW_BLITZ"));
        UUID token = store.claim(run.id(), user);
        store.begin(run.id(), user, token, "questions", "Initial question prompt");
        store.completeStep(run.id(), user, token, "questions", Map.of("text", "Questions saved before restart"), "old-model", () -> {});
        store.begin(run.id(), user, token, "thank-you", "Thank-you prompt saved before restart");
        jdbc.update("UPDATE agent_workflow_runs SET lease_until=clock_timestamp()-interval '1 second' WHERE id=?", run.id());
        service.shutdown();
        var restoredStore = new WorkflowStore(jdbc, new ObjectMapper(), new DataSourceTransactionManager(dataSource));
        service = new WorkflowService(restoredStore, definition, activities, chat);
        alwaysAnswer();
        var recovered = service.retry(run.id(), NO_EVENTS);
        assertEquals("COMPLETED", recovered.getStatus());
        assertEquals("Questions saved before restart", recovered.getArtifacts().get("questions"));
        assertEquals(List.of(1,2,1), recovered.getSteps().stream().map(AgentDtos.StepResult::getAttempts).toList());
        verify(chat).generateWorkflow(any(UUID.class), anyString(), anyString(), eq("Thank-you prompt saved before restart"), eq(1500), eq(0.7));
        verify(chat, times(2)).generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7));
    }
    @Test void networkingDraftRemainsAvailableWhenFollowUpFails() {
        when(chat.generateWorkflow(any(UUID.class), anyString(), anyString(), anyString(), eq(1500), eq(0.7)))
                .thenReturn(answer("Company background"), answer("Original outreach draft"))
                .thenThrow(new AiServiceException("follow-up failed"))
                .thenReturn(answer("Follow-up"), answer("Fit questions"), answer("Summary"));
        var failed = service.start(request("STEALTH_NETWORKING"), NO_EVENTS);
        assertEquals("FAILED", failed.getStatus());
        assertEquals("Original outreach draft", failed.getArtifacts().get("emailDraft"));
        var recovered = service.retry(UUID.fromString(failed.getWorkflowId()), NO_EVENTS);
        assertEquals("COMPLETED", recovered.getStatus());
        assertEquals("Original outreach draft", recovered.getArtifacts().get("emailDraft"));
        assertEquals(List.of(1,1,2,1,1), recovered.getSteps().stream().map(AgentDtos.StepResult::getAttempts).toList());
        verify(chat, times(3)).generateWorkflow(any(UUID.class), anyString(), anyString(), contains("Original outreach draft"), eq(1500), eq(0.7));
    }
    @Test void concurrentStartRequestsCreateOnlyOneRunAndOnePlan() throws Exception {
        var request = request("CUSTOM");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<UUID> prepare = () -> {
                authenticate(user);
                try { barrier.await(10, TimeUnit.SECONDS); return service.prepare(request).id(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(prepare); var second = executor.submit(prepare);
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, count("agent_workflow_runs"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM agent_workflow_steps s JOIN agent_workflow_runs r ON r.id=s.run_id WHERE r.user_id=?", Integer.class, user));
        verifyNoInteractions(chat);
    }
    private Map<String, Object> candidates() {
        var job = new HashMap<String, Object>(Map.of("id", UUID.randomUUID().toString(), "new", true, "title", "Engineer", "company", "Test", "location", "Remote", "url", "https://example.test/job", "description", "Job description"));
        return new HashMap<>(Map.of("jobs", new ArrayList<>(List.of(job))));
    }
}
