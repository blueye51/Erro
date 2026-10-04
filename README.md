# E.R.R.O.

A project by Eric and Robin, starting with a React + TypeScript chat interface
in `web`. Visitors can send a message to the Java/Maven Spring Boot backend in
`backend/`, which calls the configured AI provider and returns a formatted reply.
Each message is independent; nothing is saved and there are no separate
conversations. PostgreSQL, Redis, and local MinIO are connected for future work.

Read [the project context](docs/project-context.md) for the background, possible
future directions, and guidance for AI assistants. The direction is still open;
electrical work, marketing, AI, and a future portfolio are possibilities.

## Run with Docker Compose

From the repository root:

Add `AI_API_KEY` to your existing ignored `.env` (or create it from `.env.example`
if absent). The default adapter uses OpenAI Responses with `gpt-5.4-mini`;
`AI_MODEL` and `AI_ENDPOINT` are configurable. Keep the key out of `web/`.

```sh
docker compose up --build -d
```

Open <http://localhost:5173> once Vite is ready. MinIO's console is at
<http://localhost:9001>. Local credentials and optional port overrides are in
[.env.example](.env.example); copy it to `.env` to customize them. The first build
takes longer because it compiles the final MinIO community release from source.

The backend waits for healthy services and logs successful connection checks:

```sh
docker compose logs backend
```

See [Infrastructure](docs/infrastructure.md) for pinned versions, service ports,
Maven commands, persistence, and the later switch to Amazon S3. Vite source files
are mounted for live updates; rebuild the backend after Java changes.

[Chat setup](docs/chat.md) documents the endpoint, environment variables, errors,
tests, and how to connect a separately hosted frontend and backend. Without a
provider key, the stack still starts and chat reports that it is not configured.

[Railway setup](docs/railway.md) gives the complete steps and variables for the
existing web service plus a new backend, PostgreSQL 18, and Redis. The Railway
backend uses `S3_ENABLED=false` until Amazon S3 is added. Local Compose continues
to connect to MinIO. These changes do not create or deploy Railway resources.

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

## Check the app

From `web`:

```sh
npm run lint
npm run build
```
