package com.jobpilot.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.workflow.WorkflowService;
import com.jobpilot.ai.workflow.WorkflowStore;
import com.jobpilot.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The shared runner is exercised against PostgreSQL in WorkflowPersistenceTest. */
class WorkflowFailureTest {
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final AgentOrchestrator sync = new AgentOrchestrator(workflows);
    private final StreamingAgentOrchestrator streaming = new StreamingAgentOrchestrator(workflows, new ObjectMapper().findAndRegisterModules());
    @AfterEach void cleanup() { streaming.shutdown(); }

    @Test void synchronousFacadeKeepsThePersistedFailureStatus() {
        var request = AgentDtos.OrchestrationRequest.builder().goal("Prepare interview").build();
        var failed = AgentDtos.OrchestrationResponse.builder().status("FAILED").artifacts(Map.of("questions", "Saved questions")).build();
        when(workflows.start(eq(request), any())).thenReturn(failed);
        assertSame(failed, sync.orchestrate(request));
    }
    @Test void streamingReturnsSavedArtifactsAndActualFailedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        var run = new WorkflowStore.Run(id, UUID.randomUUID(), "CUSTOM", 1, Map.of(), Map.of(), "PENDING", null, false, null, null, null, List.of());
        when(workflows.prepare(any())).thenReturn(run);
        when(workflows.execute(eq(id), eq(false), any())).thenAnswer(invocation -> {
            java.util.function.BiConsumer<String, Object> events = invocation.getArgument(2);
            events.accept("thought", Map.of("agent", "COACH", "thought", "Generate questions"));
            return AgentDtos.OrchestrationResponse.builder().workflowId(id.toString()).status("FAILED")
                    .artifacts(Map.of("questions", "Saved questions")).retryable(true).build();
        });
        var mvc = MockMvcBuilders.standaloneSetup(new AgentController(sync, streaming, null, null)).build();
        var result = mvc.perform(post("/api/v1/agents/orchestrate/stream").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Help me prepare\",\"workflowType\":\"CUSTOM\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"type\":\"thought\"")))
                .andExpect(content().string(containsString("\"type\":\"workflow_complete\"")))
                .andExpect(content().string(containsString("\"status\":\"FAILED\"")))
                .andExpect(content().string(containsString("\"questions\":\"Saved questions\"")))
                .andExpect(content().string(containsString("\"retryable\":true")));
    }
    @Test void retryRejectsForeignWorkflowBeforeDispatch() {
        UUID id = UUID.randomUUID();
        when(workflows.get(id)).thenThrow(new ResourceNotFoundException("Workflow", "id", id));
        assertThrows(ResourceNotFoundException.class, () -> streaming.retryStream(id));
        verify(workflows, never()).execute(any(), anyBoolean(), any());
    }
    @Test void blankGoalsAreRejectedByTheApi() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AgentController(sync, streaming, null, null)).build();
        mvc.perform(post("/api/v1/agents/orchestrate/stream").contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workflows);
    }
}
