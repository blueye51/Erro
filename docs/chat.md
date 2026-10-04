# Stateless AI chat

Eric requested a working chat with exactly one request/reply flow and no saved
history or separate conversations. The initial adapter implements OpenAI's
Responses API. Its endpoint, model, and API key are configuration, not browser
settings. No provider account or cloud resources are created by this change.

## How it works

1. The visitor types a message and presses Send or Enter. Shift+Enter adds a line.
2. React sends only `{ "message": "..." }` to `POST /api/chat`.
3. Spring validates the message, then `AiService` sends it to the configured
   provider with `store: false` and a bounded output token count.
4. The service extracts assistant text or a refusal, normalizes whitespace, and
   returns `{ "reply": "..." }`. It does not return reasoning or provider metadata.
5. React displays the reply using `react-markdown` 10.1.0 for paragraphs, headings,
   lists, links, and code. Raw HTML is skipped, unsafe link protocols are filtered,
   and Markdown images render only their alt text, without fetching external images.

Only one request can be pending in a page at a time. The composer shows a waiting
state, prevents duplicate sends, and restores an unsuccessful message for retry.
No fake AI replies are used when a provider is unavailable.

Messages exist in React memory only. Refreshing or leaving the page clears them;
the app uses no local storage, session storage, cookies, database writes, Redis
keys, or S3 objects for chat. The AI receives only the current message, so follow-up
questions must include any context they need. There are no conversation IDs,
previous response IDs, saved chat lists, attachments, accounts, tools, or streaming.

OpenAI documents [`store: false`](https://developers.openai.com/api/docs/guides/migrate-to-responses)
for disabling stored response state. This setting does not promise zero provider
retention; the provider's own data policies still apply. The application does not
log message bodies or provider credentials.

## Configure and run locally

Edit the existing ignored root `.env` and add `AI_API_KEY` with your provider key.
If `.env` does not exist, copy `.env.example` first. Preserve existing overrides,
including `POSTGRES_PORT=5433` on Eric's machine. Do not put the key in `web/` or
any `VITE_` variable.

```sh
docker compose up --build -d
docker compose logs backend
```

Open <http://localhost:5173>. Vite forwards `/api` to `http://backend:8080` in
Compose. On the host, Vite's default proxy target is `http://localhost:8080`.
The backend is also published at `127.0.0.1:8080` for local API tools.
After editing backend environment settings, recreate the backend with
`docker compose up -d backend`; a simple container restart does not reload `.env`.

The backend starts without an AI key so infrastructure development still works.
Chat then returns HTTP 503 with `AI chat is not configured yet.`

## Configuration

| Variable | Used by | Default / purpose |
| --- | --- | --- |
| `AI_API_KEY` | Backend | Empty; required for live replies. Passed only in the provider Authorization header. |
| `AI_ENDPOINT` | Backend | `https://api.openai.com/v1/responses`; full Responses API URL. Other providers must implement this same request/response schema. Chat Completions and Anthropic Messages APIs require a different adapter. |
| `AI_MODEL` | Backend | `gpt-5.4-mini`; must be available to the configured provider account. |
| `AI_MAX_OUTPUT_TOKENS` | Backend | 2048; configurable from 1 to 32768, subject to the provider/model limits. |
| `AI_TIMEOUT` | Backend | `60s` read timeout; connection timeout is five seconds. No application retries. |
| `BACKEND_PORT` | Compose | 8080 on the host; the container stays on 8080. |
| `PORT` | Backend | 8080; supports the listening port supplied by a hosting platform. Compose explicitly sets 8080. |
| `CHAT_ALLOWED_ORIGINS` | Backend | `http://localhost:5173`; comma-separated exact browser origins allowed to POST to `/api/chat`. |
| `BACKEND_PROXY_TARGET` | Vite dev server | `http://localhost:8080`; Compose sets `http://backend:8080`. Configured in `web/.env` for host development. |
| `VITE_API_BASE_URL` | Frontend build | Empty for same-origin `/api/chat`; set to a backend origin when hosting separately. Public configuration, never a secret. |

The browser waits at most 65 seconds. Keep `AI_TIMEOUT` shorter than that or
update the browser timeout along with it. Incomplete, empty, or malformed provider
responses become an API error rather than a silently truncated answer.

## API contract

`POST /api/chat`, with `Content-Type: application/json`:

```json
{ "message": "Explain what a relay does." }
```

`message` must be nonblank and at most 8,000 characters. Leading and trailing
whitespace is trimmed. Responses have `Cache-Control: no-store`.

Success, HTTP 200:

```json
{ "reply": "A **relay** is an electrically operated switch." }
```

Errors use `{ "error": "A short, safe message." }`:

| Status | Meaning |
| --- | --- |
| 400 | Missing, blank, oversized message, or malformed JSON. |
| 502 | Provider failure, unreachable provider, or unusable reply. |
| 503 | Missing key/model, rejected provider credentials, or provider rate limit. |
| 504 | Provider timeout. |

Upstream error bodies and credentials are not included in browser errors. The
endpoint has no visitor authentication or application rate limiting; requests
use the server's configured provider account. CORS limits browser origins and
does not authenticate direct HTTP callers.

## Separately hosted frontend and backend

For the existing Railway project, follow [Railway setup](railway.md) for the
exact service settings, PostgreSQL/Redis references, CORS, and frontend rebuild.
S3 can be disabled there with `S3_ENABLED=false`, without disabling the database
and Redis startup checks.

This change does not deploy anything. The existing Railway frontend continues
to build `web/`. Vite's development proxy is not included in the static build.

To connect deployed services, deploy `backend/Dockerfile` using `backend/` as its
build context, give the backend its infrastructure and AI environment variables,
and set `CHAT_ALLOWED_ORIGINS=https://erro.ink` (include other origins only if used).
Then set `VITE_API_BASE_URL` on the frontend service to the backend's HTTPS origin
and rebuild the frontend. Alternatively, route `/api` to the backend at the same
origin. Infrastructure startup verification still applies to the backend.

## Implementation and checks

- `backend/.../ai/AiService.java` owns the provider call and reply extraction;
  `AiProperties.java` binds provider settings.
- `backend/.../chat/ChatController.java` owns `/api/chat` and input validation;
  `ChatErrorHandler.java` returns safe errors.
- `backend/.../config/ChatWebConfiguration.java` configures CORS for this endpoint.
- `web/src/chat.ts` owns the browser request and timeout; `App.tsx` holds only
  page-local messages and renders the UI.

Run `./mvnw verify` from `backend/` (JDK 25), or build the backend Docker image.
`ChatApiTest` starts a local mock HTTP provider and exercises the real controller
and AI service: message isolation, no stored response state, reply extraction,
input validation, CORS, provider failures, refusal replies, missing credentials,
and timeouts. It makes no paid API calls and needs no real credentials.

For frontend changes, run `npm run build` and `npm run lint` from `web/`, then
check desktop/mobile layout, sending, Markdown, loading/errors, and refresh.

## Verification — 2026-10-03

- All 17 backend tests passed in the Java 25 Docker build, including the real
  HTTP provider adapter against a local mock server.
- Frontend production build and lint passed; Compose configuration validated.
- Browser checks passed through Vite → the running backend → a separate mock
  provider container. They covered replies, duplicate-send prevention, provider
  errors, restoring the draft, retrying, and Shift+Enter versus Enter.
- Desktop and mobile screenshots were reviewed. Layout checks passed at 390px
  and 320px widths, including long words and horizontally scrolling code blocks.
  The chat log and code blocks support keyboard focus. The mobile accessibility
  scan reported no WCAG 2 A/AA or WCAG 2.1 AA violations.
- Browser checks confirmed that raw HTML/scripts and unsafe link protocols did
  not execute, Markdown images made no external requests, and refreshing cleared
  messages with no browser storage or cookies created by the app.
- The normal Compose frontend proxy returned the expected missing-key message
  from the backend. Infrastructure startup checks passed; PostgreSQL still had
  zero public application tables and Redis had zero keys after chat testing.

A live OpenAI request was not tested because no `AI_API_KEY` was configured.
Provider/model access must be verified with the configured account. Temporary
mock services are only test fixtures and are not part of the application stack.
