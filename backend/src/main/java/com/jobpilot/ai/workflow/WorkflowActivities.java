package com.jobpilot.ai.workflow;

import com.jobpilot.ai.service.*;
import com.jobpilot.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** External activities execute outside checkpoint transactions; local imports execute inside them. */
@Service
@RequiredArgsConstructor
public class WorkflowActivities {
    private final EmbeddingService embeddings;
    private final JobRepository jobs;
    private final SerpApiClient search;
    private final JdbcTemplate jdbc;
    private final com.jobpilot.job.service.JobWriter writer;

    public Map<String, Object> search(WorkflowStore.Run run) {
        String query = run.context().get("query").toString();
        var found = jobs.findTop5ByUserIdOrderByEmbedding(run.user(), embeddings.getEmbedding(query));
        var candidates = new ArrayList<Map<String, Object>>();
        for (var job : found) candidates.add(Map.of("id", job.getId().toString(), "new", false,
                "title", Objects.toString(job.getTitle(), ""), "company", Objects.toString(job.getCompany(), ""),
                "description", WorkflowDefinition.clip(job.getRawText(), 4000)));
        if (found.size() < 3) {
            for (var job : search.searchJobs(query).stream().limit(10).toList()) {
                var candidate = new HashMap<String, Object>();
                candidate.put("id", UUID.randomUUID().toString()); candidate.put("new", true);
                candidate.put("title", WorkflowDefinition.clip(Objects.toString(job.get("title"), "Untitled role"), 255));
                candidate.put("company", WorkflowDefinition.clip(Objects.toString(job.get("company"), ""), 255));
                candidate.put("location", WorkflowDefinition.clip(Objects.toString(job.get("location"), ""), 255));
                candidate.put("url", WorkflowDefinition.clip(Objects.toString(job.get("url"), ""), 1000));
                candidate.put("description", WorkflowDefinition.clip(Objects.toString(job.get("description"), ""), 12000));
                candidates.add(candidate);
            }
        }
        return new HashMap<>(Map.of("jobs", candidates));
    }
    @SuppressWarnings("unchecked")
    public void saveJobs(UUID user, Map<String, Object> output) {
        // Serialize this workflow's imports for the owner, and commit them with the step checkpoint.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, user);
        var ids = new LinkedHashSet<String>();
        for (var job : (List<Map<String, Object>>) output.get("jobs")) {
            UUID id = UUID.fromString(job.get("id").toString());
            if (Boolean.TRUE.equals(job.get("new"))) {
                String url = job.get("url").toString();
                id = writer.save(com.jobpilot.job.entity.Job.builder().id(id).userId(user)
                        .title(job.get("title").toString()).company(job.get("company").toString())
                        .location(job.get("location").toString()).sourceUrl(url)
                        .rawText(job.get("description").toString()).build());
                job.put("id", id.toString());
            }
            ids.add(id.toString());
        }
        output.put("scoutedJobs", new ArrayList<>(ids));
    }
}
