package com.jobpilot.auth.service;

import com.jobpilot.auth.dto.*;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.DuplicateResourceException;
import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service handling registration, login, and token refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final com.jobpilot.ai.service.CreditService creditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role("USER")
                .build();

        user = userRepository.saveAndFlush(user);
        log.info("User registered: {}", user.getEmail());

        return buildTokenResponse(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.getEnabled()) {
            throw new BadRequestException("Account is disabled");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildTokenResponse(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.isRefreshTokenValid(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(jwtUtil.extractUserId(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BadCredentialsException("Account is disabled");
        }

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildTokenResponse(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new BadRequestException("If an account exists with this email, a reset link has been sent."));

        String token = java.util.UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(java.time.LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // Reset delivery is not exposed until a real email provider is integrated.
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getResetTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset successful for user: {}", user.getEmail());
    }

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .user(TokenResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .onboardingCompleted(user.getOnboardingCompleted())
                        .workMode(user.getWorkMode())
                        .targetRole(user.getTargetRole())
                        .currentOrg(user.getCurrentOrg())
                        .targetSalary(user.getTargetSalary())
                        .credits(creditService.getRemainingCredits(user.getId()))
                        .build())
                .build();
    }
}
