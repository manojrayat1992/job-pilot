# Resume review and versioning increment

Implemented 23 October 2026, following the first outstanding priority in [STABILIZATION.md](STABILIZATION.md) and the document-integrity recommendations in [PROJECT-ASSESSMENT.md](PROJECT-ASSESSMENT.md).

## User flow

1. Open Resume Hub and choose the sparkle action on a source resume.
2. Choose a saved job or paste its description. Generate a draft.
3. Compare the original and proposed text. New or rewritten lines are highlighted. The job description is saved with the draft and remains visible during review.
4. Confirm factual accuracy, then approve. Approval creates a separate resume in Resume Hub; it does not change or activate the source.
5. Download the approved PDF, or close and reopen the saved draft later. Approved resumes can be selected through the existing application tracker.

Manual edits now save another resume version. The original content remains unchanged. The optimizer's save action also routes through the draft review gate, using the source/job context captured when the optimization was requested.

## Implementation

- Migration `V16__resume_review_drafts.sql` adds persisted drafts, original/proposed JSON snapshots, the target job description, approval timestamp and approved-resume reference. Resumes record parent and target-job references.
- Drafts are separate from the regular resume library. No draft is activated, embedded or exported before approval. The review dialog lists the latest 50 drafts per source and can load a particular older draft directly.
- Draft generation validates source and job ownership before calling AI. Provider execution occurs outside a long database transaction. Provider/schema/fact-check failure does not persist a draft or modify the source.
- Approval uses a database row lock and transaction. Concurrent/repeated approvals return one approved resume. If the approved resume is later deleted, retries do not recreate it.
- The approved resume contains a deep copy of the reviewed JSON and canonical text derived from that JSON. It inherits no source file path, ATS score or vector. Embedding work is scheduled after the document transaction commits.
- Content changes create a new resume row. JPA marks saved raw text and parsed JSON as non-updatable. Metadata and active status remain editable. The PDF associated with a draft reads its approved resume, so later content edits cannot change it.
- Version numbers indicate depth from a parent. Sibling variants can share a number; UUIDs identify individual versions. This is not a globally sequential version counter.
- The legacy `tailor-and-download` endpoint now rejects the request and directs clients to draft review. This intentional behavior change requires updating old clients.
- PDF export now includes website, experience location, education GPA and project URL when present. Missing experience dates no longer become an invented “Present”.

## Validation boundaries

The supported schema includes personal information, summary, experience, education, skills and projects, including existing date aliases. Validation checks field types, supported keys and size bounds. Older builder payloads with a duplicate summary inside `personalInfo` are normalized without changing the source; conflicting summaries are rejected. Null collection fields are normalized to empty collections.

AI tailoring can rewrite the summary, existing experience bullets and existing project descriptions. Protected fields—including identity, employers, titles, chronology, education, skills, project names/URLs/technologies and entry order—must remain unchanged. New numeric tokens are rejected.

These checks do **not** prove semantic factuality. An invented qualitative achievement or an existing number reused in a different context can still pass. The user must review the complete draft. Evidence-linked claims and evaluation against a representative dataset remain outstanding. Unsupported custom schemas receive an error rather than silently losing fields; correct them using the supported editor schema before retrying.

## Verification

Fresh local verification used a disposable PostgreSQL 16 database with pgvector, real Flyway migrations, JPA repositories and transaction management. AI, parsing, file storage and job lookup were mocked in the database slice; it does not boot the entire production application.

| Check | Result |
|---|---|
| Java 21 `mvn -o verify` with database tests enabled | 70 tests passed, zero failures/errors/skips; executable JAR packaged |
| Migrations on an empty test database | All 16 migrations applied successfully |
| Database integration suite | 5 tests: persisted review/export, concurrent approval, ownership, deleted approved-copy retry, immutable content columns |
| Document/service regression suite | 17 tests: original preservation, approval/review gate, PDF text extraction, copy-on-write edits, provider/validation failure, ownership, compatibility and after-commit embedding scheduling |
| Frontend production build | Passed |
| Frontend lint | Zero errors, 73 pre-existing warnings |

Logs: [backend](audit/resume-versioning-backend.log), [frontend build](audit/resume-versioning-build.log), [lint](audit/resume-versioning-lint.log).

No live AI-provider evaluation, browser walkthrough, PDF visual-layout review, production deployment or production-data migration was performed. PDF content was verified by extracting text from generated files. Existing bundle-size and lint warnings remain.

## Running the database tests

Use an empty disposable database only. The test applies the application's migrations and inserts/deletes test users. The example credentials below are exclusively for this isolated test container.

```sh
docker run --detach --rm --name jobpilot-resume-review-test \
  --publish 127.0.0.1:55439:5432 \
  --env POSTGRES_USER=review_test \
  --env POSTGRES_PASSWORD=local_review_test_only \
  --env POSTGRES_DB=review_test pgvector/pgvector:pg16
```

After PostgreSQL is ready, from the project root with Java 21 selected:

```sh
JOBPILOT_REVIEW_DB_URL=jdbc:postgresql://127.0.0.1:55439/review_test mvn -f backend/pom.xml verify
docker stop jobpilot-resume-review-test
```

Without `JOBPILOT_REVIEW_DB_URL`, the five database tests are skipped; the other 65 tests run normally. CI supplies its own PostgreSQL service and enables these tests. Remote CI has not yet run.

## Remaining work

- Provider configuration, actual model selection, typed failure handling and removal of remaining simulated audio behavior are the next stabilization priority.
- Generated output rejected by validation may already have consumed a credit in the existing AI service. Atomic reservation/refunds, usage accounting and cost controls remain separate work.
- Embeddings are newly generated from canonical version text after commit, but delivery is still asynchronous and best-effort. Durable retries and the wider job/vector read-write inconsistency remain unresolved.
- This increment does not retrofit lineage or correct previously overwritten resumes. Existing malformed or unsupported legacy documents need review.
- Deleting a source deletes its draft history; derived resumes remain independent and their parent reference is cleared. Full account-level retention/deletion and original-upload lifecycle handling require the broader privacy/storage review.
- Full application startup, live UI behavior, representative document formats, Unicode/font coverage, export layout, provider output quality and deployed end-to-end smoke tests remain release gates.

### V20 embedding delivery follow-up

The earlier in-memory after-commit scheduling has been replaced by transactional database queue triggers for all resume sources. Atomic chunk replacement, lease recovery and owner-scoped retry are described in [SEARCH-INTEGRITY.md](SEARCH-INTEGRITY.md). Original/version preservation is unchanged.
