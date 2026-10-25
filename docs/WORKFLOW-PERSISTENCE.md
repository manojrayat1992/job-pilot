# Saved workflows and resumable drafts — 24 October 2026

The Agent Command Center now saves workflow history and each completed step in PostgreSQL. Users can reopen generated outputs after leaving the page and resume unfinished work without regenerating completed steps. Both synchronous and streamed requests use the same execution service.

This is the persistence/recovery increment of [STABILIZATION.md](STABILIZATION.md). It covers `AUTO_PILOT`, `INTERVIEW_BLITZ`, `STEALTH_NETWORKING`, and `CUSTOM` in the Agent Command Center. Other standalone AI tools retain their existing storage behavior.

## User-visible behavior

- **Saved workflows** lists the current user's runs in pages of 20. Opening a run shows its goal, status, steps, attempt counts, errors and completed outputs.
- Resume suggestions, interview questions, thank-you drafts, outreach/follow-up drafts, company background, compensation guidance, fit questions and final reports remain available after a later step fails.
- **Resume unfinished steps** reuses saved completed outputs. Retry uses the original request, source snapshots, step definitions and the prompt saved before that step's first attempt. Editing the current form does not change an existing run.
- A disconnected stream does not cancel the background run. Reopening a running run polls its saved state. An application restart stops execution, but completed database checkpoints survive. Once the old execution lease expires, the run is shown as `INTERRUPTED` and can be resumed by the user.
- Generated messages remain drafts. No messages or employer applications are sent. Resume suggestions are not approved resume versions; the existing resume review/versioning flow remains the path for producing those.
- Historical runs from before this migration were held only in memory and cannot be recovered by the new history view.

## Storage and execution

Flyway V18 adds `agent_workflow_runs` and `agent_workflow_steps`. Existing migrations are unchanged. A run stores its owner, request idempotency key, definition version, request/context snapshot, timestamps, status, error and execution lease. A step stores its stable key, order, agent, action, prompt snapshot, status, attempts, output, model and timestamps.

The database enforces a unique `(user_id, request_id)` for starts and a unique `(run_id, step_key)` for checkpoints. A repeated start with the same request ID and inputs observes the existing run; different inputs using that ID return a conflict. The frontend retains a request ID while a start response is uncertain. A deliberate new run receives a new ID.

A run is claimed under a database row lock. Its execution token and 90-second lease prevent concurrent attempts from committing the same run. A live runner renews the lease every 20 seconds. Each checkpoint verifies the current token; a stale worker cannot write after a successor has claimed the run. Already completed steps are skipped, including when retrying a completed run.

Provider work happens outside database transactions. Job imports and the search step's completion commit together; rollback leaves neither new jobs nor a successful checkpoint. Imports made by these workflows are serialized per owner and reuse existing source URLs where available. V20 extends deduplication to the shared job writer and adds database identity guards for the other job/tracker creation paths; see [SEARCH-INTEGRITY.md](SEARCH-INTEGRITY.md).

V19 adds atomic reservations at the shared provider boundary. A successful workflow response and its charge commit in the credit ledger before the workflow checkpoint. A retry reuses that saved response without another provider call or charge, even if the checkpoint failed. Unresolved reservations block replay until operator reconciliation. See [CREDIT-ACCOUNTING.md](CREDIT-ACCOUNTING.md) for the credit policy and recovery procedure.

Streaming continues to use a bounded pool of four workers with 32 queued tasks. Runs are persisted before dispatch. If the queue is full, the saved pending run can be resumed from history. Legacy synchronous requests still execute on their request thread.

## API

All endpoints require authentication and enforce run ownership. Unknown and foreign run IDs return not found.

| Method and path | Behavior |
|---|---|
| `POST /api/v1/agents/orchestrate` | Start/observe a synchronous run; optional UUID `requestId` deduplicates starts |
| `POST /api/v1/agents/orchestrate/stream` | Start/observe the same persisted execution through SSE |
| `GET /api/v1/agents/workflows?page=0` | Current user's history; 20 summaries per page |
| `GET /api/v1/agents/workflows/{id}` | Saved status, steps and outputs; does not execute work |
| `POST /api/v1/agents/workflows/{id}/retry` | Resume unfinished steps synchronously |
| `POST /api/v1/agents/workflows/{id}/retry/stream` | Resume unfinished steps through SSE |

Responses distinguish `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` and the derived `INTERRUPTED` state. `retryable` is true for pending, failed and interrupted runs. A concurrent synchronous retry returns 409; the streaming adapter reports the conflict through its error event and directs the user to saved history. SSE is a progress view, not the source of truth.

## Recovery limits

- Recovery is user-triggered. There is no automatic database queue sweeper, automatic provider retry loop, cancellation endpoint or general workflow engine in this increment.
- A provider can finish before the app saves its response in the ledger. A process failure in that window leaves an unresolved reservation; retry blocks until support reconciles it. Responses already settled in the ledger are reusable even without a completed workflow checkpoint. Remote execution is not guaranteed exactly once; after an operator refunds an unresolved call, another attempt can incur provider usage.
- Leases prevent stale database writes; they cannot cancel a provider request already in flight. Provider timeouts, cancellation and recovery policies still need the broader orchestration work.
- Workflow snapshots and outputs can contain personal resume data. They are scoped to the owner and foreign keys remove them when that user's database row is deleted. A complete retention/deletion product flow and backup retention policy remain open.
- The existing SerpAPI adapter still treats missing credentials and some search failures as empty results. Search quality/failure classification remain open. V20 subsequently fixes embedding consistency and queues every workflow import for indexing; see SEARCH-INTEGRITY.md.
- Definition version 1 is persisted. Future execution semantics must explicitly support old definitions or migrate them; unknown versions are rejected rather than resumed with different behavior.

## Verification

Java 21 `mvn -o verify` passed: **116 tests, zero failures/errors/skips**, executable JAR packaged. This includes 16 new PostgreSQL workflow integration cases plus five existing database tests. The streaming facade tests were updated for the shared runner; the previous workflow failure scenarios are now also covered through real database persistence.

The workflow tests cover:

- outputs loaded through a fresh store/runner instance;
- failed middle/report steps retried without regenerating completed outputs;
- original outreach retained through follow-up failure;
- cross-user read/retry/list rejection and foreign source-reference rejection;
- start-key collisions, concurrent start deduplication and concurrent retry exclusion;
- expired leases, stale-worker rejection and resumed work through a reconstructed runner;
- disconnected event listeners with continued checkpointing;
- job-import rollback and no repeat imports on retry;
- bounded history and cascading removal with an owner row.

Provider and search responses are mocked. Restart recovery is exercised through fresh service instances and database lease expiry, not by killing a deployed application process. No paid-provider calls, browser-interaction test, container rebuild or live application database mutation was performed.

Frontend production build passed. Lint passed with zero errors and the existing 73 warnings. The bundle is approximately 833 kB and still produces Vite's chunk-size warning. `git diff --check` passed.

Logs: [backend](audit/workflow-backend.log), [frontend build](audit/workflow-frontend-build.log), [frontend lint](audit/workflow-frontend-lint.log).

## Applying the change

The running application was not restarted. After configuring the separate chat/audio credentials described in [AI-PROVIDER-STABILIZATION.md](AI-PROVIDER-STABILIZATION.md), rebuild the backend and frontend. Flyway applies V18 at backend startup:

```sh
docker compose --env-file .env -f docker/docker-compose.yml up -d --build backend frontend
```

The disposable test database is separate from the project database. CI already supplies `JOBPILOT_REVIEW_DB_URL`, so the new database tests participate in the existing backend job. The remote CI run and rebuilt-container smoke test remain unverified.

## Temporal evaluation: one workflow first

This increment provides durable product history and checkpoints. Temporal is still the proposed direction for automatic recovery, scheduled retries, cancellation and human-review waits. The evaluation below is a design/compatibility review, not a completed Temporal runtime prototype.

The official Java SDK supports workflow/activity execution with explicit timeouts and retry options; activities must still be designed to tolerate replay. See [Temporal Java activity execution](https://docs.temporal.io/develop/java/activities/execution) and [activity idempotency](https://docs.temporal.io/activity-definition#idempotency). Those requirements are why application-side draft storage and side-effect boundaries remain necessary with a workflow framework.

The direct `temporal-spring-ai` integration is currently documented as public preview and requires Spring AI 1.1.0 or newer and Temporal Java SDK 1.35.0 or newer. This repository uses Spring AI 1.0.0-M4. Adopting that integration therefore requires a separate compatibility upgrade; alternatively, regular Java activities could wrap the current chat service. This conclusion is based on the checked-in POM and [Temporal's Spring AI prerequisites](https://docs.temporal.io/develop/java/integrations/spring-ai#prerequisites).

Use **Interview Blitz** as the first runtime prototype:

1. Start one Temporal workflow with the saved run ID and explicit owner identity; do not depend on a request-thread security context inside workers.
2. Implement activities for company background, questions, thank-you draft and report. Persist output under the stable run/step identity. Pass IDs through history where possible rather than copying whole resumes into workflow history.
3. Make timeouts/retry limits explicit, distinguish non-retryable authorization/validation errors, and gate retries with the credit reservation ledger.
4. Replace the local execution lease/loop for this workflow with Temporal ownership; do not run both schedulers over the same run. Retain PostgreSQL history as the product-facing projection.
5. Prove worker kill/restart, duplicate starts, cancellation, provider-response/save ambiguity, and a human-review pause with local test infrastructure before enabling the workflow in production.

Broader migration should follow that runtime evidence. Atomic reservations are now implemented; provider-quality evaluation and provider-cost reconciliation remain open alongside this evaluation.

### V20 indexing follow-up

Search activities now query the canonical `job_embeddings` store and include only completed indexes. Workflow imports use the same atomic JobWriter as manual/research imports. Their embedding tasks commit with the job-import checkpoint and survive process restart. Existing workflow recovery guarantees remain in place.
