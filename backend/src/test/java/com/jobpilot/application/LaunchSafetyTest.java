package com.jobpilot.application;

import com.jobpilot.ai.agent.AgentController;
import com.jobpilot.ai.dto.AgentAutoApplyRequest;
import com.jobpilot.ai.dto.AgentResearchResponse;
import com.jobpilot.ai.service.AgentService;
import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.application.service.EmailSyncService;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.FeatureUnavailableException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.networking.service.HiringManagerService;
import com.jobpilot.resume.repository.ResumeRepository;
import com.jobpilot.user.controller.BillingController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LaunchSafetyTest {
    @Mock JobRepository jobs;
    @Mock ResumeRepository resumes;
    @Mock ApplicationRepository applications;
    @InjectMocks AgentService service;
    UUID userId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String jobUrl = "https://employer.example.test/job/123";

    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "user@example.test", "USER"), null));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    AgentAutoApplyRequest request() {
        AgentAutoApplyRequest request = new AgentAutoApplyRequest();
        request.setJobs(List.of(AgentResearchResponse.RankedJob.builder().title("Engineer").company("Employer")
                .id(jobId).url(jobUrl).source("Web").matchScore(80).build()));
        return request;
    }

    @Test void selectedJobsAreSavedWithoutClaimingSubmission() {
        when(jobs.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(com.jobpilot.job.entity.Job.builder().id(jobId).userId(userId).company("Employer").title("Engineer").build()));
        service.saveJobs(request());
        var captor = ArgumentCaptor.forClass(JobApplication.class);
        verify(applications).save(captor.capture());
        assertEquals(JobApplication.ApplicationStatus.SAVED, captor.getValue().getStatus());
        assertNull(captor.getValue().getAppliedDate());
        assertEquals(userId, captor.getValue().getUserId());
    }

    @Test void repeatedSaveDoesNotCreateAnotherTrackerEntry() {
        when(jobs.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(com.jobpilot.job.entity.Job.builder().id(jobId).userId(userId).company("Employer").title("Engineer").build()));
        when(applications.existsByUserIdAndJobId(userId, jobId)).thenReturn(true);
        service.saveJobs(request());
        verify(applications, never()).save(any());
    }

    @Test void anotherUsersResumeCannotBeAttached() {
        var request = request();
        request.setResumeId(UUID.randomUUID());
        when(resumes.findByIdAndUserId(request.getResumeId(), userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.saveJobs(request));
        verifyNoInteractions(jobs, applications);
    }

    @Test void legacyAutoApplyDoesNotPretendToSubmit() {
        AgentController controller = new AgentController(null, null, service, null);
        assertThrows(FeatureUnavailableException.class, () -> controller.autoApply(request()));
        verifyNoInteractions(jobs, applications);
    }

    @Test void unfinishedIntegrationsFailExplicitly() {
        assertThrows(FeatureUnavailableException.class, () -> new EmailSyncService().syncInbox());
        HiringManagerService contacts = new HiringManagerService();
        assertThrows(FeatureUnavailableException.class, () -> contacts.search("example.test", null, null, null));
        assertThrows(FeatureUnavailableException.class, () -> contacts.unlockContact(UUID.randomUUID()));
        assertThrows(FeatureUnavailableException.class, () -> new BillingController().createCheckoutSession());
        assertThrows(FeatureUnavailableException.class, () -> new BillingController().handleStripeWebhook());
    }
}
