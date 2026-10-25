package com.jobpilot.ai.service;

import com.jobpilot.common.exception.*;
import com.jobpilot.common.util.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Short independent transactions: remote calls never hold wallet locks. */
@Service
public class CreditService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public CreditService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Ticket(UUID id, boolean cached, String content, String model) {}

    public Optional<Ticket> completed(String key, String fingerprint) {
        UUID user = SecurityUtils.getCurrentUserId();
        unlimited(user); // Also checks that the account is enabled.
        var rows = jdbc.queryForList("SELECT id,fingerprint,cached_content,model FROM ai_credit_ledger WHERE user_id=? AND operation_key=? AND status='SETTLED'", user, key);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        if (!fingerprint.equals(row.get("fingerprint"))) throw new CreditConflictException("Operation inputs changed; start a new workflow.");
        if (row.get("cached_content") == null) throw new CreditConflictException("Operation already completed.");
        return Optional.of(new Ticket((UUID) row.get("id"), true, (String) row.get("cached_content"), (String) row.get("model")));
    }
    public Ticket reserve(String key, String fingerprint, String feature, String model) {
        UUID user = SecurityUtils.getCurrentUserId();
        return tx.execute(status -> {
            lockAccount(user);
            var previous = jdbc.queryForList("SELECT * FROM ai_credit_ledger WHERE user_id=? AND operation_key=? ORDER BY attempt DESC LIMIT 1", user, key);
            int attempt = 1;
            if (!previous.isEmpty()) {
                var row = previous.getFirst();
                if (!fingerprint.equals(row.get("fingerprint"))) throw new CreditConflictException("Operation inputs changed; start a new workflow.");
                if ("SETTLED".equals(row.get("status"))) {
                    if (row.get("cached_content") == null) throw new CreditConflictException("Operation already completed.");
                    return new Ticket((UUID) row.get("id"), true, (String) row.get("cached_content"), (String) row.get("model"));
                }
                if ("RESERVED".equals(row.get("status"))) throw new CreditConflictException("This AI call is pending or its outcome is unknown. Check usage history; support must reconcile interrupted calls before retry.");
                attempt = ((Number) row.get("attempt")).intValue() + 1;
            }
            int amount = unlimited(user) ? 0 : 1;
            if (jdbc.update("UPDATE ai_credit_accounts SET available=available-? WHERE user_id=? AND available>=?", amount, user, amount) != 1)
                throw new CreditExhaustedException("No AI credits are available. Pending calls may be holding credits; check usage history.");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ai_credit_ledger(id,user_id,operation_key,fingerprint,attempt,feature,requested_model,status,credits) VALUES(?,?,?,?,?,?,?,'RESERVED',?)",
                    id, user, key, fingerprint, attempt, feature, model, amount);
            return new Ticket(id, false, null, null);
        });
    }

    public void settle(Ticket ticket, String model, String cachedContent, Long promptTokens, Long completionTokens, long durationMs) {
        UUID user = SecurityUtils.getCurrentUserId();
        tx.executeWithoutResult(status -> {
            lockAccount(user);
            var row = owned(ticket.id(), user);
            if ("SETTLED".equals(row.get("status"))) return;
            if (!"RESERVED".equals(row.get("status"))) throw new CreditConflictException("This reservation was released; its late response cannot be committed.");
            jdbc.update("UPDATE ai_credit_ledger SET status='SETTLED',model=?,cached_content=?,prompt_tokens=?,completion_tokens=?,duration_ms=?,completed_at=now() WHERE id=?",
                    model, cachedContent, validTokens(promptTokens), validTokens(completionTokens), Math.max(0, durationMs), ticket.id());
        });
    }

    /** Failed requests refund app credits, even when a provider might have incurred cost. */
    public void release(Ticket ticket) {
        UUID user = SecurityUtils.getCurrentUserId();
        tx.executeWithoutResult(status -> {
            lockAccount(user);
            refund(owned(ticket.id(), user), "Request failed; provider cost may be unknown", null);
        });
    }

    /** An operator must first check the provider and stop the old worker. Never expires/refunds automatically. */
    public void releaseInterrupted(UUID id, String reason) {
        UUID admin = SecurityUtils.getCurrentUserId();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled AND upper(role)='ADMIN' FROM users WHERE id=?", Boolean.class, admin)))
            throw new AccessDeniedException("Administrator required");
        if (reason == null || reason.isBlank() || reason.length() > 400) throw new BadRequestException("A reconciliation reason of 1–400 characters is required");
        tx.executeWithoutResult(status -> {
            var owners = jdbc.queryForList("SELECT user_id FROM ai_credit_ledger WHERE id=?", id);
            if (owners.isEmpty()) throw new ResourceNotFoundException("Credit reservation", "id", id);
            UUID user = (UUID) owners.getFirst().get("user_id");
            lockAccount(user);
            var row = owned(id, user);
            if (!"RESERVED".equals(row.get("status"))) throw new CreditConflictException("Only unresolved reservations can be reconciled");
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT created_at < now()-interval '10 minutes' FROM ai_credit_ledger WHERE id=?", Boolean.class, id)))
                throw new CreditConflictException("This request is too recent to reconcile");
            refund(row, reason.trim(), admin);
        });
    }
    private void refund(Map<String, Object> row, String reason, UUID admin) {
        if (!"RESERVED".equals(row.get("status"))) return; // Idempotent; settled calls cannot be refunded by error cleanup.
        jdbc.update("UPDATE ai_credit_accounts SET available=available+? WHERE user_id=?", row.get("credits"), row.get("user_id"));
        jdbc.update("UPDATE ai_credit_ledger SET status='RELEASED',resolution=?,reconciled_by=?,completed_at=now() WHERE id=?", reason, admin, row.get("id"));
    }
    private Map<String, Object> owned(UUID id, UUID user) {
        var rows = jdbc.queryForList("SELECT * FROM ai_credit_ledger WHERE id=? AND user_id=?", id, user);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Credit reservation", "id", id);
        return rows.getFirst();
    }
    private void lockAccount(UUID user) {
        var rows = jdbc.queryForList("SELECT user_id FROM ai_credit_accounts WHERE user_id=? FOR UPDATE", user);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Credit account", "user", user);
    }
    private boolean unlimited(UUID user) {
        var row = jdbc.queryForMap("SELECT role,enabled FROM users WHERE id=?", user);
        if (!Boolean.TRUE.equals(row.get("enabled"))) throw new AccessDeniedException("Account disabled");
        return Set.of("PRO", "ADMIN", "ENTERPRISE").contains(row.get("role").toString().toUpperCase(Locale.ROOT));
    }
    private Long validTokens(Long value) { return value != null && value >= 0 ? value : null; }
    public int getRemainingCredits() { return getRemainingCredits(SecurityUtils.getCurrentUserId()); }
    public int getRemainingCredits(UUID user) {
        return unlimited(user) ? Integer.MAX_VALUE : jdbc.queryForObject("SELECT available FROM ai_credit_accounts WHERE user_id=?", Integer.class, user);
    }
    public void addCredits(UUID user, int amount, String reference) {
        if (amount <= 0 || reference == null || reference.isBlank() || reference.length() > 200) throw new BadRequestException("Positive amount and unique grant reference required");
        tx.executeWithoutResult(status -> {
            lockAccount(user);
            var prior = jdbc.queryForList("SELECT amount FROM ai_credit_grants WHERE user_id=? AND reference=?", user, reference);
            if (!prior.isEmpty()) {
                if (((Number) prior.getFirst().get("amount")).intValue() != amount) throw new CreditConflictException("Grant reference already used with another amount");
                return;
            }
            jdbc.update("INSERT INTO ai_credit_grants(user_id,reference,amount) VALUES(?,?,?)", user, reference, amount);
            jdbc.update("UPDATE ai_credit_accounts SET available=available+? WHERE user_id=?", amount, user);
        });
    }
}
