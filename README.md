# E.R.R.O.

A project by Eric Rand and Robin Robert Antonis, with a React + TypeScript
homepage in `web`: introduction, About us, Contact, and an expandable AI corner.
Visitors can send a message to the Java/Maven Spring Boot backend in
`backend/`, which calls the configured AI provider and returns a formatted reply.
Each message is independent; nothing is saved and there are no separate
conversations. PostgreSQL, Redis, and local MinIO are connected for future work.

Read [the project context](docs/project-context.md) for the background, possible
future directions, and guidance for AI assistants. The direction is still open;
electrical work, marketing, AI, and a future portfolio are possibilities.
See [Homepage](docs/homepage.md) for the founder photos, draft copy, and contact.

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
