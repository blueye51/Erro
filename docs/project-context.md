# E.R.R.O. project context

Last updated: 2026-10-04

## People and name

E.R.R.O. is a shared project by **Eric and Robin**. Eric is the repository owner
and the person working with the AI assistant. Robin is Eric's friend. The name
comes from their names; no expanded phrase or other acronym has been agreed.

They want a starting point for making a project together. They do not yet have
a fixed business plan, product specification, or final direction.

## Ideas being explored

Possible directions include electrical work, marketing, and AI that could help
with electrical topics or tasks. These are initial ideas, not confirmed services
or product commitments. The site might instead become a portfolio or a home for
their future projects. Other directions remain possible.

Do not present E.R.R.O. as an established business, a working AI assistant, or a
finished product. Do not invent credentials, customers, projects, testimonials,
or promises about what Eric or Robin can provide.

## Current website

Eric has now requested a working, minimal AI chat in the existing branded,
responsive interface. This explicitly replaces the earlier inactive preview.

Current requirements:

- React and TypeScript on Vite, in `web/`, connected to `POST /api/chat`.
- A visitor sends a message; the backend calls the configured AI provider,
  extracts the assistant's reply, and returns it for display as Markdown.
- Each request contains only the new message. No earlier messages, conversation
  identifiers, or provider response IDs are sent as context.
- Messages exist only in React state while the page is open. Refreshing clears
  them. No saved history, different conversations, or browser/server persistence.
- No uploads, accounts, tools, streaming, or additional workflows.
- No new-conversation, history, or attachment controls.
- Local presentation interactions, such as opening the project note, are fine.
- Serve assets locally; no third-party fonts or analytics. The browser calls the
  backend only; the backend holds the AI key and calls the provider.
- Keep the branding and copy open enough to support a later change of direction.

The displayed possibilities remain ideas, not clickable prompts or working
tools. The page identifies E.R.R.O. as a project by Eric and Robin. Working chat
does not confirm an electrical, marketing, or other business direction.
See [Chat](chat.md) for the API contract, provider setup, and verification.

## Backend foundation

Eric has explicitly requested a Java/Maven Spring Boot backend in `backend/`,
connected to PostgreSQL 18, Redis, and MinIO through Docker Compose. The later
chat request adds an HTTP API and an AI service to that foundation.

The confirmed backend scope is:

- Use stable releases and Java LTS; pin versions rather than using preview builds.
- Configure the database, Redis connection, and an AWS SDK S3 client.
- Verify connections at startup with read-only checks.
- One chat endpoint and one configurable AI provider service. The initial
  implementation supports OpenAI's Responses API; the key, endpoint, and model
  are backend settings. This is an implementation default, not a commitment to
  a particular provider for future product work.
- No entities, application tables, migrations, bucket creation, authentication
  features, saved chats, or conversation management. Chat does not use PostgreSQL,
  Redis, or MinIO for storage.

Eric chose the final MinIO community release for **local development only**, with
the intention to switch to **Amazon S3 later**. The community server is archived;
the Compose image builds the final release from its pinned source. Use the AWS
S3 client so that a future switch uses storage configuration rather than a
MinIO-specific application SDK. See [Infrastructure](infrastructure.md) for
versions and local commands.

Eric has also requested Railway setup guidance for adding the backend,
PostgreSQL 18, and Redis alongside the existing web service. Disable S3 on
Railway for now with `S3_ENABLED=false`; PostgreSQL and Redis startup checks
remain enabled. The deployment guide and configuration are prepared locally;
Railway resources have not been created or verified from this workspace.
See [Railway setup](railway.md) for the complete dashboard procedure and variables.

## Repository and hosting

- Repository: <https://github.com/blueye51/Erro>.
- `develop` is the working branch; `main` is used for the Railway deployment.
- The website domain is **erro.ink**.
- Railway builds the `web/` directory with Railpack and `npm run build`.
- The planned Railway backend builds `backend/Dockerfile` with root directory
  `/backend`. Browser chat calls its public HTTPS domain; PostgreSQL and Redis
  are reached through Railway's private network in the same project/environment.
- Root `compose.yaml` runs Vite at <http://localhost:5173> with live updates,
  plus the backend, PostgreSQL, Redis, and local MinIO.
- See the [root README](../README.md) for local setup and checks.

## Guidance for future AI work

Read this document before proposing features or changing the site's identity.
Treat Eric and Robin's uncertainty as intentional. Ask about consequential
product decisions instead of selecting a business model on their behalf.

Keep working chat limited to the confirmed request/reply flow. Do not add memory,
saved conversations, authentication, tracking, or an electrical advice workflow
just because the page resembles a chat product. Record later decisions here so
future work builds on confirmed direction.

Eric requires all new work to be documented in `docs/` as part of the change.
Keep setup, configuration, workflows, decisions, and verification notes current;
see the [documentation policy](README.md#documentation-policy).
