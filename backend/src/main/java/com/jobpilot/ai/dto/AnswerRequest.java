package com.jobpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AnswerRequest {

    @NotNull(message = "Resume ID is required")
    private UUID resumeId;

    @NotBlank(message = "Question is required")
    private String question;

    /** Optional job description for context */
    private String jobText;
}
