package com.jobpilot.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT utility for token generation and validation.
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes())));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return generateAccessToken(userId, email, role, UUID.randomUUID());
    }

    public String generateAccessToken(UUID userId, String email, String role, UUID sessionId) {
        return buildToken(Map.of(
                "userId", userId.toString(),
                "type", "access",
                "sid", sessionId.toString(),
                "role", role
        ), email, accessTokenExpirationMs);
    }

    public String generateRefreshToken(UUID userId, String email) {
        return generateRefreshToken(userId, email, UUID.randomUUID(), new Date(System.currentTimeMillis()+refreshTokenExpirationMs));
    }

    public String generateRefreshToken(UUID userId, String email, UUID sessionId, Date expiresAt) {
        return buildToken(Map.of("userId", userId.toString(), "type", "refresh", "sid", sessionId.toString()), email, expiresAt);
    }

    public UUID extractSessionId(String token) {
        return UUID.fromString(extractClaim(token, claims -> claims.get("sid", String.class)));
    }

    public long refreshLifetimeMs() { return refreshTokenExpirationMs; }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        return buildToken(claims, subject, new Date(System.currentTimeMillis()+expirationMs));
    }

    private String buildToken(Map<String, Object> claims, String subject, Date expiresAt) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuer("job-pilot")
                .claim("aud", "job-pilot-api")
                .subject(subject)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, claims -> claims.get("userId", String.class)));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
        }
        return false;
    }

    public boolean isAccessTokenValid(String token) {
        return hasValidPurpose(token, "access");
    }

    public boolean isRefreshTokenValid(String token) {
        return hasValidPurpose(token, "refresh");
    }

    public record RefreshIdentity(UUID userId, UUID sessionId) {}

    /** Validate and read one claims snapshot, avoiding expiration between successive parses. */
    public Optional<RefreshIdentity> refreshIdentity(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (!hasValidPurpose(claims, "refresh")) return Optional.empty();
            return Optional.of(new RefreshIdentity(UUID.fromString(claims.get("userId", String.class)),
                    UUID.fromString(claims.get("sid", String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private boolean hasValidPurpose(String token, String purpose) {
        try {
            return hasValidPurpose(extractAllClaims(token), purpose);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private boolean hasValidPurpose(Claims claims, String purpose) {
        UUID.fromString(claims.get("userId", String.class));
        UUID.fromString(claims.get("sid", String.class));
        UUID.fromString(claims.getId());
        return claims.getExpiration() != null && claims.getIssuedAt() != null
                && purpose.equals(claims.get("type", String.class))
                && claims.getSubject() != null && !claims.getSubject().isBlank();
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .requireIssuer("job-pilot")
                .requireAudience("job-pilot-api")
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
