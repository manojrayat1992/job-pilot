package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.CultureFitAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CultureFitAssessmentRepository extends JpaRepository<CultureFitAssessment, UUID> {
    List<CultureFitAssessment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
