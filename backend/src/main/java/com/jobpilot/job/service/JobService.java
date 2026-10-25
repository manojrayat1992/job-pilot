package com.jobpilot.job.service;

import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.dto.JobAnalysisRequest;
import com.jobpilot.job.dto.JobDTO;
import com.jobpilot.job.dto.MatchScoreResponse;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final AiService aiService;
    private final JobWriter jobWriter;

    @Transactional
    public JobDTO analyzeJob(JobAnalysisRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        if (request.getUrl() != null && !request.getUrl().isBlank() && !request.getUrl().trim().equals("#")) {
            var existing = jobRepository.findFirstByUserIdAndSourceUrlOrderByCreatedAtAsc(userId, request.getUrl().trim());
            if (existing.isPresent()) return toDTO(existing.get());
        }
        String rawText = resolveJobText(request);

        // Parse JD using AI
        Map<String, Object> parsedJson = aiService.parseJobDescription(rawText);

        // Extract top-level fields from parsed JSON
        String title = request.getTitle() != null ? request.getTitle()
                : (String) parsedJson.getOrDefault("title", "Untitled Position");

        Job job = Job.builder()
                .userId(userId)
                .title(title)
                .company((String) parsedJson.get("company"))
                .location((String) parsedJson.get("location"))
                .sourceUrl(request.getUrl())
                .rawText(rawText)
                .parsedJson(parsedJson)
                .experienceLevel((String) parsedJson.get("experienceLevel"))
                .jobType((String) parsedJson.get("jobType"))
                .salaryRange((String) parsedJson.get("salaryRange"))
                .skillsRequired(toStringArray(parsedJson.get("requiredSkills")))
                .skillsNice(toStringArray(parsedJson.get("niceToHaveSkills")))
                .build();

        UUID savedId = jobWriter.save(job);
        job = jobRepository.findByIdAndUserId(savedId, userId).orElseThrow();
        log.info("Job analyzed and saved: id={}, title={}", job.getId(), title);

        // The database queues embedding work in the same transaction as the job.

        return toDTO(job);
    }

    public Page<JobDTO> listJobs(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toDTO);
    }

    public JobDTO getJob(UUID jobId) {
        return toDTO(findByIdAndCurrentUser(jobId));
    }

    @Transactional
    public void deleteJob(UUID jobId) {
        Job job = findByIdAndCurrentUser(jobId);
        jobRepository.delete(job);
        log.info("Job deleted: id={}", jobId);
    }

    public MatchScoreResponse matchScore(UUID jobId, UUID resumeId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Job job = findByIdAndCurrentUser(jobId);
        return aiService.calculateMatchScore(resumeId, job);
    }

    private String resolveJobText(JobAnalysisRequest request) {
        if (request.getText() != null && !request.getText().isBlank()) {
            return request.getText().trim();
        }
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            try {
                log.info("Fetching job description from URL: {}", request.getUrl());
                Document doc = Jsoup.connect(request.getUrl())
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(10000)
                        .get();
                
                // Get the main text content, focusing on the body
                String text = doc.body().text();
                if (text.isBlank()) {
                    throw new BadRequestException("Fetched page is empty or could not be read.");
                }
                return text;
            } catch (Exception e) {
                log.error("Failed to fetch job description from URL: {}", request.getUrl(), e);
                throw new BadRequestException("Failed to fetch job description from URL. Please paste the text manually.");
            }
        }
        throw new BadRequestException("Either 'text' or 'url' must be provided");
    }

    @SuppressWarnings("unchecked")
    private String[] toStringArray(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        return null;
    }

    private Job findByIdAndCurrentUser(UUID jobId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId));
    }

    private JobDTO toDTO(Job j) {
        return JobDTO.builder()
                .id(j.getId().toString())
                .title(j.getTitle())
                .company(j.getCompany())
                .location(j.getLocation())
                .sourceUrl(j.getSourceUrl())
                .rawText(j.getRawText())
                .parsedJson(j.getParsedJson())
                .experienceLevel(j.getExperienceLevel())
                .jobType(j.getJobType())
                .salaryRange(j.getSalaryRange())
                .skillsRequired(j.getSkillsRequired())
                .skillsNice(j.getSkillsNice())
                .createdAt(j.getCreatedAt())
                .build();
    }
}
