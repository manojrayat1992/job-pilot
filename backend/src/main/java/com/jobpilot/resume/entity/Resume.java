package com.jobpilot.resume.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resume entity — stores uploaded resume data with parsed JSON structure.
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "raw_text", columnDefinition = "TEXT", updatable = false)
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> parsedJson;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "ats_score")
    private Integer atsScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ats_issues", columnDefinition = "jsonb")
    private Map<String, Object> atsIssues;

    @Column(name = "is_transient", nullable = false)
    @Builder.Default
    private Boolean isTransient = false;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags;


    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "parent_resume_id")
    private UUID parentResumeId;

    @Column(name = "target_job_id")
    private UUID targetJobId;

    @Column(name = "target_job_description", columnDefinition = "TEXT")
    private String targetJobDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
