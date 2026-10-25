package com.jobpilot.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDTO {

    private String id;
    private String title;
    private int version;
    private String parentResumeId;
    private String targetJobId;
    private boolean active;
    private String rawText;
    private Map<String, Object> parsedJson;
    private String fileName;
    private String fileType;
    private Integer atsScore;
    private Map<String, Object> atsIssues;
    private String[] tags;
    private Instant createdAt;
    private Instant updatedAt;
}
