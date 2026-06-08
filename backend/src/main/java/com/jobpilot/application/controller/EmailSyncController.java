package com.jobpilot.application.controller;

import com.jobpilot.application.service.EmailSyncService;
import com.jobpilot.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/email")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Integrations", description = "External service integrations (Email, Job Boards)")
public class EmailSyncController {

    private final EmailSyncService emailSyncService;

    @PostMapping("/sync")
    @Operation(summary = "Synchronize application statuses from email inbox")
    public ApiResponse<Map<String, Integer>> sync() {
        int updatedCount = emailSyncService.syncInbox();
        return ApiResponse.ok(Map.of("updatedApplications", updatedCount), 
                updatedCount > 0 ? "Successfully synced " + updatedCount + " updates." : "No new updates found.");
    }
}
