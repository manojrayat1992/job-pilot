package com.jobpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.ai.dto.AgentAutoApplyRequest;
import com.jobpilot.ai.dto.AgentResearchRequest;
import com.jobpilot.ai.dto.AgentResearchResponse;
import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.common.exception.AiServiceException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.job.entity.Job;
import com.jobpilot.job.repository.JobRepository;
import com.jobpilot.resume.entity.Resume;
import com.jobpilot.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatGenerationService chatGenerationService;
    private final EmbeddingService embeddingService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    private final SerpApiClient serpApiClient;
    private final ApplicationRepository applicationRepository;
    private final com.jobpilot.job.service.JobWriter jobWriter;

    public AgentResearchResponse researchJobs(AgentResearchRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<String> thoughts = new ArrayList<>();
        
        // 1. Fetch Resume Context
        Resume resume = null;
        if (request.getResumeId() != null) {
            resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId)
                    .orElseThrow(() -> new com.jobpilot.common.exception.ResourceNotFoundException("Resume", "id", request.getResumeId()));
        }
        String resumeText = resume != null ? resume.getRawText() : "No resume provided.";

        // --- AGENT STEP A & B: Internal Search ---
        thoughts.add("Thought: I need to search the internal vector database first to see if we already have matching opportunities.");
        thoughts.add("Action: searchInternalVectors(\"" + request.getQuery() + "\")");
        
        float[] queryEmbedding = embeddingService.getEmbedding(request.getQuery());
        List<Job> internalJobs = jobRepository.findTop5ByUserIdOrderByEmbedding(userId, queryEmbedding);
        
        // --- AGENT STEP C: Observation ---
        thoughts.add("Observation: Found " + internalJobs.size() + " potential matches in the local database.");

        // --- AGENT STEP D & E: Web Search (Conditional) ---
        List<Map<String, Object>> webJobs = new ArrayList<>();
        if (internalJobs.size() < 3) {
            thoughts.add("Thought: Internal results are insufficient (less than 3 matches). I need to conduct a broader web search for fresh listings.");
            thoughts.add("Action: callSerpAPI(\"remote " + request.getQuery() + " jobs\")");
            
            // Call real SerpAPI
            webJobs = serpApiClient.searchJobs(request.getQuery());
            
            // --- AGENT STEP F: Observation ---
            if (webJobs.isEmpty()) {
                thoughts.add("Observation: No external job listings found via SerpAPI. Proceeding with internal results only.");
            } else {
                thoughts.add("Observation: Successfully retrieved " + webJobs.size() + " fresh job listings from LinkedIn and Indeed via SerpAPI.");
            }
        } else {
            thoughts.add("Thought: Sufficient internal matches found. I will prioritize these and skip the external web search to maximize relevance.");
        }

        // --- AGENT STEP G & H: Compilation ---
        thoughts.add("Thought: Now I will compile a comprehensive match analysis report by comparing the user's resume against the aggregated job list.");
        thoughts.add("Action: generateReport(resumeId, jobsList)");
        
        return compileFinalReport(request.getQuery(), resumeText, internalJobs, webJobs, thoughts);
    }

    private AgentResearchResponse compileFinalReport(String query, String resumeText, List<Job> internalJobs, List<Map<String, Object>> webJobs, List<String> thoughts) {
        // The model ranks stable candidates; listing identity/content comes from the actual source.
        var candidates = new LinkedHashMap<String, AgentResearchResponse.RankedJob>();
        for (var job : internalJobs) candidates.put("candidate-" + candidates.size(), AgentResearchResponse.RankedJob.builder()
                .id(job.getId()).title(job.getTitle()).company(job.getCompany()).location(job.getLocation())
                .url(job.getSourceUrl()).description(job.getRawText()).source("Internal").build());
        for (var job : webJobs.stream().limit(10).toList()) candidates.put("candidate-" + candidates.size(), AgentResearchResponse.RankedJob.builder()
                .title(Objects.toString(job.get("title"), "Untitled role")).company(Objects.toString(job.get("company"), "Unknown company"))
                .location(Objects.toString(job.get("location"), "")).url(Objects.toString(job.get("url"), ""))
                .description(Objects.toString(job.get("description"), "")).source("Web").build());
        if (candidates.isEmpty()) return AgentResearchResponse.builder().report("No job listings were found.").jobs(List.of()).thoughts(thoughts).build();
        try {
            var promptCandidates = new ArrayList<Map<String,Object>>();
            candidates.forEach((key,job) -> promptCandidates.add(Map.of("candidateId",key,"title",Objects.toString(job.getTitle(),""),
                    "company",Objects.toString(job.getCompany(),""),"description",truncate(job.getDescription(),1000))));
            String prompt = "Rank only these job candidates for the query and resume. Return JSON with report and jobs (at most 10). "
                    + "Each jobs item must contain candidateId copied exactly, matchScore (0-100), and matchReason. Do not invent candidates.\nQuery: "
                    + query + "\nResume: " + truncate(resumeText,2000) + "\nCandidates: " + objectMapper.writeValueAsString(promptCandidates);
            var result = chatGenerationService.generate(null,prompt,2000,0.7);
            var parsed = com.jobpilot.common.util.JsonExtractor.extractRequiredJson(result.content());
            if (!(parsed.get("jobs") instanceof List<?> rankings)) throw new AiServiceException("Missing candidate rankings");
            var selected = new ArrayList<AgentResearchResponse.RankedJob>();
            var seen = new HashSet<String>();
            for (Object item : rankings.stream().limit(10).toList()) {
                if (!(item instanceof Map<?,?> ranking) || !(ranking.get("candidateId") instanceof String key) || !candidates.containsKey(key))
                    throw new AiServiceException("Ranking referenced an unknown candidate");
                if (!seen.add(key)) continue;
                var job = candidates.get(key);
                job.setMatchScore(ranking.get("matchScore") instanceof Number number ? Math.max(0,Math.min(100,number.intValue())) : null);
                job.setMatchReason(Objects.toString(ranking.get("matchReason"),""));
                selected.add(job);
            }
            // Validate every candidate before creating records; generated URLs/descriptions are never persisted.
            UUID user = SecurityUtils.getCurrentUserId();
            for (var job : selected) if (job.getId()==null) job.setId(createNewJob(user,job).getId());
            return AgentResearchResponse.builder().report(Objects.toString(parsed.get("report"),""))
                    .jobs(selected).thoughts(thoughts).build();
        } catch (com.jobpilot.common.exception.CreditExhaustedException | com.jobpilot.common.exception.CreditConflictException e) {
            throw e;
        } catch (Exception e) { throw new AiServiceException("Could not rank job candidates",e); }
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveJobs(AgentAutoApplyRequest request) {
        UUID userId=SecurityUtils.getCurrentUserId();
        if(request.getResumeId()!=null) resumeRepository.findByIdAndUserId(request.getResumeId(),userId)
                .orElseThrow(()->new com.jobpilot.common.exception.ResourceNotFoundException("Resume","id",request.getResumeId()));
        applicationRepository.lockOwner(userId);
        for(var ranked:request.getJobs()) {
            Job job;
            if(ranked.getId()!=null) job=jobRepository.findByIdAndUserId(ranked.getId(),userId)
                    .orElseThrow(()->new com.jobpilot.common.exception.ResourceNotFoundException("Job","id",ranked.getId()));
            else {
                if("Internal".equalsIgnoreCase(ranked.getSource())) throw new com.jobpilot.common.exception.BadRequestException("Run research again to select an internal job by its saved ID");
                job=createNewJob(userId,ranked);
            }
            if(applicationRepository.existsByUserIdAndJobId(userId,job.getId())) continue;
            applicationRepository.save(JobApplication.builder().userId(userId).jobId(job.getId()).resumeId(request.getResumeId())
                    .company(Objects.toString(job.getCompany(),ranked.getCompany())).position(Objects.toString(job.getTitle(),ranked.getTitle()))
                    .status(JobApplication.ApplicationStatus.SAVED).source("Job Research")
                    .notes("Saved for review. No application has been submitted.").build());
        }
    }
    private Job createNewJob(UUID user,AgentResearchResponse.RankedJob ranked) {
        UUID id=jobWriter.save(Job.builder().userId(user).title(ranked.getTitle()).company(ranked.getCompany())
                .location(ranked.getLocation()).sourceUrl(ranked.getUrl()).rawText(Objects.toString(ranked.getDescription(),""))
                .build());
        return jobRepository.findByIdAndUserId(id,user).orElseThrow();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... [truncated]";
    }
}
