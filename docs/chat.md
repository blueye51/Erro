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
A previous frontend can still read `reply` during a rolling deployment.

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

## Historical verification (before electrical retrieval)

Earlier checks in the project history verified the original one-message-only
request flow, provider error handling and safe Markdown. That scope is superseded
by the explicit electrical-knowledge and active-problem-context request on
2026-10-05.
