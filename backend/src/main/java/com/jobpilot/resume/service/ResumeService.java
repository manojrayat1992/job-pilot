package com.jobpilot.resume.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.resume.dto.ResumeDTO;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import com.jobpilot.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeParserService parserService;
    private final PdfExportService pdfExportService;
    private final FileStorageService fileStorageService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ResumeContentService contentService;

    @Transactional
    public ResumeDTO uploadAndParse(MultipartFile file, String title, boolean isTransient) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // 1. Extract raw text using Tika
        String rawText = parserService.extractText(file);

        // 2. Store original file
        String filePath = fileStorageService.store(file, "resumes/" + userId);

        // 3. Parse with AI to get structured JSON
        Map<String, Object> parsedJson = aiService.parseResumeText(rawText);

        // 4. Determine version number (only count permanent resumes)
        long existingCount = resumeRepository.countByUserIdAndIsTransientFalse(userId);

        // 5. Build & save entity
        Resume resume = Resume.builder()
                .userId(userId)
                .title(title != null ? title : file.getOriginalFilename())
                .version((int) existingCount + 1)
                .isActive(existingCount == 0) // First resume is auto-active
                .rawText(rawText)
                .parsedJson(parsedJson)
                .isTransient(isTransient)
                .filePath(filePath)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .build();

        resume = resumeRepository.save(resume);
        log.info("Resume uploaded and parsed: id={}, user={}", resume.getId(), userId);

        // The database queues embeddings when the source transaction commits.

        return toDTO(resume);
    }

    @Transactional
    public ResumeDTO createResume(String title, Map<String, Object> parsedJson) {
        UUID userId = SecurityUtils.getCurrentUserId();
        long existingCount = resumeRepository.countByUserIdAndIsTransientFalse(userId);

        Map<String, Object> finalJson = parsedJson != null ? parsedJson : Map.of(
                "personalInfo", Map.of("name", "", "email", ""),
                "summary", "",
                "experience", java.util.List.of(),
                "education", java.util.List.of(),
                "skills", java.util.List.of(),
                "projects", java.util.List.of()
        );

        finalJson = contentService.validatedCopy(finalJson);
        Resume resume = Resume.builder()
                .userId(userId)
                .title(title != null && !title.isBlank() ? title : "New Resume")
                .version((int) existingCount + 1)
                .isActive(existingCount == 0)
                .parsedJson(finalJson)
                .rawText(contentService.toText(finalJson))
                .fileType("application/json")
                .build();

        resume = resumeRepository.save(resume);
        log.info("Resume created: id={}, user={}", resume.getId(), userId);
        return toDTO(resume);
    }

    public Page<ResumeDTO> listResumes(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return resumeRepository.findByUserIdAndIsTransientFalseOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDTO);
    }

    public ResumeDTO getResume(UUID resumeId) {
        Resume resume = findByIdAndCurrentUser(resumeId);
        return toDTO(resume);
    }

    @Transactional
    public ResumeDTO activateResume(UUID resumeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Resume resume = findByIdAndCurrentUser(resumeId);

        resumeRepository.deactivateAllForUser(userId);
        resume.setIsActive(true);
        resume = resumeRepository.save(resume);

        log.info("Resume activated: id={}", resumeId);
        return toDTO(resume);
    }

    @Transactional
    public void deleteResume(UUID resumeId) {
        Resume resume = findByIdAndCurrentUser(resumeId);

        if (resume.getFilePath() != null) {
            fileStorageService.delete(resume.getFilePath());
        }
        resumeRepository.delete(resume);
        log.info("Resume deleted: id={}", resumeId);
    }

    public byte[] downloadPdf(UUID resumeId) {
        Resume resume = findByIdAndCurrentUser(resumeId);
        if (resume.getParsedJson() == null) {
            throw new BadRequestException("Resume has not been parsed yet");
        }
        return pdfExportService.generatePdf(resume.getParsedJson());
    }

    public byte[] downloadTailoredPdf(UUID resumeId, String jobDescription) {
        findByIdAndCurrentUser(resumeId);
        throw new BadRequestException("Create and review a tailoring draft before exporting it.");
    }

    @Transactional
    public ResumeDTO createReviewedVersion(Resume source, Map<String, Object> proposed, UUID jobId, String jobText) {
        if (!SecurityUtils.getCurrentUserId().equals(source.getUserId())) {
            throw new ResourceNotFoundException("Resume", "id", source.getId());
        }
        var version = newVersion(source, proposed);
        String title = source.getTitle();
        version.setTitle(title.substring(0, Math.min(title.length(), 220)) + " (Reviewed)");
        version.setTargetJobId(jobId);
        version.setTargetJobDescription(jobText);
        version = resumeRepository.save(version);
        return toDTO(version);
    }

    private Resume newVersion(Resume source, Map<String, Object> proposed) {
        var clean = contentService.validatedCopy(proposed);
        return Resume.builder().userId(source.getUserId()).title(source.getTitle())
                .version(source.getVersion() + 1).parentResumeId(source.getId())
                .targetJobId(source.getTargetJobId()).targetJobDescription(source.getTargetJobDescription())
                .parsedJson(clean).rawText(contentService.toText(clean)).fileType("application/json")
                .templateId(source.getTemplateId()).tags(source.getTags() == null ? null : source.getTags().clone())
                .isActive(false).isTransient(false).build();
    }

    @Transactional
    public ResumeDTO updateResume(UUID resumeId, Map<String, Object> updates) {
        Resume resume = findByIdAndCurrentUser(resumeId);

        boolean contentChanged = updates.containsKey("parsedJson");
        if (contentChanged) {
            if (!(updates.get("parsedJson") instanceof Map<?, ?>)) throw new BadRequestException("Invalid resume content.");
            @SuppressWarnings("unchecked")
            var proposed = (Map<String, Object>) updates.get("parsedJson");
            resume = newVersion(resume, proposed);
        }
        if (updates.containsKey("title")) {
            if (!(updates.get("title") instanceof String title) || title.isBlank() || title.length() > 255) {
                throw new BadRequestException("Resume title must be between 1 and 255 characters.");
            }
            resume.setTitle((String) updates.get("title"));
        }
        if (updates.get("tags") instanceof java.util.List<?> list) {
            resume.setTags(list.stream().map(Object::toString).toArray(String[]::new));
        }

        resume = resumeRepository.save(resume);
        return toDTO(resume);
    }

    @Transactional
    public ResumeDTO runAtsAnalysis(UUID resumeId) {
        Resume resume = findByIdAndCurrentUser(resumeId);
        if (resume.getRawText() == null || resume.getRawText().isBlank()) {
            throw new BadRequestException("Resume text is empty. Cannot perform ATS analysis.");
        }

        com.jobpilot.ai.dto.AtsAnalysisResponse atsAnalysis = aiService.analyzeAtsCompatibility(resume.getUserId(), resume.getRawText());
        
        resume.setAtsScore(atsAnalysis.getScore());
        resume.setAtsIssues(objectMapper.convertValue(atsAnalysis, new TypeReference<Map<String, Object>>() {}));
        
        resume = resumeRepository.save(resume);
        log.info("On-demand ATS analysis completed for resume: {}", resumeId);
        return toDTO(resume);
    }

    private Resume findByIdAndCurrentUser(UUID resumeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", resumeId));
    }

    private ResumeDTO toDTO(Resume r) {
        return ResumeDTO.builder()
                .id(r.getId().toString())
                .title(r.getTitle())
                .version(r.getVersion())
                .parentResumeId(r.getParentResumeId() == null ? null : r.getParentResumeId().toString())
                .targetJobId(r.getTargetJobId() == null ? null : r.getTargetJobId().toString())
                .active(r.getIsActive())
                .rawText(r.getRawText())
                .parsedJson(r.getParsedJson())
                .fileName(r.getFileName())
                .fileType(r.getFileType())
                .atsScore(r.getAtsScore())
                .atsIssues(r.getAtsIssues())
                .tags(r.getTags())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
