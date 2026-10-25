package com.jobpilot.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** One 384-dimensional embedding space for jobs, resume chunks and search queries. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private static final int CHUNK_SIZE=1000, CHUNK_OVERLAP=200;

    public float[] getEmbedding(String text) {
        if (text==null || text.isBlank()) throw new IllegalArgumentException("Embedding text is empty");
        float[] vector=embeddingModel.embed(text);
        validate(vector);
        return vector;
    }
    public static String vectorLiteral(float[] vector) {
        validate(vector);
        var result=new StringJoiner(",","[","]");
        for(float value:vector) result.add(Float.toString(value));
        return result.toString();
    }
    private static void validate(float[] vector) {
        if(vector==null || vector.length!=384) throw new IllegalArgumentException("Expected 384 embedding dimensions");
        boolean nonzero=false;
        for(float value:vector) {
            if(!Float.isFinite(value)) throw new IllegalArgumentException("Embedding contains non-finite values");
            nonzero |= value!=0;
        }
        if(!nonzero) throw new IllegalArgumentException("Cannot search with a zero embedding");
    }
    public record Chunk(int index,String text,String vector) {}
    public List<Chunk> resumeChunks(String text) {
        var result=new ArrayList<Chunk>();
        if(text==null || text.isBlank()) return result;
        int start=0;
        while(start<text.length()) {
            int end=Math.min(start+CHUNK_SIZE,text.length());
            if(end<text.length()) {
                int boundary=Math.max(text.lastIndexOf('.',end-1),text.lastIndexOf('\n',end-1));
                if(boundary>start+CHUNK_SIZE/2) end=boundary+1;
            }
            String chunk=text.substring(start,end).trim();
            if(!chunk.isBlank()) result.add(new Chunk(result.size(),chunk,vectorLiteral(getEmbedding(chunk))));
            if(end==text.length()) break;
            start=Math.max(start+1,end-CHUNK_OVERLAP);
        }
        return result;
    }
    public String buildRagContext(UUID userId,String jobText) {
        try {
            String vector=vectorLiteral(getEmbedding(jobText));
            List<String> chunks=jdbcTemplate.queryForList("""
                SELECT re.chunk_text FROM resume_embeddings re JOIN resumes r ON r.id=re.resume_id
                JOIN embedding_tasks t ON t.kind='RESUME' AND t.entity_id=r.id AND t.status='COMPLETED'
                WHERE r.user_id=? AND r.is_transient=false AND re.embedding IS NOT NULL
                ORDER BY re.embedding <=> cast(? as vector),re.resume_id,re.chunk_index LIMIT 5
                """,String.class,userId,vector);
            return chunks.isEmpty()?null:"Here are relevant excerpts from your past resumes:\n\n"+String.join("\n\n---\n\n",chunks);
        } catch(Exception e) {
            log.warn("RAG retrieval unavailable ({})",e.getClass().getSimpleName());
            return null;
        }
    }
}
