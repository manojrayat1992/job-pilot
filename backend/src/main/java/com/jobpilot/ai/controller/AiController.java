package com.jobpilot.ai.controller;

import com.jobpilot.ai.dto.*;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Services", description = "AI-powered resume optimization, cover letter generation, and interview prep")
public class AiController {

    private final AiService aiService;

    @PostMapping("/optimize-resume")
    @Operation(summary = "Optimize resume for a specific job description")
    public ResponseEntity<ApiResponse<OptimizeResponse>> optimizeResume(
            @Valid @RequestBody OptimizeRequest request) {
        OptimizeResponse response = aiService.optimizeResume(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Resume optimized successfully"));
    }

    @PostMapping("/cover-letter")
    @Operation(summary = "Generate a personalized cover letter")
    public ResponseEntity<ApiResponse<CoverLetterResponse>> generateCoverLetter(
            @Valid @RequestBody CoverLetterRequest request) {
        CoverLetterResponse response = aiService.generateCoverLetter(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cover letter generated"));
    }

    @PostMapping("/answer-question")
    @Operation(summary = "Generate an interview question answer")
    public ResponseEntity<ApiResponse<AnswerResponse>> answerQuestion(
            @Valid @RequestBody AnswerRequest request) {
        AnswerResponse response = aiService.answerQuestion(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Answer generated"));
    }

    @PostMapping("/transcribe")
    @Operation(summary = "Transcribe audio for interview practice")
    public ResponseEntity<ApiResponse<String>> transcribeAudio(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String transcription = aiService.transcribeAudio(file);
        return ResponseEntity.ok(ApiResponse.ok(transcription, "Transcription successful"));
    }

    @PostMapping("/feedback")
    @Operation(summary = "Submit feedback for an AI response")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @Valid @RequestBody AiFeedbackRequest request) {
        aiService.submitFeedback(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Feedback submitted successfully"));
    }
}
