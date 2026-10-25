package com.jobpilot.ai.service;

import com.jobpilot.ai.entity.ModelVersion;
import com.jobpilot.ai.repository.ModelVersionRepository;
import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.exception.FeatureUnavailableException;
import com.jobpilot.common.util.JsonExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatGenerationServiceTest {
    private final ModelVersionRepository repository = mock(ModelVersionRepository.class);
    private final ModelRoutingService routing = new ModelRoutingService(repository);
    private final ChatModel model = mock(ChatModel.class);
    private final CreditService credits = mock(CreditService.class);
    private final ChatGenerationService service = new ChatGenerationService(ChatClient.builder(model), routing, credits);
    @org.junit.jupiter.api.BeforeEach void creditTicket() {
        lenient().when(credits.reserve(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new CreditService.Ticket(java.util.UUID.randomUUID(), false, null, null));
    }

    private ModelVersion route(String name, int weight) {
        return ModelVersion.builder().versionName("alias-" + name).modelPath(name).trafficWeight(weight).isActive(true).build();
    }
    private void reply(String text) {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    @Test void selectedModelReachesSpringAiAndIsReportedWithoutLeakingRequestOptions() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(route("selected-model", 100)), List.of(route("next-model", 100)));
        reply("valid answer");
        assertEquals("selected-model", service.generate("system", "first", 1500, 0.5).model());
        assertEquals("next-model", service.generate("second").model());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertEquals("selected-model", prompts.getAllValues().get(0).getOptions().getModel());
        assertEquals(1500, prompts.getAllValues().get(0).getOptions().getMaxTokens());
        assertEquals("next-model", prompts.getAllValues().get(1).getOptions().getModel());
        assertNull(prompts.getAllValues().get(1).getOptions().getMaxTokens());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void emptyProviderResultsFail(String text) {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(route("model", 1)));
        reply(text);
        assertThrows(AiServiceException.class, () -> service.generate("prompt"));
    }
    @Test void transportFailureIsNotReturnedAsGeneratedText() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(route("model", 1)));
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(AiServiceException.class, () -> service.generate("prompt"));
    }
    @Test void zeroWeightRoutesNeverReceiveTraffic() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(route("disabled", 0), route("enabled", Integer.MAX_VALUE)));
        assertEquals("enabled", routing.getTargetModel().getModelPath());
    }
    @Test void noRoutesOrInvalidWeightsFailBeforeContactingProvider() {
        for (var routes : List.of(List.<ModelVersion>of(), List.of(route("model", 0)), List.of(route("model", -1)), List.of(route(" ", 1)))) {
            when(repository.findByIsActiveTrue()).thenReturn(routes);
            assertThrows(FeatureUnavailableException.class, () -> service.generate("prompt"));
        }
        verify(model, never()).call(any(Prompt.class));
    }
    @Test void sumOfValidWeightsCannotOverflowAnInteger() {
        when(repository.findByIsActiveTrue()).thenReturn(List.of(route("one", Integer.MAX_VALUE), route("two", Integer.MAX_VALUE)));
        assertNotNull(routing.getTargetModel());
    }
    @Test void structuredCallsRejectRawTextButAcceptFencedJson() {
        assertThrows(AiServiceException.class, () -> JsonExtractor.extractRequiredJson("provider error page"));
        assertThrows(AiServiceException.class, () -> JsonExtractor.extractRequiredJson("{}"));
        assertEquals("answer", JsonExtractor.extractRequiredJson("```json\n{\"content\":\"answer\"}\n```").get("content"));
    }
}
