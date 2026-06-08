package com.jobpilot.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import com.jobpilot.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CopilotController {

    private final ResumeRepository resumeRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @PostMapping("/map-fields")
    public ResponseEntity<ApiResponse<Map<String, Object>>> mapFields(
            @RequestBody MapFieldsRequest request) {
        
        log.info("Received field mapping request for {} fields", request.getFields().size());
        
        Resume resume = resumeRepository.findById(request.getResumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId()));
        
        try {
            // Convert list of fields to a simple JSON array string for the AI
            String formFieldsJson = objectMapper.writeValueAsString(request.getFields());
            
            // Perform the semantic mapping using AI
            Map<String, Object> mappings = aiService.mapFormFields(
                    formFieldsJson, 
                    resume.getParsedJson() != null ? resume.getParsedJson().toString() : "", 
                    ""
            );
            
            return ResponseEntity.ok(ApiResponse.ok(mappings, "Fields mapped successfully"));
        } catch (Exception e) {
            log.error("Failed to process field mapping: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(ApiResponse.error("AI processing failed"));
        }
    }

    @lombok.Data
    public static class MapFieldsRequest {
        private java.util.UUID resumeId;
        private List<String> fields;
    }
}
