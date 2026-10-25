package com.jobpilot.job.repository;

import com.jobpilot.job.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Job> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
        SELECT j.* FROM jobs j JOIN job_embeddings e ON e.job_id=j.id
        JOIN embedding_tasks t ON t.kind='JOB' AND t.entity_id=j.id AND t.status='COMPLETED'
        WHERE j.user_id=?1 AND e.embedding IS NOT NULL
        ORDER BY e.embedding <=> cast(?2 as vector),j.id LIMIT 5
        """, nativeQuery = true)
    List<Job> searchIndexedJobs(UUID userId, String vector);

    default List<Job> findTop5ByUserIdOrderByEmbedding(UUID userId, float[] embedding) {
        return searchIndexedJobs(userId, com.jobpilot.ai.service.EmbeddingService.vectorLiteral(embedding));
    }

    Optional<Job> findFirstByUserIdAndSourceUrlOrderByCreatedAtAsc(UUID userId, String sourceUrl);

    Optional<Job> findByUserIdAndSourceUrl(UUID userId, String sourceUrl);

    boolean existsByUserIdAndSourceUrl(UUID userId, String sourceUrl);

    @Query(value = "SELECT id FROM jobs WHERE user_id = ?1 AND source_url = ?2 LIMIT 1", nativeQuery = true)
    Optional<UUID> findIdByUserIdAndSourceUrl(UUID userId, String sourceUrl);
}
