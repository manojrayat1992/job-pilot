package com.jobpilot.resume.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.resume.entity.ResumeTemplate;
import com.jobpilot.resume.repository.ResumeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resume-templates")
@RequiredArgsConstructor
public class ResumeTemplateController {

    private final ResumeTemplateRepository templateRepository;

    @GetMapping
    public ApiResponse<List<ResumeTemplate>> getAllTemplates() {
        return ApiResponse.ok(templateRepository.findByIsActiveTrue());
    }
}
