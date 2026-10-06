# E.R.R.O. project context

Last updated: 2026-10-06

## People and name

E.R.R.O. is a shared project by **Eric Rand and Robin Robert Antonis**. Eric is the repository owner
and the person working with the AI assistant. Robin is Eric's friend. The name
comes from their names; no expanded phrase or other acronym has been agreed.

They started with a shared project and have since confirmed the electrical
assistant direction below. Business and purchasing details remain in development.

## Confirmed product direction — 2026-10-05

Eric has now requested an electrical equipment/component selection assistant with
retrieval from a curated knowledge base, deterministic calculations and validation,
source citations and preparation for a manufacturer product catalog. The initial
market is Estonia/EU. This supersedes the earlier minimal stateless-chat-only
scope and the earlier uncertainty about an electrical product direction. It does
not establish business credentials, customers, guaranteed compliance or a finished
purchasing service.

The existing Spring Boot/React architecture and deployed Railway application are
to be extended, not rebuilt. The frontend remains provisional and retains the
cream/green branding, answer details and protected knowledge management.

## Chat as the main website experience — 2026-10-06

Eric requested that visitors land directly in the chat, with About us and Contact
us accessible through navigation. The chat is now the main workspace, with
editable starter prompts for motor protection, component comparisons and parts
planning. About/contact are separate hash views. Navigating between them keeps
the conversation and draft in page memory; refresh and New problem clear it.

Eric intends to connect product information, quantities, shipping and prices so
the assistant can help plan customer projects. This is the planned next stage,
not a live stock, pricing, quotation, delivery or purchasing integration. This
frontend change adds no commerce endpoints and makes no availability promises.

## Current application

- React/TypeScript/Vite in `web/`, Spring Boot/Java/Maven in `backend/`.
- Existing `POST /api/chat` now runs intent/parameter extraction, bounded knowledge
  retrieval, deterministic electrical checks, optional catalog retrieval and AI
  generation. It retains `reply` and adds structured sources/calculations/warnings.
- Messages and active problem context exist only in page memory. Up to eight
  previous user inputs (24,000 characters total) may be supplied for follow-ups.
  No assistant-generated ratings are reused as user input. New problem and refresh
  clear context; obvious topic changes also reset it. There is no saved chat,
  conversation database, visitor account system or browser storage.
- The homepage opens the electrical chat immediately. Chat/About us/Contact us
  navigation exposes the supplied founder photos, draft bios and contact email
  in separate views. See [Homepage](homepage.md) for behavior and verification.
- The knowledge admin UI is at `/#knowledge`. A backend-only operator token gates
  every `/api/admin` endpoint. No existing authentication system was present.
- Sources, chunks and product/catalog relationships persist in PostgreSQL under
  the dedicated `erro_knowledge` schema using Flyway migrations. Chat content is
  not persisted. Redis and S3 do not store chat or knowledge content.
- A small starter library includes source/scope metadata and two attributed open
  theory summaries. Full copyrighted standards are not included. Owner-licensed
  standards/manufacturer content require explicit indexing and provider-processing
  rights. Markdown/text ingestion is implemented; URL downloads and PDFs are not.
- DeepSeek remains the configured chat provider using the Responses API shape.
  AI secrets stay in backend environment configuration. Embeddings are optional
  and independently configured; keyword retrieval works without an embedding key.
- Do not fabricate product specifications, compatibility, standard clauses or
  compliance. The source corpus must be curated. Metadata-only standards cannot
  establish full requirements. Electrical assistance is not a purchase approval.

See [Electrical knowledge](knowledge.md) for architecture, administration,
licensing, source ingestion, catalog DTOs, configuration and verification limits.

## Backend infrastructure

The foundation remains Java 25, Spring Boot, PostgreSQL 18, Redis and an AWS SDK
S3 client under Docker Compose. Startup checks verify PostgreSQL, Redis and S3
when enabled; Flyway now creates the knowledge/catalog schema first. The user's
latest instruction confirms an existing Railway deployment with connected
PostgreSQL. Its live configuration has not been independently inspected here.

The final MinIO community release is local development only; Amazon S3 remains
a future plan. Railway uses `S3_ENABLED=false` until storage is needed. No bucket
creation or uploads are added. See [Infrastructure](infrastructure.md) and
[Railway setup](railway.md).

## Repository and hosting

Eric decided to keep React and Spring Boot as **separate Railway services** for
now. Do not bundle the React build into Spring Boot. Eric requested an **Nginx
reverse proxy**, matching the architecture in his wiki. The web Docker image now
serves the React build and forwards `/api/*` to the runtime `API_UPSTREAM` address.
React still runs in the browser and uses relative API paths. The implementation
is verified locally; Railway migration is not verified. See [Web proxy](web-proxy.md).

- Repository: <https://github.com/blueye51/Erro>.
- `develop` is the working branch; `main` is used for the Railway deployment.
- The website domain is **erro.ink**.
- Railway web should build `web/Dockerfile` with root directory `/web`, replacing
  the earlier Railpack deployment. Its public domain remains `erro.ink`.
- Railway backend builds `backend/Dockerfile` with root directory `/backend`.
  Nginx reaches it privately; PostgreSQL and Redis also use the private network
  in the same project/environment. A separate backend public domain is unnecessary.
- Root `compose.yaml` runs Nginx at <http://localhost:5173>, plus the backend,
  PostgreSQL, Redis, and local MinIO. The optional `dev` profile runs Vite with
  live updates at <http://localhost:5174>.
- See the [root README](../README.md) for local setup and checks.

## Guidance for future AI work

Read this context and [Electrical knowledge](knowledge.md) before extending the
assistant. Keep source authority, law/standards, manufacturer specifications,
calculation results and generated explanation distinct. Expand the curated corpus
and tested extraction rules deliberately. Saved chats, visitor accounts, tools,
PDF/URL import and purchasing are not implemented and need explicit future scope.

Eric requires all new work to be documented in `docs/` as part of the change.
Keep setup, configuration, workflows, decisions and verification notes current;
see the [documentation policy](README.md#documentation-policy).
