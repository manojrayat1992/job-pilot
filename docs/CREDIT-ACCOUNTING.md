# AI credit accounting — 24 October 2026

This increment implements atomic **app credits**, not payment processing or a provider invoice. It follows the data/cost integrity priority in PROJECT-ASSESSMENT.md and STABILIZATION.md.

## Charging policy

- One credit is reserved before each chat generation, transcription or speech request. Successful nonempty text/valid audio settles that reservation. A workflow or voice turn can require several credits.
- PRO, ADMIN and ENTERPRISE remain unlimited under the existing entitlement policy; their ledger entries reserve/charge zero credits. This does not enable checkout or establish paid entitlement correctness.
- Provider exceptions and empty/unusable responses refund the app reservation. Network failures may still have incurred provider cost; an app refund is not evidence of a provider refund.
- Subsequent JSON/feature validation or application persistence failures do not refund a completed model call. Full output-schema validation and end-to-end operation pricing remain separate work. This boundary is disclosed in the usage page.
- Local embeddings and job search are outside this app-credit policy. SerpAPI can still have separate costs. No currency price or cost is invented from token totals.

## Database and transaction guarantees

Flyway V19 creates `ai_credit_accounts`, `ai_credit_ledger` and `ai_credit_grants`. It copies existing `users.credits` balances (negative/null values become zero) and initializes accounts for new users using an insert trigger. `users.credits` becomes a legacy/initial field; old code must not be used to spend or grant credits after migration. Login, refresh, registration and profile responses now read the account balance. Registration flushes the new user so its account trigger is visible before constructing the response.

Reservations, settlements, refunds and grants use short `REQUIRES_NEW` transactions. Each mutation locks the owner's account first. A conditional debit and nonnegative database constraint prevent overspending. Provider requests execute after reservation commit, without holding a wallet lock. Caller transaction rollback cannot erase successful provider usage. A stale JPA profile save cannot overwrite the separate spendable balance.

Each ledger attempt stores owner, operation key, input fingerprint, attempt number, feature, requested/selected model, provider-reported model when available, credits, status, timestamps and reported token usage. Successful workflow text is cached for recovery. Raw prompts, audio and standalone response text are not copied into this ledger. All these rows cascade on account deletion; a complete product deletion and backup-retention policy remains open.

Grants require a positive amount and a stable reference. Replaying the same reference/amount is a no-op; changing the amount conflicts. No public grant/payment endpoint is introduced.

## Retry and failure behavior

| Situation | Credit and retry behavior |
|---|---|
| Two requests compete for the final credit | One reservation succeeds; the other receives 402 before provider dispatch |
| Same operation is already reserved | 409; no second debit or provider dispatch |
| Provider fails or returns empty output | Reservation released once; a workflow retry creates a separate audited attempt |
| Provider succeeds and settlement commits | One charge; workflow response cached with the charge |
| Workflow checkpoint fails after settlement | Retry reuses the ledger response, including with zero remaining credits or inactive routes |
| Caller JPA transaction rolls back | Independently recorded successful call remains charged |
| Process stops, or settlement storage fails after provider success | Reservation stays held; automatic replay/refund is blocked |
| Old worker returns after an operator refund | Its released attempt cannot settle or overwrite a later attempt |

Workflow keys are `workflow:<run UUID>:<step key>` scoped by owner. Fingerprints bind the frozen prompt and generation settings. Completed checkpoints are still skipped by the workflow engine. Standalone HTTP requests use new operation IDs: this increment does **not** deduplicate an arbitrary repeated standalone client request.

Spring AI's `spring.ai.retry.max-attempts` is explicitly 1. The bundled M4 configuration metadata defaults to 10; hidden provider retries would otherwise bypass the visible attempt history. This change introduces no provider auto-retry scheduler.

## Unresolved calls and reconciliation

A `RESERVED` attempt older than ten minutes is displayed as `UNKNOWN` / **Needs review**. This is a view of an unresolved outcome, not proof that the provider stopped. There is no automatic timeout refund. Users see held credits and reservation IDs in the usage page; workflow failures explain when support review is required.

Operator procedure:

1. Identify the reservation ID, its owner and operation. The usage endpoint exposes only the current user's latest 20 attempts, without cached contents. Operators can query older pending rows in PostgreSQL using their normal controlled database access:

   ```sql
   SELECT id, user_id, operation_key, requested_model, credits, created_at
   FROM ai_credit_ledger
   WHERE status = 'RESERVED' AND created_at < now() - interval '10 minutes'
   ORDER BY created_at;
   ```

2. Stop/confirm termination of the old worker and check available provider records. Leases and ledger fencing do not cancel a live remote request. If retaining the unknown outcome, leave the reservation held.
3. If approving an app-credit refund, an authenticated ADMIN calls `POST /api/v1/ai/admin/credits/{reservationId}/release` with JSON `{"reason":"Worker stopped; provider checked; app-credit refund approved"}`. Authentication, ADMIN authority, current database role/enabled state, minimum age and unresolved status are enforced. A reason of 1–400 characters is mandatory. The transaction records the administrator and reason and restores the held credit once. Settled calls cannot be refunded through this endpoint.
4. The owner can retry the workflow. A new provider attempt may incur additional provider cost; remote exactly-once execution cannot be guaranteed in this failure window.

This is a support API/runbook, not a complete administrator reconciliation UI. Provider request-ID correlation and automated provider reconciliation are follow-up work.

## Usage display

`GET /api/v1/ai/usage` now reads the ledger rather than legacy feature logs. It reports available/reserved credits, settled app credits, recorded attempts, reported token totals, completed calls missing token reports, per-model totals and recent activity. Unknown usage stays null on individual entries; default Spring `EmptyUsage` is not presented as an actual zero-token report. Audio token usage is unknown with the current response formats. A model is provider-reported when present, otherwise the selected request model is shown.

Historical `ai_logs` remain available to existing feature/admin analytics; they are not backfilled as paid usage. The dashboard explicitly states its ledger-start boundary and that token totals are partial, not a provider bill. Feature-level legacy logs and administrator analytics are not yet unified with this ledger.

## Verification

- Java 21, `mvn -o -f backend/pom.xml clean verify`: **131 tests, 0 failures/errors/skips**, executable JAR packaged. A clean rebuild was needed after inconsistent generated class files were detected.
- **44 PostgreSQL integration tests**, including 21 credit/gateway cases and 2 JPA cases alongside the existing 21 resume/workflow cases. All 19 Flyway migrations ran on a disposable PostgreSQL 16/pgvector database.
- Credit tests cover eight simultaneous requests for the last credit; no provider dispatch at zero; refunds/retries; unlimited and disabled roles; owner isolation; idempotent grants; real Spring AI gateway metadata; unknown usage; audio charging/refunds; cached workflow recovery after a failed checkpoint; stale-attempt fencing; interrupted-call admin reconciliation; JPA registration/profile consistency and rollback.
- MVC tests enforce anonymous/non-admin denial for reconciliation and ADMIN access.
- Frontend production build passed. Lint: **0 errors, 70 existing warnings**; typing the usage contract removed three prior warnings. The roughly 835 kB main bundle still triggers Vite's size warning.

Evidence: [backend](audit/credit-backend.log), [frontend build](audit/credit-frontend-build.log), [lint](audit/credit-frontend-lint.log).

Providers were mocked; no paid provider request, real account mutation, live migration, browser walkthrough, app restart or remote CI run was performed. Tests demonstrate the covered database failure paths, not production load capacity. Independent ledger transactions also need spare pool capacity when a caller already has an open JPA transaction; broader transaction duration/load controls remain operational work.

## Applying this change

Back up the intended database and deploy the matching backend/frontend together. Stop old backend workers before V19: they still mutate `users.credits` and cannot safely share balances with the new code. Do not run old and new accounting versions concurrently. A new backend startup runs Flyway V19. Reverting the backend alone would use the stale legacy balance; rollback needs deliberate balance reconciliation or restore from the pre-migration backup.

The running local Docker application has not been updated by this task. Source changes remain uncommitted and the full recovery archive is `/private/tmp/jobpilot-after-credit-accounting.tgz` (excludes real environment secrets and ignored build/dependency files).

Remaining data/cost work: embedding consistency, global job-save idempotency, strict output schemas, a versioned provider price catalog and invoice reconciliation, token/budget/rate limits, per-feature idempotency beyond workflows, full subscription lifecycle, and deployment/load validation.
