package com.jobpilot.auth.service;

import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** A login creates a token family. Rotation, replay revocation and logout serialize on the owner. */
@Service
public class AuthSessionService {
    private final JdbcTemplate jdbc;
    private final JwtUtil jwt;
    private final TransactionTemplate required;
    private final TransactionTemplate independent;
    public AuthSessionService(JdbcTemplate jdbc, JwtUtil jwt, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.jwt=jwt;
        required=new TransactionTemplate(manager);
        independent=new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Tokens(UUID userId, String accessToken, String refreshToken) {}

    public Tokens create(User authenticated) {
        return required.execute(tx->{
            var user=lockUser(authenticated.getId());
            // A password reset or disabling the account during password verification must win.
            if(user==null || !Boolean.TRUE.equals(user.get("enabled")) || !Objects.equals(user.get("password_hash"),authenticated.getPasswordHash()))
                throw denied();
            UUID session=UUID.randomUUID();
            var expires=Timestamp.from(Instant.now().plusMillis(jwt.refreshLifetimeMs()));
            jdbc.update("INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,?)",session,authenticated.getId(),expires);
            return issue(authenticated.getId(),session,user,expires);
        });
    }

    public Tokens rotate(String refreshToken) {
        var identity=jwt.refreshIdentity(refreshToken).orElseThrow(this::denied);
        UUID userId=identity.userId(), session=identity.sessionId();
        // Return an unsuccessful outcome, commit the revocation, THEN throw. A 401 must not roll it back.
        Tokens result=independent.execute(tx->{
            var user=lockUser(userId);
            if(user==null || !Boolean.TRUE.equals(user.get("enabled")))return null;
            var rows=jdbc.queryForList("""
                SELECT s.expires_at,t.used_at FROM auth_sessions s JOIN auth_refresh_tokens t ON t.session_id=s.id
                WHERE s.id=? AND s.user_id=? AND t.token_hash=? AND s.revoked_at IS NULL
                  AND s.expires_at>now() AND t.expires_at>now() FOR UPDATE OF s,t
                """,session,userId,hash(refreshToken));
            if(rows.isEmpty())return null;
            var row=rows.getFirst();
            if(row.get("used_at")!=null) {
                revoke(session,userId,"REFRESH_REPLAY");
                return null;
            }
            jdbc.update("UPDATE auth_refresh_tokens SET used_at=now() WHERE token_hash=?",hash(refreshToken));
            return issue(userId,session,user,(Timestamp)row.get("expires_at"));
        });
        if(result==null)throw denied();
        return result;
    }

    public boolean isActive(UUID session,UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN users u ON u.id=s.user_id
            WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>now() AND u.enabled)
            """,Boolean.class,session,user));
    }

    /** Possession of an issued refresh token can revoke its family even after that token was rotated. */
    public void logout(String refreshToken) {
        var identity=jwt.refreshIdentity(refreshToken);
        if(identity.isEmpty())return;
        UUID user=identity.get().userId(),session=identity.get().sessionId();
        independent.executeWithoutResult(tx->{
            if(lockUser(user)==null)return;
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_refresh_tokens WHERE session_id=? AND token_hash=?)",Boolean.class,session,hash(refreshToken))))
                revoke(session,user,"LOGOUT");
        });
    }
    public void logoutAll(UUID user) {
        independent.executeWithoutResult(tx->{
            if(lockUser(user)==null)return;
            jdbc.update("UPDATE auth_sessions SET revoked_at=now(),revocation_reason='LOGOUT_ALL' WHERE user_id=? AND revoked_at IS NULL",user);
        });
    }
    private Tokens issue(UUID id,UUID session,Map<String,Object> user,Timestamp expires) {
        String email=user.get("email").toString();
        String refresh=jwt.generateRefreshToken(id,email,session,expires);
        jdbc.update("INSERT INTO auth_refresh_tokens(token_hash,session_id,expires_at) VALUES(?,?,?)",hash(refresh),session,expires);
        return new Tokens(id,jwt.generateAccessToken(id,email,user.get("role").toString(),session),refresh);
    }
    private Map<String,Object> lockUser(UUID user) {
        var rows=jdbc.queryForList("SELECT email,role,enabled,password_hash FROM users WHERE id=? FOR UPDATE",user);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private void revoke(UUID session,UUID user,String reason) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=now(),revocation_reason=? WHERE id=? AND user_id=? AND revoked_at IS NULL",reason,session,user);
    }
    private BadCredentialsException denied(){return new BadCredentialsException("Session invalid or expired; sign in again");}
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
