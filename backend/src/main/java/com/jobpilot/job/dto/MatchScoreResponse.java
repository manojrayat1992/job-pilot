package com.jobpilot.job.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScoreResponse {

    private int overallScore;           // 0-100
    private int keywordMatchScore;      // 0-100
    private int experienceMatchScore;   // 0-100
    private int skillsMatchScore;       // 0-100
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> suggestions;
    private String summary;
}
