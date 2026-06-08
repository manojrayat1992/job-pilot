package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.GhostApplySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GhostApplySessionRepository extends JpaRepository<GhostApplySession, UUID> {
    List<GhostApplySession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
