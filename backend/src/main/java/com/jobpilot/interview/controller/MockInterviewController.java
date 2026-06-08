package com.jobpilot.interview.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.interview.dto.InterviewResponse;
import com.jobpilot.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    public ApiResponse<InterviewResponse> startInterview(@RequestBody Map<String, Object> request) {
        String role = (String) request.get("role");
        String resumeIdStr = (String) request.get("resumeId");
        UUID resumeId = resumeIdStr != null ? UUID.fromString(resumeIdStr) : null;
        
        return ApiResponse.ok(interviewService.startInterview(role, resumeId), "Interview session initialized");
    }

    @PostMapping("/{sessionId}/message")
    public ApiResponse<InterviewResponse> sendMessage(@PathVariable UUID sessionId, @RequestBody Map<String, String> request) {
        String answer = request.get("answer");
        return ApiResponse.ok(interviewService.processUserAnswer(sessionId, answer), "Agent processed answer");
    }

    @PostMapping(value = "/{sessionId}/message/audio", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> sendAudioMessage(
            @PathVariable UUID sessionId, 
            @RequestPart("audio") MultipartFile audioFile) {
        
        Map<String, Object> result = interviewService.processUserAudio(sessionId, audioFile);
        return ApiResponse.ok(result, "Audio processed successfully");
    }

    @GetMapping("/{sessionId}/report")
    public ApiResponse<InterviewResponse> getReport(@PathVariable UUID sessionId) {
        com.jobpilot.common.util.SecurityUtils.getCurrentUserId(); // Ensure auth
        com.jobpilot.interview.entity.InterviewSession session = interviewService.getSessionById(sessionId);
        return ApiResponse.ok(interviewService.getSessionSummary(session), "Feedback report retrieved");
    }
}
