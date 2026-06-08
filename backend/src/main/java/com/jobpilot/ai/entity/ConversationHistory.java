package com.jobpilot.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "conversation_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "interview_session_id")
    private UUID interviewSessionId;

    @Column(name = "agent_persona")
    private String agentPersona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shadow_scores")
    private Map<String, Object> shadowScores;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
