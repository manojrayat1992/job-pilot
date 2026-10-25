package com.jobpilot.ai.controller;

import com.jobpilot.ai.service.CreditService;
import com.jobpilot.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/admin/credits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CreditAdminController {
    private final CreditService credits;
    public record Reconciliation(@NotBlank @Size(max=400) String reason) {}
    @PostMapping("/{reservationId}/release")
    public ApiResponse<Void> release(@PathVariable UUID reservationId, @Valid @RequestBody Reconciliation request) {
        credits.releaseInterrupted(reservationId, request.reason());
        return ApiResponse.ok(null);
    }
}
