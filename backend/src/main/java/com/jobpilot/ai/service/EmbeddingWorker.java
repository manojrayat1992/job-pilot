package com.jobpilot.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Persisted delivery, bounded retries and token-fenced atomic replacement of derived vectors. */
@Slf4j
@Service
public class EmbeddingWorker {
    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    private final TransactionTemplate tx;
    public EmbeddingWorker(JdbcTemplate jdbc,EmbeddingService embeddings,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.embeddings=embeddings;this.tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Task(String kind,UUID id,UUID token,int attempt) {
        String table() { return kind.equals("JOB")?"jobs":"resumes"; }
    }
    public Task claim() {
        return tx.execute(status->{
            var rows=jdbc.queryForList("""
                SELECT kind,entity_id,attempts FROM embedding_tasks
                WHERE ((status IN ('PENDING','FAILED') AND attempts<5 AND available_at<=now())
                    OR (status='RUNNING' AND lease_until<now()))
                ORDER BY available_at,entity_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
            if(rows.isEmpty()) return null;
            var row=rows.getFirst();String kind=row.get("kind").toString();UUID id=(UUID)row.get("entity_id");
            int attempt=((Number)row.get("attempts")).intValue()+1;
            if(attempt>5) {
                jdbc.update("UPDATE embedding_tasks SET status='FAILED',token=NULL,lease_until=NULL,last_error='Worker lease expired; retry limit reached',updated_at=now() WHERE kind=? AND entity_id=?",kind,id);
                return null;
            }
            UUID token=UUID.randomUUID();
            jdbc.update("UPDATE embedding_tasks SET status='RUNNING',attempts=?,token=?,lease_until=now()+interval '2 minutes',updated_at=now() WHERE kind=? AND entity_id=?",attempt,token,kind,id);
            return new Task(kind,id,token,attempt);
        });
    }
    @Scheduled(fixedDelayString="${app.search.embedding-poll-ms:5000}")
    public void poll() {
        try {
            for(int i=0;i<10;i++) { var task=claim();if(task==null)break;process(task); }
        } catch(Exception e) { log.warn("Embedding queue unavailable ({})",e.getClass().getSimpleName()); }
    }
    public void process(Task task) {
        try {
            var sources=jdbc.queryForList("SELECT raw_text FROM "+task.table()+" WHERE id=?",task.id());
            if(sources.isEmpty()) return; // A source delete also deletes its queue entry.
            String text=Objects.toString(sources.getFirst().get("raw_text"),"");
            var chunks=task.kind().equals("RESUME")?embeddings.resumeChunks(text):List.<EmbeddingService.Chunk>of();
            String vector=task.kind().equals("JOB")&&!text.isBlank()?EmbeddingService.vectorLiteral(embeddings.getEmbedding(text)):null;
            tx.executeWithoutResult(status->{
                // Source writers lock the parent before its trigger touches the queue. Match that order.
                if(jdbc.queryForList("SELECT id FROM "+task.table()+" WHERE id=? FOR UPDATE",task.id()).isEmpty())return;
                var owned=jdbc.queryForList("SELECT entity_id FROM embedding_tasks WHERE kind=? AND entity_id=? AND token=? AND status='RUNNING' AND lease_until>now() FOR UPDATE",task.kind(),task.id(),task.token());
                if(owned.isEmpty())return;
                if(task.kind().equals("JOB")) {
                    jdbc.update("DELETE FROM job_embeddings WHERE job_id=?",task.id());
                    if(vector!=null)jdbc.update("INSERT INTO job_embeddings(job_id,embedding) VALUES(?,cast(? as vector))",task.id(),vector);
                } else {
                    jdbc.update("DELETE FROM resume_embeddings WHERE resume_id=?",task.id());
                    for(var chunk:chunks)jdbc.update("INSERT INTO resume_embeddings(resume_id,embedding,chunk_text,chunk_index) VALUES(?,cast(? as vector),?,?)",task.id(),chunk.vector(),chunk.text(),chunk.index());
                }
                jdbc.update("UPDATE embedding_tasks SET status='COMPLETED',token=NULL,lease_until=NULL,last_error=NULL,updated_at=now() WHERE kind=? AND entity_id=?",task.kind(),task.id());
            });
        } catch(Exception e) {
            log.warn("Embedding {} {} failed ({})",task.kind(),task.id(),e.getClass().getSimpleName());
            tx.executeWithoutResult(status->jdbc.update("""
                UPDATE embedding_tasks SET status='FAILED',token=NULL,lease_until=NULL,
                  last_error='Embedding generation or storage failed',available_at=now()+(? * interval '1 second'),updated_at=now()
                WHERE kind=? AND entity_id=? AND token=? AND status='RUNNING'
                """,Math.min(300,10*(1<<task.attempt())),task.kind(),task.id(),task.token()));
        }
    }
}
