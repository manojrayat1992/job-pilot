package com.jobpilot.user.service;

import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.user.dto.UpdateProfileRequest;
import com.jobpilot.user.dto.UserDTO;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final com.jobpilot.ai.service.CreditService creditService;

    public UserDTO getCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateProfile(UpdateProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getLocation() != null) {
            user.setLocation(request.getLocation().trim());
        }
        if (request.getLinkedinUrl() != null) {
            user.setLinkedinUrl(request.getLinkedinUrl().trim());
        }

        user = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
        return toDTO(user);
    }

    @Transactional
    public UserDTO completeOnboarding(com.jobpilot.user.dto.OnboardingRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setWorkMode(request.getWorkMode());
        user.setTargetRole(request.getTargetRole());
        user.setCurrentOrg(request.getCurrentOrg());
        user.setTargetSalary(request.getTargetSalary());
        user.setOnboardingCompleted(true);

        user = userRepository.save(user);
        log.info("Onboarding completed for user: {}", user.getEmail());
        return toDTO(user);
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .location(user.getLocation())
                .linkedinUrl(user.getLinkedinUrl())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .onboardingCompleted(user.getOnboardingCompleted())
                .workMode(user.getWorkMode())
                .targetRole(user.getTargetRole())
                .currentOrg(user.getCurrentOrg())
                .targetSalary(user.getTargetSalary())
                .credits(creditService.getRemainingCredits(user.getId()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
