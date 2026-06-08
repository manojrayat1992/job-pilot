package com.jobpilot.networking.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.networking.entity.HiringManager;
import com.jobpilot.networking.service.HiringManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/networking")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Networking", description = "Hiring manager search and contact management")
public class HiringManagerController {

    private final HiringManagerService hiringManagerService;

    @GetMapping("/search")
    @Operation(summary = "Search for hiring managers and recruiters")
    public ApiResponse<List<HiringManager>> search(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String country) {
        return ApiResponse.ok(hiringManagerService.search(domain, function, level, country));
    }

    @PostMapping("/unlock/{id}")
    @Operation(summary = "Unlock contact details using 1 credit")
    public ApiResponse<HiringManager> unlock(@PathVariable UUID id) {
        return ApiResponse.ok(hiringManagerService.unlockContact(id));
    }
}
