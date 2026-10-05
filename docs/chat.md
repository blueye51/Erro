# Stateless AI chat

Eric requested a working chat with exactly one request/reply flow and no saved
history or separate conversations. Eric selected **DeepSeek** on 2026-10-04.
The adapter uses the OpenAI-compatible Responses API format, which DeepSeek
supports. Its endpoint, model, API key, and reasoning effort are backend
configuration. No provider account or cloud resources are created by this change.

## How it works

Chat is now in the homepage's **AI corner**, below About us. Open the
“What’s on your mind?” panel to use it. Home, About us, and Contact remain
available while chatting. Collapsing the panel keeps its messages in memory;
refreshing clears them. See [Homepage](homepage.md) for the surrounding interface.

1. The visitor types a message and presses Send or Enter. Shift+Enter adds a line.
2. React sends only `{ "message": "..." }` to `POST /api/chat`.
3. Spring validates the message, then `AiService` sends it to the configured
   provider with `store: false`, a bounded output token count, and the configured
   reasoning effort. The default `none` disables DeepSeek thinking mode.
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

DeepSeek documents its [Responses API](https://api-docs.deepseek.com/guides/responses_api/)
as stateless: it ignores `store` and returns `store: false`. The adapter continues
sending `store: false` for compatible providers that implement it. This does not
promise zero provider retention; the provider's own data policies still apply.
The application does not log message bodies or provider credentials.

## DeepSeek setup

DeepSeek runs its own service with a compatible API format; using that format
does not mean requests are sent to OpenAI. No SDK or additional provider adapter
is needed for our current request/reply flow.

Use these backend settings in Railway or the ignored local `.env`:

```dotenv
AI_ENDPOINT=https://api.deepseek.com/responses
AI_MODEL=deepseek-flash
AI_REASONING_EFFORT=none
```

Set `AI_API_KEY` separately to the DeepSeek key. Replace any previously configured
OpenAI key, endpoint, and model together. These defaults are also in
`application.yaml`, Compose, and `.env.example`; existing environment values take
precedence. Railway needs a backend redeploy after variable changes. The optional
reasoning setting requires the updated backend code to be deployed as well.

DeepSeek's [current model list](https://api-docs.deepseek.com/quick_start/pricing/)
names `deepseek-flash` (DeepSeek-V4.1-Flash) and `deepseek-v4-pro`. The project
defaults to Flash for its basic chat. These names were verified on 2026-10-04;
model availability remains provider/account dependent.

DeepSeek enables thinking by default. Its
[Responses request format](https://api-docs.deepseek.com/api/create-response/)
accepts `reasoning: {"effort": "none"}` to disable it; `low`, `high`, and `max`
enable thinking. The app exposes this through `AI_REASONING_EFFORT`. An empty
value omits the parameter and leaves the provider's default behavior. Thinking
tokens share the output token limit, and the browser still has a 65-second
timeout. The page only displays the final assistant message, even if a provider
returns reasoning items.

## Configure and run locally

Edit the existing ignored root `.env` and add `AI_API_KEY` with your DeepSeek key.
If `.env` does not exist, copy `.env.example` first. Preserve existing overrides,
including `POSTGRES_PORT=5433` on Eric's machine. Do not put the key in `web/` or
any `VITE_` variable.

```sh
docker compose up --build -d
docker compose logs backend
```

Open <http://localhost:5173>. Nginx serves the built frontend and forwards `/api`
to `http://backend:8080` in Compose. For Vite live reload, run the optional
`web-dev` service on port 5174. Host Vite forwards to `http://localhost:8080`.
The backend is also published at `127.0.0.1:8080` for local API tools.
After editing backend environment settings, recreate the backend with
`docker compose up -d backend`; a simple container restart does not reload `.env`.

The backend starts without an AI key so infrastructure development still works.
Chat then returns HTTP 503 with `AI chat is not configured yet.`

## Configuration

| Variable | Used by | Default / purpose |
| --- | --- | --- |
| `AI_API_KEY` | Backend | Empty; required for live replies. Passed only in the provider Authorization header. |
| `AI_ENDPOINT` | Backend | `https://api.deepseek.com/responses`; full Responses API URL. Other providers must implement this same request/response schema. Chat Completions and Anthropic Messages APIs require a different adapter. |
| `AI_MODEL` | Backend | `deepseek-flash`; must be available to the configured provider account. |
| `AI_REASONING_EFFORT` | Backend | `none`; sent as `reasoning.effort`. Empty omits it. Supported values depend on the provider/model. Compose preserves an explicitly empty value. |
| `AI_MAX_OUTPUT_TOKENS` | Backend | 2048; configurable from 1 to 32768, subject to the provider/model limits. |
| `AI_TIMEOUT` | Backend | `60s` read timeout; connection timeout is five seconds. No application retries. |
| `BACKEND_PORT` | Compose | 8080 on the host; the container stays on 8080. |
| `PORT` | Backend and Nginx, separately | 8080 in each container; both support the listening port supplied by a hosting platform. |
| `CHAT_ALLOWED_ORIGINS` | Backend | `http://localhost:5173`; comma-separated exact browser origins allowed to POST to `/api/chat`. |
| `BACKEND_PROXY_TARGET` | Vite dev server | `http://localhost:8080`; Compose `web-dev` sets `http://backend:8080`. Configured in `web/.env` for host development. |
| `API_UPSTREAM` | Nginx runtime | `http://backend:8080`; set to the private backend origin on Railway. See [Web proxy](web-proxy.md). |
| `WEB_PORT`, `VITE_PORT` | Compose | Nginx and optional Vite host ports, defaults 5173 and 5174. |

The old `VITE_API_BASE_URL` setting has been removed. The browser always requests
`/api/chat` on the website's own origin; private routing is handled by Nginx.

The browser waits at most 65 seconds; Nginx permits 70 seconds of upstream read
silence. Keep `AI_TIMEOUT` shorter than the browser timeout or
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
exact service settings, PostgreSQL/Redis references, and Nginx deployment.
S3 can be disabled there with `S3_ENABLED=false`, without disabling the database
and Redis startup checks.

Deploy `web/Dockerfile` with root directory `/web` and `backend/Dockerfile` with
root directory `/backend`. Set web `API_UPSTREAM` to backend's private HTTP origin
and backend `CHAT_ALLOWED_ORIGINS=https://erro.ink` (include other web origins only
if used). Web serves the website and forwards `/api/chat`; only web needs a public
domain. Remove any old `VITE_API_BASE_URL` setting. Infrastructure startup checks
still apply to backend. See [Web proxy](web-proxy.md) for runtime settings and tests.

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

The mock provider uses DeepSeek's Responses response shape, including
`reasoning_text` items that must not appear in chat. Tests also cover the configured
reasoning effort and omitting the setting when empty.

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

## DeepSeek configuration verification — 2026-10-04

- Compose configuration and the backend Docker build passed. Maven ran 18 tests
  against the local mock provider, covering the DeepSeek Responses shape,
  `reasoning.effort=none`, omission when empty, message isolation, and existing
  validation, error, timeout, and CORS behavior.
- Resolved Compose settings use the DeepSeek endpoint, `deepseek-flash`, and
  reasoning effort `none`. An explicitly empty effort is preserved as intended.
- The rebuilt local backend started and verified PostgreSQL, Redis, and MinIO.
- No frontend changes were needed. No live DeepSeek call was made: Eric has the
  key, but it has not been supplied to this workspace. Configure it on Railway
  and verify one reply after deploying the backend update.

## Nginx integration verification — 2026-10-04

The browser now uses `/api/chat` on its own origin. A browser check through the
production Nginx image and real Spring Boot backend with a mock provider passed
for sending, loading, Markdown, safe errors, retrying, mobile layout, and clearing
messages on refresh. The app created no browser storage or cookies. See
[Web proxy verification](web-proxy.md#results--2026-10-04) for the complete results.
