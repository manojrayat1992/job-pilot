package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.EmailSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailSequenceRepository extends JpaRepository<EmailSequence, UUID> {
    List<EmailSequence> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
