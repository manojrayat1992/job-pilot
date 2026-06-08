package com.jobpilot.ai.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingService embeddingService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    private final SerpApiClient serpApiClient;
    private final ApplicationRepository applicationRepository;

    public AgentResearchResponse researchJobs(AgentResearchRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<String> thoughts = new ArrayList<>();
        
        // 1. Fetch Resume Context
        Resume resume = null;
        if (request.getResumeId() != null) {
            resume = resumeRepository.findByIdAndUserId(request.getResumeId(), userId).orElse(null);
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
        // Truncate resume text to keep total prompt size manageable
        String truncatedResume = truncate(resumeText, 2000);

        // Combine all jobs for the LLM to rank, with truncated descriptions
        StringBuilder jobsContext = new StringBuilder();
        int count = 1;
        for (Job j : internalJobs) {
            jobsContext.append(String.format("Job %d (Internal):\nTitle: %s\nCompany: %s\nDescription: %s\n\n", 
                count++, j.getTitle(), j.getCompany(), truncate(j.getRawText(), 500)));
        }
        for (Map<String, Object> j : webJobs) {
            jobsContext.append(String.format("Job %d (Web):\nTitle: %s\nCompany: %s\nURL: %s\nDescription: %s\n\n", 
                count++, j.get("title"), j.get("company"), j.get("url"), truncate((String) j.get("description"), 500)));
        }

        String prompt = String.format("""
            You are a Job Research Agent. Below is a user query, their resume (truncated), and a list of jobs found.
            
            User Query: %s
            
            Resume: %s
            
            Jobs Found:
            %s
            
            Your Task:
            1. Analyze how well each job fits the user's resume and query.
            2. Produce a professional summary report (1 paragraph max).
            3. Return ONLY a valid JSON object.
            4. Include only the TOP 10 most relevant jobs to keep the response within limits.
            5. Be extremely concise in 'matchReason' and 'description' (max 2 short sentences each).
            
            JSON Structure:
            {
              "report": "Overall analysis...",
              "jobs": [
                {
                  "title": "...",
                  "company": "...",
                  "location": "...",
                  "url": "...",
                  "description": "...",
                  "matchScore": 85,
                  "matchReason": "Why it fits...",
                  "source": "Internal/Web"
                }
              ]
            }
            """, query, truncatedResume, jobsContext.toString());

        try {
            ChatClient chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                    .withModel("llama-3.1-8b-instant")
                    .withMaxTokens(2000)
                    .withTemperature(0.7)
                    .build())
                .build();
            
            String response = chatClient.prompt().user(prompt).call().content();
            log.debug("Agent Raw Response: {}", response);
            
            // Extract JSON - handle cases where LLM might wrap in markdown
            String cleaned = response.trim();
            if (cleaned.contains("```json")) {
                cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
                if (cleaned.contains("```")) {
                    cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
                }
            } else {
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                if (start != -1 && end != -1) cleaned = cleaned.substring(start, end + 1);
            }
            cleaned = cleaned.trim();
            
            // Use a more lenient mapper for AI-generated JSON
            ObjectMapper aiMapper = new ObjectMapper();
            aiMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            aiMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            aiMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            aiMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            Map<String, Object> parsed = aiMapper.readValue(cleaned, Map.class);
            
            List<Map<String, Object>> jobsData = (List<Map<String, Object>>) parsed.get("jobs");
            List<AgentResearchResponse.RankedJob> rankedJobs = jobsData.stream()
                .map(m -> AgentResearchResponse.RankedJob.builder()
                    .title((String) m.get("title"))
                    .company((String) m.get("company"))
                    .location((String) m.get("location"))
                    .url((String) m.get("url"))
                    .description((String) m.get("description"))
                    .matchScore(m.get("matchScore") != null ? ((Number) m.get("matchScore")).intValue() : 0)
                    .matchReason((String) m.get("matchReason"))
                    .source((String) m.get("source"))
                    .build())
                .toList();

            // Automatically save web results to the internal database so they appear in "Jobs"
            UUID userId = SecurityUtils.getCurrentUserId();
            rankedJobs.stream()
                .filter(rj -> "Web".equalsIgnoreCase(rj.getSource()))
                .forEach(rj -> {
                    if (rj.getUrl() != null && !jobRepository.existsByUserIdAndSourceUrl(userId, rj.getUrl())) {
                        createNewJob(userId, rj);
                    }
                });

            return AgentResearchResponse.builder()
                .report((String) parsed.get("report"))
                .jobs(rankedJobs)
                .thoughts(thoughts)
                .build();
        } catch (Exception e) {
            log.error("Agent failed to compile report", e);
            throw new AiServiceException("Agent failed to compile report: " + e.getMessage(), e);
        }
    }

    public void autoApply(AgentAutoApplyRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        
        for (AgentResearchResponse.RankedJob rj : request.getJobs()) {
            UUID jobId;
            if ("Internal".equalsIgnoreCase(rj.getSource())) {
                // If internal, try to find it (using a simplified search or just create if missing)
                jobId = jobRepository.findTop5ByUserIdOrderByEmbedding(userId, embeddingService.getEmbedding(rj.getTitle() + " " + rj.getCompany()))
                        .stream()
                        .filter(j -> j.getTitle().equalsIgnoreCase(rj.getTitle()) && j.getCompany().equalsIgnoreCase(rj.getCompany()))
                        .findFirst()
                        .map(Job::getId)
                        .orElseGet(() -> createNewJob(userId, rj).getId());
            } else {
                // If Web, check if we already saved it by ID to avoid vector column issues
                jobId = jobRepository.findIdByUserIdAndSourceUrl(userId, rj.getUrl())
                        .orElseGet(() -> createNewJob(userId, rj).getId());
            }
            
            // 2. Create Application
            JobApplication application = JobApplication.builder()
                    .userId(userId)
                    .jobId(jobId)
                    .resumeId(request.getResumeId())
                    .company(rj.getCompany())
                    .position(rj.getTitle())
                    .status(JobApplication.ApplicationStatus.APPLIED)
                    .appliedDate(java.time.LocalDate.now())
                    .source("Agent Auto-Apply")
                    .notes("Automatically applied via Autonomous Job Researcher. Match Score: " + rj.getMatchScore() + "%")
                    .build();
            
            applicationRepository.save(application);
        }
    }

    private Job createNewJob(UUID userId, AgentResearchResponse.RankedJob rj) {
        Job job = Job.builder()
                .userId(userId)
                .title(rj.getTitle())
                .company(rj.getCompany())
                .location(rj.getLocation())
                .sourceUrl(rj.getUrl())
                .rawText(rj.getDescription() != null ? rj.getDescription() : rj.getMatchReason())
                .embedding(embeddingService.getEmbedding(rj.getTitle() + " " + rj.getCompany() + " " + rj.getDescription()))
                .build();
        return jobRepository.save(job);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... [truncated]";
    }
}
