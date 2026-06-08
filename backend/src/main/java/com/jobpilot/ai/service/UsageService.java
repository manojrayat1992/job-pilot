package com.jobpilot.ai.service;

import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final AiLogRepository aiLogRepository;
    private final CreditService creditService;

    public Map<String, Object> getUserUsageStats() {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Get total remaining credits
        int credits = creditService.getRemainingCredits();

        // Get recent AI activity
        List<AiLog> recentLogs = aiLogRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);

        // Map logs to a clean DTO list
        List<Map<String, Object>> activityLog = recentLogs.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", log.getId());
            map.put("feature", log.getFeature() != null ? log.getFeature() : "General AI");
            map.put("model", log.getModel());
            int prompt = log.getPromptTokens() != null ? log.getPromptTokens() : 0;
            int completion = log.getCompletionTokens() != null ? log.getCompletionTokens() : 0;
            map.put("tokensUsed", prompt + completion);
            map.put("durationMs", log.getLatencyMs());
            map.put("timestamp", log.getCreatedAt().toString());
            map.put("status", log.getStatus() != null ? log.getStatus() : "SUCCESS");
            return map;
        }).collect(Collectors.toList());

        // Get aggregate usage stats per model
        List<Object[]> modelStatsRaw = aiLogRepository.findUsageStatsByUserId(userId);
        List<Map<String, Object>> modelStats = new ArrayList<>();
        
        long totalTokensAllTime = 0;
        long totalRequestsAllTime = 0;

        for (Object[] row : modelStatsRaw) {
            String model = (String) row[0];
            long reqCount = ((Number) row[1]).longValue();
            long tokens = row[2] != null ? ((Number) row[2]).longValue() : 0;
            
            totalTokensAllTime += tokens;
            totalRequestsAllTime += reqCount;

            modelStats.add(Map.of(
                    "model", model != null ? model : "Unknown",
                    "requestCount", reqCount,
                    "tokensUsed", tokens
            ));
        }

        return Map.of(
                "credits", Map.of(
                        "remaining", credits,
                        "isUnlimited", credits == Integer.MAX_VALUE
                ),
                "summary", Map.of(
                        "totalTokens", totalTokensAllTime,
                        "totalRequests", totalRequestsAllTime
                ),
                "modelStats", modelStats,
                "recentActivity", activityLog
        );
    }
}
