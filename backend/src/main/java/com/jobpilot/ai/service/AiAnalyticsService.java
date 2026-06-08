package com.jobpilot.ai.service;

import com.jobpilot.ai.repository.AiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalyticsService {

    private final AiLogRepository aiLogRepository;
    private static final double RATING_THRESHOLD = 3.5;

    /**
     * Nightly job to compute satisfaction rates per prompt template.
     * Runs at 2 AM every day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void computeSatisfactionRates() {
        log.info("Starting nightly AI satisfaction analysis...");
        
        List<Object[]> results = aiLogRepository.findAverageRatingByTemplate();
        
        for (Object[] row : results) {
            String template = (String) row[0];
            Double avgRating = (Double) row[1];
            Long totalLogs = (Long) row[2];
            
            log.info("Template: {}, Average Rating: {}, Total Responses: {}", 
                    template, String.format("%.2f", avgRating), totalLogs);
            
            if (avgRating < RATING_THRESHOLD && totalLogs >= 5) {
                log.warn("CRITICAL: Prompt template '{}' has low satisfaction ({}) – consider revising.", 
                        template, String.format("%.2f", avgRating));
                
                // In a real scenario, we might send an email or create an admin notification here
                flagForReview(template, avgRating, totalLogs);
            }
        }
        
        log.info("AI satisfaction analysis complete.");
    }

    private void flagForReview(String template, Double avgRating, Long totalLogs) {
        // Mock flagging logic
        log.info("FLAGGED FOR REVIEW: {} (Rating: {}, Volume: {})", template, avgRating, totalLogs);
    }
}
