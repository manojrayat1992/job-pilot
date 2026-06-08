package com.jobpilot.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerpApiClient {

    private final RestTemplate restTemplate;

    @Value("${app.serpapi.api-key:}")
    private String apiKey;

    @Value("${app.serpapi.base-url:https://serpapi.com}")
    private String baseUrl;

    /**
     * Search for jobs using SerpAPI's Google Jobs endpoint
     * @param query Search query (e.g., "senior software engineer remote")
     * @return List of job maps with keys: title, company, location, url, description
     */
    public List<Map<String, Object>> searchJobs(String query) {
        // If no API key is configured, return empty list
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_serpapi_key_here")) {
            log.warn("SerpAPI key not configured. Skipping external job search.");
            return new ArrayList<>();
        }

        try {
            String url = String.format("%s/search.json?engine=google_jobs&q=%s&api_key=%s",
                    baseUrl, query, apiKey);

            log.info("Calling SerpAPI for job search: {}", query);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response == null) {
                log.warn("SerpAPI returned null response");
                return new ArrayList<>();
            }

            return parseJobsResponse(response);
        } catch (HttpClientErrorException e) {
            log.error("SerpAPI client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (HttpServerErrorException e) {
            log.error("SerpAPI server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error calling SerpAPI", e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse SerpAPI response and extract job listings
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJobsResponse(Map<String, Object> response) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        
        // SerpAPI returns jobs in "jobs_results" array
        Object jobsResults = response.get("jobs_results");
        if (!(jobsResults instanceof List<?>)) {
            log.warn("No jobs_results found in SerpAPI response");
            return jobs;
        }

        List<?> jobList = (List<?>) jobsResults;
        log.info("Found {} jobs from SerpAPI", jobList.size());

        for (Object jobObj : jobList) {
            if (!(jobObj instanceof Map)) continue;
            
            Map<String, Object> job = (Map<String, Object>) jobObj;
            
            try {
                Map<String, Object> jobMap = Map.of(
                    "title", job.getOrDefault("title", "Unknown Title"),
                    "company", job.getOrDefault("company_name", "Unknown Company"),
                    "location", job.getOrDefault("location", "Unknown Location"),
                    "url", extractApplyUrl(job),
                    "description", job.getOrDefault("description", "")
                );
                jobs.add(jobMap);
            } catch (Exception e) {
                log.warn("Failed to parse job entry: {}", e.getMessage());
            }
        }

        return jobs;
    }

    /**
     * Extract the apply URL from job data
     * SerpAPI may have apply_options array or direct apply_link
     */
    @SuppressWarnings("unchecked")
    private String extractApplyUrl(Map<String, Object> job) {
        // Check for apply_options array first
        Object applyOptions = job.get("apply_options");
        if (applyOptions instanceof List<?> && !((List<?>) applyOptions).isEmpty()) {
            Object firstOption = ((List<?>) applyOptions).get(0);
            if (firstOption instanceof Map) {
                Object link = ((Map<String, Object>) firstOption).get("link");
                if (link != null) {
                    return link.toString();
                }
            }
        }
        
        // Fall back to apply_link or job_id
        Object applyLink = job.get("apply_link");
        if (applyLink != null) {
            return applyLink.toString();
        }
        
        Object jobId = job.get("job_id");
        if (jobId != null) {
            return String.format("https://www.google.com/search?q=job+%s", jobId.toString());
        }
        
        return "#";
    }
}
