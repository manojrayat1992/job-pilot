package com.jobpilot.auth;

import com.jobpilot.common.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    static final String KEY = "test-only-signing-key-at-least-32-bytes-long";
    final JwtUtil jwt = new JwtUtil(KEY, 900000, 604800000);
    final UUID id = UUID.randomUUID(), session = UUID.randomUUID();

    @Test void tokensHaveDistinctPurposesAndUniqueIds() {
        String access = jwt.generateAccessToken(id, "user@example.test", "USER", session);
        String refresh = jwt.generateRefreshToken(id, "user@example.test", session, new Date(System.currentTimeMillis()+60000));
        assertTrue(jwt.isAccessTokenValid(access)); assertFalse(jwt.isRefreshTokenValid(access));
        assertTrue(jwt.isRefreshTokenValid(refresh)); assertFalse(jwt.isAccessTokenValid(refresh));
        assertEquals(session, jwt.extractSessionId(access));
        assertNotEquals(access, jwt.generateAccessToken(id, "user@example.test", "USER", session));
        assertNotEquals(refresh, jwt.generateRefreshToken(id, "user@example.test", session, new Date(System.currentTimeMillis()+60000)));
    }
    @Test void malformedAndExpiredTokensAreRejected() {
        assertFalse(jwt.isAccessTokenValid("not-a-token")); assertFalse(jwt.isRefreshTokenValid(null));
        assertFalse(jwt.isRefreshTokenValid(jwt.generateRefreshToken(id,"user@example.test",session,new Date(0))));
    }
    @Test void issuerAudienceAndRequiredClaimsAreEnforced() {
        var claims = new HashMap<String,Object>(Map.of("userId",id.toString(),"sid",session.toString(),"type","refresh",
                "jti",UUID.randomUUID().toString(),"iss","job-pilot","aud","job-pilot-api","sub","user@example.test",
                "exp",new Date(System.currentTimeMillis()+60000),"iat",new Date()));
        assertTrue(jwt.isRefreshTokenValid(sign(claims)));
        for (String key : List.of("userId","sid","jti","iss","aud","sub","exp","iat","type")) {
            var missing=new HashMap<>(claims);missing.remove(key);
            assertFalse(jwt.isRefreshTokenValid(sign(missing)),"Missing "+key);
        }
        for (String key : List.of("userId","sid","jti","iss","aud","type")) {
            var incorrect=new HashMap<>(claims);incorrect.put(key,"wrong");
            assertFalse(jwt.isRefreshTokenValid(sign(incorrect)),"Wrong "+key);
        }
    }
    @Test void wrongSignatureIsRejected() {
        var other=new JwtUtil("another-signing-key-at-least-32-bytes-long",900000,604800000);
        assertFalse(jwt.isRefreshTokenValid(other.generateRefreshToken(id,"user@example.test")));
    }
    private String sign(Map<String,Object> claims) {
        return Jwts.builder().claims(claims).signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
