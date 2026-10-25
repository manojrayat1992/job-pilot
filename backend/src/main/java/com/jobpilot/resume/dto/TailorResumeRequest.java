package com.jobpilot.resume.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TailorResumeRequest(UUID jobId, @Size(max = 30000) String jobDescription, java.util.Map<String, Object> proposedResume) {}
