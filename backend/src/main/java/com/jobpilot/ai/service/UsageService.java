package com.jobpilot.ai.service;

import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UsageService {
    private final JdbcTemplate jdbc;
    private final CreditService creditService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> getUserUsageStats() {
        UUID user = SecurityUtils.getCurrentUserId();
        int remaining = creditService.getRemainingCredits();
        var summary = jdbc.queryForMap("""
            SELECT count(*) AS "totalRequests", coalesce(sum(prompt_tokens+completion_tokens),0) AS "totalTokens",
              count(*) FILTER(WHERE status='SETTLED' AND (prompt_tokens IS NULL OR completion_tokens IS NULL)) AS "unknownTokenRequests",
              coalesce(sum(credits) FILTER(WHERE status='RESERVED'),0) AS "reservedCredits",
              coalesce(sum(credits) FILTER(WHERE status='SETTLED'),0) AS "chargedCredits",
              count(*) FILTER(WHERE status='RESERVED' AND created_at < now()-interval '10 minutes') AS "unresolvedRequests"
            FROM ai_credit_ledger WHERE user_id=?
            """, user);
        var models = jdbc.queryForList("""
            SELECT coalesce(model,requested_model) AS model, count(*) AS "requestCount",
              coalesce(sum(prompt_tokens+completion_tokens),0) AS "tokensUsed",
              count(*) FILTER(WHERE status='SETTLED' AND (prompt_tokens IS NULL OR completion_tokens IS NULL)) AS "unknownTokenRequests"
            FROM ai_credit_ledger WHERE user_id=? GROUP BY coalesce(model,requested_model) ORDER BY count(*) DESC
            """, user);
        var activity = jdbc.queryForList("""
            SELECT id, feature, coalesce(model,requested_model) AS model, prompt_tokens+completion_tokens AS "tokensUsed",
              duration_ms AS "durationMs", created_at AS timestamp,
              CASE WHEN status='RESERVED' AND created_at < now()-interval '10 minutes' THEN 'UNKNOWN' ELSE status END AS status,
              CASE WHEN status='SETTLED' THEN credits ELSE 0 END AS "chargedCredits",
              CASE WHEN status='RESERVED' THEN credits ELSE 0 END AS "reservedCredits", resolution
            FROM ai_credit_ledger WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 20
            """, user);
        return Map.of("credits", Map.of("remaining", remaining, "isUnlimited", remaining == Integer.MAX_VALUE,
                        "reserved", summary.get("reservedCredits")),
                "summary", summary, "modelStats", models, "recentActivity", activity);
    }
}
