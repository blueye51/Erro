# Chat API

The existing same-origin `POST /api/chat` now runs the electrical knowledge
pipeline documented in [Electrical knowledge](knowledge.md). The endpoint and
`reply` field are preserved; frontend and backend remain separate services.

## Contract

```json
{"message":"400 V","problemContext":["I have a 7.5 kW three-phase motor"]}
```

`message` is required, 1–8,000 characters after validation; `problemContext` is
optional, up to eight prior user inputs and 24,000 characters total. It is never
stored server-side. The UI keeps it only in memory and clears it with New problem
or refresh. Prior assistant replies are not sent as technical inputs.

Successful responses contain `reply`, `requestId`, `sources`, `calculations`,
`assumptions`, `warnings`, `missingInformation`, `validations`, `products` and
`contextReset`. Source IDs are mapped from actual retrieved chunks. The frontend
renders Markdown safely and places engineering details in expandable sections.
It opens directly on the homepage. About us/Contact us navigation preserves the
current draft and conversation; browser refresh still clears them. Starter prompts
fill an editable draft and do not call the API until sent. See [Homepage](homepage.md).
A previous frontend can still read `reply` during a rolling deployment.

## Short-term chat memory

The current chat remembers up to **eight successful user messages**, within a
**24,000-character** total. Each follow-up includes those messages in chronological
order through the existing `problemContext` field. The oldest messages are dropped
when either limit is reached. Failed or pending sends are not added to memory,
so retrying does not duplicate them. Navigation to About us or Contact us preserves
memory in the current page; New problem, a full refresh, or closing the page clears
it. There is no timer, cross-tab memory, saved history or browser/server storage.

The UI displays the number of remembered user messages and a notice when the
backend resets context. The backend still resets for explicit new-problem
requests and clearly separate equipment questions. A follow-up longer than 500
characters no longer causes a reset on its own. Explicit continuations such as
“What enclosure would work for it?” retain the current problem. Topic detection
remains a limited heuristic; use New problem when changing equipment/conditions.

For generation, the provider's JSON input separates `userQuestion` (the latest
message) from `previousUserMessages` (accepted earlier user inputs). The system
instruction explicitly uses those inputs to understand follow-ups and avoid
repeating questions already answered. Retrieval/calculations still use the
combined active problem. The protected retrieval debugger shows the same input
shape. Earlier assistant replies are not sent back as user evidence or assumed
to be verified specifications; questions requiring an unavailable earlier answer
may need the visitor to restate the relevant detail. The provider still receives
`store: false` and no conversation/previous-response IDs.

Rebuild and deploy **both web and backend** to use the updated memory behavior
and system instruction. There are no new credentials, variables or migrations.

After `npm run build` from `web/`, run `npm run test:chat-memory` there. This
starts a temporary local production preview and uses intercepted API fixtures,
without backend services or real provider credentials. Install Chromium once
with `npx playwright install chromium`. Backend `./mvnw verify` tests the actual
provider envelope against a local mock HTTP server.

## Reply formatting

Assistant replies use `react-markdown` with `remark-gfm` for GitHub-flavored
Markdown, including pipe tables. Tables render as semantic HTML with column
headers, visible cell borders and alternating row backgrounds. Each table has a
keyboard-focusable horizontal scroll region on narrow screens; wide content does
not force the entire page to scroll sideways. Links, emphasis and inline code
inside cells retain their formatting. Code blocks remain literal text.

Raw HTML is still skipped, Markdown images render as their alt text, and the
renderer retains its default URL sanitization. Table support does not require
raw HTML parsing or change the backend response. See the
[upstream plugin documentation](https://github.com/remarkjs/remark-gfm) for syntax.
Run `npm ci` in `web/` after updating dependencies, then build/redeploy the web
service. There are no new environment variables or migrations for this change.

## Error handling

Errors retain `{ "error": "safe message" }`: 400 for invalid input, 503 for
missing/invalid provider configuration or rate limiting, 502 for provider failures
or invalid replies, and 504 for provider timeout. Provider bodies and credentials
are never returned. A deterministic electrical FAIL can return a useful answer
without calling the model, even when the chat provider is not configured.

The backend always supplies the electrical system instruction, sends bounded
reference context and sets provider `store=false`. It sends no provider response
IDs and creates no server-side conversations. Retrieved document text is untrusted
reference data, not instructions. Database knowledge/catalog persistence is
separate from chat memory.

## Configuration

The existing backend settings remain `AI_API_KEY`, `AI_ENDPOINT`, `AI_MODEL`,
`AI_REASONING_EFFORT`, `AI_MAX_OUTPUT_TOKENS` and `AI_TIMEOUT`. Defaults are the
project's DeepSeek Responses endpoint, `deepseek-flash`, reasoning effort `none`,
2048 output tokens and a 60s timeout. An empty effort omits that provider field.
No key belongs in `web/`, `VITE_*` or the repository. Configure model/endpoint/key
together and verify account access after deployment. Embeddings use independent
`EMBEDDING_*` configuration; see the knowledge guide.

`CHAT_ALLOWED_ORIGINS` controls the existing chat CORS policy. Railway should
include `https://erro.ink,https://www.erro.ink`; local default is
`http://localhost:5173`. Admin APIs are same-origin and require a bearer token.
The Vite development server proxies `/api`, and production Nginx uses backend-only
`API_UPSTREAM`. No public backend domain is required. The public chat retains its
existing anonymous access model; CORS is not authentication.

## Verification

Run `./mvnw verify` from `backend/` on Java 25. Tests use a local HTTP mock provider
and no real keys. Run `./scripts/verify-knowledge.sh` at the repository root for
isolated PostgreSQL retrieval/migration tests. See [Electrical knowledge](knowledge.md)
for this implementation's complete verification and deployment checklist.

## Chat memory verification — 2026-10-26

- Frontend build and lint passed. `npm run test:chat-memory` passed against the
  production frontend with mock API responses: remembered follow-ups, navigation,
  eight-message/24,000-character bounds, duplicate-send prevention, error/retry,
  explicit and backend-requested resets, refresh clearing, Markdown, and desktop/
  mobile layouts at 1440, 768, 390 and 320px. No browser storage or cookies were
  created. A mobile screenshot was inspected.
- Maven `verify` passed using a temporary Temurin JDK 25: **53 tests passed**,
  **7 PostgreSQL integration tests skipped** because no test database was supplied.
  Added regressions verify long/related follow-ups, reset isolation, latest-message
  vs chronological-user-memory provider input, no server/provider conversations,
  and rejection of oversized context. HTTP provider tests use a local mock.
- Docker Desktop's Linux engine/WSL integration was unavailable. Attempts to run
  `docker compose config --quiet`, build web/backend images and execute
  `scripts/verify-knowledge.sh` failed before validation/build/test execution.
  Compose startup connection checks and the Docker end-to-end browser fixture
  could not be rerun in this session. No database schema or infrastructure
  configuration changed. Rerun these checks with Docker available before
  deployment. No live AI call or Railway deployment was performed.

## Historical verification (before electrical retrieval)

Earlier checks in the project history verified the original one-message-only
request flow, provider error handling and safe Markdown. That scope is superseded
by the explicit electrical-knowledge and active-problem-context request on
2026-10-05.
