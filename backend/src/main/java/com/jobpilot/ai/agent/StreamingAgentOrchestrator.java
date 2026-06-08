package com.jobpilot.ai.agent;

import com.jobpilot.ai.service.CreditService;
import com.jobpilot.ai.service.EmbeddingService;
import com.jobpilot.ai.service.SerpApiClient;
import com.jobpilot.common.util.JsonExtractor;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.networking.repository.HiringManagerRepository;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * SSE-based agent orchestrator that streams thoughts in real-time
 * to the frontend instead of blocking until completion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingAgentOrchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingService embeddingService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final HiringManagerRepository hiringManagerRepository;
    private final SerpApiClient serpApiClient;
    private final CreditService creditService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Creates an SSE emitter that streams agent thoughts in real-time.
     */
    public SseEmitter orchestrateStream(AgentDtos.OrchestrationRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2-minute timeout

        executor.execute(() -> {
            try {
                UUID userId = SecurityUtils.getCurrentUserId();
                String type = resolveWorkflowType(request);

                // Verify credits before starting
                creditService.requireCredits();

                AgentWorkflow wf = AgentWorkflow.builder()
                        .workflowType(type)
                        .userGoal(request.getGoal())
                        .userId(userId)
                        .startedAt(Instant.now())
                        .build();

                sendEvent(emitter, "workflow_start", Map.of(
                        "workflowId", wf.getWorkflowId(),
                        "workflowType", type
                ));

                sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR,
                        "Received goal: \"" + request.getGoal() + "\"");
                sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR,
                        "Selected workflow: " + type);

                try {
                    switch (type) {
                        case "AUTO_PILOT" -> executeAutoPilotStreaming(wf, request, userId, emitter);
                        case "INTERVIEW_BLITZ" -> executeInterviewBlitzStreaming(wf, request, userId, emitter);
                        case "STEALTH_NETWORKING" -> executeStealthNetworkingStreaming(wf, request, userId, emitter);
                        default -> executeCustomStreaming(wf, request, userId, emitter);
                    }
                    wf.setStatus(AgentWorkflow.WorkflowStatus.COMPLETED);
                } catch (Exception e) {
                    log.error("Streaming workflow {} failed", wf.getWorkflowId(), e);
                    wf.setStatus(AgentWorkflow.WorkflowStatus.FAILED);
                    sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR,
                            "Workflow encountered an error: " + e.getMessage());
                }

                wf.setCompletedAt(Instant.now());

                // Send final report
                String report = compileFinalReport(wf);
                wf.setFinalReport(report);

                sendEvent(emitter, "workflow_complete", Map.of(
                        "workflowId", wf.getWorkflowId(),
                        "status", wf.getStatus().name(),
                        "finalReport", report,
                        "durationMs", wf.getTotalDurationMs(),
                        "artifacts", wf.getArtifacts()
                ));

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE orchestration failed", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE connection error: {}", e.getMessage()));

        return emitter;
    }

    // ─── Streaming Workflow: AUTO_PILOT ───
    private void executeAutoPilotStreaming(AgentWorkflow wf, AgentDtos.OrchestrationRequest req,
                                           UUID userId, SseEmitter emitter) {
        String searchQuery = buildSearchQuery(req);

        sendThought(emitter, wf, AgentPersonality.SCOUT, "Searching for: " + searchQuery);
        float[] embedding = embeddingService.getEmbedding(searchQuery);
        List<Job> jobs = jobRepository.findTop5ByUserIdOrderByEmbedding(userId, embedding);
        sendThought(emitter, wf, AgentPersonality.SCOUT, "Found " + jobs.size() + " internal matches.");

        if (jobs.size() < 3) {
            sendThought(emitter, wf, AgentPersonality.SCOUT, "Expanding to web search...");
            List<Map<String, Object>> webJobs = serpApiClient.searchJobs(searchQuery);
            sendThought(emitter, wf, AgentPersonality.SCOUT,
                    "Web search returned " + webJobs.size() + " results.");
            List<UUID> savedIds = new ArrayList<>();
            for (Map<String, Object> wj : webJobs) {
                try {
                    Job newJob = Job.builder()
                            .userId(userId)
                            .title((String) wj.getOrDefault("title", "Unknown Title"))
                            .company((String) wj.getOrDefault("companyName", "Unknown Company"))
                            .location((String) wj.getOrDefault("location", ""))
                            .sourceUrl((String) wj.getOrDefault("jobUrl", ""))
                            .rawText(wj.get("description") != null ? (String) wj.get("description") : "No description")
                            .parsedJson(wj).build();
                    jobRepository.save(newJob);
                    savedIds.add(newJob.getId());
                    jobs.add(newJob);
                } catch (Exception e) { log.warn("Failed to save web job", e); }
            }
            if (!savedIds.isEmpty()) {
                wf.getArtifacts().put("scoutedJobs", savedIds);
                sendThought(emitter, wf, AgentPersonality.SCOUT,
                        "Saved " + savedIds.size() + " new jobs to your Kanban board.");
            }
        } else {
            wf.getArtifacts().put("scoutedJobs",
                    jobs.stream().map(Job::getId).collect(Collectors.toList()));
        }

        sendThought(emitter, wf, AgentPersonality.GHOSTWRITER, "Preparing application materials...");
        Resume resume = resolveResume(req.getResumeId(), userId);
        if (resume != null && !jobs.isEmpty()) {
            sendThought(emitter, wf, AgentPersonality.GHOSTWRITER,
                    "Tailoring resume for top match: " + jobs.get(0).getTitle());
            creditService.requireCredits();
            String advice = callAgent(AgentPersonality.GHOSTWRITER,
                    "Provide 3 key tips for tailoring a resume for '" + jobs.get(0).getTitle() +
                    "' at " + jobs.get(0).getCompany() + ". Keep to 3-4 sentences.");
            creditService.consumeCredit();
            sendThought(emitter, wf, AgentPersonality.GHOSTWRITER, "Resume advice: " + truncate(advice, 200));
        }

        sendThought(emitter, wf, AgentPersonality.ANALYST, "Researching compensation benchmarks...");
        creditService.requireCredits();
        String salaryAdvice = callAgent(AgentPersonality.ANALYST,
                "Provide a brief salary range estimate for '" + searchQuery + "' roles. Return a single paragraph.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.ANALYST, "Salary analysis: " + truncate(salaryAdvice, 200));

        sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR, "Compiling final report from all agents.");
    }

    // ─── Streaming Workflow: INTERVIEW_BLITZ ───
    private void executeInterviewBlitzStreaming(AgentWorkflow wf, AgentDtos.OrchestrationRequest req,
                                                UUID userId, SseEmitter emitter) {
        sendThought(emitter, wf, AgentPersonality.COACH, "Initiating interview preparation protocol.");

        String jobContext = "General interview preparation";
        if (req.getJobId() != null) {
            Job job = jobRepository.findByIdAndUserId(req.getJobId(), userId).orElse(null);
            if (job != null) {
                jobContext = job.getTitle() + " at " + job.getCompany();
                sendThought(emitter, wf, AgentPersonality.SCOUT, "Target: " + jobContext);
            }
        }

        if (req.getTargetCompany() != null) {
            sendThought(emitter, wf, AgentPersonality.ANALYST, "Researching company culture...");
            creditService.requireCredits();
            String insights = callAgent(AgentPersonality.ANALYST,
                    "Provide a brief overview of " + req.getTargetCompany() + "'s interview process. 3-4 sentences.");
            creditService.consumeCredit();
            sendThought(emitter, wf, AgentPersonality.ANALYST, truncate(insights, 200));
        }

        sendThought(emitter, wf, AgentPersonality.COACH, "Generating role-specific questions...");
        creditService.requireCredits();
        String questions = callAgent(AgentPersonality.COACH,
                "Generate 5 likely interview questions for: " + jobContext + ". Return ONLY a JSON array of strings.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.COACH, "Questions prepared. Ready for practice.");
        wf.getArtifacts().put("practiceUrl", "/ai/mock-interview?mode=blitz");
        wf.getArtifacts().put("questions", questions);

        sendThought(emitter, wf, AgentPersonality.GHOSTWRITER, "Drafting thank-you email template...");
        creditService.requireCredits();
        callAgent(AgentPersonality.GHOSTWRITER,
                "Draft a brief post-interview thank-you email for " +
                (req.getTargetRole() != null ? req.getTargetRole() : "the position") + ". Under 100 words.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.GHOSTWRITER, "Thank-you email template ready.");
    }

    // ─── Streaming Workflow: STEALTH_NETWORKING ───
    private void executeStealthNetworkingStreaming(AgentWorkflow wf, AgentDtos.OrchestrationRequest req,
                                                   UUID userId, SseEmitter emitter) {
        String company = req.getTargetCompany() != null ? req.getTargetCompany() : "target company";
        String role = req.getTargetRole() != null ? req.getTargetRole() : "desired role";

        sendThought(emitter, wf, AgentPersonality.OUTREACH, "Initiating stealth networking protocol.");
        sendThought(emitter, wf, AgentPersonality.SCOUT, "Researching " + company + "...");

        creditService.requireCredits();
        String companyResearch = callAgent(AgentPersonality.SCOUT,
                "Research " + company + ": 1) Hiring trends, 2) Key departments, 3) Culture summary. 3-4 sentences.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.SCOUT, truncate(companyResearch, 200));

        sendThought(emitter, wf, AgentPersonality.OUTREACH, "Drafting personalized outreach sequence...");
        creditService.requireCredits();
        String outreach = callAgent(AgentPersonality.OUTREACH,
                "Draft a cold outreach email to a hiring manager at " + company + " for " + role +
                ". Under 150 words. Be warm, specific, professional.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.OUTREACH, "Initial outreach email drafted.");
        wf.getArtifacts().put("emailDraft", outreach);

        sendThought(emitter, wf, AgentPersonality.ANALYST, "Assessing culture fit probability...");
        creditService.requireCredits();
        String fitAnalysis = callAgent(AgentPersonality.ANALYST,
                "Rate culture fit (1-10) for " + company + " and explain in 2 sentences.");
        creditService.consumeCredit();
        sendThought(emitter, wf, AgentPersonality.ANALYST, truncate(fitAnalysis, 200));
    }

    // ─── Streaming Workflow: CUSTOM ───
    private void executeCustomStreaming(AgentWorkflow wf, AgentDtos.OrchestrationRequest req,
                                        UUID userId, SseEmitter emitter) {
        sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR, "Executing custom goal analysis...");
        creditService.requireCredits();
        String result = callAgent(AgentPersonality.ORCHESTRATOR,
                "The user's goal is: \"" + req.getGoal() + "\". " +
                "Provide a strategic action plan with 3-5 concrete steps. " +
                "Context: role = " + req.getTargetRole() + ", company = " + req.getTargetCompany());
        creditService.consumeCredit();
        wf.setFinalReport(result);
        sendThought(emitter, wf, AgentPersonality.ORCHESTRATOR, "Strategy complete.");
    }

    // ─── Helpers ───

    private void sendThought(SseEmitter emitter, AgentWorkflow wf,
                              AgentPersonality agent, String thought) {
        wf.addThought(agent, thought);
        try {
            emitter.send(SseEmitter.event()
                    .name("thought")
                    .data(Map.of(
                            "agent", agent.getDisplayName(),
                            "emoji", agent.getEmoji(),
                            "thought", thought,
                            "timestamp", Instant.now().toString()
                    )));
        } catch (IOException e) {
            log.warn("Failed to send SSE thought: {}", e.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event '{}': {}", name, e.getMessage());
        }
    }

    private String callAgent(AgentPersonality agent, String userMessage) {
        try {
            ChatClient client = chatClientBuilder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .withModel(agent.getPreferredModel())
                            .withMaxTokens(1500)
                            .withTemperature(0.7)
                            .build())
                    .build();
            return client.prompt().system(agent.getSystemPrompt()).user(userMessage).call().content();
        } catch (Exception e) {
            log.error("Agent {} failed: {}", agent.getDisplayName(), e.getMessage());
            return "[Agent " + agent.getDisplayName() + " encountered an error]";
        }
    }

    private Resume resolveResume(UUID resumeId, UUID userId) {
        if (resumeId != null) {
            return resumeRepository.findByIdAndUserId(resumeId, userId).orElse(null);
        }
        return resumeRepository.findByUserIdAndIsActiveTrue(userId)
                .orElse(null);
    }

    private String buildSearchQuery(AgentDtos.OrchestrationRequest req) {
        String role = req.getTargetRole() != null ? req.getTargetRole().trim() : "";
        String company = req.getTargetCompany() != null ? req.getTargetCompany().trim() : "";
        String query = (role + " " + company).trim();
        return query.isEmpty() ? req.getGoal() : query;
    }

    private String resolveWorkflowType(AgentDtos.OrchestrationRequest req) {
        if (req.getWorkflowType() != null && !req.getWorkflowType().isBlank())
            return req.getWorkflowType().toUpperCase();
        String goal = req.getGoal().toLowerCase();
        if (goal.contains("apply") || goal.contains("find") || goal.contains("search")) return "AUTO_PILOT";
        if (goal.contains("interview") || goal.contains("prepare") || goal.contains("practice")) return "INTERVIEW_BLITZ";
        if (goal.contains("network") || goal.contains("connect") || goal.contains("outreach")) return "STEALTH_NETWORKING";
        return "CUSTOM";
    }

    private String compileFinalReport(AgentWorkflow wf) {
        return callAgent(AgentPersonality.ORCHESTRATOR,
                "Compile a professional summary from these agent activities. " +
                "User goal: " + wf.getUserGoal() + "\n\n" +
                "Agent Log:\n" + String.join("\n", wf.getThoughts()) +
                "\n\nProvide a 2-3 paragraph executive summary with key findings and next steps.");
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
