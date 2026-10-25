package com.jobpilot.auth.filter;

import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT authentication filter that validates Bearer tokens on every request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtUtil.isAccessTokenValid(token)) {
            try {
                UUID userId = jwtUtil.extractUserId(token);
                userRepository.findById(userId)
                        .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                        .ifPresent(user -> {
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    new AuthenticatedUser(userId, user.getEmail(), user.getRole()), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            } catch (Exception e) {
                log.debug("Failed to set authentication from JWT: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * Principal object stored in SecurityContext after successful JWT validation.
     */
    public record AuthenticatedUser(UUID userId, String email, String role) {}
}
