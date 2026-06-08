package com.jobpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverLetterResponse {

    private String coverLetter;
    private Object highlights;
    private int wordCount;
    private java.util.UUID aiLogId;
}
