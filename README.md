# E.R.R.O.

A project by Eric Rand and Robin Robert Antonis: an electrical equipment selection
assistant for Estonia/EU, built on the existing React + TypeScript frontend and
Java/Maven Spring Boot backend. Chat retrieves curated knowledge, performs
applicable deterministic checks/calculations and returns sources and missing data.
Follow-up details stay in page memory and clear on refresh or **New problem**.
Knowledge and catalog data use PostgreSQL; chat content is not saved.

Read [Project context](docs/project-context.md) and [Electrical knowledge](docs/knowledge.md)
for architecture, licensing, source management, product imports and verification.
The provisional homepage and founder presentation remain in place.

## Run with Docker Compose

From the repository root:

Add `AI_API_KEY` to your existing ignored `.env` (or create it from `.env.example`
if absent). Eric selected DeepSeek: the default endpoint is
`https://api.deepseek.com/responses`, with model `deepseek-flash` and
`AI_REASONING_EFFORT=none` for basic chat. The adapter uses the Responses API
format; `AI_MODEL` and `AI_ENDPOINT` remain configurable. Keep the key out of
`web/`. Replace any older OpenAI values already set in your environment.

```sh
docker compose up --build -d
```

Open <http://localhost:5173> once Nginx is ready. It serves the built React app
and forwards `/api/chat` privately to Spring Boot. MinIO's console is at
<http://localhost:9001>. Local credentials and optional port overrides are in
[.env.example](.env.example); copy it to `.env` to customize them. The first build
takes longer because it compiles the final MinIO community release from source.

The backend waits for healthy services and logs successful connection checks:

```sh
docker compose logs backend
```

See [Infrastructure](docs/infrastructure.md) for pinned versions, service ports,
Maven commands, persistence, and the later switch to Amazon S3. Rebuild web after
frontend/Nginx changes and backend after Java changes. For Vite live updates,
run `docker compose --profile dev up -d web-dev` and open <http://localhost:5174>.

[Chat setup](docs/chat.md) documents the endpoint, environment variables, errors,
tests, and the request/reply flow. [Web proxy](docs/web-proxy.md) covers Nginx,
runtime `API_UPSTREAM` configuration, and proxy tests. Without a
provider key, the stack still starts and chat reports that it is not configured.

[Railway setup](docs/railway.md) gives the complete steps and variables for the
existing web service plus a new backend, PostgreSQL 18, and Redis. The Railway
backend uses `S3_ENABLED=false` until Amazon S3 is added. Local Compose continues
to connect to MinIO. Deploy web using `web/Dockerfile`, set its
`API_UPSTREAM=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080`, and remove the old
`VITE_API_BASE_URL`. Only web needs a public domain. These changes do not create
or deploy Railway resources.

To stop and remove the containers while keeping local data:

```sh
docker compose down
```

## Knowledge management

Set a random `KNOWLEDGE_ADMIN_TOKEN` (at least 32 characters) on the backend, then
open `/#knowledge`. The token is never a frontend build variable. Import reviewed
Markdown/text with metadata and indexing permission. Without a token the admin
API is disabled. The migrations seed scope metadata and two open-license theory
summaries; full standards and manufacturer documentation need authorized imports.

Keyword retrieval works immediately. Optional `EMBEDDING_*` backend settings add
semantic retrieval. See [the knowledge guide](docs/knowledge.md) before enabling
embeddings, ingesting licensed content or deploying the migrations to Railway.

## Run locally

For just the frontend, with Node.js 24 and npm installed:

```sh
cd web
npm ci
npm run dev
```

Vite forwards `/api` to the backend at `localhost:8080`. If Nginx already occupies
5173, use `npm run dev -- --port 5174` or the Compose `web-dev` service above.

## Check the app

From `web`:

```sh
npm run lint
npm run build
```

For backend unit/API tests on Java 25, run `./mvnw verify` from `backend/`.
For isolated PostgreSQL migration/retrieval tests, run
`./scripts/verify-knowledge.sh` from the repository root. No real AI keys are used.

End-to-end browser checks use `./scripts/verify-browser.sh` after
`cd web && npm ci && npx playwright install chromium`. This creates an isolated
local stack with a mock AI; see [verification details](docs/knowledge.md).
