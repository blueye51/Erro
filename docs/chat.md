# Chat API

The website now uses [saved guest chats](guest-chats.md). Browser refresh restores
the conversation instead of clearing it. There is no login: a private cookie
identifies a guest, and PostgreSQL holds messages and document evidence.

## Request/reply flow

1. GET `/api/guest` restores/creates the guest cookie and returns a CSRF token.
2. GET `/api/chats/{id}` loads an owned chat, message page, files and pending reply.
3. POST `/api/chats/{id}/messages` sends only `{requestId, message, attachmentIds}`
   with cookies and `X-CSRF-Token`. It returns 202 immediately.
4. The backend claims a durable request, retrieves relevant saved user inputs and
   recent dialogue, retrieves document/knowledge evidence, runs the existing
   electrical checks and calls the existing `AiService`.
5. The browser polls the request until COMPLETE or FAILED. Successful messages
   and structured answer details are committed together. Failures restore the
   draft. Reloading resumes an in-progress request; **Cancel reply** unblocks it.

The response saved with each assistant message retains `reply`, `requestId`,
`sources`, `calculations`, `assumptions`, `warnings`, `missingInformation`,
`validations`, `products`, `contextReset` and `documentSources`.
Safe Markdown, engineering details, deterministic FAIL handling, citations and
the existing protected knowledge UI remain in use. See [Electrical knowledge](knowledge.md)
and [Documents](documents.md) for evidence boundaries.

## Model context and continuity

The complete conversation is saved, but it is not blindly resent to the model.
The server selects recent user messages and relevant older user messages with
PostgreSQL text search, plus recent dialogue. The generation input is capped at
32,000 serialized characters, with explicit omission/incomplete flags. Dropping
context for one answer never deletes saved messages. Structured document items
are sent once; document evidence takes priority over older assistant dialogue.
Coverage distinguishes omitted excerpts from readings with uncertain positions,
so a prompt limit is not described as an unreadable original drawing.

`userQuestion` is the latest input; `previousUserMessages` contains selected
chronological technical inputs. `previousConversation` includes recent user and
assistant utterances for references such as “the second option you mentioned.”
Earlier assistant ratings remain unverified and never enter deterministic
parameter extraction as if the visitor supplied them. `referenceData` carries
electrical/knowledge evidence and `documentData` carries bounded document
evidence. Duplicated retrieval-query text and long calculation input maps are
excluded from generation; full calculation details remain in the saved answer.

Only source/excerpt IDs actually supplied to generation can become citations.
The provider receives `store: false`, no tools and no provider conversation IDs.
No provider-side history store replaces the app's PostgreSQL conversation data.
**New chat** isolates a different project; topic-reset rules can still clear
inherited technical context without deleting the visible saved conversation.

## Reply formatting

`react-markdown` and `remark-gfm` render tables, lists, emphasis, links and code.
Wide tables scroll inside their own keyboard-focusable region. Raw HTML is
skipped, image Markdown becomes alt text, and links retain URL sanitization and
`noopener noreferrer`. Loading/error states and answer details work after refresh.

## Configuration

Existing backend settings remain `AI_API_KEY`, `AI_ENDPOINT`, `AI_MODEL`,
`AI_REASONING_EFFORT`, `AI_MAX_OUTPUT_TOKENS` and `AI_TIMEOUT`. Defaults are the
DeepSeek Responses endpoint, `deepseek-flash`, effort `none`, 2048 output tokens
and a 60-second timeout. Empty effort omits that field. Credentials stay on the
backend, never in `web/`, `VITE_*`, cookies or committed files. Embeddings remain
independently configured by `EMBEDDING_*`.

`CHAT_ALLOWED_ORIGINS` must include the public web origins; Railway normally uses
`https://erro.ink,https://www.erro.ink`. Set development origins explicitly when
using nondefault ports. Saved-chat requests use same-origin cookies and a CSRF
header. `CHAT_COOKIE_SECURE` defaults true for production and false in local
Compose; `CHAT_GUEST_TTL` defaults to 365 days. See the [guest guide](guest-chats.md)
for complete endpoints, cookie/deletion behavior, schema and processing leases.

Provider error bodies/keys are not returned or logged. Reply errors become safe
FAILED request records. Parser, database, authorization and quota errors retain
safe `{error: ...}` responses. A deterministic electrical FAIL can still answer
without a configured model.

## API contract

The saved API is documented in [Guest endpoints](guest-chats.md#api).

### Compatibility endpoint

`POST /api/chat` retains the earlier direct/stateless integration contract:
`{message, problemContext?, attachmentIds?}` with optional legacy problem token.
Its bounded `problemContext` is not the website's saved-chat mechanism. The
website no longer sends it or manages eight-message memory in React.
Legacy capability upload endpoints remain for existing integrations/tests; new
guest chat endpoints store and authorize their own documents in PostgreSQL.

## Verification

Run backend `./mvnw verify`, frontend `npm run build` and `npm run lint`, and the
database/browser runners documented in [Saved guest chats](guest-chats.md).
Browser coverage includes document processing followed by one-letter questions,
refresh/restoration, pending recovery, cancellation/errors, guest isolation and
delete-all rotation. Tests use local mock AI providers and no real credentials.
