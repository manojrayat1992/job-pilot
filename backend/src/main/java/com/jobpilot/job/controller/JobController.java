package com.jobpilot.job.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.dto.PageResponse;
import com.jobpilot.job.dto.JobAnalysisRequest;
import com.jobpilot.job.dto.JobDTO;
import com.jobpilot.job.dto.MatchScoreResponse;
import com.jobpilot.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Jobs", description = "Job description analysis and matching")
public class JobController {

    private final JobService jobService;

    @PostMapping("/analyze")
    @Operation(summary = "Analyze a job description (paste text or provide URL)")
    public ResponseEntity<ApiResponse<JobDTO>> analyze(@RequestBody JobAnalysisRequest request) {
        JobDTO dto = jobService.analyzeJob(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Job description analyzed successfully"));
    }

    @GetMapping
    @Operation(summary = "List saved job descriptions")
    public ResponseEntity<ApiResponse<PageResponse<JobDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = jobService.listJobs(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(pageResult)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific job")
    public ResponseEntity<ApiResponse<JobDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved job")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Job deleted"));
    }

    @PostMapping("/{id}/match")
    @Operation(summary = "Calculate match score between a job and resume")
    public ResponseEntity<ApiResponse<MatchScoreResponse>> match(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        UUID resumeId = UUID.fromString(body.get("resumeId"));
        return ResponseEntity.ok(ApiResponse.ok(jobService.matchScore(id, resumeId)));
    }
}
