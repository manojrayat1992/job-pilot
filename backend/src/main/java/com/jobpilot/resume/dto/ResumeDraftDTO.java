package com.jobpilot.resume.dto;

import java.time.Instant;
import java.util.UUID;

public record ResumeDraftDTO(UUID id, UUID sourceResumeId, UUID targetJobId, String jobDescription,
        String originalText, String proposedText, UUID approvedResumeId, Instant approvedAt, Instant createdAt) {}
