package com.jobpilot.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.application.controller.CopilotController;
import com.jobpilot.auth.filter.JwtAuthenticationFilter;
import com.jobpilot.common.exception.GlobalExceptionHandler;
import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SecurityBoundaryTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = "app.cors.allowed-origins=https://app.example.test")
class SecurityBoundaryTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, CopilotController.class,
            GlobalExceptionHandler.class, com.jobpilot.ai.controller.CreditAdminController.class, Probe.class})
    static class Config {
        @Bean JwtUtil jwtUtil() { return new JwtUtil("test-only-signing-key-at-least-32-bytes-long", 900000, 604800000); }
        @Bean com.jobpilot.auth.service.AuthSessionService sessions() { return mock(com.jobpilot.auth.service.AuthSessionService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ResumeRepository resumes() { return mock(ResumeRepository.class); }
        @Bean com.jobpilot.ai.service.CreditService credits() { return mock(com.jobpilot.ai.service.CreditService.class); }
        @Bean AiService ai() { return mock(AiService.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    }

    @RestController
    static class Probe {
        @GetMapping("/api/v1/ai/admin/models") public String admin() { return "admin"; }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtUtil jwt;
    @Autowired com.jobpilot.auth.service.AuthSessionService sessions;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;
    @Autowired AiService ai;
    @Autowired com.jobpilot.ai.service.CreditService credits;
    MockMvc mvc;
    User user;
    UUID resumeId;

    @BeforeEach void setUp() {
        reset(users, resumes, ai, credits, sessions);
        when(sessions.isActive(any(), any())).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = User.builder().id(UUID.randomUUID()).email("candidate@example.test").role("USER").build();
        resumeId = UUID.randomUUID();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private String token() { return jwt.generateAccessToken(user.getId(), user.getEmail(), user.getRole()); }
    private String body() { return "{\"resumeId\":\"" + resumeId + "\",\"fields\":[\"Name\"]}"; }

    @Test void onlyAdministratorsCanReconcileCreditReservations() throws Exception {
        UUID reservation=UUID.randomUUID();
        String url="/api/v1/ai/admin/credits/"+reservation+"/release";
        String reason="{\"reason\":\"Worker stopped and provider checked\"}";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization","Bearer "+token()).contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isForbidden());
        verifyNoInteractions(credits);
        user.setRole("ADMIN");
        mvc.perform(post(url).header("Authorization","Bearer "+token()).contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isOk());
        verify(credits).releaseInterrupted(reservation,"Worker stopped and provider checked");
    }

    @Test void anonymousCannotMapResume() throws Exception {
        mvc.perform(post("/api/v1/copilot/map-fields").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(resumes, ai);
    }

    @Test void refreshTokenCannotCallResourceApis() throws Exception {
        mvc.perform(post("/api/v1/copilot/map-fields")
                .header("Authorization", "Bearer " + jwt.generateRefreshToken(user.getId(), user.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
        verifyNoInteractions(resumes, ai);
    }

    @Test void userCannotReadAnotherCandidatesResume() throws Exception {
        when(resumes.findByIdAndUserId(resumeId, user.getId())).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/copilot/map-fields").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isNotFound());
        verifyNoInteractions(ai);
    }

    @Test void ownerCanMapTheirResume() throws Exception {
        when(resumes.findByIdAndUserId(resumeId, user.getId())).thenReturn(Optional.of(
                Resume.builder().id(resumeId).userId(user.getId()).parsedJson(Map.of("name", "Candidate")).build()));
        when(ai.mapFormFields(anyString(), anyString(), anyString())).thenReturn(Map.of("Name", "Candidate"));
        mvc.perform(post("/api/v1/copilot/map-fields").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.Name").value("Candidate"));
    }

    @Test void ordinaryUserCannotAdministerModels() throws Exception {
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    @Test void administratorCanAccessModels() throws Exception {
        user.setRole("ADMIN");
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk());
    }

    @Test void revokedSessionCannotAccessResources() throws Exception {
        when(sessions.isActive(any(), any())).thenReturn(false);
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + token()))
                .andExpect(status().isUnauthorized());
    }

    @Test void sessionStoreFailureDoesNotAuthenticate() throws Exception {
        when(sessions.isActive(any(), any())).thenThrow(new IllegalStateException("Database unavailable"));
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + token()))
                .andExpect(status().isUnauthorized());
    }

    @Test void disabledAccountCannotUseAnExistingAccessToken() throws Exception {
        String token = token();
        user.setEnabled(false);
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void roleChangeInvalidatesOldAdminAuthority() throws Exception {
        String staleAdminToken = jwt.generateAccessToken(user.getId(), user.getEmail(), "ADMIN");
        mvc.perform(get("/api/v1/ai/admin/models").header("Authorization", "Bearer " + staleAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test void untrustedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/copilot/map-fields").header("Origin", "https://evil.example.test")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test void configuredOriginCanPreflight() throws Exception {
        mvc.perform(options("/api/v1/copilot/map-fields").header("Origin", "https://app.example.test")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"));
    }
}
