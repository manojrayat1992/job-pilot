package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.SalaryNegotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SalaryNegotiationRepository extends JpaRepository<SalaryNegotiation, UUID> {
    List<SalaryNegotiation> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
