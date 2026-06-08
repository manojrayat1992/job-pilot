package com.jobpilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {

    private String answer;
    private Object tips;
    private Object followUpQuestions;
    private java.util.UUID aiLogId;
}
