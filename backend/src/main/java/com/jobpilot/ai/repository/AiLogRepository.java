package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.AiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiLogRepository extends JpaRepository<AiLog, UUID> {
    
    @Query("SELECT l.promptTemplate as template, AVG(l.rating) as averageRating, COUNT(l) as totalLogs " +
           "FROM AiLog l WHERE l.rating IS NOT NULL GROUP BY l.promptTemplate")
    List<Object[]> findAverageRatingByTemplate();

    @Query("FROM AiLog l WHERE l.rating = 5 AND l.scoreJump > 10")
    List<AiLog> findHighPerformingLogs();

    List<AiLog> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT l.model as model, COUNT(l) as requestCount, SUM(COALESCE(l.promptTokens, 0) + COALESCE(l.completionTokens, 0)) as totalTokens " +
           "FROM AiLog l WHERE l.userId = :userId GROUP BY l.model")
    List<Object[]> findUsageStatsByUserId(UUID userId);
}
