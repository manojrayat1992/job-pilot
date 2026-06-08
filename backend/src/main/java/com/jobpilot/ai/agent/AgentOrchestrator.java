package com.jobpilot.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.service.AiService;
import com.jobpilot.ai.service.EmbeddingService;
import com.jobpilot.ai.service.SerpApiClient;
import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.networking.entity.HiringManager;
import com.jobpilot.networking.repository.HiringManagerRepository;
import com.jobpilot.networking.service.HiringManagerService;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The AgentOrchestrator coordinates multiple specialized AI agents
 * to fulfill complex, multi-step user goals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final AiService aiService;
    private final EmbeddingService embeddingService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final HiringManagerService hiringManagerService;
    private final HiringManagerRepository hiringManagerRepository;
    private final SerpApiClient serpApiClient;
    private final ObjectMapper objectMapper;

    /**
     * Main entry point — routes the user's goal to the appropriate workflow.
     */
    public AgentDtos.OrchestrationResponse orchestrate(AgentDtos.OrchestrationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String type = resolveWorkflowType(request);

        AgentWorkflow workflow = AgentWorkflow.builder()
                .workflowType(type)
                .userGoal(request.getGoal())
                .userId(userId)
                .startedAt(Instant.now())
                .build();

        workflow.addThought(AgentPersonality.ORCHESTRATOR, "Received goal: \"" + request.getGoal() + "\"");
        workflow.addThought(AgentPersonality.ORCHESTRATOR, "Selected workflow: " + type);

        try {
            switch (type) {
                case "AUTO_PILOT" -> executeAutoPilot(workflow, request, userId);
                case "INTERVIEW_BLITZ" -> executeInterviewBlitz(workflow, request, userId);
                case "STEALTH_NETWORKING" -> executeStealthNetworking(workflow, request, userId);
                default -> executeCustom(workflow, request, userId);
            }
            workflow.setStatus(AgentWorkflow.WorkflowStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Workflow {} failed", workflow.getWorkflowId(), e);
            workflow.setStatus(AgentWorkflow.WorkflowStatus.FAILED);
            workflow.addThought(AgentPersonality.ORCHESTRATOR, "Workflow encountered an error: " + e.getMessage());
        }

        workflow.setCompletedAt(Instant.now());
        return buildResponse(workflow);
    }

    // ─── Workflow: AUTO_PILOT ───
    private void executeAutoPilot(AgentWorkflow wf, AgentDtos.OrchestrationRequest req, UUID userId) {
        // Build search query with fallback to the user's goal text
        String searchQuery = buildSearchQuery(req);
        
        // Step 1: Scout finds jobs
        wf.addThought(AgentPersonality.SCOUT, "Searching for jobs matching: " + searchQuery);
        
        float[] embedding = embeddingService.getEmbedding(searchQuery);
        List<Job> jobs = jobRepository.findTop5ByUserIdOrderByEmbedding(userId, embedding);
        wf.addThought(AgentPersonality.SCOUT, "Found " + jobs.size() + " internal matches.");

        List<Map<String, Object>> webJobs = new ArrayList<>();
        List<UUID> savedJobIds = new ArrayList<>();
        if (jobs.size() < 3) {
            wf.addThought(AgentPersonality.SCOUT, "Insufficient matches. Expanding to web search...");
            webJobs = serpApiClient.searchJobs(searchQuery);
            wf.addThought(AgentPersonality.SCOUT, "Web search returned " + webJobs.size() + " results.");
            
            // SIDE EFFECT: Save found jobs to Kanban
            for (Map<String, Object> wj : webJobs) {
                try {
                    Job newJob = Job.builder()
                            .userId(userId)
                            .title((String) wj.getOrDefault("title", "Unknown Title"))
                            .company((String) wj.getOrDefault("companyName", "Unknown Company"))
                            .location((String) wj.getOrDefault("location", ""))
                            .sourceUrl((String) wj.getOrDefault("jobUrl", ""))
                            .rawText(wj.get("description") != null ? (String) wj.get("description") : "No description")
                            .parsedJson(wj)
                            .build();
                    jobRepository.save(newJob);
                    savedJobIds.add(newJob.getId());
                    jobs.add(newJob);
                } catch (Exception e) {
                    log.warn("Failed to save web job", e);
                }
            }
            if (!savedJobIds.isEmpty()) {
                wf.getArtifacts().put("scoutedJobs", savedJobIds);
                wf.addThought(AgentPersonality.SCOUT, "Saved " + savedJobIds.size() + " new jobs to your Kanban board.");
            }
        } else {
            // Add internal jobs to artifacts so the UI button still works
            wf.getArtifacts().put("scoutedJobs", jobs.stream().map(Job::getId).collect(Collectors.toList()));
        }

        // Step 2: Ghostwriter prepares materials
        wf.addThought(AgentPersonality.GHOSTWRITER, "Preparing application materials...");
        Resume resume = null;
        if (req.getResumeId() != null) {
            resume = resumeRepository.findByIdAndUserId(req.getResumeId(), userId).orElse(null);
        } else {
            // Auto-fetch the user's active resume
            resume = resumeRepository.findByUserIdAndIsActiveTrue(userId).orElse(null);
            if (resume == null) {
                // Fallback to latest resume if no active one
                var pages = resumeRepository.findByUserIdAndIsTransientFalseOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 1));
                if (pages.hasContent()) {
                    resume = pages.getContent().get(0);
                }
            }
        }
        
        if (resume != null && !jobs.isEmpty()) {
            wf.addThought(AgentPersonality.GHOSTWRITER, "Tailoring resume for top match: " + jobs.get(0).getTitle());
            String tailoredAdvice = callAgent(AgentPersonality.GHOSTWRITER,
                    "Rewrite the objective and top 3 bullet points of this resume to perfectly match the job '" + jobs.get(0).getTitle() + 
                    "' at " + jobs.get(0).getCompany() + ". Return a raw JSON object with 'newObjective' and 'newBullets' (array). Resume: " + truncate(resume.getRawText(), 1500) +
                    " Job: " + truncate(jobs.get(0).getRawText(), 800));
            
            // SIDE EFFECT: Create a tailored version of the resume
            Resume tailoredResume = Resume.builder()
                    .userId(userId)
                    .title(resume.getTitle() + " (Tailored for " + jobs.get(0).getCompany() + ")")
                    .rawText(tailoredAdvice) // Simplified for demo, would merge JSON in prod
                    .isTransient(false)
                    .build();
            resumeRepository.save(tailoredResume);
            wf.getArtifacts().put("tailoredResumeId", tailoredResume.getId());
            
            wf.addThought(AgentPersonality.GHOSTWRITER, "Created new tailored resume variant for " + jobs.get(0).getCompany() + ".");
        } else if (!jobs.isEmpty()) {
            // No resume provided — give general advice
            String advice = callAgent(AgentPersonality.GHOSTWRITER,
                    "Provide 3 key tips for crafting a strong application for '" + jobs.get(0).getTitle() +
                    "' at " + jobs.get(0).getCompany() + ". Be specific and actionable. Keep to 3-4 sentences.");
            wf.addThought(AgentPersonality.GHOSTWRITER, "Application tips: " + truncate(advice, 200));
        } else {
            String advice = callAgent(AgentPersonality.GHOSTWRITER,
                    "Provide 3 general resume tips for someone targeting '" + searchQuery + "' roles. Keep brief.");
            wf.addThought(AgentPersonality.GHOSTWRITER, "General tips: " + truncate(advice, 200));
        }

        // Step 3: Analyst provides salary intel
        wf.addThought(AgentPersonality.ANALYST, "Researching compensation benchmarks...");
        String salaryAdvice = callAgent(AgentPersonality.ANALYST,
                "Provide a brief salary range estimate for '" + searchQuery + "' roles. Return a single paragraph.");
        wf.addThought(AgentPersonality.ANALYST, "Salary analysis: " + truncate(salaryAdvice, 200));

        // Step 4: Compile final report
        wf.addThought(AgentPersonality.ORCHESTRATOR, "Compiling final report from all agents.");
        wf.setFinalReport(compileFinalReport(wf));
    }

    // ─── Workflow: INTERVIEW_BLITZ ───
    private void executeInterviewBlitz(AgentWorkflow wf, AgentDtos.OrchestrationRequest req, UUID userId) {
        wf.addThought(AgentPersonality.COACH, "Initiating interview preparation protocol.");

        // Step 1: Get job context
        String jobContext = "General interview preparation";
        if (req.getJobId() != null) {
            Job job = jobRepository.findByIdAndUserId(req.getJobId(), userId).orElse(null);
            if (job != null) {
                jobContext = job.getTitle() + " at " + job.getCompany() + ": " + truncate(job.getRawText(), 1000);
                wf.addThought(AgentPersonality.SCOUT, "Target: " + job.getTitle() + " at " + job.getCompany());
            }
        }

        // Step 2: Analyst researches company
        if (req.getTargetCompany() != null) {
            wf.addThought(AgentPersonality.ANALYST, "Researching company culture and interview style...");
            String companyInsights = callAgent(AgentPersonality.ANALYST,
                    "Provide a brief overview of " + req.getTargetCompany() + 
                    "'s interview process and culture. 3-4 sentences max.");
            wf.addThought(AgentPersonality.ANALYST, truncate(companyInsights, 200));
        }

        // Step 3: Coach generates questions
        wf.addThought(AgentPersonality.COACH, "Generating role-specific interview questions...");
        String questions = callAgent(AgentPersonality.COACH,
                "Generate 5 likely interview questions for this role. Return ONLY a JSON array of strings. Job: " + jobContext);
        wf.addThought(AgentPersonality.COACH, "Questions prepared. Ready for practice.");
        
        // SIDE EFFECT: Provide deep link for practice
        wf.getArtifacts().put("practiceUrl", "/ai/mock-interview?mode=blitz");
        wf.getArtifacts().put("questions", questions);

        // Step 4: Ghostwriter prepares follow-up
        wf.addThought(AgentPersonality.GHOSTWRITER, "Drafting thank-you email template...");
        String thankYou = callAgent(AgentPersonality.GHOSTWRITER,
                "Draft a brief post-interview thank-you email template for the role: " + 
                (req.getTargetRole() != null ? req.getTargetRole() : "the position") + 
                ". Keep under 100 words.");
        wf.addThought(AgentPersonality.GHOSTWRITER, "Thank-you email template ready.");

        wf.setFinalReport(compileFinalReport(wf));
    }

    // ─── Workflow: STEALTH_NETWORKING ───
    private void executeStealthNetworking(AgentWorkflow wf, AgentDtos.OrchestrationRequest req, UUID userId) {
        wf.addThought(AgentPersonality.OUTREACH, "Initiating stealth networking protocol.");

        String company = req.getTargetCompany() != null ? req.getTargetCompany() : "target company";
        String role = req.getTargetRole() != null ? req.getTargetRole() : "desired role";

        // Step 1: Scout researches the company
        wf.addThought(AgentPersonality.SCOUT, "Researching " + company + "...");
        String companyResearch = callAgent(AgentPersonality.SCOUT,
                "Research " + company + " and provide: 1) Recent hiring trends, 2) Key departments, 3) Culture summary. 3-4 sentences.");
        wf.addThought(AgentPersonality.SCOUT, truncate(companyResearch, 200));

        // Step 2: Outreach drafts cold email
        wf.addThought(AgentPersonality.OUTREACH, "Drafting personalized outreach sequence...");
        String resumeContext = "";
        if (req.getResumeId() != null) {
            Resume resume = resumeRepository.findByIdAndUserId(req.getResumeId(), userId).orElse(null);
            if (resume != null) resumeContext = truncate(resume.getRawText(), 1000);
        }
        
        String outreach = callAgent(AgentPersonality.OUTREACH,
                "Draft a cold outreach email to a hiring manager at " + company + " for a " + role + 
                " role. Resume context: " + resumeContext + ". Keep under 150 words. Be warm, specific, professional.");
        wf.addThought(AgentPersonality.OUTREACH, "Initial outreach email drafted.");

        // Step 3: Outreach drafts follow-up
        wf.addThought(AgentPersonality.OUTREACH, "Creating follow-up sequence...");
        String followUp = callAgent(AgentPersonality.OUTREACH,
                "Draft a brief follow-up email (day 5) for the cold outreach to " + company + 
                " for " + role + ". Under 80 words. Reference the original email.");
        wf.addThought(AgentPersonality.OUTREACH, "3-step outreach sequence complete.");

        // SIDE EFFECT: Save a hiring manager lead to networking CRM
        HiringManager newLead = HiringManager.builder()
                .name("Hiring Manager")
                .companyName(company)
                .jobTitle("Head of " + role)
                .function(role)
                .email("placeholder@" + company.toLowerCase().replace(" ", "") + ".com")
                .build();
        hiringManagerRepository.save(newLead);
        wf.getArtifacts().put("savedLeadId", newLead.getId());
        wf.getArtifacts().put("emailDraft", outreach);
        wf.addThought(AgentPersonality.OUTREACH, "Saved new hiring manager lead to your Networking CRM.");

        // Step 4: Analyst assesses culture fit
        wf.addThought(AgentPersonality.ANALYST, "Assessing culture fit probability...");
        String fitAnalysis = callAgent(AgentPersonality.ANALYST,
                "Based on this resume and company, rate culture fit (1-10) and explain in 2 sentences. " +
                "Company: " + company + ". Resume: " + truncate(resumeContext, 500));
        wf.addThought(AgentPersonality.ANALYST, truncate(fitAnalysis, 200));

        wf.setFinalReport(compileFinalReport(wf));
    }

    // ─── Workflow: CUSTOM ───
    private void executeCustom(AgentWorkflow wf, AgentDtos.OrchestrationRequest req, UUID userId) {
        wf.addThought(AgentPersonality.ORCHESTRATOR, "Executing custom goal analysis...");
        
        String result = callAgent(AgentPersonality.ORCHESTRATOR,
                "The user's goal is: \"" + req.getGoal() + "\". " +
                "Analyze this and provide a strategic action plan with 3-5 concrete steps. " +
                "Context: target role = " + req.getTargetRole() + ", target company = " + req.getTargetCompany());
        
        wf.addThought(AgentPersonality.ORCHESTRATOR, "Strategy complete.");
        wf.setFinalReport(result);
    }

    // ─── Core Agent Call ───
    private String callAgent(AgentPersonality agent, String userMessage) {
        try {
            ChatClient client = chatClientBuilder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .withModel(agent.getPreferredModel())
                            .withMaxTokens(1500)
                            .withTemperature(0.7)
                            .build())
                    .build();

            return client.prompt()
                    .system(agent.getSystemPrompt())
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Agent {} failed: {}", agent.getDisplayName(), e.getMessage());
            return "[Agent " + agent.getDisplayName() + " encountered an error]";
        }
    }

    // ─── Helpers ───

    private String buildSearchQuery(AgentDtos.OrchestrationRequest req) {
        String role = req.getTargetRole() != null ? req.getTargetRole().trim() : "";
        String company = req.getTargetCompany() != null ? req.getTargetCompany().trim() : "";
        
        String query = (role + " " + company).trim();
        if (query.isEmpty()) {
            return req.getGoal();
        }
        return query;
    }

    private String resolveWorkflowType(AgentDtos.OrchestrationRequest req) {
        if (req.getWorkflowType() != null && !req.getWorkflowType().isBlank()) {
            return req.getWorkflowType().toUpperCase();
        }
        String goal = req.getGoal().toLowerCase();
        if (goal.contains("apply") || goal.contains("find") || goal.contains("search")) return "AUTO_PILOT";
        if (goal.contains("interview") || goal.contains("prepare") || goal.contains("practice")) return "INTERVIEW_BLITZ";
        if (goal.contains("network") || goal.contains("connect") || goal.contains("outreach") || goal.contains("reach out")) return "STEALTH_NETWORKING";
        return "CUSTOM";
    }

    private String compileFinalReport(AgentWorkflow wf) {
        return callAgent(AgentPersonality.ORCHESTRATOR,
                "Compile a professional summary report from these agent activities. " +
                "User goal: " + wf.getUserGoal() + "\n\n" +
                "Agent Log:\n" + String.join("\n", wf.getThoughts()) +
                "\n\nProvide a 2-3 paragraph executive summary with key findings and next steps.");
    }

    private AgentDtos.OrchestrationResponse buildResponse(AgentWorkflow wf) {
        return AgentDtos.OrchestrationResponse.builder()
                .workflowId(wf.getWorkflowId())
                .workflowType(wf.getWorkflowType())
                .status(wf.getStatus().name())
                .thoughts(wf.getThoughts())
                .finalReport(wf.getFinalReport())
                .durationMs(wf.getTotalDurationMs())
                .artifacts(wf.getArtifacts())
                .steps(wf.getSteps().stream().map(s -> AgentDtos.StepResult.builder()
                        .stepId(s.getId())
                        .agent(s.getAgent().getDisplayName())
                        .agentEmoji(s.getAgent().getEmoji())
                        .action(s.getAction())
                        .status(s.getStatus().name())
                        .durationMs(s.getDurationMs())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
