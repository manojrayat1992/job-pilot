package com.jobpilot.application.service;

import com.jobpilot.application.dto.*;
import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.entity.JobApplication.ApplicationStatus;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final com.jobpilot.resume.repository.ResumeRepository resumeRepository;
    private final com.jobpilot.job.repository.JobRepository jobRepository;

    @Transactional
    public ApplicationDTO create(CreateApplicationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        validateReferences(request, userId);
        applicationRepository.lockOwner(userId);
        if (request.getJobId() != null) {
            var existing = applicationRepository.findFirstByUserIdAndJobIdOrderByCreatedAtAsc(userId, request.getJobId());
            if (existing.isPresent()) return toDTO(existing.get());
        }

        JobApplication app = JobApplication.builder()
                .userId(userId)
                .company(request.getCompany().trim())
                .position(request.getPosition().trim())
                .jobId(request.getJobId())
                .resumeId(request.getResumeId())
                .status(parseStatus(request.getStatus(), ApplicationStatus.SAVED))
                .appliedDate(request.getAppliedDate())
                .notes(request.getNotes())
                .coverLetter(request.getCoverLetter())
                .source(request.getSource())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .build();

        app = applicationRepository.save(app);
        log.info("Application created: id={}, company={}, position={}", app.getId(), app.getCompany(), app.getPosition());
        return toDTO(app);
    }

    public Page<ApplicationDTO> list(String status, Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId();

        if (status != null && !status.isBlank()) {
            ApplicationStatus s = parseStatus(status, null);
            if (s == null) throw new BadRequestException("Invalid status: " + status);
            return applicationRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, s, pageable)
                    .map(this::toDTO);
        }

        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .map(this::toDTO);
    }

    public ApplicationDTO get(UUID id) {
        return toDTO(findByIdAndCurrentUser(id));
    }

    @Transactional
    public ApplicationDTO update(UUID id, CreateApplicationRequest request) {
        JobApplication app = findByIdAndCurrentUser(id);
        validateReferences(request, SecurityUtils.getCurrentUserId());
        applicationRepository.lockOwner(SecurityUtils.getCurrentUserId());
        if (request.getJobId() != null && !request.getJobId().equals(app.getJobId())) {
            var existing = applicationRepository.findFirstByUserIdAndJobIdOrderByCreatedAtAsc(app.getUserId(), request.getJobId());
            if (existing.isPresent() && !existing.get().getId().equals(id))
                throw new com.jobpilot.common.exception.DuplicateResourceException("This job already has an application record");
        }

        if (request.getCompany() != null) app.setCompany(request.getCompany().trim());
        if (request.getPosition() != null) app.setPosition(request.getPosition().trim());
        if (request.getStatus() != null) app.setStatus(parseStatus(request.getStatus(), app.getStatus()));
        if (request.getAppliedDate() != null) app.setAppliedDate(request.getAppliedDate());
        if (request.getNotes() != null) app.setNotes(request.getNotes());
        if (request.getCoverLetter() != null) app.setCoverLetter(request.getCoverLetter());
        if (request.getSource() != null) app.setSource(request.getSource());
        if (request.getContactName() != null) app.setContactName(request.getContactName());
        if (request.getContactEmail() != null) app.setContactEmail(request.getContactEmail());
        if (request.getPriority() != null) app.setPriority(request.getPriority());
        if (request.getJobId() != null) app.setJobId(request.getJobId());
        if (request.getResumeId() != null) app.setResumeId(request.getResumeId());

        app = applicationRepository.save(app);
        log.info("Application updated: id={}", id);
        return toDTO(app);
    }

    @Transactional
    public ApplicationDTO updateStatus(UUID id, String newStatus) {
        JobApplication app = findByIdAndCurrentUser(id);
        ApplicationStatus status = parseStatus(newStatus, null);
        if (status == null) throw new BadRequestException("Invalid status: " + newStatus);

        app.setStatus(status);
        app = applicationRepository.save(app);
        log.info("Application status updated: id={}, status={}", id, status);
        return toDTO(app);
    }

    @Transactional
    public void delete(UUID id) {
        JobApplication app = findByIdAndCurrentUser(id);
        applicationRepository.delete(app);
        log.info("Application deleted: id={}", id);
    }

    public ApplicationStatsDTO getStats() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<Object[]> rows = applicationRepository.countByStatusGrouped(userId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;

        for (Object[] row : rows) {
            String status = row[0].toString();
            Long count = (Long) row[1];
            byStatus.put(status, count);
            total += count;
        }

        return ApplicationStatsDTO.builder()
                .total(total)
                .byStatus(byStatus)
                .build();
    }

    private void validateReferences(CreateApplicationRequest request, UUID userId) {
        if (request.getResumeId() != null) {
            resumeRepository.findByIdAndUserId(request.getResumeId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId()));
        }
        if (request.getJobId() != null) {
            jobRepository.findByIdAndUserId(request.getJobId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job", "id", request.getJobId()));
        }
    }

    private JobApplication findByIdAndCurrentUser(UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", id));
    }

    private ApplicationStatus parseStatus(String status, ApplicationStatus defaultStatus) {
        if (status == null || status.isBlank()) return defaultStatus;
        try {
            return ApplicationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultStatus;
        }
    }

    private ApplicationDTO toDTO(JobApplication a) {
        return ApplicationDTO.builder()
                .id(a.getId().toString())
                .company(a.getCompany())
                .position(a.getPosition())
                .status(a.getStatus().name())
                .appliedDate(a.getAppliedDate())
                .notes(a.getNotes())
                .coverLetter(a.getCoverLetter())
                .source(a.getSource())
                .contactName(a.getContactName())
                .contactEmail(a.getContactEmail())
                .priority(a.getPriority())
                .jobId(a.getJobId() != null ? a.getJobId().toString() : null)
                .resumeId(a.getResumeId() != null ? a.getResumeId().toString() : null)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
