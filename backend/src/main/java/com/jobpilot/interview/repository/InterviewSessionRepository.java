package com.jobpilot.interview.repository;

import com.jobpilot.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    List<InterviewSession> findByUserId(UUID userId);
    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);
}
