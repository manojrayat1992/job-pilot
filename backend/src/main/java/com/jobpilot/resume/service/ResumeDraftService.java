package com.jobpilot.resume.service;

import com.jobpilot.ai.service.AiService;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.dto.*;
import com.jobpilot.resume.entity.*;
import com.jobpilot.resume.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ResumeDraftService {
    private final ResumeRepository resumes;
    private final ResumeDraftRepository drafts;
    private final JobRepository jobs;
    private final AiService ai;
    private final ResumeContentService content;
    private final ResumeService resumeService;

    // No database transaction or row lock is held across a provider call.
    public ResumeDraftDTO generate(UUID sourceId, TailorResumeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Resume source = ownedSource(sourceId, userId);
        String jobText = request.jobDescription();
        if (request.jobId() != null) {
            var job = jobs.findByIdAndUserId(request.jobId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job", "id", request.jobId()));
            jobText = job.getRawText();
        }
        if (jobText == null || jobText.isBlank() || jobText.length() > 30_000) {
            throw new BadRequestException("Provide a job description between 1 and 30000 characters.");
        }
        var original = content.validatedCopy(source.getParsedJson());
        var candidate = request.proposedResume() != null ? request.proposedResume()
                : ai.tailorResume(content.validatedCopy(original), jobText);
        var proposed = content.validateTailoring(original, candidate);
        var draft = ResumeDraft.builder().userId(userId).sourceResumeId(sourceId)
                .targetJobId(request.jobId()).jobDescription(jobText)
                .sourceSnapshot(original).proposedSnapshot(proposed).build();
        return dto(drafts.save(draft));
    }

    public List<ResumeDraftDTO> list(UUID sourceId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ownedSource(sourceId, userId);
        return drafts.findTop50BySourceResumeIdAndUserIdOrderByCreatedAtDesc(sourceId, userId).stream().map(this::dto).toList();
    }

    public ResumeDraftDTO get(UUID id) {
        return dto(ownedDraft(id));
    }

    @Transactional
    public ResumeDraftDTO approve(UUID id, boolean confirmed) {
        if (!confirmed) throw new BadRequestException("Review the draft and confirm its factual accuracy before approval.");
        UUID userId = SecurityUtils.getCurrentUserId();
        ResumeDraft draft = drafts.lockOwned(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume draft", "id", id));
        if (draft.getApprovedAt() != null) {
            if (draft.getApprovedResumeId() == null) throw new BadRequestException("The approved resume was deleted. Generate a new draft.");
            return dto(draft); // Retried approval never creates another resume.
        }
        var source = ownedSource(draft.getSourceResumeId(), userId);
        var approved = resumeService.createReviewedVersion(source, draft.getProposedSnapshot(), draft.getTargetJobId(), draft.getJobDescription());
        draft.setApprovedResumeId(UUID.fromString(approved.getId()));
        draft.setApprovedAt(Instant.now());
        return dto(drafts.save(draft));
    }

    public byte[] download(UUID id) {
        var draft = ownedDraft(id);
        if (draft.getApprovedAt() == null || draft.getApprovedResumeId() == null) {
            throw new BadRequestException("Approve this draft before exporting it.");
        }
        return resumeService.downloadPdf(draft.getApprovedResumeId());
    }

    private Resume ownedSource(UUID id, UUID userId) {
        return resumes.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Resume", "id", id));
    }
    private ResumeDraft ownedDraft(UUID id) {
        return drafts.findByIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume draft", "id", id));
    }
    private ResumeDraftDTO dto(ResumeDraft d) {
        return new ResumeDraftDTO(d.getId(), d.getSourceResumeId(), d.getTargetJobId(), d.getJobDescription(),
                content.toText(d.getSourceSnapshot()), content.toText(d.getProposedSnapshot()),
                d.getApprovedResumeId(), d.getApprovedAt(), d.getCreatedAt());
    }
}
