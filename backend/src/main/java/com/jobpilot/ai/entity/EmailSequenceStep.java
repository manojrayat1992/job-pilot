package com.jobpilot.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_sequence_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSequenceStep {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Column(nullable = false)
    private String channel;

    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
