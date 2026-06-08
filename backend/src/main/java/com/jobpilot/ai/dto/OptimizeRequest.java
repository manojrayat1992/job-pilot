package com.jobpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OptimizeRequest {

    @NotNull(message = "Resume ID is required")
    private UUID resumeId;

    private UUID jobId;

    private String jobText;
}
