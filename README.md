# Job Pilot

Job Pilot is a prototype application workspace built with Java 21, Spring Boot, Spring AI, React, TypeScript, PostgreSQL, and pgvector. It supports resume suggestions, cover-letter drafts, interview practice, job research, an application tracker, and saved Agent Command Center workflows with resumable drafts.

Start with the [project assessment](docs/PROJECT-ASSESSMENT.md) for the technical and commercial review, and the [stabilization notes](docs/STABILIZATION.md) for the changes made after that assessment. This repository is not yet ready for a public paid launch.

## Current feature boundaries

- Job research saves selected jobs as **SAVED**. It does not submit applications to employers.
- Email synchronization, hiring-contact discovery/unlock, extension autofill, and paid upgrades are disabled until their real integrations are implemented and verified.
- AI-generated text needs user review. Reviewed resume versions are implemented; live provider quality evaluation, automatic workflow recovery and cancellation remain open work. Durable embedding delivery and atomic app-credit accounting are implemented; provider currency accounting remains open. Agent Command Center history and user-triggered step recovery are implemented; see [saved workflows](docs/WORKFLOW-PERSISTENCE.md).
- Redis, Playwright, and Stripe dependencies are present; their presence does not establish a working production integration.

## Reviewed resume versions

In Resume Hub, use the sparkle action to generate a draft, compare changes, confirm factual accuracy and save a separate resume before PDF export. Manual content edits also preserve the original. See [the versioning guide](docs/RESUME-VERSIONING.md) for behavior, migration V16, tests and remaining limits.

## Local setup

Run the following commands from this directory, which contains `backend`, `frontend`, and `docker`.

Prerequisites: Java 21, Maven 3.8+, Node.js 22, npm, and Docker Compose. There is no Maven wrapper in this repository. The examples assume a POSIX shell.

1. Copy `.env.example` to `.env` and fill it in. `JWT_SECRET` is required; generate a unique value with `openssl rand -base64 48`. Never commit real credentials.
2. Set `GROQ_API_KEY` for chat. Set `OPENAI_API_KEY` separately only if you need interview voice input/output. If your old `OPENAI_API_KEY` contained a Groq key, move that value to `GROQ_API_KEY`; never reuse it for OpenAI audio. Missing audio credentials disable voice explicitly while text remains available. `SERPAPI_KEY` is optional for external job research. See [AI provider configuration and verification](docs/AI-PROVIDER-STABILIZATION.md).
3. Load your trusted local environment file into the shell. Spring Boot does not automatically load the root `.env` file:

```sh
set -a
. ./.env
set +a
```

4. Start local infrastructure:

```sh
docker compose --env-file .env -f docker/docker-compose.yml up -d postgres redis
```

The Compose database credentials must match the backend environment. The defaults are for local development only.

5. Start the backend in that same shell:

```sh
cd backend
mvn spring-boot:run
```

The backend listens on `http://localhost:8080`; Swagger UI is at `/swagger-ui.html`. Application settings live in `backend/src/main/resources/application.yml`. Flyway runs database migrations on application startup.

6. In another terminal, from the directory containing this README:

```sh
cd frontend
npm ci
npm run dev
```

Vite serves the UI at `http://localhost:5173` and proxies `/api` to the local backend. Use `frontend/package-lock.json` with npm for repeatable installations. The separate pnpm lockfile is not maintained by this verification workflow.

`VITE_API_URL` is optional. Leaving it unset uses the current origin, including the Vite proxy. For a separate API host, configure its origin at build time and set `CORS_ORIGINS` on the backend to the exact frontend origins. Production hosting must proxy `/api` if the frontend uses the current origin.

## Verification

From `backend`, with Java 21 selected:

```sh
mvn verify
```

From `frontend`:

```sh
npm test
npm run lint
npm run build
```

`npm test` runs the refresh-session coordination tests with Node.js 22.6 or newer. Lint has known warnings; they are documented in the stabilization notes. Backend tests cover service logic, provider contracts with mocked responses, and Spring MVC security/SSE behavior. Set `JOBPILOT_REVIEW_DB_URL` to an empty disposable PostgreSQL database to include the database integration tests; see the stabilization notes. No live AI provider test is implied.

GitHub Actions runs backend verification and frontend installation, tests, lint, and build. The workflow still needs its first remote execution after the changes are pushed.

## Packaging

From `backend`:

```sh
mvn clean package
```

A Dockerfile is supplied in `backend`; it uses a Maven builder and a glibc-based Temurin runtime. Keep the runtime compatible with the native DJL tokenizer and ONNX libraries; the previous Alpine runtime failed at startup. The running ARM64 container was checked on 24 October 2026: native dependencies resolved, ONNX initialized, and `/actuator/health` returned `UP`. See [stabilization notes](docs/STABILIZATION.md) for the verification scope. End-to-end AI feature behavior and fresh container-build reproducibility remain separate checks.

No license grant is established here; add an appropriate license file before distributing under a named license.

AI calls now use the [credit accounting ledger](docs/CREDIT-ACCOUNTING.md): one app credit per successful chat/audio call, atomic reservations, refunds for provider errors, and reusable workflow responses. V19 moves spendable balances into `ai_credit_accounts`; `users.credits` is retained only as the legacy/initial balance. Unresolved calls require operator reconciliation. Provider currency costs and paid upgrades are still unavailable.

Search now reads the same embedding tables used by ingestion. [Search integrity](docs/SEARCH-INTEGRITY.md) describes V20's durable indexing queue, retry/status endpoints, concurrent job/tracker save guards and source-backed research IDs. V20 queues existing sources for rebuilding, so deploy matching backend/frontend with old workers stopped and allow time for indexing. The Jobs page shows preparation status and retry controls.

Authentication now uses persisted sessions and single-use refresh tokens. Logout revokes the current session; **Sign out all devices** revokes every session for that account. V21 and the matching frontend require all existing users to sign in again. Serve the UI over HTTPS (localhost is supported for development); automatic refresh requires browser Web Locks to coordinate tabs. See [authentication session security](docs/AUTH-SESSION-SECURITY.md) for API behavior, deployment steps and remaining risks.
