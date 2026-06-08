package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.EmailSequenceStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailSequenceStepRepository extends JpaRepository<EmailSequenceStep, UUID> {
    List<EmailSequenceStep> findBySequenceIdOrderByStepNumberAsc(UUID sequenceId);
}
