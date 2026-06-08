package com.jobpilot.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDTO {

    private String id;
    private String company;
    private String position;
    private String status;
    private LocalDate appliedDate;
    private String notes;
    private String coverLetter;
    private String source;
    private String contactName;
    private String contactEmail;
    private String priority;
    private String jobId;
    private String resumeId;
    private Instant createdAt;
    private Instant updatedAt;
}
