package com.jobpilot.common.util;

import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility to extract the current authenticated user from SecurityContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static AuthenticatedUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user in context");
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public static String getCurrentUserEmail() {
        return getCurrentUser().email();
    }
}
