package com.jobpilot.ai.controller;

import com.jobpilot.ai.agent.AgentDtos;
import com.jobpilot.ai.entity.CultureFitAssessment;
import com.jobpilot.ai.entity.EmailSequence;
import com.jobpilot.ai.entity.ModelVersion;
import com.jobpilot.ai.entity.SalaryNegotiation;
import com.jobpilot.ai.entity.ConversationHistory;
import com.jobpilot.ai.repository.CultureFitAssessmentRepository;
import com.jobpilot.ai.repository.EmailSequenceRepository;
import com.jobpilot.ai.repository.ModelVersionRepository;
import com.jobpilot.ai.repository.SalaryNegotiationRepository;
import com.jobpilot.ai.repository.ConversationHistoryRepository;
import com.jobpilot.ai.service.*;
import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for all new AI-powered features:
 * - Salary Negotiation
 * - LinkedIn Profile Optimizer
 * - Email Drip Sequences
 * - Company Culture Fit
 * - Resume Version Diffing
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Features", description = "Advanced AI-powered career tools")
public class NewFeaturesController {

    private final SalaryNegotiationService salaryService;
    private final LinkedInOptimizerService linkedInService;
    private final EmailDripService emailDripService;
    private final CultureFitService cultureFitService;
    private final ResumeDiffService resumeDiffService;
    private final UsageService usageService;
    private final SalaryNegotiationRepository salaryNegotiationRepository;
    private final CultureFitAssessmentRepository cultureFitAssessmentRepository;
    private final EmailSequenceRepository emailSequenceRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ConversationHistoryRepository conversationHistoryRepository;

    // ─── Usage Dashboard ───

    @GetMapping("/usage")
    @Operation(summary = "Get user AI usage statistics and activity log")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUsageStats() {
        Map<String, Object> stats = usageService.getUserUsageStats();
        return ResponseEntity.ok(ApiResponse.ok(stats, "Usage stats retrieved"));
    }

    // ─── Salary Negotiation ───

    @PostMapping("/salary/analyze")
    @Operation(summary = "Analyze a job offer and generate counter-offer strategy")
    public ResponseEntity<ApiResponse<AgentDtos.SalaryAnalysisResponse>> analyzeSalary(
            @RequestBody AgentDtos.SalaryAnalysisRequest request) {
        AgentDtos.SalaryAnalysisResponse response = salaryService.analyzeOffer(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Salary analysis complete"));
    }

    @GetMapping("/salary/history")
    @Operation(summary = "Get user's salary negotiation history")
    public ResponseEntity<ApiResponse<List<SalaryNegotiation>>> getSalaryHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<SalaryNegotiation> history = salaryNegotiationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Salary history retrieved"));
    }

    // ─── LinkedIn Optimizer ───

    @PostMapping("/linkedin/optimize")
    @Operation(summary = "Optimize LinkedIn profile sections for maximum recruiter visibility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> optimizeLinkedIn(
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = linkedInService.optimizeProfile(
                request.get("headline"),
                request.get("summary"),
                request.get("experience"),
                request.get("targetRole")
        );
        return ResponseEntity.ok(ApiResponse.ok(result, "LinkedIn optimization complete"));
    }

    // ─── Email Drip Sequences ───

    @PostMapping("/outreach/sequence")
    @Operation(summary = "Generate a full 5-step email drip sequence for cold outreach")
    public ResponseEntity<ApiResponse<AgentDtos.OutreachResponse>> generateEmailSequence(
            @RequestBody AgentDtos.OutreachRequest request) {
        AgentDtos.OutreachResponse response = emailDripService.generateSequence(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Email sequence generated"));
    }

    @GetMapping("/outreach/history")
    @Operation(summary = "Get user's email outreach sequence history")
    public ResponseEntity<ApiResponse<List<EmailSequence>>> getEmailSequenceHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<EmailSequence> history = emailSequenceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Email sequence history retrieved"));
    }

    // ─── Culture Fit ───

    @PostMapping("/culture-fit")
    @Operation(summary = "Assess culture fit between candidate and target company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assessCultureFit(
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = cultureFitService.assessCultureFit(
                request.get("companyName"),
                request.get("values")
        );
        return ResponseEntity.ok(ApiResponse.ok(result, "Culture fit assessment complete"));
    }

    @GetMapping("/culture-fit/history")
    @Operation(summary = "Get user's culture fit assessment history")
    public ResponseEntity<ApiResponse<List<CultureFitAssessment>>> getCultureFitHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<CultureFitAssessment> history = cultureFitAssessmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Culture fit history retrieved"));
    }

    // ─── Resume Diffing ───

    @PostMapping("/resume/diff")
    @Operation(summary = "Compute a visual diff between two resume versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diffResumes(
            @RequestBody Map<String, Object> request) {
        String originalText = (String) request.get("originalText");
        String tailoredText = (String) request.get("tailoredText");
        Map<String, Object> result = resumeDiffService.computeDiff(originalText, tailoredText);
        return ResponseEntity.ok(ApiResponse.ok(result, "Resume diff computed"));
    }

    @PostMapping("/resume/diff/structured")
    @Operation(summary = "Compute a structured section-by-section diff between two resume JSON versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diffResumesStructured(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> original = (Map<String, Object>) request.get("original");
        @SuppressWarnings("unchecked")
        Map<String, Object> tailored = (Map<String, Object>) request.get("tailored");
        Map<String, Object> result = resumeDiffService.computeStructuredDiff(original, tailored);
        return ResponseEntity.ok(ApiResponse.ok(result, "Structured diff computed"));
    }

    // ─── Model Version Management (Admin) ───

    @GetMapping("/admin/models")
    @Operation(summary = "Get all AI model versions (Admin)")
    public ResponseEntity<ApiResponse<List<ModelVersion>>> getAllModelVersions() {
        List<ModelVersion> models = modelVersionRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(models, "Model versions retrieved"));
    }

    @PostMapping("/admin/models")
    @Operation(summary = "Create a new AI model version (Admin)")
    public ResponseEntity<ApiResponse<ModelVersion>> createModelVersion(@RequestBody Map<String, Object> request) {
        ModelVersion model = ModelVersion.builder()
                .versionName((String) request.get("versionName"))
                .modelPath((String) request.get("modelPath"))
                .isActive((Boolean) request.getOrDefault("isActive", false))
                .trafficWeight(((Number) request.getOrDefault("trafficWeight", 0)).intValue())
                .build();
        model = modelVersionRepository.save(model);
        return ResponseEntity.ok(ApiResponse.ok(model, "Model version created"));
    }

    @PutMapping("/admin/models/{id}")
    @Operation(summary = "Update an AI model version (Admin)")
    public ResponseEntity<ApiResponse<ModelVersion>> updateModelVersion(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        ModelVersion model = modelVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Model version not found"));
        
        if (request.containsKey("isActive")) {
            model.setIsActive((Boolean) request.get("isActive"));
        }
        if (request.containsKey("trafficWeight")) {
            model.setTrafficWeight(((Number) request.get("trafficWeight")).intValue());
        }
        if (request.containsKey("modelPath")) {
            model.setModelPath((String) request.get("modelPath"));
        }
        
        model = modelVersionRepository.save(model);
        return ResponseEntity.ok(ApiResponse.ok(model, "Model version updated"));
    }

    @DeleteMapping("/admin/models/{id}")
    @Operation(summary = "Delete an AI model version (Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteModelVersion(@PathVariable UUID id) {
        modelVersionRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Model version deleted"));
    }

    // ─── Conversation History ───

    @GetMapping("/conversation-history")
    @Operation(summary = "Get user's conversation history")
    public ResponseEntity<ApiResponse<List<ConversationHistory>>> getConversationHistory(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) UUID resumeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<ConversationHistory> history;
        
        if (jobId != null) {
            history = conversationHistoryRepository.findByUserIdAndJobIdOrderByCreatedAtAsc(userId, jobId);
        } else if (resumeId != null) {
            history = conversationHistoryRepository.findByUserIdAndResumeIdOrderByCreatedAtAsc(userId, resumeId);
        } else {
            history = conversationHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        
        return ResponseEntity.ok(ApiResponse.ok(history, "Conversation history retrieved"));
    }
}
