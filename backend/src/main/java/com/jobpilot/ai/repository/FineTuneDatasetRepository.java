package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.FineTuneDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FineTuneDatasetRepository extends JpaRepository<FineTuneDataset, UUID> {
    boolean existsByAiLogId(UUID aiLogId);
}
