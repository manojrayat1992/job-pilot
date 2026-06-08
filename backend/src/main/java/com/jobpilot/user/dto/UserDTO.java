package com.jobpilot.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private String id;
    private String email;
    private String fullName;
    private String phone;
    private String location;
    private String linkedinUrl;
    private String avatarUrl;
    private String role;
    private Boolean onboardingCompleted;
    private String workMode;
    private String targetRole;
    private String currentOrg;
    private String targetSalary;
    private Integer credits;
    private Instant createdAt;
}
