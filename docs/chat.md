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

## Clarification before evaluation

The assistant is instructed to decide whether a missing detail would materially
change the requested assessment. When it would, and the user can reasonably
supply it, the assistant explains why and asks one or two short numbered
questions before giving the final evaluation. Answer them together in the normal
message box; free text, "I don't know" and a request to proceed are supported.
The next reply uses those answers and the original request. Questions and answers
are ordinary saved messages, so refreshing between them preserves the exchange.

For example, an enclosure planning question might need the installation location
and desired spare allowance. An explanation or readable-document summary should
usually be answered directly. The assistant should reuse supplied information,
avoid repeating unavailable details, and prefer one clarification round. Another
round is reserved for a newly revealed essential ambiguity. If details remain
unknown, it should give supported results and clearly labelled planning scenarios,
withholding any unsupported final selection. Existing deterministic failures are
reported immediately and cannot be postponed by questions.

This is a model-guided conversation policy in `knowledge/electrical-system.txt`,
using the existing single generation per message. There is no additional preflight
AI call, form, API field, database migration or configuration switch. Rebuild and
deploy the backend to enable it. The model chooses which questions are useful;
the two-question limit and judgment are instructions, not a deterministic schema
or guarantee of engineering completeness. Prompt changes do not enlarge the
response-token allowance or resolve a production limit override.

## Model context and continuity

The complete conversation is saved, but it is not blindly resent to the model.
The server selects recent user messages and relevant older user messages with
PostgreSQL text search, plus recent dialogue. The generation input is capped at
32,000 serialized characters, with explicit omission/incomplete flags. Dropping
context for one answer never deletes saved messages. Structured document items
use a shared column schema and short citation IDs; document evidence takes priority over older assistant dialogue.
The latest assistant reply, when at most 2,400 characters, is retained ahead of
additional evidence so a brief answer to clarification questions keeps its meaning.
It remains untrusted dialogue, never a deterministic technical input. The same
32,000-character ceiling and evidence-omission warnings still apply; a topic reset
clears this dialogue along with the preceding user context.
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
DeepSeek Responses endpoint, `deepseek-flash`, effort `none`, 8192 output tokens
and a 60-second timeout. Empty effort omits that field. Credentials stay on the
backend, never in `web/`, `VITE_*`, cookies or committed files. Embeddings remain
independently configured by `EMBEDDING_*`.

The reply allowance was increased from 2,048 to 8,192 tokens on 2026-10-27 so
normal document analyses and inventories can finish. This is a ceiling, not a
requested answer length. It is separate from the unchanged 32,000-character
input envelope and PostgreSQL chat storage. Explicit environment overrides still
win: on an existing Railway backend, change `AI_MAX_OUTPUT_TOKENS=2048` to `8192`
(or remove the override to use the new default) and deploy the variable change.
Changing only the repository default cannot override a Railway variable.
The provider's [Responses API](https://api-docs.deepseek.com/guides/responses_api/)
supports this output limit and reports truncation separately from input limits.

`CHAT_ALLOWED_ORIGINS` must include the public web origins; Railway normally uses
`https://erro.ink,https://www.erro.ink`. Set development origins explicitly when
using nondefault ports. Saved-chat requests use same-origin cookies and a CSRF
header. `CHAT_COOKIE_SECURE` defaults true for production and false in local
Compose; `CHAT_GUEST_TTL` defaults to 365 days. See the [guest guide](guest-chats.md)
for complete endpoints, cookie/deletion behavior, schema and processing leases.

Provider error bodies/keys are not returned or logged. Reply errors become safe
FAILED request records. A provider-reported `max_output_tokens` interruption
explicitly identifies the response length limit and suggests a concise answer
or one section. Other incomplete replies suggest retrying; neither is presented
as proof that the user message or stored chat is too long. Partial technical
answers are not published. Incomplete technical answers are still withheld; no automatic paid retry is added. Parser, database, authorization and quota errors retain
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

### Longer replies and chat layout — 2026-10-27

Frontend build/lint and Maven `verify` passed; all 103 backend tests ran with no
failures or skips against a disposable native PostgreSQL 16 database. The API
tests verify the 8,192-token default and preservation of an explicit smaller
override. The browser fixture checks a long saved reply, reading from its
beginning after refresh, growing/clearing the composer, and overflow at
1440/768/390/320px. Both native browser suites passed, including sending,
loading/errors, Markdown formatting, saved message/attachment restoration and
guest-data deletion across tabs. Desktop/mobile screenshots were inspected.

Compose configuration validation passed using the Windows Docker CLI. Docker
image builds, `scripts/verify-knowledge.sh` and Compose startup connection checks
could not run: WSL Docker integration was unavailable and the Windows Docker
engine pipe was missing. The native fixtures use JDK 25, PostgreSQL 16 and Nginx
1.24; the pinned production images and Redis/S3 startup checks remain unverified.
No live provider request or Railway variable change was made for this check.

### Clarification flow — 2026-10-28

Frontend build/lint passed. Maven `verify` passed all 105 tests with no skips
against disposable PostgreSQL 16. Tests cover retaining short clarification
replies under document pressure within the 32,000-character envelope, restoring
questions from PostgreSQL, combining user answers with the original request,
isolating generated example ratings from deterministic inputs, and clearing
dialogue on a topic reset. The API test checks that generation receives the
clarification policy; existing deterministic failure tests still pass.

Both native browser suites passed with the built app and local mock AI, covering
two numbered questions without an early evaluation, refresh before answering,
free-text/unknown answers, a saved provisional evaluation and new-chat isolation.
The mobile clarification screenshot was inspected. Existing sending/loading/error,
attachment restoration, deletion and responsive-layout checks also passed. The
table keyboard check initially timed out after viewport changes; it now refocuses
the scroll region and uses a 100 ms keypress before checking actual horizontal
movement. No production layout change was needed.

Compose configuration validation passed through the Windows Docker CLI. The
backend image build, `scripts/verify-knowledge.sh` and Compose startup checks
remain unavailable because WSL Docker integration and the Windows engine pipe
are unavailable. Native verification uses JDK 25, PostgreSQL 16 and Nginx 1.24.
The AI provider in automated checks is a local fixture: these tests establish
instruction delivery and the saved conversation flow, not the live model's
judgment about which questions to ask. No production deployment or live provider
evaluation was performed for this change.
