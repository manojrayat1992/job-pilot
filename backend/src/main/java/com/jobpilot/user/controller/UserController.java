package com.jobpilot.user.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.user.dto.OnboardingRequest;
import com.jobpilot.user.dto.UpdateProfileRequest;
import com.jobpilot.user.dto.UserDTO;
import com.jobpilot.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserDTO>> getMe() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getCurrentUser()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(request), "Profile updated"));
    }

    @PostMapping("/onboarding")
    @Operation(summary = "Complete user onboarding and set preferences")
    public ResponseEntity<ApiResponse<UserDTO>> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        UserDTO updatedUser = userService.completeOnboarding(request);
        return ResponseEntity.ok(ApiResponse.ok(updatedUser, "Onboarding completed"));
    }
}
