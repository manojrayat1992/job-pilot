package com.jobpilot.job.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobAnalysisRequest {

    /**
     * Raw job description text (paste from job posting).
     */
    private String text;

    /**
     * URL to a job posting (LinkedIn, Indeed, etc.) — alternative to text.
     */
    private String url;

    /**
     * Optional title override.
     */
    private String title;
}
