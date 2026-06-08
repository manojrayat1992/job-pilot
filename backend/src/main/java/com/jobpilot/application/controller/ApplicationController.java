package com.jobpilot.application.controller;

import com.jobpilot.application.dto.*;
import com.jobpilot.application.service.ApplicationService;
import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Applications", description = "Job application tracking and management")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Create a new job application")
    public ResponseEntity<ApiResponse<ApplicationDTO>> create(
            @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationDTO dto = applicationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Application created"));
    }

    @GetMapping
    @Operation(summary = "List job applications (optional status filter)")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationDTO>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = applicationService.list(status, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(pageResult)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application details")
    public ResponseEntity<ApiResponse<ApplicationDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.get(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an application")
    public ResponseEntity<ApiResponse<ApplicationDTO>> update(
            @PathVariable UUID id,
            @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.update(id, request), "Application updated"));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update application status")
    public ResponseEntity<ApiResponse<ApplicationDTO>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.updateStatus(id, status), "Status updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Application deleted"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get application statistics for dashboard")
    public ResponseEntity<ApiResponse<ApplicationStatsDTO>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getStats()));
    }
}
