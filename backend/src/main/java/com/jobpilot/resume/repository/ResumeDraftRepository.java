package com.jobpilot.resume.repository;

import com.jobpilot.resume.entity.ResumeDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ResumeDraftRepository extends JpaRepository<ResumeDraft, UUID> {
    Optional<ResumeDraft> findByIdAndUserId(UUID id, UUID userId);
    List<ResumeDraft> findTop50BySourceResumeIdAndUserIdOrderByCreatedAtDesc(UUID sourceResumeId, UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ResumeDraft d where d.id = :id and d.userId = :userId")
    Optional<ResumeDraft> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
