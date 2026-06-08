package com.jobpilot.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String fullName;

    @Size(max = 50)
    private String phone;

    @Size(max = 255)
    private String location;

    @Size(max = 500)
    private String linkedinUrl;
}
