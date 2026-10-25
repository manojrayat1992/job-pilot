# Search and saved-job integrity — 25 October 2026

This increment completes the embedding storage/delivery and concurrent job-save work identified in PROJECT-ASSESSMENT.md and STABILIZATION.md. It does not introduce Temporal or replace the existing workflow engine.

## Consistent search storage

Job search now joins `job_embeddings` to owned jobs and completed indexing tasks. Every job writer uses the same path, including normal job analysis, research imports and workflow imports. The unused legacy `jobs.embedding` and `resumes.embedding` columns remain in the database for historical compatibility but are no longer mapped or used by application search.

Job embeddings use saved `raw_text`; resume embeddings use overlapping chunks of saved canonical text. Queries use the same configured local `EmbeddingModel`. Vectors must contain exactly 384 finite values and must not be all zero. Empty sources complete without a vector, so they do not appear in semantic results. Resume retrieval excludes transient resumes and scopes results to the current owner; it can still retrieve multiple permanent versions, as before.

Pending, failed or interrupted indexing is excluded from search. Old vectors can remain stored during a rebuild, but are not served as current context. Resume chunking now stops at the end of the text instead of adding a duplicate trailing overlap chunk.

## Durable embedding delivery

Flyway V20 adds `embedding_tasks`. Database triggers enqueue work in the same transaction as job/resume creation or a raw-text change. Source deletion removes its queue entry, and vector foreign keys handle derived-row deletion. This replaces the earlier in-memory after-commit callback and fire-and-forget embedding methods.

A scheduled worker polls every five seconds by default (`app.search.embedding-poll-ms`), handling up to ten tasks sequentially per poll. Claims use `FOR UPDATE SKIP LOCKED`, a two-minute lease and a fresh execution token. Generation runs outside the replacement transaction. The worker then locks the source and verifies ownership of the current queue token/lease before atomically replacing all vectors and marking the task complete. Source edits, deletion, retries or another worker's claim invalidate an older result. Inference or database-write failures cannot leave a half-replaced vector set.

Failed attempts back off for 20, 40, 80 and 160 seconds before the next attempt, with at most five attempts. Expired worker leases can be reclaimed after restart. Exhausted tasks remain failed until explicitly retried. This is durable application-specific delivery; it is not a general workflow framework or a guarantee that inference executes exactly once.

Owner-scoped endpoints:

| Endpoint | Behavior |
|---|---|
| `GET /api/v1/search/indexing` | Latest 100 owned job/resume indexing tasks, status, attempts, sanitized error and timestamps |
| `POST /api/v1/search/indexing/{JOB\|RESUME}/{id}/retry` | Requeue a failed or expired task; reject foreign/missing sources |

The Jobs page refreshes these statuses every ten seconds while open and provides a retry button for failures. Tasks outside the latest 100 are shown as status unavailable rather than inferred ready. Resume retry is available through the API; a dedicated resume-indexing UI remains future work.

## Concurrent save protection

`JobWriter` is the shared database insertion boundary. V20 computes and uniquely enforces an owner-scoped identity:

- With a nonblank source URL other than `#`: SHA-256 of the trimmed URL. Query parameters, case and fragments are deliberately not rewritten; different URLs remain distinct.
- Without a usable source URL: SHA-256 of the exact title, company, location and source text. This is exact content identity, not semantic duplicate detection.

`INSERT ... ON CONFLICT` returns the canonical saved ID without overwriting existing content or parsed metadata. A known URL is checked before normal AI analysis; simultaneous requests that both miss that check can still invoke the provider, but save one job. This increment does not promise request-level credit idempotency for standalone analysis.

Manual tracker creation and research selection serialize per owner, reuse an existing application for the selected job, and preserve its status, notes and history. A database unique claim provides a second guard. Manual tracker records without a job ID are not deduplicated. A distinct reapplication to the same saved job is not represented as a second linked record by these create paths.

V20 preserves historical duplicate job/application rows and their IDs/references. The oldest row claims the identity for future saves; retained duplicates remain visible and are not silently merged. Editing a historical duplicate without changing its source identity does not make it claim an already-owned key. If the canonical row is deleted, a surviving duplicate inherits the claim. Redundant rows in the derived `job_embeddings` table are collapsed to one per job before its unique index is added; source jobs and tracker history are not deleted by the migration.

## Research provenance and identity

Research ranking now supplies stable candidate identifiers to the model and accepts only identifiers from those supplied candidates. Titles, employers, descriptions, URLs and source labels come from actual input listings, not generated replacements. Model-generated scores/reasons remain estimates requiring evaluation. Unknown candidate IDs fail before importing any selected jobs.

Responses carry saved job IDs. Selecting an internal result validates that exact owned ID; it no longer embeds a title/company string and guesses the target job. Old clients sending an internal result without an ID are asked to rerun research. Missing URLs are allowed because identity can come from the saved ID or exact source content. SerpAPI availability/failure reporting and scraped-source quality are unchanged.

## Verification

Java 21 `mvn -o -f backend/pom.xml clean verify`: **152 tests, zero failures/errors/skips**, executable JAR packaged. This includes **66 PostgreSQL integration tests**: the previous 44 plus 21 search-integrity tests and one populated-database upgrade test. One obsolete mock-only async scheduling test was replaced by real transaction/outbox coverage.

Covered behaviors include:

- committed queue visibility and rollback; actual pgvector ranking through the JPA repository; owner isolation and exclusion of unfinished indexes;
- worker reconstruction, expired leases, stale results, source edits/deletion during inference, failure backoff and manual retry;
- atomic vector replacement under simulated inference and database failure, dimension/value validation and chunk boundaries;
- eight concurrent saves of one URL, owner separation, URL-less content identity, direct SQL duplicate rejection, and the normal analysis/parsed-skills path;
- concurrent manual/research tracker saves and preservation of existing notes/status;
- source-backed ranking IDs and rejection of fabricated candidates;
- V19 → V20 upgrade seeded with duplicate jobs and application histories, preservation of references, and canonical-claim inheritance.

Frontend production build passed. ESLint: **0 errors, 70 warnings**. Vite still reports the approximately 836 kB main bundle.

Evidence: [backend verification](audit/search-backend.log), [frontend build](audit/search-build.log), [frontend lint](audit/search-lint.log).

The database and pgvector operations were real; embeddings, chat and web search were mocked. This proves the tested storage/concurrency behavior, not real-model semantic relevance, ONNX inference performance, live source quality or production throughput. No browser walkthrough, paid provider request, live database migration, application restart or remote CI run was performed.

## Deployment and operations

Back up the intended database and stop older backend workers before deploying the matching backend/frontend. V20 adds constraints/triggers and queues a rebuild for all existing jobs/resumes. Old indexing code must not run alongside the new worker. Search results may initially be sparse while the backlog is processed; inspect queue state rather than treating this as lost source data. This task has not rebuilt or restarted the running local application.

Useful read-only operational queries:

```sql
SELECT kind, status, count(*) FROM embedding_tasks GROUP BY kind,status;
SELECT kind, entity_id, attempts, last_error, updated_at
FROM embedding_tasks WHERE status='FAILED' AND attempts>=5 ORDER BY updated_at;
SELECT user_id, count(*) FROM jobs WHERE dedup_key IS NULL GROUP BY user_id;
SELECT user_id, count(*) FROM applications
WHERE job_id IS NOT NULL AND dedup_job_id IS NULL GROUP BY user_id;
```

Keep the local model and embedding dimensions consistent across instances. Changing models requires stopping old workers and deliberately requeuing every source; mixing vectors from different models is unsupported. Queue throughput, model runtime timeouts, resource limits and a broader indexing admin UI still need production validation. Outbox polling currently shares the application's scheduling infrastructure.

All changes remain uncommitted. Recovery archive: `/private/tmp/jobpilot-after-search-integrity.tgz`, containing the current modified/untracked project files and excluding real environment files and ignored build/dependency directories.

Remaining roadmap: provider price/invoice accounting, full output schemas and evaluation datasets, refresh-token rotation/revocation and other security hardening, deployed end-to-end journey verification, followed by the narrow pilot and subscription lifecycle. Existing SSRF/URL-fetch controls, dependency issues, retention policies and backup/restore gates remain open.
