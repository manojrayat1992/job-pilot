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

    @Query(value = "SELECT * FROM jobs WHERE user_id = ?1 ORDER BY embedding <=> cast(?2 as vector) LIMIT 5", nativeQuery = true)
    List<Job> findTop5ByUserIdOrderByEmbedding(UUID userId, float[] embedding);

    Optional<Job> findByUserIdAndSourceUrl(UUID userId, String sourceUrl);

    boolean existsByUserIdAndSourceUrl(UUID userId, String sourceUrl);

    @Query(value = "SELECT id FROM jobs WHERE user_id = ?1 AND source_url = ?2 LIMIT 1", nativeQuery = true)
    Optional<UUID> findIdByUserIdAndSourceUrl(UUID userId, String sourceUrl);
}
