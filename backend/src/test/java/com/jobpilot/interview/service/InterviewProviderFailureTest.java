package com.jobpilot.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.ai.service.ChatGenerationService;
import com.jobpilot.ai.repository.ConversationHistoryRepository;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.*;
import com.jobpilot.interview.entity.InterviewSession;
import com.jobpilot.interview.repository.InterviewSessionRepository;
import com.jobpilot.resume.repository.ResumeRepository;
import org.junit.jupiter.api.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewProviderFailureTest {
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final ConversationHistoryRepository history = mock(ConversationHistoryRepository.class);
    private final AiService ai = mock(AiService.class);
    private final ChatGenerationService chat = mock(ChatGenerationService.class);
    private final InterviewService service = new InterviewService(sessions, history, mock(ResumeRepository.class), ai,
            new ObjectMapper(), chat, new DefaultResourceLoader());
    private final UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    private final MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(user, "owner@example.test", "USER"), null));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void ownedSession() {
        when(sessions.findByIdAndUserId(id, user)).thenReturn(Optional.of(InterviewSession.builder().id(id).userId(user)
                .targetRole("Engineer").status(InterviewSession.InterviewStatus.ACTIVE).currentTurn(1).maxTurns(10).build()));
    }
    @Test void foreignSessionCannotTriggerAudioCharges() {
        assertThrows(ResourceNotFoundException.class, () -> service.processUserAudio(id, file));
        verifyNoInteractions(ai, chat, history);
    }
    @Test void failedTranscriptionDoesNotAdvanceInterview() {
        ownedSession();
        when(ai.transcribeAudio(file)).thenThrow(new AiServiceException("transcription failed"));
        assertThrows(AiServiceException.class, () -> service.processUserAudio(id, file));
        verifyNoInteractions(chat, history);
        verify(sessions, never()).save(any());
    }
    @Test void speechFailureRetainsTextResponseAndReportsDegradation() {
        ownedSession();
        when(ai.transcribeAudio(file)).thenReturn("My answer");
        when(chat.generate(anyString())).thenReturn(
                new ChatGenerationService.Result("{\"nextPersona\":\"TECH_LEAD\",\"instruction\":\"Ask a question\"}", "model"),
                new ChatGenerationService.Result("{\"content\":\"Explain your design\",\"shadowScore\":70}", "model"));
        when(ai.generateSpeech("Explain your design")).thenThrow(new AiServiceException("speech failed"));
        var response = service.processUserAudio(id, file);
        assertEquals("My answer", response.get("transcribedText"));
        assertNull(response.get("audioBase64"));
        assertTrue(response.get("audioError").toString().contains("answer was saved"));
        verify(history, times(2)).save(any());
        verify(sessions).save(any());
    }
    @Test void malformedAgentOutputCannotBecomeAnInterviewMessage() {
        when(sessions.save(any())).thenAnswer(inv -> { InterviewSession session = inv.getArgument(0); session.setId(id); return session; });
        when(chat.generate(anyString())).thenReturn(new ChatGenerationService.Result("not JSON", "model"));
        assertThrows(AiServiceException.class, () -> service.startInterview("Engineer", null));
        verify(history, never()).save(any());
    }
}
