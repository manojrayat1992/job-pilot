package com.jobpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeResponse {

    private Object optimizedResume;
    private Object changes;
    private List<String> keywordsAdded;
    private List<String> missingKeywords;
    private Integer matchScore;
    private String scoreImprovement;
    private java.util.UUID aiLogId;
}
