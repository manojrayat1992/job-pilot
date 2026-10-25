package com.jobpilot.ai.workflow;

import com.jobpilot.ai.agent.AgentDtos.OrchestrationRequest;
import com.jobpilot.ai.agent.AgentPersonality;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Snapshot inputs and versioned step definitions once, before any provider work. */
@Service
@RequiredArgsConstructor
public class WorkflowDefinition {
    private final ResumeRepository resumes;
    private final JobRepository jobs;
    public record Step(String key, AgentPersonality agent, String action, String kind, String artifact, String prompt) {}
    public record Plan(String type, Map<String, Object> context, List<Step> steps) {}

    public Plan plan(OrchestrationRequest request, UUID user) {
        String type = resolveType(request);
        String resume = "No resume supplied. Do not invent experience.";
        if (request.getResumeId() != null) {
            resume = resumes.findByIdAndUserId(request.getResumeId(), user)
                    .orElseThrow(() -> new ResourceNotFoundException("Resume", "id", request.getResumeId())).getRawText();
        } else {
            var active = resumes.findByUserIdAndIsActiveTrue(user);
            if (active.isPresent()) resume = active.get().getRawText();
        }
        String role = text(request.getTargetRole(), "the target role");
        String company = text(request.getTargetCompany(), "the target company");
        String job = role + " at " + company;
        if (request.getJobId() != null) {
            var selected = jobs.findByIdAndUserId(request.getJobId(), user)
                    .orElseThrow(() -> new ResourceNotFoundException("Job", "id", request.getJobId()));
            job = selected.getTitle() + " at " + selected.getCompany() + ": " + clip(selected.getRawText(), 4000);
        }
        String query = (text(request.getTargetRole(), "") + " " + text(request.getTargetCompany(), "")).trim();
        if (query.isEmpty()) query = request.getGoal();
        var context = Map.<String, Object>of("resume", clip(resume, 8000), "job", job, "query", query);
        var steps = new ArrayList<Step>();
        switch (type) {
            case "AUTO_PILOT" -> {
                steps.add(new Step("search", AgentPersonality.SCOUT, "Find and save matching jobs", "SEARCH", "scoutedJobs", query));
                steps.add(chat("resume", AgentPersonality.GHOSTWRITER, "Prepare resume suggestions", "resumeSuggestions",
                        "Suggest an objective and three bullet improvements for this resume. Preserve facts; do not invent experience. " +
                                "Resume: " + clip(resume, 8000) + "\nJobs: {{jobContext}}"));
                steps.add(chat("salary", AgentPersonality.ANALYST, "Draft compensation guidance", "salaryAdvice",
                        "Give compensation guidance for " + query + ". Label estimates and uncertainty. Do not claim live market research or invent citations."));
            }
            case "INTERVIEW_BLITZ" -> {
                if (request.getTargetCompany() != null && !request.getTargetCompany().isBlank())
                    steps.add(chat("company", AgentPersonality.ANALYST, "Prepare company background", "companyInsights",
                            "Give interview preparation background for " + company + ". Clearly distinguish general guidance from verified facts; no live research was performed."));
                steps.add(chat("questions", AgentPersonality.COACH, "Generate practice questions", "questions",
                        "Generate five interview questions for " + job + ". Return only a JSON array of strings."));
                steps.add(chat("thank-you", AgentPersonality.GHOSTWRITER, "Draft thank-you email", "thankYouDraft",
                        "Draft a post-interview thank-you email for " + role + " at " + company + ". Under 100 words; use placeholders for unknown details."));
            }
            case "STEALTH_NETWORKING" -> {
                steps.add(chat("company", AgentPersonality.SCOUT, "Prepare company background", "companyInsights",
                        "Give background and networking considerations for " + company + ". Label uncertainty; do not claim current research or verified contacts."));
                steps.add(chat("outreach", AgentPersonality.OUTREACH, "Draft outreach email", "emailDraft",
                        "Draft an outreach email to a hiring manager at " + company + " for " + role + ". Under 150 words. " +
                                "Use placeholders for unknown recipient details. Resume: " + clip(resume, 8000)));
                steps.add(chat("follow-up", AgentPersonality.OUTREACH, "Draft follow-up email", "followUpDraft",
                        "Draft a day-five follow-up under 80 words for this draft. No message has been sent.\n{{emailDraft}}"));
                steps.add(chat("culture", AgentPersonality.ANALYST, "Identify fit questions", "cultureAnalysis",
                        "Suggest questions to evaluate fit at " + company + ". Avoid unsupported culture scores. Resume: " + clip(resume, 4000)));
            }
            case "CUSTOM" -> steps.add(chat("plan", AgentPersonality.ORCHESTRATOR, "Prepare action plan", "finalReport",
                    "Prepare a concrete action plan for: " + request.getGoal() + ". Role: " + role + ". Company: " + company + ". Do not claim actions were performed."));
            default -> throw new BadRequestException("Unsupported workflow type");
        }
        if (!type.equals("CUSTOM")) steps.add(chat("report", AgentPersonality.ORCHESTRATOR, "Compile report", "finalReport",
                "Summarize the saved outputs below for the goal: " + request.getGoal() + ". Do not claim applications or messages were sent.\n{{outputs}}"));
        return new Plan(type, context, steps);
    }
    private Step chat(String key, AgentPersonality agent, String action, String artifact, String prompt) {
        return new Step(key, agent, action, "CHAT", artifact, prompt);
    }
    private String resolveType(OrchestrationRequest request) {
        if (request.getWorkflowType() != null && !request.getWorkflowType().isBlank()) return request.getWorkflowType().toUpperCase(Locale.ROOT);
        String goal = request.getGoal().toLowerCase(Locale.ROOT);
        if (goal.matches(".*(apply|find|search).*")) return "AUTO_PILOT";
        if (goal.matches(".*(interview|prepare|practice).*")) return "INTERVIEW_BLITZ";
        if (goal.matches(".*(network|connect|outreach|reach out).*")) return "STEALTH_NETWORKING";
        return "CUSTOM";
    }
    private static String text(String text, String fallback) { return text == null || text.isBlank() ? fallback : text.trim(); }
    static String clip(String text, int length) { return text == null ? "" : text.substring(0, Math.min(length, text.length())); }
}
