package com.jobpilot.ai.service;

import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.exception.FeatureUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AudioServiceTest {
    private final RestTemplate client = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final CreditService credits = org.mockito.Mockito.mock(CreditService.class);
    private final AudioService service = new AudioService(client, "openai-audio-test-key", "whisper-1", "tts-1", "alloy", credits);
    private final MockMultipartFile file = new MockMultipartFile("file", "answer.webm", "audio/webm", new byte[]{1, 2, 3});
    private static final String BASE = "https://api.openai.com/v1/audio/";

    @Test void transcriptionUsesOpenAiCredentialsAndMultipartContract() {
        server.expect(requestTo(BASE + "transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer openai-audio-test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("filename=\"answer.webm\"")))
                .andExpect(content().string(containsString("whisper-1")))
                .andExpect(content().string(containsString("response_format")))
                .andRespond(withSuccess("{\"text\":\"My actual answer\"}", MediaType.APPLICATION_JSON));
        assertEquals("My actual answer", service.transcribe(file));
        server.verify();
    }
    @Test void speechUsesConfiguredModelAndReturnsMp3Bytes() {
        byte[] bytes = {4, 5, 6};
        var custom = new AudioService(client, "audio-only-key", "whisper-1", "tts-1-hd", "echo", credits);
        server.expect(requestTo(BASE + "speech"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer audio-only-key"))
                .andExpect(content().json("{\"model\":\"tts-1-hd\",\"input\":\"Hello\",\"voice\":\"echo\",\"response_format\":\"mp3\"}", true))
                .andRespond(withSuccess(bytes, MediaType.parseMediaType("audio/mpeg")));
        assertArrayEquals(bytes, custom.speak("Hello"));
        server.verify();
    }
    @Test void absentAudioKeyDoesNotContactProviderOrSimulateSuccess() {
        var disabled = new AudioService(client, "", "whisper-1", "tts-1", "alloy", credits);
        assertThrows(FeatureUnavailableException.class, () -> disabled.transcribe(file));
        assertThrows(FeatureUnavailableException.class, () -> disabled.speak("Hello"));
        server.verify();
    }
    @ParameterizedTest @ValueSource(ints = {401, 429, 503})
    void providerErrorsFailBothOperations(int status) {
        server.expect(requestTo(BASE + "transcriptions")).andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(BASE + "speech")).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThrows(AiServiceException.class, () -> service.transcribe(file));
        assertThrows(AiServiceException.class, () -> service.speak("Hello"));
        server.verify();
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"text\":\" \"}", "{\"text\":42}", "not json"})
    void unusableTranscriptionIsRejected(String body) {
        server.expect(requestTo(BASE + "transcriptions")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(AiServiceException.class, () -> service.transcribe(file));
        server.verify();
    }
    @Test void timeoutIsAnExplicitFailure() {
        server.expect(requestTo(BASE + "transcriptions")).andRespond(withException(new IOException("timeout")));
        assertThrows(AiServiceException.class, () -> service.transcribe(file));
        server.verify();
    }
    @Test void speechCannotReturnJsonOrEmptyAudioAsSuccess() {
        server.expect(requestTo(BASE + "speech")).andRespond(withSuccess("{\"error\":\"invalid\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "speech")).andRespond(withSuccess(new byte[0], MediaType.parseMediaType("audio/mpeg")));
        assertThrows(AiServiceException.class, () -> service.speak("Hello"));
        assertThrows(AiServiceException.class, () -> service.speak("Hello"));
        server.verify();
    }
    @Test void emptyInputDoesNotReachProvider() {
        assertThrows(IllegalArgumentException.class, () -> service.transcribe(new MockMultipartFile("file", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.speak(" "));
        server.verify();
    }
}
