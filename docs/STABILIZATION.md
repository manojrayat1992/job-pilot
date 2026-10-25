# First stabilization pass

> Follow-ups implemented: [resume review and versioning](RESUME-VERSIONING.md), then [AI provider and failure correctness](AI-PROVIDER-STABILIZATION.md), and [saved workflows/resumable drafts](WORKFLOW-PERSISTENCE.md). Current verification is 116 passing backend tests, including 21 database integration tests. The original verification table below is retained as historical evidence.

Date: 23 October 2026. This implements the first increment of the [original assessment](PROJECT-ASSESSMENT.md): make product behavior truthful, close the reviewed access-control gaps, and restore repeatable verification. It is not completion of the entire roadmap or approval for production launch.

## Changes delivered

| Area | Result |
|---|---|
| Job research | New authenticated `/api/v1/agents/save-jobs` endpoint saves selections as `SAVED`, without an applied date. It checks resume ownership and skips existing tracker entries on sequential requests. The legacy auto-apply endpoint returns 503. No employer submission is claimed. |
| Simulated integrations | Email sync, hiring-contact discovery/unlock, paid checkout, and extension autofill are unavailable. Their UI explains the limitation. The old fictional email/contact mutation paths are removed or blocked. |
| Agent artifacts | The synchronous workflow returns resume suggestions for review instead of persisting a JSON fragment as a tailored resume. Networking generates drafts without creating placeholder contacts. Search result mapping uses the provider adapter's actual `company` and `url` keys. |
| Authentication | Access and refresh tokens require their intended purpose. Protected requests load the current enabled user and role from the database. Refresh rejects disabled/deleted users. Old access tokens without a purpose claim will require a new login or refresh. |
| Authorization | Copilot requires authentication and a user-owned resume. Interview resume access, application resume/job references, and AI feedback use ownership checks. Model administration requires the ADMIN role. |
| Browser boundary | CORS uses configured exact origins. Frontend API/auth/stream requests share one configurable API base. Streaming uses the persisted access token rather than an unrelated local-storage key. |
| Billing containment | No checkout can be initiated through the billing controller. A URL query parameter cannot grant PRO in the frontend. The webhook returns 503 rather than acknowledging unprocessed events. Real subscription processing is still unimplemented. |
| Configuration | JWT secret must be supplied; the embedded SerpAPI credential fallback and mock Stripe fallback are removed. Reset tokens are no longer logged. |
| Streaming | A bounded executor replaces the unbounded pool and propagates Spring Security context. Application shutdown closes the executor. This is not durable execution. |
| Frontend | Strict TypeScript build errors are fixed; unused declarations are cleaned up. ESLint configuration is supplied, with explicit `any` left as a warning for a later contract migration. Unsupported usage statistics, compliance claims, testimonials, and paid-plan promises are removed from the landing page. |
| Build | Docker uses a Maven builder instead of an absent wrapper. CI jobs cover backend verification and frontend install/lint/build. README reflects actual commands, disabled capabilities, and configuration limitations. |

## Verification and its limits

| Check | Observed result |
|---|---|
| Java 21 `mvn -o verify` | Passed: 48 tests, zero failures/errors/skips; executable JAR packaged. |
| `npm run build` | Passed: TypeScript and Vite production bundle. |
| `npm run lint` | Passed with zero errors and 73 warnings. |
| Security regression coverage | Anonymous Copilot blocked; cross-user resume access rejected; ADMIN role enforced; stale/disabled user access rejected; refresh/access token purpose enforced; disallowed CORS origin rejected. |
| Reference regression coverage | Application create/update reject foreign references before mutation; interviews reject foreign resumes before creating sessions; selected jobs save without claiming submission. |
| Disabled feature coverage | Service/controller tests assert unfinished integrations fail explicitly. |

Logs: [backend verification](audit/stabilization-backend.log), [frontend build](audit/stabilization-build.log), and [frontend lint](audit/stabilization-lint.log).

Backend verification uses mocks and an MVC security slice. It does not demonstrate PostgreSQL/Flyway behavior, transaction locking, a live AI provider, Stripe, email delivery, employer submissions, or browser interaction. No full UI walkthrough, Docker build/run, deployed smoke test, or remote CI run was performed. The frontend still has a roughly 824 kB minified main bundle and a Vite chunk-size warning. Dependency upgrades were not part of this pass; the original dependency-audit findings remain open.

## Operational considerations

- Supply a unique `JWT_SECRET` before starting the backend. Existing untyped access tokens are rejected. Rotating a previously used signing secret also invalidates existing refresh tokens.
- Removing a credential from source does not revoke it. Any previously exposed real provider credentials need rotation in the provider account and appropriate history remediation.
- Existing fictional contacts, altered application statuses, and prior PRO entitlements have not been deleted or corrected. Audit existing records before migration; the code change prevents the reviewed future mutations, not historical contamination.
- If a live Stripe endpoint is registered, it will receive 503 until billing is correctly implemented. Reconcile actual payments/events separately before enabling paid upgrades.
- Frontend `VITE_API_URL` defaults to the current origin. A production reverse proxy must route `/api`, or a separate API origin must be supplied at build time with matching CORS configuration.
- The two checked-in TypeScript build-info files remain baseline artifacts. Newly generated build output is ignored. npm is the verified package-manager path; the alternate pnpm lockfile has not been synchronized.

## Remaining work, in order

1. **Safe application documents — first increment completed.** Source-preserving edits, persisted review drafts, explicit approval, separate exports, ownership checks and transactional approval are implemented. See [RESUME-VERSIONING.md](RESUME-VERSIONING.md) for verification and limits. Live provider evaluation and visual PDF/UI checks remain open. Durable embedding delivery is covered by the V20 search-integrity increment.
2. **Provider and failure correctness — configuration/failure increment completed.** Chat/audio credentials are separate, all chat features apply the selected model, audio and workflow failures are explicit, and provider/SSE regression tests are in place. See [AI-PROVIDER-STABILIZATION.md](AI-PROVIDER-STABILIZATION.md). Agent Command Center workflow drafts now persist with step history and user-triggered recovery; see [WORKFLOW-PERSISTENCE.md](WORKFLOW-PERSISTENCE.md). Remaining: audit other standalone AI storage paths, full structured-output schemas, a measured evaluation dataset, and evidence/uncertainty handling for culture and salary outputs.
3. **Data and cost integrity — atomic app credits completed.** Shared chat/audio boundaries reserve credits atomically, settle successful responses, refund provider errors, cache workflow results for safe retry, and expose a usage ledger. See [CREDIT-ACCOUNTING.md](CREDIT-ACCOUNTING.md). Embedding consistency and concurrent saved-job protection are now implemented in [SEARCH-INTEGRITY.md](SEARCH-INTEGRITY.md). Remaining: provider price catalogs/invoice reconciliation, full output schemas and cost controls. App credits currently use a fixed per-call policy, not monetary token billing. Database identity claims and shared save paths now protect concurrent job/tracker creation; historical duplicates are preserved.
4. **Security and operations.** Rotate exposed credentials; implement refresh rotation/revocation, safe file handling and egress controls, rate limits, dependency remediation, backup/restore checks, retention/deletion controls, and tested deployment configuration. Complete the production security review from the original assessment.
5. **Complete a narrow end-to-end journey.** Upload resume → select real job → generate reviewed changes → export packet → manually submit → track outcomes. Verify it with a real database, controlled provider accounts, and browser tests. Hide or remove remaining unverified capabilities from launch scope.
6. **Durable workflows — persistence/recovery increment completed.** Agent Command Center runs now persist source snapshots, step outputs, errors and history; shared synchronous/streamed execution supports owner-scoped resume, completed-step reuse, concurrent execution fencing, and atomic job-import checkpoints. Recovery is user-triggered after lease expiry, not an automatic scheduler. Retain Spring AI for model/tool integration; automatic retries/timeouts, human-review waits, cancellation and full observability remain open. The proposed Java-first direction is Temporal, evaluated with one concrete workflow before broad migration. A workflow framework alone will not fix authorization, prompts, data, billing, or fabricated results. The current fixed Java sequences are custom orchestration; that fact alone does not make them incorrect.
7. **Monetization and pilot.** Implement the full subscription lifecycle and signed, idempotent webhooks before reopening checkout. Validate a narrow customer segment and willingness to pay with a small pilot; measure reviewed packets, time saved, factual edit errors, and application outcomes. Pricing and go-to-market hypotheses are in the original assessment.

Suggested acceptance gate for the next increment: a user's original resume remains unchanged after tailoring; the user can review and export a separate version; all referenced documents belong to that user; provider failures preserve recoverable state and truthful status; meaningful integration tests prove these behaviors.

## Recovery verification

On 23 October 2026, the earlier changes were recovered from this chat's recorded patches and edit commands into the fresh clone at `/Users/manojkumar/Documents/job-pilot` (base commit `ca4b411`). The work remains uncommitted for review. The original pre-stabilization backup was preserved.

Fresh verification passed: 48 backend tests and JAR packaging, frontend production build, and lint with zero errors and 73 warnings. The frontend bundle filenames and sizes match the earlier successful build. Logs: [backend](audit/recovery-backend.log), [build](audit/recovery-build.log), [lint](audit/recovery-lint.log). These checks retain the integration limits described above.

## Container startup verification — 24 October 2026

The pasted 23 October startup error originated in the Alpine runtime: the ARM64 DJL tokenizer could not load `libstdc++.so.6`. The dependency errors in ResumeController, ResumeService, AiService and EmbeddingService were cascading failures from that native-library initialization. The JTA informational message was not the cause.

On resuming work, the Dockerfile had already been changed to `eclipse-temurin:21-jre`, with Linux user/group creation and a curl health check. The existing `jobpilot-backend` container was healthy. Read-only verification confirmed:

- The running image uses Ubuntu and runs as the non-root `appuser`.
- `ldd` on the extracted ARM64 tokenizer resolves `libstdc++.so.6`, `libgcc_s.so.1`, glibc and the other dependencies, with no missing-library result.
- Startup logs show the ONNX model's input/output names and successful Spring Boot startup.
- The in-container `/actuator/health` endpoint returns `{"status":"UP"}`.

The existing runtime fix was preserved, and a Dockerfile comment records why an Alpine substitution is unsuitable for this dependency set. No rebuild, restart, database mutation, live model-inference request or paid-provider call was performed in this verification. This verifies the reported startup failure is resolved in the current container; it does not establish end-to-end AI feature correctness or reproducibility on other architectures.

DJL also tracks Alpine tokenizer compatibility in its [upstream issue](https://github.com/deepjavalibrary/djl/issues/2946).


## Saved workflow verification — 24 October 2026

[WORKFLOW-PERSISTENCE.md](WORKFLOW-PERSISTENCE.md) records the implementation, endpoints, recovery guarantees and limits, deployment command and Temporal design evaluation. Verification passed with 116 backend tests (21 database integration tests), frontend production build, and lint with zero errors/73 existing warnings. Providers were mocked and PostgreSQL was disposable. No live application restart or production database migration was performed.

## Credit accounting verification — 24 October 2026

[CREDIT-ACCOUNTING.md](CREDIT-ACCOUNTING.md) documents V19, balance migration, reservations, refunds, retry caching and operator reconciliation. Verification passed with 131 backend tests, including 44 real PostgreSQL integration tests; frontend production build passed and lint reported zero errors/70 warnings. The previous 9 mock credit tests were replaced with 21 database/gateway tests, plus 2 JPA balance tests and an additional admin authorization test. No live provider request, live database migration or application rebuild/restart was performed. Provider monetary cost remains unpriced, and successful model calls are charged even when subsequent feature validation or saving fails.

## Search and save integrity — 25 October 2026

[SEARCH-INTEGRITY.md](SEARCH-INTEGRITY.md) documents V20, the shared embedding store, transactional queue, bounded retry/recovery, owner-scoped indexing status, canonical job/application identities and source-backed research IDs. Verification passed with 152 backend tests (66 PostgreSQL integration tests), frontend build, and lint with zero errors/70 warnings. Upgrade tests preserved historical duplicate sources and tracker references. The running application has not been deployed or migrated. Real-model quality, runtime throughput and browser interaction remain unverified.
