package com.jobpilot.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fine_tune_dataset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneDataset {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "ai_log_id", nullable = false)
    private UUID aiLogId;

    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @Column(name = "input_content", nullable = false)
    private String inputContent;

    @Column(name = "output_content", nullable = false)
    private String outputContent;

    private Integer rating;

    @Column(name = "score_jump")
    private Integer scoreJump;

    @Column(name = "is_exported")
    @Builder.Default
    private Boolean isExported = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
