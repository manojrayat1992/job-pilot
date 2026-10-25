package com.jobpilot.application;

import com.jobpilot.application.dto.CreateApplicationRequest;
import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.application.service.ApplicationService;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.interview.repository.InterviewSessionRepository;
import com.jobpilot.interview.service.InterviewService;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceOwnershipTest {
    @Mock ApplicationRepository applications;
    @Mock ResumeRepository resumes;
    @Mock JobRepository jobs;
    @Mock InterviewSessionRepository sessions;
    @InjectMocks ApplicationService applicationService;
    @InjectMocks InterviewService interviewService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "owner@example.test", "USER"), null));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void applicationCannotReferenceAnotherUsersResume() {
        var request = new CreateApplicationRequest();
        request.setResumeId(UUID.randomUUID());
        when(resumes.findByIdAndUserId(request.getResumeId(), userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> applicationService.create(request));
        verifyNoInteractions(applications, jobs);
    }

    @Test void applicationCannotReferenceAnotherUsersJob() {
        var request = new CreateApplicationRequest();
        request.setJobId(UUID.randomUUID());
        when(jobs.findByIdAndUserId(request.getJobId(), userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> applicationService.create(request));
        verifyNoInteractions(applications);
    }

    @Test void rejectedReferenceLeavesExistingApplicationUnchanged() {
        UUID id = UUID.randomUUID();
        var application = JobApplication.builder().id(id).userId(userId).company("Original").build();
        when(applications.findByIdAndUserId(id, userId)).thenReturn(Optional.of(application));
        var request = new CreateApplicationRequest();
        request.setCompany("Changed");
        request.setResumeId(UUID.randomUUID());
        when(resumes.findByIdAndUserId(request.getResumeId(), userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> applicationService.update(id, request));
        assertEquals("Original", application.getCompany());
        verify(applications, never()).save(any());
    }

    @Test void interviewRejectsForeignResumeBeforeCreatingSession() {
        UUID resumeId = UUID.randomUUID();
        when(resumes.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> interviewService.startInterview("Engineer", resumeId));
        verifyNoInteractions(sessions);
    }
}
