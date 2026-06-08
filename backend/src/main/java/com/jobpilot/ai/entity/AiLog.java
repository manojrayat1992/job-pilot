package com.jobpilot.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String provider;
    private String model;
    private String feature;
    
    @Column(name = "prompt_tokens")
    private Integer promptTokens;
    
    @Column(name = "completion_tokens")
    private Integer completionTokens;
    
    @Column(name = "latency_ms")
    private Long latencyMs;
    
    private BigDecimal cost;
    private String status;

    private Integer rating;

    @Column(name = "feedback_comment")
    private String feedbackComment;

    @Column(name = "prompt_template")
    private String promptTemplate;

    @Column(name = "request_content")
    private String requestContent;

    @Column(name = "response_content")
    private String responseContent;

    @Column(name = "score_jump")
    private Integer scoreJump;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
