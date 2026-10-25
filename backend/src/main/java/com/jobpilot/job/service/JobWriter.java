package com.jobpilot.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.job.entity.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Shared atomic insert/reuse boundary. PostgreSQL computes the same identity for every writer. */
@Service
@RequiredArgsConstructor
public class JobWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @org.springframework.transaction.annotation.Transactional
    public UUID save(Job job) {
        if (job.getUserId()==null || job.getRawText()==null) throw new BadRequestException("Job owner and text are required");
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, job.getUserId());
        String json;
        try { json=job.getParsedJson()==null ? null : mapper.writeValueAsString(job.getParsedJson()); }
        catch (JsonProcessingException e) { throw new BadRequestException("Invalid parsed job"); }
        return jdbc.query(connection -> {
            var statement=connection.prepareStatement("""
                INSERT INTO jobs(id,user_id,title,company,location,source_url,raw_text,parsed_json,
                  experience_level,job_type,salary_range,skills_required,skills_nice)
                VALUES(?,?,?,?,?,?,?,cast(? as jsonb),?,?,?,?,?)
                ON CONFLICT(user_id,dedup_key) DO UPDATE SET id=jobs.id RETURNING id
                """);
            statement.setObject(1,job.getId()==null?UUID.randomUUID():job.getId());
            statement.setObject(2,job.getUserId()); statement.setString(3,job.getTitle());
            statement.setString(4,job.getCompany()); statement.setString(5,job.getLocation());
            statement.setString(6,job.getSourceUrl()==null?null:job.getSourceUrl().trim()); statement.setString(7,job.getRawText());
            statement.setString(8,json); statement.setString(9,job.getExperienceLevel()); statement.setString(10,job.getJobType());
            statement.setString(11,job.getSalaryRange());
            statement.setArray(12,job.getSkillsRequired()==null?null:connection.createArrayOf("text",job.getSkillsRequired()));
            statement.setArray(13,job.getSkillsNice()==null?null:connection.createArrayOf("text",job.getSkillsNice()));
            return statement;
        }, result -> { result.next(); return result.getObject(1,UUID.class); });
    }
}
