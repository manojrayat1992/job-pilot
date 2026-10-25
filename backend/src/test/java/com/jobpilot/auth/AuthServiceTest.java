package com.jobpilot.auth;

import com.jobpilot.auth.dto.RefreshRequest;
import com.jobpilot.auth.service.AuthService;
import com.jobpilot.common.util.JwtUtil;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final JwtUtil jwt = new JwtUtil("test-only-signing-key-at-least-32-bytes-long", 900000, 604800000);
    final AuthService auth = new AuthService(users, mock(com.jobpilot.ai.service.CreditService.class), mock(PasswordEncoder.class), jwt);
    final UUID id = UUID.randomUUID();

    @Test void accessTokenCannotBeUsedToRefresh() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(jwt.generateAccessToken(id, "user@example.test", "USER"));
        assertThrows(BadCredentialsException.class, () -> auth.refresh(request));
        verifyNoInteractions(users);
    }

    @Test void disabledAccountCannotRefresh() {
        when(users.findById(id)).thenReturn(Optional.of(User.builder().id(id).enabled(false).build()));
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(jwt.generateRefreshToken(id, "user@example.test"));
        assertThrows(BadCredentialsException.class, () -> auth.refresh(request));
    }

    @Test void enabledAccountCanRefresh() {
        when(users.findById(id)).thenReturn(Optional.of(User.builder().id(id).email("user@example.test").fullName("User").build()));
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(jwt.generateRefreshToken(id, "user@example.test"));
        var response = auth.refresh(request);
        assertTrue(jwt.isAccessTokenValid(response.getAccessToken()));
        assertTrue(jwt.isRefreshTokenValid(response.getRefreshToken()));
    }

    @Test void malformedTokensAreRejected() {
        assertFalse(jwt.isAccessTokenValid("not-a-token"));
        assertFalse(jwt.isRefreshTokenValid(null));
    }
}
