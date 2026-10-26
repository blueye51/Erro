# Saved guest chats

Eric authorized browser-recognized guest identities and database-backed chats on
2026-10-26. This replaces the website's page-only memory. There is no login,
email collection or visitor password. The existing PostgreSQL, chat/electrical
pipeline and React interface are reused.

## Using saved chats

Opening Chat creates or restores a guest automatically. **Guest chats** selects
a saved conversation. **New chat** starts a separate project without deleting
earlier chats. Refresh and returning to the same browser restore messages,
answer details and extracted document evidence. The selected chat is represented
by an opaque `?chat=` identifier; it is not a public sharing link and still
requires the owner's cookie. Older messages/chats can be loaded in pages.

Replies run as durable background requests. Refreshing during a reply restores
its progress. **Cancel reply** unblocks the composer; failures restore the draft
for retry. Identical request IDs are idempotent, preventing duplicate answers
after interrupted connections. A provider call already in progress can finish
after cancellation, but its result is not saved.

**Delete chat** removes that conversation and its documents. **Delete all my
data** asks for confirmation, then atomically deletes this guest's chats,
messages, requests, queued originals and extracted evidence, and creates a new
guest/cookie. Other guests are unaffected. Other open tabs receive a deletion
notification and clear their old view. Late worker results cannot recreate
deleted data. Database backups remain subject to the hosting retention policy.

Clearing browser cookies, changing browsers, private browsing or losing the
cookie can make old chats inaccessible; there is no account recovery or cross-device
identity. Guests expire after 365 days of inactivity by default; expired guest
records and all their dependent data are deleted by the cleanup worker.

## Identity and request protection

`erro_guest` is a random 256-bit capability in a host-only, HttpOnly,
SameSite=Strict cookie scoped to `/api`. Production defaults to Secure. The
database stores only its SHA-256 hash. No identity token is placed in URLs,
JavaScript-accessible storage, frontend build variables or logs.

Bootstrap returns a separate cookie-bound CSRF token held in page memory.
Every mutation requires `X-CSRF-Token` and the guest cookie. Explicit allowed
origins and cross-site request checks apply. Every chat, file, request and
deletion is checked against the cookie's guest; a UUID alone grants no access.
Admin knowledge endpoints retain their independent backend operator token.

## Database and processing

Flyway `V4__guest_chats.sql` adds the dedicated `erro_chat` schema alongside
`erro_knowledge`. Its tables are `guest`, `conversation`, `message`,
`chat_request` and `attachment`. Foreign-key cascades implement deletion.
Existing public and knowledge/catalog data are not replaced. The database role
must be able to create this schema and its tables/indexes. Flyway history remains
in `erro_knowledge`; do not baseline or clean the production database.

Messages include the complete successful user/assistant exchange, filenames and
the assistant's structured sources/calculations/warnings. Failed/cancelled
requests retain their draft/error for recovery but do not add a successful
message pair. All are removed by guest/chat deletion or guest expiration.

Two chat workers and two isolated document workers claim PostgreSQL jobs using
`FOR UPDATE SKIP LOCKED`.
Queued jobs survive restarts and claims work across backend replicas. Processing
leases are 150 seconds for replies and five minutes for document jobs. Expired
leases become explicit failures, allowing manual retry without an invisible
permanent lock or automatic repeated paid calls. Cleanup runs every 30 seconds.
No database connection/transaction remains open during AI or parser calls.
Keep provider timeouts below the reply lease, allowing time for retrieval; the
default 60-second timeout fits the 150-second lease.

Original file bytes are stored only while queued/processing, then cleared on
success or failure. Extracted evidence is stored with the chat until deletion or
guest expiry. It is never indexed into the curated knowledge database. There is
no persistent filesystem volume, Redis document cache, S3 upload or new service.
See [Documents](documents.md) for parser safety and engineering limits.

Limits: 1,000 chats per guest; 50 chats and 100 messages per API page; one active
reply per chat, three per guest and 64 queued/processing replies globally. Files
retain the eight-file/20 MiB each/60 MiB per-chat limits. At most eight queued or
processing files and 128 MiB of such originals are accepted globally. These
resource bounds do not impose an eight-message limit on stored history.

## Full storage, bounded AI context

Saving a chat does not enlarge a provider's context window. The frontend sends
only the latest question, request ID and selected file IDs. The server selects
recent dialogue and relevant older **user** messages using indexed PostgreSQL
text search within this chat's active topic. Selected user inputs fit 8,000
characters; selected dialogue fits another 8,000. There is no eight-message
history cutoff in the saved-chat path. Earlier assistant text is conversational
context, never verified electrical input.

`ChatPrompt` caps the final serialized generation input at **32,000 characters**,
separate from the system instruction/output budget. It removes duplicated query
text, compacts calculation/product metadata and drops lower-priority context as
needed. Only evidence actually sent can be cited. Omissions set explicit warnings
and incomplete document coverage; the complete chat remains in PostgreSQL.
This is a character budget, not a provider-specific tokenizer or a promise that
every historical fact is recalled. A new chat isolates unrelated projects.

## API

All responses use `Cache-Control: no-store`. Requests use same-origin cookies.

| Method / route | Purpose |
| --- | --- |
| GET `/api/guest` | Restore/create cookie; return CSRF token and first chat page |
| DELETE `/api/guest` | Delete all guest data and return a new guest/session |
| GET `/api/chats?offset=0` | Paginated chat summaries |
| POST `/api/chats` | New empty chat |
| GET `/api/chats/{id}?before={messageId}` | Chat, message page, file statuses, pending request |
| DELETE `/api/chats/{id}` | Delete one owned chat |
| POST `/api/chats/{id}/messages` | `{requestId, message, attachmentIds}` → 202 request status |
| GET `/api/chats/{id}/requests/{requestId}` | QUEUED, PROCESSING, COMPLETE or FAILED plus safe error |
| DELETE `/api/chats/{id}/requests/{requestId}` | Cancel an active reply |
| POST `/api/chats/{id}/attachments` | Multipart `file` → 202 file status |
| GET `/api/chats/{id}/attachments` | Owned file statuses |
| DELETE `/api/chats/{id}/attachments/{fileId}` | Remove owned file/evidence |

Messages are 1–8,000 characters and cannot contain unsupported control characters.
Responses use 400 for invalid input, 401 for an unavailable session, 403 for
CSRF/origin failure, 404 for resources outside this guest, 409 for unfinished
files/busy chats and 413/429 for limits. Database failures return safe 503 errors.
Provider failures appear on the persisted request, and the UI restores the draft.

## Configuration and deployment

Deploy **both backend and web**. The migration runs at backend startup; no manual
pre-deploy SQL, new secret or cookie-signing key is required. Keep backend private
and `S3_ENABLED=false` on Railway. Guest chat/file state is shared through
PostgreSQL, so sticky routing is not required for these endpoints.

| Backend variable | Default / usage |
| --- | --- |
| `CHAT_COOKIE_SECURE` | `true`; leave true for Railway HTTPS. Compose defaults false for localhost HTTP. Set false explicitly for host Java development over HTTP. |
| `CHAT_GUEST_TTL` | `365d`; allowed 1–730 days; cookie lifetime and inactivity expiration. |

Keep existing `AI_*`, `DOCUMENTS_ENABLED`, `DOCUMENT_VISION_MODEL`, database and
`CHAT_ALLOWED_ORIGINS` settings. `DOCUMENT_TTL`/`DOCUMENT_MAX_PROBLEMS` apply only
to the retained legacy capability API, not saved guest chats. Nginx permits large
bodies only on the two attachment upload route shapes; ordinary APIs remain 256 KiB.

## Verification workflows

Run Maven `verify`, frontend build/lint and `scripts/verify-knowledge.sh` as usual.
`scripts/verify-browser.sh` runs both browser suites against isolated Compose
services and a local mock AI. Neither suite needs real provider credentials.

When Docker is unavailable, `scripts/verify-database-local.sh` creates a private,
disposable native PostgreSQL cluster/database and runs the complete Maven suite.
Set `PG_BINDIR` to installed PostgreSQL binaries (default `/usr/lib/postgresql/16/bin`).
It never connects to an existing database and removes its cluster afterwards.

After Maven packages the jar and `npm run build` finishes, run
`scripts/verify-browser-local.sh` with native PostgreSQL and Nginx. `NGINX_BIN`
and `NGINX_MIME_FILE` select a binary/mime file if not installed in normal paths.
It starts an isolated PostgreSQL cluster, Nginx with the actual app configuration,
the packaged backend and a loopback-only mock AI. Infrastructure startup checks
are disabled only in this fixture; Redis/S3 and Compose checks remain separate.
`ERRO_KEEP_TEST_LOGS=true` preserves disposable fixture logs for diagnosis.

Browser suites cover real database saving, document follow-ups including a
one-letter question after a large upload, refresh/pending recovery, chat switching,
failure/cancellation, guest isolation, cookie rotation/deletion across tabs,
responsive layouts, source formatting and protected knowledge management.

## Verification record — 2026-10-26

- Maven `verify` passed all 98 tests with no skips against a fresh native
  PostgreSQL 16 cluster, including all migrations, knowledge/catalog tests,
  cookie/CSRF/ownership, idempotent/concurrent jobs, deletion/late results,
  expired leases, pagination, older-user-context retrieval and prompt-budget tests.
- Frontend build/lint passed. Both Playwright suites passed with the built React
  app, actual Nginx configuration, packaged Java backend, PostgreSQL and a local
  mock AI. Checks covered saved files/messages, one-letter follow-ups after a
  300+ KiB upload, refresh during a pending reply, error/cancellation recovery,
  chat switching, guest isolation, delete-all cookie rotation across tabs,
  safe reply formatting, knowledge administration and widths 1440/768/390/320.
  The mobile screenshot was inspected; local/session storage stayed empty.
- The packaged document worker passed CSV/image/invalid-location/format recovery
  checks and the supplied three-page Peakilp PDF with all 12 crops against a mock
  vision provider. The supplied file and rendered images are not committed.
- Compose configuration validation passed using the Windows Docker CLI. Docker
  image builds, `scripts/verify-knowledge.sh`, production-image proxy checks and
  Compose startup connection checks could not run: WSL integration was unavailable
  and the Windows Docker engine pipe was missing. Native fixtures used JDK 25,
  PostgreSQL 16 and Nginx 1.24; they do not replace verification against the pinned
  PostgreSQL 18.6/Nginx 1.30.5 images or Redis/S3 startup checks.

## Deployed verification — 2026-10-26

Commit `09947c5` was pushed to `main`; both Railway backend/web deployments
reported success. A fresh Chromium guest on `https://erro.ink` received the
Secure/HttpOnly/SameSite=Strict cookie and processed the owner's supplied
`26004_PP_EL-7-01_v01_Peakilp.pdf` against the configured live vision provider.
The saved attachment became READY with 42 excerpts and five uncertainty warnings.

The real provider answered a document-summary question, accepted a one-letter
`a` follow-up, restored both exchanges and the attachment after refresh, then
answered another follow-up. The database-backed API returned all six messages
with nine document citation entries. There were no browser errors; the mobile
view was inspected. A separate guest received 404 for the owned chat. Delete-all
removed the live test content and returned a fresh empty guest/chat. No original
PDF, rendered image, guest token or live provider credential was committed.
These checks establish persistence and working follow-ups, not drawing accuracy,
complete symbol counts or installation suitability.
