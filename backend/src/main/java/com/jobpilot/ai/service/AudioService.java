package com.jobpilot.ai.service;

import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.exception.FeatureUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

/** OpenAI audio has its own credentials; it never uses the Groq chat key. */
@Service
public class AudioService {
    private static final String BASE_URL = "https://api.openai.com/v1/audio/";
    private final RestTemplate client;
    private final String apiKey;
    private final String transcriptionModel;
    private final String speechModel;
    private final String voice;
    private final CreditService credits;

    @Autowired
    public AudioService(RestTemplateBuilder builder, CreditService credits,
                        @Value("${app.ai.audio.api-key:}") String apiKey,
                        @Value("${app.ai.audio.transcription-model:whisper-1}") String transcriptionModel,
                        @Value("${app.ai.audio.speech-model:tts-1}") String speechModel,
                        @Value("${app.ai.audio.voice:alloy}") String voice) {
        this(builder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(60)).build(),
                apiKey, transcriptionModel, speechModel, voice, credits);
    }

    AudioService(RestTemplate client, String apiKey, String transcriptionModel, String speechModel, String voice, CreditService credits) {
        this.client = client;
        this.apiKey = apiKey;
        this.transcriptionModel = transcriptionModel;
        this.speechModel = speechModel;
        this.voice = voice;
        this.credits = credits;
    }

    public String transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Audio file is required");
        var headers = headers(MediaType.MULTIPART_FORM_DATA);
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", file.getResource());
        body.add("model", transcriptionModel);
        body.add("response_format", "json");
        var ticket = credits.reserve(java.util.UUID.randomUUID().toString(), "audio", "transcription", transcriptionModel);
        long start = System.nanoTime();
        String transcript;
        try {
            var response = client.postForEntity(BASE_URL + "transcriptions", new HttpEntity<>(body, headers), Map.class);
            Object text = response.getBody() == null ? null : response.getBody().get("text");
            if (!response.getStatusCode().is2xxSuccessful() || !(text instanceof String) || ((String) text).isBlank()) {
                throw new AiServiceException("Audio transcription returned no usable text");
            }
            transcript = (String) text;
        } catch (Exception e) {
            try { credits.release(ticket); } catch (Exception refundError) { e.addSuppressed(refundError); }
            throw new AiServiceException("Audio transcription failed", e);
        }
        credits.settle(ticket, transcriptionModel, null, null, null, (System.nanoTime()-start)/1_000_000);
        return transcript;
    }

    public byte[] speak(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Speech text is required");
        var headers = headers(MediaType.APPLICATION_JSON);
        var body = Map.of("model", speechModel, "input", text, "voice", voice, "response_format", "mp3");
        var ticket = credits.reserve(java.util.UUID.randomUUID().toString(), "audio", "speech", speechModel);
        long start = System.nanoTime();
        byte[] audio;
        try {
            var response = client.postForEntity(BASE_URL + "speech", new HttpEntity<>(body, headers), byte[].class);
            var contentType = response.getHeaders().getContentType();
            audio = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || audio == null || audio.length == 0
                    || contentType == null || !("audio".equals(contentType.getType())
                    || MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(contentType))) {
                throw new AiServiceException("Speech provider returned no usable audio");
            }
        } catch (Exception e) {
            try { credits.release(ticket); } catch (Exception refundError) { e.addSuppressed(refundError); }
            throw new AiServiceException("Speech generation failed", e);
        }
        credits.settle(ticket, speechModel, null, null, null, (System.nanoTime()-start)/1_000_000);
        return audio;
    }

    private HttpHeaders headers(MediaType contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new FeatureUnavailableException("Voice features require an OpenAI audio API key. You can continue using text.");
        }
        var headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(contentType);
        return headers;
    }
}
