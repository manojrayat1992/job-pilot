# AI provider and failure correctness — 24 October 2026

This is the next increment of [STABILIZATION.md](STABILIZATION.md), following resume review/versioning. It fixes provider routing and misleading success behavior; durable workflows and atomic credit settlement remain separate work.

## Configuration and upgrade

| Capability | Credential | Endpoint / model configuration |
|---|---|---|
| All chat features | Required `GROQ_API_KEY` | Groq OpenAI-compatible endpoint; actual model ID selected from active, positively weighted `model_versions` rows |
| Interview transcription | Optional `OPENAI_API_KEY` | OpenAI `/v1/audio/transcriptions`; `OPENAI_TRANSCRIPTION_MODEL`, default `whisper-1` |
| Interview speech output | Same OpenAI audio key | OpenAI `/v1/audio/speech`; `OPENAI_SPEECH_MODEL`, default `tts-1`; `OPENAI_SPEECH_VOICE`, default `alloy`; MP3 output |

Move an existing Groq key out of `OPENAI_API_KEY` into `GROQ_API_KEY`. Supply a separate OpenAI key only when voice is needed. The application no longer supplies a dummy chat credential; Spring AI requires the chat key at startup. A missing audio key produces an explicit unavailable response, never a simulated transcript. Audio requests use a 10-second connect timeout and 60-second read timeout, with no automatic retry.

Your real `.env` was not edited, and running application containers were not rebuilt or restarted. After configuring the keys, a Docker installation can apply the changes with:

```sh
docker compose --env-file .env -f docker/docker-compose.yml up -d --build backend frontend
```

Flyway V17 repairs the original `base-llama-3.1` seed's prefixed model path to `llama-3.1-8b-instant`. It disables the known active, weight-100 `gpt-4o-mini-default` seed because that model does not belong at the configured Groq endpoint. Other model entries remain untouched. Admins must review customized routes for valid Groq model IDs. If no active route has positive weight, generation fails explicitly. Existing Flyway migrations are unchanged.

## Behavior delivered

- Every chat feature now uses one routing service. Selected `model_path` is applied to Spring AI request options, and existing model usage logs identify that requested model instead of an unrelated alias or hard-coded model. Options are request-local; agent defaults cannot alter subsequent feature calls.
- Empty chat responses and transport failures throw service errors. Structured-output consumers reject raw-text fallback and empty JSON objects. This is not complete per-feature schema validation.
- Synchronous and streamed workflows stop on AI failure and return `FAILED`, retaining completed artifacts in that response. Summary generation is included in the success boundary. A completed custom report is not regenerated.
- SSE JSON includes event types consumed by the frontend. The frontend handles `data:` with or without a following space, honors the actual status, and treats a stream ending without a terminal result as failure. Partial outputs can be reviewed even without a final report.
- Missing credentials, failed requests, empty/malformed transcripts and unusable audio responses cannot become successful voice output. Interview ownership is checked before transcription. Transcription failure stops the turn. Speech-generation failure after a completed text turn returns `audioError` alongside the text response; the UI displays that notice.
- Interview panel failures no longer invent dialogue or scores. Report completion is set only after report generation succeeds.
- LinkedIn, culture, salary and email-draft generation throw errors rather than returning fake-success fallback objects. Workflow thank-you drafts are included in response artifacts.

The audio request contracts were checked against the official [transcription API reference](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create) and [text-to-speech guide](https://developers.openai.com/api/docs/guides/text-to-speech). The implementation retains the existing audio model defaults; model quality and account availability have not been evaluated.

## Verification

- Java 21 `mvn -o verify`: **100 tests, zero failures/errors/skips**, executable JAR packaged. Includes 30 new routing/audio/workflow/interview tests and five existing real-database tests. The latter now verify Flyway V17 and the corrected default routes in a fresh disposable pgvector/PostgreSQL database.
- Frontend `npm run build`: passed. `npm run lint`: zero errors, 73 existing warnings. The main bundle remains about 829 kB with Vite's chunk-size warning.
- `git diff --check`: passed.
- Logs: [backend](audit/provider-backend.log), [frontend build](audit/provider-frontend-build.log), [frontend lint](audit/provider-frontend-lint.log).

Provider responses were mocked: tests cover selected model options, disabled/invalid routes, empty output, authorization/rate-limit/server errors, malformed transcription and structured text, audio response validation, workflow step/report failures, SSE payloads, interview ownership before audio, and speech failure with a preserved text turn. The disposable database container was removed after verification; the running application and its database were not changed. These are not live provider or browser-interaction tests.

## Remaining work

- Evaluate real provider responses, latency, model availability and output quality with controlled accounts and a representative dataset. No paid provider requests were made during this change.
- **Follow-up implemented:** Agent Command Center drafts and step state now persist with history and user-triggered recovery. See [WORKFLOW-PERSISTENCE.md](WORKFLOW-PERSISTENCE.md). Automatic recovery, cancellation and auditing other standalone AI storage paths remain open.
- Add complete schemas and evidence/uncertainty handling to generated analysis, especially salary and culture outputs.
- Reserve and settle credits atomically and consistently across every billable route; measure actual token usage/cost. Existing per-feature charging and logging still have gaps. Partial provider work may incur cost even when a workflow fails.
- Add durable orchestration, cancellation and idempotent retries after the underlying steps have stable failure/side-effect contracts. Spring AI remains the model integration framework; a Java workflow engine such as Temporal still needs a focused evaluation.
- Complete browser interaction checks and a rebuilt-container/live-provider smoke test. A passing mock contract does not prove credentials, provider availability or model quality.
