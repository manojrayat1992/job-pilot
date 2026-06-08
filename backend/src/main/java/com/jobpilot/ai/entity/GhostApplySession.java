package com.jobpilot.ai.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ghost_apply_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GhostApplySession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(name = "job_url", nullable = false, columnDefinition = "TEXT")
    private String jobUrl;

    private String company;
    private String position;

    @Column(nullable = false)
    @Builder.Default
    private String status = "INITIALIZING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_fields")
    private JsonNode detectedFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mappings")
    private JsonNode fieldMappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_log")
    private JsonNode executionLog;

    @Column(name = "screenshot_base64", columnDefinition = "TEXT")
    private String screenshotBase64;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "fields_filled")
    @Builder.Default
    private int fieldsFilled = 0;

    @Column(name = "total_fields")
    @Builder.Default
    private int totalFields = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
