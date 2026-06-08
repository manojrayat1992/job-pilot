package com.jobpilot.ai.service;

import com.jobpilot.common.exception.CreditExhaustedException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages AI usage credits. Every AI operation must call consumeCredit()
 * before making an LLM call. Blocks free-tier users when credits reach 0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;

    /**
     * Checks if the current user has credits available.
     * Pro users (role=PRO or ADMIN) have unlimited credits.
     *
     * @throws CreditExhaustedException if the user has no credits remaining
     */
    public void requireCredits() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Pro and Admin users have unlimited access
        if ("PRO".equalsIgnoreCase(user.getRole()) ||
            "ADMIN".equalsIgnoreCase(user.getRole()) ||
            "ENTERPRISE".equalsIgnoreCase(user.getRole())) {
            return;
        }

        if (user.getCredits() <= 0) {
            log.warn("User {} has exhausted their AI credits", userId);
            throw new CreditExhaustedException(
                    "You've used all your free AI credits. Upgrade to Pro for unlimited access.");
        }
    }

    /**
     * Consume one credit from the current user's balance.
     * Should be called after a successful AI operation.
     */
    @Transactional
    public void consumeCredit() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        // Don't decrement for premium users
        if ("PRO".equalsIgnoreCase(user.getRole()) ||
            "ADMIN".equalsIgnoreCase(user.getRole()) ||
            "ENTERPRISE".equalsIgnoreCase(user.getRole())) {
            return;
        }

        int remaining = Math.max(0, user.getCredits() - 1);
        user.setCredits(remaining);
        userRepository.save(user);
        log.info("Credit consumed for user {}. Remaining: {}", userId, remaining);
    }

    /**
     * Get remaining credits for the current user.
     */
    public int getRemainingCredits() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return 0;

        if ("PRO".equalsIgnoreCase(user.getRole()) ||
            "ADMIN".equalsIgnoreCase(user.getRole())) {
            return Integer.MAX_VALUE; // Unlimited
        }
        return user.getCredits();
    }

    /**
     * Add credits to a user (e.g., after purchase or monthly reset).
     */
    @Transactional
    public void addCredits(UUID userId, int amount) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        user.setCredits(user.getCredits() + amount);
        userRepository.save(user);
        log.info("Added {} credits to user {}. New balance: {}", amount, userId, user.getCredits());
    }
}
