package com.jobpilot.resume.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "resume_drafts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumeDraft {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, updatable = false) private UUID userId;
    @Column(nullable = false, updatable = false) private UUID sourceResumeId;
    @Column(updatable = false) private UUID targetJobId;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT") private String jobDescription;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb") private Map<String, Object> sourceSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb") private Map<String, Object> proposedSnapshot;
    private UUID approvedResumeId;
    private Instant approvedAt;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
}
