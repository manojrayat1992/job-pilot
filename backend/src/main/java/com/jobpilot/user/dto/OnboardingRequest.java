package com.jobpilot.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {
    
    @NotBlank(message = "Work mode is required")
    private String workMode;
    
    @NotBlank(message = "Target role is required")
    private String targetRole;
    
    private String currentOrg;
    
    private String targetSalary;
}
