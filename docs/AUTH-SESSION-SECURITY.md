# Authentication session security

Implemented locally on 25 October 2026. This increment follows the security work in [STABILIZATION.md](STABILIZATION.md); it has not been deployed to the running application.

## Behavior and guarantees

Each registration/login creates a separate persisted session. Access JWTs carry that session ID. Protected requests verify JWT signature, issuer, audience, purpose, required identifiers and expiry, then check the session and the account in PostgreSQL. Authorization still uses the account's current role.

Each refresh consumes one token and issues a new access/refresh pair in one transaction. PostgreSQL stores SHA-256 hashes of refresh tokens, never their JWT strings in the new tables. Used hashes remain available for replay detection. Sessions have an absolute expiry based on the configured refresh lifetime (currently seven days); refreshing does not extend that deadline. Access tokens retain their configured lifetime (currently 15 minutes), but cannot outlive their active server session.

Reusing an issued refresh token revokes its entire session family. Descendant refresh tokens and existing access tokens then fail on subsequent requests. The unsuccessful refresh outcome commits before returning 401, so error handling cannot undo replay revocation. Locks on the account row serialize creation, rotation, logout and account changes. Two simultaneous refreshes with the same token produce one initial winner, followed by family revocation when the duplicate is processed. There is deliberately no replay grace period. This rotation/replay behavior follows the pattern in [RFC 9700, section 4.14.2](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2).

A database trigger revokes every session when the password hash changes or the account becomes disabled. Re-enabling the account does not reactivate those sessions. Session creation rechecks the password-hash snapshot and enabled state under a lock, preventing a login verified before a concurrent password reset from issuing a usable session afterward. Password-reset token consumption now locks the user row; concurrent use succeeds only once. Password reset/email delivery is still not exposed through public routes.

## API

| Endpoint | Credential | Effect |
|---|---|---|
| `POST /api/v1/auth/register` | Registration fields | Creates account and session. |
| `POST /api/v1/auth/login` | Email/password | Creates a new session. |
| `POST /api/v1/auth/refresh` | JSON `refreshToken` | Rotates once; invalid, expired, revoked or replayed credentials return 401. |
| `POST /api/v1/auth/logout` | JSON `refreshToken` | Idempotently revokes that issued token's session. A rotated ancestor can also revoke its family. Invalid tokens are a no-op; response reveals no session existence. |
| `POST /api/v1/auth/logout-all` | Bearer access token | Revokes all current sessions belonging to the authenticated account. |

Logout accepts its refresh credential without requiring a still-valid access token. Knowing a session ID, presenting an access token in place of a refresh token, or presenting a signed but unissued refresh token cannot rotate or revoke a legitimate session. Logout-all is protected; it is not included in the anonymous auth-route allowlist. A database failure cannot authenticate a protected request.

## Browser behavior

Login, registration, refresh and current-session logout use the same Web Lock across tabs sharing the origin and localStorage. A late 401 reuses credentials already rotated by another request instead of exchanging the old token again. Requests capture a login identifier; retries reject if that login has changed. Logout-all also checks the initiating login before dispatch. A late refresh response cannot restore a cleared session or overwrite a newer login.

Storage events update other tabs. Persistence version 1 drops old browser credentials on upgrade. The legacy Login/Register components and the current AuthPage use the store's shared login methods. The header exposes current-session logout and **Sign out all devices**. A failed logout reports that server revocation was not confirmed; a successful local clear alone is not represented as logout on every device.

The UI needs HTTPS (or localhost for development). Automatic renewal requires Web Locks. Where locks are unavailable, the client requires a new login instead of sending a potentially duplicate refresh. A refresh network failure also clears local credentials because the server may already have consumed the token; there is no automatic retry of that credential. Network calls in login/registration/refresh/current logout have a 15-second timeout.

## Verification

- Java 21 offline Maven verification: **169 tests, zero failures/errors/skips**, including **81 real PostgreSQL integration tests**. Executable JAR packaged.
- V20 → V21 populated upgrade preserves account data/password hashes, revokes legacy token rows and installs the account-change trigger.
- Auth database/MVC tests cover rotation, hashed storage, fixed expiry, replay revocation surviving a 401, concurrent refresh, logout with a rotated ancestor, repeated logout, logout-all authorization/owner isolation, disable/re-enable, password changes, stale login snapshots, profile edits, expired sessions, unknown tokens, registration/login, and concurrent reset-token use.
- JWT and security-slice tests cover purpose, required claims, issuer/audience, signatures, revoked sessions and database failure.
- **Eight frontend coordination tests** cover parallel requests sharing one exchange, delayed 401s, login changes, late responses after logout, network uncertainty, queued logout and incomplete responses. These exercise the coordinator with a simulated shared lock/store, not actual browser tabs.
- Frontend TypeScript/Vite build passed. ESLint: **0 errors, 70 existing warnings**. The roughly 839 kB main bundle still produces Vite's size warning.

Logs: [backend](audit/auth-backend.log), [frontend tests](audit/auth-frontend-tests.log), [build](audit/auth-build.log), [lint](audit/auth-lint.log). CI now runs `npm test` using Node.js 22; the script needs Node.js 22.6 or newer.

No live provider call, live database migration, application restart, real-browser multi-tab walkthrough, device test, load test or remote CI execution was performed. Database tests ran in a separate disposable PostgreSQL/pgvector container. These checks establish the tested behavior, not deployment readiness.

## Deployment and operations

1. Back up the intended database and verify the restore procedure before rollout. Keep a recovery copy of the matching backend and frontend source.
2. Stop older backend instances and deploy matching backend/frontend artifacts together. Flyway applies V21 at backend startup. Do not run older auth code alongside this version; older code ignores persisted revocations.
3. Require every existing user to sign in again. Legacy JWTs lack the required session/issuer/audience/identifier claims. V21 marks rows in the unused legacy `refresh_tokens` table revoked; it preserves accounts and their passwords. The frontend migration clears legacy browser credentials.
4. Verify two sessions for one test account: refresh one, sign it out, confirm it cannot access protected APIs while the other can; then sign out all devices. Exercise multi-tab renewal, password/disable revocation, and connection failure on a deployed test environment.
5. Observe session-query latency and failures. Protected API requests now depend on a database session lookup. No session cache or traffic-capacity claim is included.

Read-only operational checks:

```sql
SELECT revocation_reason, count(*) FROM auth_sessions
WHERE revoked_at IS NOT NULL GROUP BY revocation_reason;
SELECT count(*) FROM auth_sessions
WHERE revoked_at IS NULL AND expires_at > now();
SELECT count(*) FROM auth_sessions WHERE expires_at <= now();
```

Used token hashes must remain for the entire family lifetime to detect replay. No automatic purge is added. Set an operational retention policy and batch-delete expired session families only after their absolute expiry plus an agreed diagnostic buffer; the foreign key cascades to their token hashes. Do not remove used-token history from a still-live family. Legacy token rows and historical diagnostics also need an explicit retention decision.

Tokens remain in localStorage and are therefore exposed to scripts executing in the origin. HttpOnly-cookie transport with its corresponding CSRF design, XSS/CSP review, rate limits, credential rotation, URL-fetch/SSRF protections, safe uploads and dependency remediation remain separate work. Revocation affects subsequent authentication checks; it does not cancel a request or streamed/background workflow already authorized. The raw workflow SSE client does not perform seamless token renewal. Stored workflow history remains available after signing in again. A successful logout-all revokes sessions existing when its transaction executes; a later valid password login can create a new one.

Recovery snapshot for this increment: `/private/tmp/jobpilot-after-auth-session-security.tgz`. It contains tracked and non-ignored project files, including prior committed source and current changes, but excludes real environment files, Git history, build output and installed dependencies. Its manifest records the base commit and file hashes. This is a source recovery snapshot, not a database backup; copy it out of temporary storage if you need durable retention.

Next engineering increment: URL-fetch/SSRF and upload security, followed by rate limits/dependency remediation and a deployed end-to-end journey before a narrow pilot.
