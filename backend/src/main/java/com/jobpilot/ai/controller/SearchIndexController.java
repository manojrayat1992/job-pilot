package com.jobpilot.ai.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/v1/search/indexing")
@RequiredArgsConstructor
public class SearchIndexController {
    private final JdbcTemplate jdbc;
    @GetMapping
    public ApiResponse<List<Map<String,Object>>> list() {
        UUID user=SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(jdbc.queryForList("""
            SELECT t.kind,t.entity_id,t.status,t.attempts,t.last_error,t.updated_at FROM embedding_tasks t
            LEFT JOIN jobs j ON t.kind='JOB' AND j.id=t.entity_id
            LEFT JOIN resumes r ON t.kind='RESUME' AND r.id=t.entity_id
            WHERE j.user_id=? OR r.user_id=? ORDER BY t.updated_at DESC,t.entity_id LIMIT 100
            """,user,user));
    }
    @PostMapping("/{kind}/{id}/retry") @Transactional
    public ApiResponse<Void> retry(@PathVariable String kind,@PathVariable UUID id) {
        String table=switch(kind) { case "JOB"->"jobs";case "RESUME"->"resumes";default->throw new BadRequestException("Invalid source kind"); };
        if(jdbc.queryForList("SELECT id FROM "+table+" WHERE id=? AND user_id=? FOR UPDATE",id,SecurityUtils.getCurrentUserId()).isEmpty())
            throw new ResourceNotFoundException("Index source","id",id);
        jdbc.update("""
            UPDATE embedding_tasks SET status='PENDING',generation=generation+1,attempts=0,token=NULL,lease_until=NULL,
                last_error=NULL,available_at=now(),updated_at=now() WHERE kind=? AND entity_id=? AND
                (status='FAILED' OR (status='RUNNING' AND lease_until<now()))
            """,kind,id);
        return ApiResponse.ok(null);
    }
}
