package com.jobpilot.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Embedding service — generates vector embeddings and stores them in pgvector.
 * Also provides RAG context retrieval via similarity search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    private static final int CHUNK_SIZE = 1000;  // characters per chunk
    private static final int CHUNK_OVERLAP = 200;

    /**
     * Chunk the resume text, embed each chunk, and store in resume_embeddings.
     */
    public void embedAndStoreResume(UUID resumeId, String text) {
        // Delete existing embeddings for this resume
        jdbcTemplate.update("DELETE FROM resume_embeddings WHERE resume_id = ?", resumeId);

        List<String> chunks = chunkText(text);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = getEmbedding(chunk);
            String vectorStr = toVectorString(embedding);

            jdbcTemplate.update(
                    "INSERT INTO resume_embeddings (id, resume_id, embedding, chunk_text, chunk_index) " +
                    "VALUES (uuid_generate_v4(), ?, cast(? as vector), ?, ?)",
                    resumeId, vectorStr, chunk, i
            );
        }

        log.info("Stored {} embedding chunks for resume {}", chunks.size(), resumeId);
    }

    /**
     * Embed the full job description and store in job_embeddings.
     */
    public void embedAndStoreJob(UUID jobId, String text) {
        jdbcTemplate.update("DELETE FROM job_embeddings WHERE job_id = ?", jobId);

        float[] embedding = getEmbedding(text);
        String vectorStr = toVectorString(embedding);

        jdbcTemplate.update(
                "INSERT INTO job_embeddings (id, job_id, embedding) " +
                "VALUES (uuid_generate_v4(), ?, cast(? as vector))",
                jobId, vectorStr
        );

        log.info("Stored embedding for job {}", jobId);
    }

    /**
     * Build RAG context by finding the most similar resume chunks to the job text.
     */
    public String buildRagContext(UUID userId, String jobText) {
        try {
            float[] queryEmbedding = getEmbedding(jobText);
            String vectorStr = toVectorString(queryEmbedding);

            List<String> similarChunks = jdbcTemplate.queryForList(
                    """
                    SELECT re.chunk_text
                    FROM resume_embeddings re
                    JOIN resumes r ON re.resume_id = r.id
                    WHERE r.user_id = ?
                    ORDER BY re.embedding <=> cast(? as vector)
                    LIMIT 5
                    """,
                    String.class,
                    userId, vectorStr
            );

            if (similarChunks.isEmpty()) {
                return null;
            }

            return "Here are relevant excerpts from your past resumes:\n\n" +
                    String.join("\n\n---\n\n", similarChunks);

        } catch (Exception e) {
            log.warn("RAG context retrieval failed: {}", e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    public float[] getEmbedding(String text) {
        return embeddingModel.embed(text);
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // Try to break at a sentence boundary
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int breakPoint = Math.max(lastPeriod, lastNewline);
                if (breakPoint > start + CHUNK_SIZE / 2) {
                    end = breakPoint + 1;
                }
            }

            chunks.add(text.substring(start, end).trim());
            int nextStart = end - CHUNK_OVERLAP;
            start = (nextStart <= start) ? end : nextStart;
            if (start >= text.length()) break;
        }

        return chunks;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
