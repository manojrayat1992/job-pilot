package com.jobpilot.application.repository;

import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.entity.JobApplication.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<JobApplication, UUID> {
    @Query(value="SELECT id FROM users WHERE id=?1 FOR UPDATE",nativeQuery=true)
    UUID lockOwner(UUID userId);

    Optional<JobApplication> findFirstByUserIdAndJobIdOrderByCreatedAtAsc(UUID userId, UUID jobId);


    Page<JobApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Page<JobApplication> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, ApplicationStatus status, Pageable pageable);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);

    @Query("SELECT a.status, COUNT(a) FROM JobApplication a WHERE a.userId = :userId GROUP BY a.status")
    List<Object[]> countByStatusGrouped(@Param("userId") UUID userId);

    List<JobApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
