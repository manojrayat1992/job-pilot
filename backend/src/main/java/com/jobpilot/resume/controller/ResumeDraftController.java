package com.jobpilot.resume.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.resume.dto.*;
import com.jobpilot.resume.service.ResumeDraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResumeDraftController {
    private final ResumeDraftService service;
    public record ApprovalRequest(boolean confirmed) {}

    @PostMapping("/resumes/{id}/drafts")
    public ResponseEntity<ApiResponse<ResumeDraftDTO>> generate(@PathVariable UUID id, @Valid @RequestBody TailorResumeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.generate(id, request), "Draft saved for review. Original unchanged."));
    }
    @GetMapping("/resumes/{id}/drafts")
    public ApiResponse<List<ResumeDraftDTO>> list(@PathVariable UUID id) { return ApiResponse.ok(service.list(id)); }
    @GetMapping("/resume-drafts/{id}")
    public ApiResponse<ResumeDraftDTO> get(@PathVariable UUID id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping("/resume-drafts/{id}/approve")
    public ApiResponse<ResumeDraftDTO> approve(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ApiResponse.ok(service.approve(id, request.confirmed()));
    }
    @GetMapping("/resume-drafts/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviewed-resume.pdf\"")
                .body(service.download(id));
    }
}
