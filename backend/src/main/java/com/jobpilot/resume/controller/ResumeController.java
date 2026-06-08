package com.jobpilot.resume.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.dto.PageResponse;
import com.jobpilot.resume.dto.ResumeDTO;
import com.jobpilot.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resumes", description = "Resume upload, parsing, versioning, and PDF export")
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and parse a resume (PDF/DOCX)")
    public ResponseEntity<ApiResponse<ResumeDTO>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "isTransient", defaultValue = "false") boolean isTransient) {
        ResumeDTO dto = resumeService.uploadAndParse(file, title, isTransient);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Resume uploaded and parsed successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a blank resume or create from existing data")
    public ResponseEntity<ApiResponse<ResumeDTO>> create(@RequestBody(required = false) Map<String, Object> body) {
        String title = body != null ? (String) body.get("title") : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedJson = body != null ? (Map<String, Object>) body.get("parsedJson") : null;
        
        ResumeDTO dto = resumeService.createResume(title, parsedJson);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "Resume created successfully"));
    }

    @GetMapping
    @Operation(summary = "List current user's resumes")
    public ResponseEntity<ApiResponse<PageResponse<ResumeDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = resumeService.listResumes(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(pageResult)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific resume")
    public ResponseEntity<ApiResponse<ResumeDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.getResume(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update resume metadata (title, tags, parsed content)")
    public ResponseEntity<ApiResponse<ResumeDTO>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.updateResume(id, updates)));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Set a resume as the active version")
    public ResponseEntity<ApiResponse<ResumeDTO>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.activateResume(id), "Resume activated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resume")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        resumeService.deleteResume(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Resume deleted"));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download resume as PDF")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        byte[] pdfBytes = resumeService.downloadPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resume.pdf\"")
                .body(pdfBytes);
    }

    @PostMapping("/{id}/tailor-and-download")
    @Operation(summary = "Tailor resume to job description and download as PDF")
    public ResponseEntity<byte[]> tailorAndDownload(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        String jobDescription = request.get("jobDescription");
        byte[] pdfBytes = resumeService.downloadTailoredPdf(id, jobDescription);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored_resume.pdf\"")
                .body(pdfBytes);
    }

    @PostMapping("/{id}/analyze-ats")
    @Operation(summary = "Perform deep ATS compatibility analysis")
    public ResponseEntity<ApiResponse<ResumeDTO>> analyzeAts(@PathVariable UUID id) {
        ResumeDTO dto = resumeService.runAtsAnalysis(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "ATS analysis completed"));
    }
}
