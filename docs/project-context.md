# E.R.R.O. project context

Last updated: 2026-10-28

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
the conversation and draft while navigating. Saved guest chats now survive refresh,
as authorized below; unsent drafts remain in page memory.

Eric intends to connect product information, quantities, shipping and prices so
the assistant can help plan customer projects. This is the planned next stage,
not a live stock, pricing, quotation, delivery or purchasing integration. This
frontend change adds no commerce endpoints and makes no availability promises.

## Current application

On 2026-10-26 Eric authorized file uploads and AI document analysis inside the
electrical chat, then explicitly requested replacing temporary chat memory with
full backend database persistence. Every visitor is a guest, with no login. A
private browser cookie recognizes the guest; PostgreSQL saves separate chats,
full messages/answer details and extracted attachment evidence. Refresh restores
the selected chat, and queued work can finish while the page reloads. **New chat**
starts a separate conversation. Guests can delete a chat or **Delete all my data**
to erase all their chat/document data and rotate to a fresh guest identity.

PDFs, images, Word/Excel, CSV and text use direct extraction plus vision, bounded
retrieval/citations and scoped electrical checks/catalog data. Documents belong
to their chat and never enter permanent knowledge automatically. Parser originals
are discarded after processing; extracted evidence survives with the chat.
See [Guest chats](guest-chats.md) and [Chat documents](documents.md). The supplied
three-page Peakilp visual PDF passed a live saved-chat check: extraction, a
one-letter follow-up, refresh restoration and another follow-up. This verifies
the processing/persistence path, not drawing interpretation or exhaustive quantities.
Readable visual content remains available with warnings when model-generated
locations or structure are unreliable. These readings are marked incomplete,
have no usable quantities for unlocated items, and cannot establish exhaustive
counts/DIN totals. Located readings can support a labelled provisional inventory.
On 2026-10-27 Eric reported that cabinet estimates were refusing on partial
retrieval. Full-width PDF bands now preserve schedule columns, compact evidence
reduces omissions, and coverage separates prompt limits from uncertain readings.
Requested planning estimates may show explicit assumed widths/spare allowances;
these must never become verified specifications or deterministic technical inputs.
Later that day Eric requested longer replies and a larger, more readable chat.
The default response allowance is now 8,192 tokens, independently of bounded AI
inputs and full stored history. The chat has a wider transcript, larger text and
a growing multiline composer. Existing production output-limit overrides need
the same update; see [Chat configuration](chat.md#configuration).
On 2026-10-28 Eric requested useful clarification before a final evaluation.
The assistant is now instructed to ask up to two focused questions when their
answers would materially change the assessment, wait for the user's reply, and
then continue using the saved exchange. It should answer directly when enough
information is available and give qualified supported results when the user
cannot supply more. Short preceding assistant replies receive prompt space so
clarification survives large document context; they remain unverified dialogue.
See [Clarification before evaluation](chat.md#clarification-before-evaluation).

- React/TypeScript/Vite in `web/`, Spring Boot/Java/Maven in `backend/`.
- The website uses cookie-scoped `/api/guest` and `/api/chats` routes, durable
  asynchronous generation and document queues, and PostgreSQL-owned history.
  `ChatService` retains knowledge retrieval, deterministic checks and source details.
  The earlier stateless `/api/chat` remains a compatibility endpoint.
- Complete history is stored separately from model context. Follow-ups select
  recent dialogue and relevant older user inputs within a 32,000-character
  generation envelope. Generated ratings are never reused as deterministic
  technical inputs. Topic changes reset inherited technical context without
  deleting history. There are no visitor logins or cross-device recovery.
- The homepage opens the electrical chat immediately. Chat/About us/Contact us
  navigation exposes founder names, draft bios and contact email in separate
  views. Eric requested permanent removal of both founder portraits on 2026-10-26:
  About us uses text only, with no profile pictures or image placeholders. The
  portrait assets are removed from the frontend. See [Homepage](homepage.md)
  for behavior and verification.
- The knowledge admin UI is at `/#knowledge`. A backend-only operator token gates
  every `/api/admin` endpoint. No existing authentication system was present.
- Sources, chunks and product/catalog relationships persist in PostgreSQL under
  the dedicated `erro_knowledge` schema using Flyway migrations. Migration V4 adds guest-owned
  chats/documents in `erro_chat` in the same database. Redis and S3 do not store
  chat or knowledge content.
- A small starter library includes source/scope metadata and two attributed open
  theory summaries. Full copyrighted standards are not included. Owner-licensed
  standards/manufacturer content require explicit indexing and provider-processing
  rights. Permanent knowledge ingestion remains Markdown/text only; temporary
  chat uploads do not grant indexing rights or become knowledge sources.
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
when enabled; Flyway creates knowledge/catalog and guest-chat tables first. The user's
latest instruction confirms an existing Railway deployment with connected
PostgreSQL. Its live configuration has not been independently inspected here.

The final MinIO community release is local development only; Amazon S3 remains
a future plan. Railway uses `S3_ENABLED=false` until storage is needed. No bucket
creation or S3 uploads are added; chat documents use isolated temporary processing
with PostgreSQL-backed job/evidence storage. See [Infrastructure](infrastructure.md) and
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
and tested extraction rules deliberately. Visitor logins, tools, permanent PDF/URL knowledge import and purchasing need
explicit future scope. Guest chats and private document analysis are authorized
and saved in the existing PostgreSQL database.

Eric requires all new work to be documented in `docs/` as part of the change.
Keep setup, configuration, workflows, decisions and verification notes current;
see the [documentation policy](README.md#documentation-policy).
