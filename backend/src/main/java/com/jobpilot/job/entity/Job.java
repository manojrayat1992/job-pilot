package com.jobpilot.job.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jobpilot.job.converter.VectorConverter;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String title;
    private String company;
    private String location;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json", columnDefinition = "jsonb")
    private Map<String, Object> parsedJson;

    @Column(name = "experience_level")
    private String experienceLevel;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "salary_range")
    private String salaryRange;

    @Column(name = "skills_required", columnDefinition = "TEXT[]")
    private String[] skillsRequired;

    @Column(name = "skills_nice", columnDefinition = "TEXT[]")
    private String[] skillsNice;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @Convert(converter = VectorConverter.class)
    private float[] embedding;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
