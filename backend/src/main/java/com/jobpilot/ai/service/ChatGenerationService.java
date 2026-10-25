package com.jobpilot.ai.service;

import com.jobpilot.common.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Routing and credit accounting for every chat call, including interviews and research. */
@Service
@RequiredArgsConstructor
public class ChatGenerationService {
    private final ChatClient.Builder chatClientBuilder;
    private final ModelRoutingService routingService;
    private final CreditService credits;

    public record Result(String content, String model) {}
    public Result generate(String userPrompt) { return generate(null, userPrompt, null, null); }
    public Result generate(String systemPrompt, String userPrompt, Integer maxTokens, Double temperature) {
        return generateOperation(UUID.randomUUID().toString(), "chat", false, systemPrompt, userPrompt, maxTokens, temperature);
    }
    public Result generateFeature(String feature, String system, String prompt, Integer maxTokens, Double temperature) {
        return generateOperation(UUID.randomUUID().toString(), feature, false, system, prompt, maxTokens, temperature);
    }
    public Result generateWorkflow(UUID run, String step, String system, String prompt, Integer maxTokens, Double temperature) {
        return generateOperation("workflow:" + run + ":" + step, "workflow:" + step, true, system, prompt, maxTokens, temperature);
    }
    private Result generateOperation(String key, String feature, boolean cache, String system, String prompt, Integer maxTokens, Double temperature) {
        String hash = fingerprint(system, prompt, maxTokens, temperature);
        if (cache) {
            var completed = credits.completed(key, hash);
            if (completed.isPresent()) return new Result(completed.get().content(), completed.get().model());
        }
        var target = routingService.getTargetModel();
        var options = OpenAiChatOptions.builder().withModel(target.getModelPath());
        if (maxTokens != null) options.withMaxTokens(maxTokens);
        if (temperature != null) options.withTemperature(temperature);
        var ticket = credits.reserve(key, hash, feature, target.getModelPath());
        if (ticket.cached()) return new Result(ticket.content(), ticket.model());
        long start = System.nanoTime();
        ChatResponse response;
        String content;
        try {
            var request = chatClientBuilder.build().prompt().options(options.build()).user(prompt);
            if (system != null) request.system(system);
            response = request.call().chatResponse();
            content = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? null : response.getResult().getOutput().getContent();
            if (content == null || content.isBlank()) throw new AiServiceException("Chat provider returned an empty response");
        } catch (Exception e) {
            try { credits.release(ticket); } catch (Exception refundError) { e.addSuppressed(refundError); }
            if (e instanceof AiServiceException ai) throw ai;
            throw new AiServiceException("Chat provider request failed", e);
        }
        var metadata = response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        String reportedModel = metadata == null ? null : metadata.getModel();
        String model = reportedModel == null || reportedModel.isBlank() ? target.getModelPath() : reportedModel;
        // EmptyUsage is a Spring default, not a provider report of zero usage.
        boolean reported = usage != null && !(usage instanceof EmptyUsage);
        // If persistence fails after provider success, retain the reservation for reconciliation.
        credits.settle(ticket, model, cache ? content : null, reported ? usage.getPromptTokens() : null,
                reported ? usage.getGenerationTokens() : null, (System.nanoTime() - start) / 1_000_000);
        return new Result(content, model);
    }
    private String fingerprint(Object... values) {
        try {
            var input = new StringBuilder();
            for (Object value : values) {
                String text = value == null ? "" : value.toString();
                input.append(value == null ? -1 : text.length()).append(':').append(text);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
