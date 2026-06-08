package com.jobpilot.resume.repository;

import com.jobpilot.resume.entity.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    Page<Resume> findByUserIdAndIsTransientFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Resume r SET r.isActive = false WHERE r.userId = :userId AND r.isActive = true")
    void deactivateAllForUser(@Param("userId") UUID userId);

    Optional<Resume> findByUserIdAndIsActiveTrue(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndIsTransientFalse(UUID userId);
}
