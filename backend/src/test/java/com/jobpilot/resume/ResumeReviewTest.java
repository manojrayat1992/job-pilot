package com.jobpilot.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.auth.filter.JwtAuthenticationFilter.AuthenticatedUser;
import com.jobpilot.common.exception.*;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.dto.*;
import com.jobpilot.resume.entity.*;
import com.jobpilot.resume.repository.*;
import com.jobpilot.resume.service.*;
import com.jobpilot.storage.service.FileStorageService;
import org.apache.tika.Tika;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResumeReviewTest {
    private final UUID userId = UUID.randomUUID(), sourceId = UUID.randomUUID();
    private final ResumeRepository resumes = mock(ResumeRepository.class);
    private final ResumeDraftRepository drafts = mock(ResumeDraftRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final AiService ai = mock(AiService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResumeContentService content = new ResumeContentService(mapper);
    private final PdfExportService pdf = new PdfExportService();
    private final ResumeService resumeService = new ResumeService(resumes, mock(ResumeParserService.class), pdf,
            mock(FileStorageService.class), ai, mapper, content);
    private final ResumeDraftService service = new ResumeDraftService(resumes, drafts, jobs, ai, content, resumeService);
    private Resume source;
    private final Map<UUID, Resume> stored = new HashMap<>();
    private ResumeDraft savedDraft;

    static Map<String, Object> sample() {
        return new LinkedHashMap<>(Map.of(
                "personalInfo", Map.of("name", "Alex Sample", "email", "alex@example.test", "website", "https://example.test"),
                "summary", "Engineer building reliable services.",
                "experience", List.of(Map.of("title", "Engineer", "company", "Sample Co", "startDate", "2020", "endDate", "2024",
                        "bullets", List.of("Built Java services for customers."))),
                "education", List.of(Map.of("degree", "BSc", "institution", "Example University", "gpa", "3.5")),
                "skills", List.of("Java"), "projects", List.of()));
    }
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "alex@example.test", "USER"), null));
        source = Resume.builder().id(sourceId).userId(userId).title("Original").parsedJson(sample())
                .rawText("Original uploaded text").filePath("source.pdf").fileName("source.pdf")
                .isActive(true).atsScore(90).build();
        stored.put(sourceId, source);
        when(resumes.findByIdAndUserId(any(), eq(userId))).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(resumes.save(any())).thenAnswer(call -> {
            Resume r = call.getArgument(0); if (r.getId() == null) r.setId(UUID.randomUUID()); stored.put(r.getId(), r); return r;
        });
        when(drafts.save(any())).thenAnswer(call -> {
            savedDraft = call.getArgument(0); if (savedDraft.getId() == null) savedDraft.setId(UUID.randomUUID()); return savedDraft;
        });
        when(drafts.lockOwned(any(), eq(userId))).thenAnswer(call -> Optional.ofNullable(savedDraft));
        when(drafts.findByIdAndUserId(any(), eq(userId))).thenAnswer(call -> Optional.ofNullable(savedDraft));
        when(ai.tailorResume(anyMap(), anyString())).thenAnswer(call -> {
            var proposed = content.validatedCopy(call.getArgument(0));
            proposed.put("summary", "Engineer focused on reliable services."); return proposed;
        });
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    private ResumeDraftDTO generate() { return service.generate(sourceId, new TailorResumeRequest(null, "Java engineer", null)); }

    @Test void generateReviewApproveAndExportPreservesOriginal() throws Exception {
        var draft = generate();
        assertNull(draft.approvedResumeId());
        assertThrows(BadRequestException.class, () -> service.download(draft.id()));
        assertThrows(BadRequestException.class, () -> service.approve(draft.id(), false));
        var approved = service.approve(draft.id(), true);
        var version = stored.get(approved.approvedResumeId());
        assertNotEquals(sourceId, version.getId());
        assertEquals(sourceId, version.getParentResumeId());
        assertEquals("Original uploaded text", source.getRawText());
        assertEquals(sample(), source.getParsedJson());
        assertEquals("source.pdf", source.getFilePath());
        assertTrue(source.getIsActive());
        assertFalse(version.getIsActive());
        assertNull(version.getFilePath());
        assertNull(version.getAtsScore());
        assertEquals(content.toText(version.getParsedJson()), version.getRawText());
        verify(resumes, never()).save(source);
        String exported = new Tika().parseToString(new ByteArrayInputStream(service.download(draft.id())));
        assertTrue(exported.contains("Engineer focused on reliable services."));
        assertTrue(exported.contains("Alex Sample"));
        assertTrue(exported.contains("Sample Co"));
        assertTrue(exported.contains("https://example.test"));
        assertTrue(exported.contains("GPA: 3.5"));
    }
    @Test void repeatedApprovalReturnsSameDocument() {
        var draft = generate();
        var first = service.approve(draft.id(), true);
        var second = service.approve(draft.id(), true);
        assertEquals(first.approvedResumeId(), second.approvedResumeId());
        verify(resumes, times(1)).save(any());
    }
    @Test void manualContentEditCreatesAnotherVersion() {
        var proposed = sample(); proposed.put("summary", "User edited summary.");
        var updated = resumeService.updateResume(sourceId, Map.of("parsedJson", proposed, "title", "Edited"));
        assertNotEquals(sourceId.toString(), updated.getId());
        assertEquals(sourceId.toString(), updated.getParentResumeId());
        assertEquals("Original", source.getTitle());
        assertEquals("Original uploaded text", source.getRawText());
        assertEquals(sample(), source.getParsedJson());
        assertTrue(updated.getRawText().contains("User edited summary."));
    }
    @Test void oldTailorAndDownloadCannotBypassReview() {
        assertThrows(BadRequestException.class, () -> resumeService.downloadTailoredPdf(sourceId, "job"));
        verifyNoInteractions(ai);
        verify(resumes, never()).save(any());
    }
    @Test void foreignSourceRejectedBeforeProviderCall() {
        assertThrows(ResourceNotFoundException.class, () -> service.generate(UUID.randomUUID(), new TailorResumeRequest(null, "job", null)));
        verifyNoInteractions(ai);
        verify(drafts, never()).save(any());
    }
    @Test void foreignJobRejectedBeforeProviderCall() {
        UUID jobId = UUID.randomUUID();
        when(jobs.findByIdAndUserId(jobId, userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.generate(sourceId, new TailorResumeRequest(jobId, null, null)));
        verifyNoInteractions(ai);
    }
    @Test void savedJobUsesItsOwnedDescription() {
        UUID jobId = UUID.randomUUID();
        when(jobs.findByIdAndUserId(jobId, userId)).thenReturn(Optional.of(Job.builder().id(jobId).userId(userId).rawText("Owned job text").build()));
        var result = service.generate(sourceId, new TailorResumeRequest(jobId, "unrelated", null));
        assertEquals("Owned job text", result.jobDescription());
        assertEquals(jobId, result.targetJobId());
    }
    @Test void foreignDraftCannotBeReadApprovedOrExported() {
        UUID id = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.get(id));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(id, true));
        assertThrows(ResourceNotFoundException.class, () -> service.download(id));
    }
    @Test void providerFailureDoesNotPersistOrMutate() {
        doThrow(new AiServiceException("Provider unavailable")).when(ai).tailorResume(anyMap(), anyString());
        assertThrows(AiServiceException.class, this::generate);
        assertEquals(sample(), source.getParsedJson());
        verify(drafts, never()).save(any());
        verify(resumes, never()).save(any());
    }
    @Test void protectedFactChangeIsRejected() {
        var proposed = sample(); proposed.put("skills", List.of("Java", "Rust"));
        doReturn(proposed).when(ai).tailorResume(anyMap(), anyString());
        assertThrows(BadRequestException.class, this::generate);
        verify(drafts, never()).save(any());
    }
    @Test void inventedMetricIsRejected() {
        var proposed = sample(); proposed.put("summary", "Improved revenue by 47%.");
        assertThrows(BadRequestException.class, () -> content.validateTailoring(sample(), proposed));
    }
    @Test void malformedSchemaRejectedBeforePersistence() {
        doReturn(Map.of("error", "provider error")).when(ai).tailorResume(anyMap(), anyString());
        assertThrows(BadRequestException.class, this::generate);
        verify(drafts, never()).save(any());
    }
    @Test void editedApprovedCopyCannotChangePreviouslyApprovedExport() throws Exception {
        var draft = generate();
        var approved = service.approve(draft.id(), true);
        var edited = sample(); edited.put("summary", "Later unrelated edit.");
        resumeService.updateResume(approved.approvedResumeId(), Map.of("parsedJson", edited));
        String exported = new Tika().parseToString(new ByteArrayInputStream(service.download(draft.id())));
        assertTrue(exported.contains("Engineer focused on reliable services."));
        assertFalse(exported.contains("Later unrelated edit."));
    }
    @Test void suppliedOptimizerOutputUsesTheSameReviewGateWithoutAnotherAiCall() {
        var proposed = sample(); proposed.put("summary", "Engineer focused on reliable services.");
        var draft = service.generate(sourceId, new TailorResumeRequest(null, "job", proposed));
        assertNull(draft.approvedAt());
        verifyNoInteractions(ai);
    }
    @Test void legacyBuilderSummaryAndNullListsNormalizeWithoutChangingInput() {
        var legacy = sample();
        legacy.put("personalInfo", Map.of("name", "Alex", "summary", "Engineer building reliable services."));
        legacy.put("projects", null);
        var normalized = content.validatedCopy(legacy);
        assertFalse(((Map<?, ?>) normalized.get("personalInfo")).containsKey("summary"));
        assertTrue(((Map<?, ?>) legacy.get("personalInfo")).containsKey("summary"));
        assertEquals(List.of(), normalized.get("projects"));
        assertNull(legacy.get("projects"));
    }
    @Test void wrongCollectionTypesAreRejected() {
        var invalid = sample(); invalid.put("skills", "Java");
        assertThrows(BadRequestException.class, () -> content.validatedCopy(invalid));
    }

}
