package com.jobpilot.job.dto;

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
public class JobDTO {

    private String id;
    private String title;
    private String company;
    private String location;
    private String sourceUrl;
    private String rawText;
    private Map<String, Object> parsedJson;
    private String experienceLevel;
    private String jobType;
    private String salaryRange;
    private String[] skillsRequired;
    private String[] skillsNice;
    private Instant createdAt;
}
