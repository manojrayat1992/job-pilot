package com.jobpilot.ai.service;

import com.jobpilot.common.exception.CreditExhaustedException;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CreditService — validates credit enforcement logic.
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreditService creditService;

    private UUID userId;
    private User freeUser;
    private User proUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        freeUser = User.builder()
                .id(userId)
                .email("free@test.com")
                .fullName("Free User")
                .passwordHash("hash")
                .role("USER")
                .credits(5)
                .build();

        proUser = User.builder()
                .id(userId)
                .email("pro@test.com")
                .fullName("Pro User")
                .passwordHash("hash")
                .role("PRO")
                .credits(0)
                .build();

        // Mock security context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null));
    }

    @Test
    @DisplayName("Should allow free user with credits")
    void shouldAllowFreeUserWithCredits() {
        when(userRepository.findById(any())).thenReturn(Optional.of(freeUser));
        assertDoesNotThrow(() -> creditService.requireCredits());
    }

    @Test
    @DisplayName("Should block free user with zero credits")
    void shouldBlockFreeUserWithZeroCredits() {
        freeUser.setCredits(0);
        when(userRepository.findById(any())).thenReturn(Optional.of(freeUser));
        assertThrows(CreditExhaustedException.class, () -> creditService.requireCredits());
    }

    @Test
    @DisplayName("Should always allow Pro users even with 0 credits")
    void shouldAlwaysAllowProUsers() {
        when(userRepository.findById(any())).thenReturn(Optional.of(proUser));
        assertDoesNotThrow(() -> creditService.requireCredits());
    }

    @Test
    @DisplayName("Should decrement credits for free user on consume")
    void shouldDecrementCredits() {
        when(userRepository.findById(any())).thenReturn(Optional.of(freeUser));
        when(userRepository.save(any())).thenReturn(freeUser);

        creditService.consumeCredit();

        assertEquals(4, freeUser.getCredits());
        verify(userRepository).save(freeUser);
    }

    @Test
    @DisplayName("Should NOT decrement credits for Pro user on consume")
    void shouldNotDecrementProCredits() {
        when(userRepository.findById(any())).thenReturn(Optional.of(proUser));

        creditService.consumeCredit();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return unlimited for Pro users")
    void shouldReturnUnlimitedForPro() {
        when(userRepository.findById(any())).thenReturn(Optional.of(proUser));

        int remaining = creditService.getRemainingCredits();
        assertEquals(Integer.MAX_VALUE, remaining);
    }

    @Test
    @DisplayName("Should return exact credits for free users")
    void shouldReturnExactCreditsForFree() {
        when(userRepository.findById(any())).thenReturn(Optional.of(freeUser));

        int remaining = creditService.getRemainingCredits();
        assertEquals(5, remaining);
    }

    @Test
    @DisplayName("Should add credits to user")
    void shouldAddCredits() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(freeUser));
        when(userRepository.save(any())).thenReturn(freeUser);

        creditService.addCredits(userId, 10);

        assertEquals(15, freeUser.getCredits());
        verify(userRepository).save(freeUser);
    }

    @Test
    @DisplayName("Credits should never go below zero")
    void creditsShouldNotGoBelowZero() {
        freeUser.setCredits(0);
        when(userRepository.findById(any())).thenReturn(Optional.of(freeUser));
        when(userRepository.save(any())).thenReturn(freeUser);

        creditService.consumeCredit();

        assertEquals(0, freeUser.getCredits());
    }
}
