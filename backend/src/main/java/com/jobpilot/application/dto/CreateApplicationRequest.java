package com.jobpilot.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateApplicationRequest {

    @NotBlank(message = "Company name is required")
    private String company;

    @NotBlank(message = "Position is required")
    private String position;

    private UUID jobId;
    private UUID resumeId;
    private String status;
    private LocalDate appliedDate;
    private String notes;
    private String coverLetter;
    private String source;
    private String contactName;
    private String contactEmail;
    private String priority;
}
