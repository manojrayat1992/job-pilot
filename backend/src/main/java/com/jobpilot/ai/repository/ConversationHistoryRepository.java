package com.jobpilot.ai.repository;

import com.jobpilot.ai.entity.ConversationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, UUID> {
    
    @Query(value = "SELECT * FROM conversation_history WHERE user_id = ?1 AND job_id = ?2 ORDER BY created_at DESC LIMIT ?3", nativeQuery = true)
    List<ConversationHistory> findLastInteractions(UUID userId, UUID jobId, int limit);

    List<ConversationHistory> findByInterviewSessionIdOrderByCreatedAtAsc(UUID sessionId);
    
    List<ConversationHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<ConversationHistory> findByUserIdAndJobIdOrderByCreatedAtAsc(UUID userId, UUID jobId);
    
    List<ConversationHistory> findByUserIdAndResumeIdOrderByCreatedAtAsc(UUID userId, UUID resumeId);
}
