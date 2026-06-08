package com.jobpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CoverLetterRequest {

    @NotNull(message = "Resume ID is required")
    private UUID resumeId;

    @NotBlank(message = "Job description text is required")
    private String jobText;

    /** Tone: formal, confident, enthusiastic, concise */
    private String tone;

    /** Length: short, medium, long */
    private String length;
}
