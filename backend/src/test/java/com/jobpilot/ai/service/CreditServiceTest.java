package com.jobpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.entity.ModelVersion;
import com.jobpilot.ai.workflow.*;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.*;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.repository.ResumeRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL locks/transactions and shared gateway; providers are mocked. */
@EnabledIfEnvironmentVariable(named="JOBPILOT_REVIEW_DB_URL", matches=".+")
class CreditServiceTest {
    static DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    CreditService credits;
    final UUID user=UUID.randomUUID(), foreign=UUID.randomUUID();
    final ChatModel provider=mock(ChatModel.class);
    final ModelRoutingService routing=mock(ModelRoutingService.class);
    ChatGenerationService chat;
    @BeforeAll static void migrate() {
        ds=new DriverManagerDataSource(System.getenv("JOBPILOT_REVIEW_DB_URL"),"review_test","local_review_test_only");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds); credits=new CreditService(jdbc,manager);
        for (UUID id:List.of(user,foreign)) jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES(?,?,?,?)",id,id+"@credit.test","test-only","Credit Test");
        authenticate(user);
        when(routing.getTargetModel()).thenReturn(ModelVersion.builder().modelPath("requested-model").build());
        chat=new ChatGenerationService(ChatClient.builder(provider),routing,credits);
        reply("Saved answer",new DefaultUsage(10L,20L));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); jdbc.update("DELETE FROM users WHERE id IN (?,?)",user,foreign); }
    void authenticate(UUID id) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"test@credit.test","USER"),null)); }
    CreditService.Ticket reserve(String key) { return credits.reserve(key,"same-inputs","test","test-model"); }
    Map<String,Object> row(UUID id) { return jdbc.queryForMap("SELECT * FROM ai_credit_ledger WHERE id=?",id); }
    int attempts() { return jdbc.queryForObject("SELECT count(*) FROM ai_credit_ledger WHERE user_id=?",Integer.class,user); }
    void reply(String text, Usage usage) {
        when(provider.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().withModel("reported-model").withUsage(usage).build()));
    }
    @Test void newlyCreatedUsersHaveAccountsAndProfileSavesCannotRestoreSpendableCredits() {
        assertEquals(5,credits.getRemainingCredits()); reserve("one");
        jdbc.update("UPDATE users SET credits=100,full_name='Edited profile' WHERE id=?",user);
        assertEquals(4,credits.getRemainingCredits());
    }
    @Test void onlyOneConcurrentRequestCanSpendTheLastCredit() throws Exception {
        jdbc.update("UPDATE ai_credit_accounts SET available=1 WHERE user_id=?",user);
        var barrier=new CyclicBarrier(8);
        try (var executor=Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<8;i++) futures.add(executor.submit(()->{
                authenticate(user);
                try { barrier.await(10,TimeUnit.SECONDS); reserve(UUID.randomUUID().toString()); return true; }
                catch(CreditExhaustedException e) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            int successes=0; for(var f:futures) if(f.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
        assertEquals(0,credits.getRemainingCredits()); assertEquals(1,attempts());
    }
    @Test void duplicateInFlightOperationIsRejectedWithoutAnotherDebit() {
        reserve("same"); assertThrows(CreditConflictException.class,()->reserve("same")); assertEquals(4,credits.getRemainingCredits());
    }
    @Test void successfulSettlementAndRefundCleanupAreIdempotent() {
        var ticket=reserve("one"); credits.settle(ticket,"actual","saved",10L,20L,45);
        credits.settle(ticket,"actual","different",10L,20L,45); credits.release(ticket);
        assertEquals(4,credits.getRemainingCredits()); assertEquals("saved",row(ticket.id()).get("cached_content"));
        assertTrue(reserve("one").cached()); assertEquals(1,attempts());
    }
    @Test void failedAttemptRefundsOnceAndRetryGetsANewAuditableAttempt() {
        var first=reserve("retry"); credits.release(first); credits.release(first); assertEquals(5,credits.getRemainingCredits());
        var second=reserve("retry"); assertNotEquals(first.id(),second.id()); assertEquals(2,attempts()); assertEquals(4,credits.getRemainingCredits());
        assertThrows(CreditConflictException.class,()->credits.settle(first,"late","late",null,null,0));
    }
    @Test void changingInputsWithSameOperationKeyIsRejected() {
        reserve("one"); assertThrows(CreditConflictException.class,()->credits.reserve("one","changed","test","model")); assertEquals(1,attempts());
    }
    @Test void unlimitedRolesAreConsistentAndStillRecordUsage() {
        for(String role:List.of("PRO","ADMIN","ENTERPRISE")) {
            jdbc.update("UPDATE users SET role=? WHERE id=?",role,user);
            jdbc.update("UPDATE ai_credit_accounts SET available=0 WHERE user_id=?",user);
            var ticket=reserve(role); credits.settle(ticket,"model",null,null,null,1);
            assertEquals(Integer.MAX_VALUE,credits.getRemainingCredits()); assertEquals(0,row(ticket.id()).get("credits"));
        }
        assertEquals(3,attempts());
    }
    @Test void disabledUsersCannotReserve() {
        jdbc.update("UPDATE users SET enabled=false WHERE id=?",user);
        assertThrows(AccessDeniedException.class,()->reserve("no")); assertEquals(0,attempts());
    }
    @Test void foreignUsersCannotSettleReleaseOrReadUsageForAnotherAccount() {
        var ticket=reserve("one"); authenticate(foreign);
        assertThrows(ResourceNotFoundException.class,()->credits.release(ticket));
        assertThrows(ResourceNotFoundException.class,()->credits.settle(ticket,"model",null,null,null,0));
        var usage=new UsageService(jdbc,credits).getUserUsageStats();
        assertTrue(((List<?>)usage.get("recentActivity")).isEmpty());
        assertEquals(5,credits.getRemainingCredits());
    }
    @Test void grantsRequirePositiveAmountAndAStableReference() {
        assertThrows(BadRequestException.class,()->credits.addCredits(user,0,"one"));
        credits.addCredits(user,10,"purchase-one"); credits.addCredits(user,10,"purchase-one");
        assertEquals(15,credits.getRemainingCredits());
        assertThrows(CreditConflictException.class,()->credits.addCredits(user,20,"purchase-one"));
    }
    @Test void callerTransactionRollbackDoesNotEraseSuccessfulRemoteUsage() {
        new TransactionTemplate(manager).executeWithoutResult(status->{chat.generate("call");status.setRollbackOnly();});
        assertEquals(4,credits.getRemainingCredits()); assertEquals(1,attempts());
    }
    @Test void providerFailureAndEmptyResponseRefundCredits() {
        when(provider.call(any(Prompt.class))).thenThrow(new IllegalStateException("timeout"));
        assertThrows(AiServiceException.class,()->chat.generate("first")); assertEquals(5,credits.getRemainingCredits());
        reply(" ",new EmptyUsage()); assertThrows(AiServiceException.class,()->chat.generate("second"));
        assertEquals(5,credits.getRemainingCredits()); assertEquals(2,attempts());
    }
    @Test void zeroBalancePreventsProviderDispatch() {
        jdbc.update("UPDATE ai_credit_accounts SET available=0 WHERE user_id=?",user);
        assertThrows(CreditExhaustedException.class,()->chat.generate("no")); verify(provider,never()).call(any(Prompt.class)); assertEquals(0,attempts());
    }
    @Test void actualReportedTokensModelAndUnknownUsageRemainDistinct() {
        chat.generate("one"); reply("No usage report",new EmptyUsage()); chat.generate("two");
        var usage=new UsageService(jdbc,credits).getUserUsageStats();
        var summary=(Map<?,?>)usage.get("summary");
        assertEquals(30L,((Number)summary.get("totalTokens")).longValue());
        assertEquals(1L,((Number)summary.get("unknownTokenRequests")).longValue());
        assertEquals("reported-model",jdbc.queryForObject("SELECT model FROM ai_credit_ledger WHERE user_id=? LIMIT 1",String.class,user));
    }
    @Test void persistedModelResponseSurvivesGatewayReconstructionAndZeroBalance() {
        UUID run=UUID.randomUUID(); var first=chat.generateWorkflow(run,"report","system","prompt",1500,.7);
        jdbc.update("UPDATE ai_credit_accounts SET available=0 WHERE user_id=?",user);
        when(routing.getTargetModel()).thenThrow(new FeatureUnavailableException("Routes unavailable"));
        var recovered=new ChatGenerationService(ChatClient.builder(provider),routing,new CreditService(jdbc,manager));
        assertEquals(first,recovered.generateWorkflow(run,"report","system","prompt",1500,.7));
        verify(provider).call(any(Prompt.class)); assertEquals(1,attempts());
    }
    @Test void checkpointFailureReusesProviderResultWithoutChargingTwice() {
        var store=spy(new WorkflowStore(jdbc,new ObjectMapper(),manager));
        var definition=new WorkflowDefinition(mock(ResumeRepository.class),mock(JobRepository.class));
        var service=new WorkflowService(store,definition,mock(WorkflowActivities.class),chat);
        doThrow(new IllegalStateException("checkpoint unavailable")).doCallRealMethod().when(store)
                .completeStep(any(),any(),any(),anyString(),anyMap(),anyString(),any(Runnable.class));
        try {
            var failed=service.start(AgentDtos.OrchestrationRequest.builder().goal("Review my next step").workflowType("CUSTOM").build(),(name,data)->{});
            assertEquals("FAILED",failed.getStatus()); assertEquals(4,credits.getRemainingCredits());
            var done=service.retry(UUID.fromString(failed.getWorkflowId()),(name,data)->{});
            assertEquals("COMPLETED",done.getStatus()); assertEquals("Saved answer",done.getFinalReport());
            verify(provider).call(any(Prompt.class)); assertEquals(1,attempts()); assertEquals(4,credits.getRemainingCredits());
        } finally {service.shutdown();}
    }
    @Test void settlementStorageFailureKeepsReservationInsteadOfRefundingSuccessfulProviderWork() {
        var failing=spy(credits);
        doThrow(new IllegalStateException("database unavailable")).when(failing).settle(any(),anyString(),any(),any(),any(),anyLong());
        var gateway=new ChatGenerationService(ChatClient.builder(provider),routing,failing);
        assertThrows(IllegalStateException.class,()->gateway.generate("one")); assertEquals(4,credits.getRemainingCredits());
        assertEquals("RESERVED",jdbc.queryForObject("SELECT status FROM ai_credit_ledger WHERE user_id=?",String.class,user));
        verify(failing,never()).release(any());
    }
    @Test void expiredReservationIsHeldAndRequiresAdminReconciliationBeforeRetry() {
        var ticket=reserve("interrupted");
        jdbc.update("UPDATE ai_credit_ledger SET created_at=now()-interval '11 minutes' WHERE id=?",ticket.id());
        assertThrows(CreditConflictException.class,()->reserve("interrupted")); assertEquals(4,credits.getRemainingCredits());
        var stats=new UsageService(jdbc,credits).getUserUsageStats();
        assertEquals("UNKNOWN",((Map<?,?>)((List<?>)stats.get("recentActivity")).getFirst()).get("status"));
        assertThrows(AccessDeniedException.class,()->credits.releaseInterrupted(ticket.id(),"Reviewed"));
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",foreign); authenticate(foreign);
        credits.releaseInterrupted(ticket.id(),"Worker stopped; provider checked; refund approved");
        assertEquals(foreign,row(ticket.id()).get("reconciled_by"));
        assertThrows(CreditConflictException.class,()->credits.releaseInterrupted(ticket.id(),"Repeat"));
        authenticate(user); assertEquals(5,credits.getRemainingCredits()); reserve("interrupted"); assertEquals(4,credits.getRemainingCredits());
    }
    @Test void audioUsesTheSameBalanceAndRefundsFailedTranscription() {
        var client=new org.springframework.web.client.RestTemplate();
        var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(client).build();
        var audio=new AudioService(client,"test-key","whisper-1","tts-1","alloy",credits);
        var file=new org.springframework.mock.web.MockMultipartFile("file","audio.webm","audio/webm",new byte[]{1});
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.openai.com/v1/audio/speech"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(new byte[]{1,2},org.springframework.http.MediaType.parseMediaType("audio/mpeg")));
        assertThrows(AiServiceException.class,()->audio.transcribe(file)); assertEquals(5,credits.getRemainingCredits());
        jdbc.update("UPDATE ai_credit_accounts SET available=1 WHERE user_id=?",user);
        assertArrayEquals(new byte[]{1,2},audio.speak("Hello")); assertEquals(0,credits.getRemainingCredits());
        assertThrows(CreditExhaustedException.class,()->audio.transcribe(file));
        server.verify(); assertEquals(2,attempts());
    }
    @Test void missingAudioCredentialsDoNotReserveCredits() {
        var audio=new AudioService(new org.springframework.web.client.RestTemplate(),"","whisper-1","tts-1","alloy",credits);
        assertThrows(FeatureUnavailableException.class,()->audio.speak("Hello")); assertEquals(0,attempts());
    }
    @Test void administratorsCannotReleaseRecentOrSettledReservations() {
        var ticket=reserve("recent"); jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",user);
        assertThrows(CreditConflictException.class,()->credits.releaseInterrupted(ticket.id(),"too soon"));
        assertThrows(BadRequestException.class,()->credits.releaseInterrupted(ticket.id()," "));
        credits.settle(ticket,"model",null,null,null,1);
        assertThrows(CreditConflictException.class,()->credits.releaseInterrupted(ticket.id(),"settled"));
    }
}
