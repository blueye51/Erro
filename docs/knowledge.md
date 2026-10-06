# Electrical knowledge and catalog

The existing React → same-origin Nginx → Spring Boot chat now retrieves electrical
knowledge from the existing PostgreSQL database before generation. No second chat
server or Python service is involved. Redis and S3 remain infrastructure clients;
knowledge lives in PostgreSQL. Product selection is engineering assistance, not an
automatic declaration of installation suitability or compliance.

## Architecture

`ChatController` accepts the existing `message` plus optional bounded
`problemContext` (up to eight previous user messages, 24,000 characters total).
`ChatService` resolves the active problem with `ProblemContextService`, calls
`ElectricalContextService`, generates through the existing `AiService`, and maps
citations with `CitationMapper`. The response keeps `reply` for compatibility and
adds `requestId`, `sources`, `calculations`, `assumptions`, `warnings`,
`missingInformation`, `validations`, `products`, and `contextReset`.

The electrical context runs deterministic intent classification, parameter
normalization, calculations, evidence retrieval and product checks. General
conversation remains available. Intent detection and extraction are conservative
English/limited Estonian heuristics, not complete natural-language understanding.
Unknown or conflicting values remain unresolved. Inspect the debugger for every
new query pattern before treating extracted values as dependable.

The browser retains previous user inputs only for the active problem. There are
no accounts, saved chats, chat database rows, or browser storage. **New problem**
clears context and messages. Obvious equipment/topic changes reset inherited
parameters; long self-contained questions also reset. Ambiguous overlapping
changes need the user's explicit reset. Previous AI replies are never used as
technical input. Refreshing clears everything, including the administrator token.

## Database and migration safety

Spring Boot's Flyway starter applies:

- `V1__knowledge_and_catalog.sql`: documents/chunks, products, voltage-specific
  product ratings, product standards and sourced compatibility relationships.
- `V2__optional_pgvector.sql`: enables `vector` in `public` if available and
  permitted. Ordinary PostgreSQL remains supported; no image/version change is
  made to an existing database.
- `V3__starter_source_directory.sql`: 20 small source entries and searchable chunks.

Application tables and Flyway history are in the dedicated `erro_knowledge`
schema. No public application tables are altered, dropped or automatically
baselined. Existing unrelated data is preserved. The backend database role must
be able to create this schema and tables, and read/write them afterwards.
Migration failures stop startup. Flyway clean is disabled. Use the usual database
backup/change-review process before production schema changes. No Hibernate
schema generation is used.

Documents retain source type, jurisdiction, language, publisher, source link,
standard number/family, version, edition, amendment, dates, manufacturer/product
family, copyright/permission notes, normalized content, SHA-256 hash, enabled
state and ingestion status. Chunks retain section paths, order, estimated token
counts, provenance, text and optional embedding/model identity. Effective dates
in the future are excluded. Dates are nullable rather than invented.

Indexes cover text search (GIN), source scope/standard, document chunk order,
embedding model, manufacturer/type/current and product relationships. JDBC query
timeouts bound database retrieval. No raw chat or retrieval text is persisted.

## Retrieval and embeddings

`KnowledgeRetrievalService` abstracts retrieval; `EmbeddingService` abstracts the
provider. The default needs **no embedding account**: PostgreSQL `simple`
full-text search, exact standard matches, terminology expansion and metadata
filtering. Expansion assists search; RCD/RCCB/RCBO or Icu/Ics/Icn are not treated
as electrically interchangeable.

With an embedding endpoint/model configured, ingestion sends a batch of small
chunks to an OpenAI-compatible `/embeddings` API. Query retrieval makes one
embedding call before generation. The endpoint is independent of the existing
DeepSeek chat endpoint; no DeepSeek embedding capability is assumed. Supply an
endpoint supporting `model`, `input`, `dimensions` and an indexed `data` array.
Vectors must have the configured dimensions, finite values and a non-zero norm.
Model identity includes model name, dimensions and endpoint fingerprint. Changing
any requires re-indexing documents; old vectors are never compared to a different
embedding space. Existing lexical evidence remains searchable during rollout.

`KnowledgeRepository` uses pgvector cosine distance if the extension is installed
in `public`; otherwise it computes exact cosine over PostgreSQL numeric arrays.
The fallback performs a scan of matching embeddings in SQL, not an in-memory load
of the corpus. This is suitable for a small library. Large libraries need a
vector column/ANN index or a different vector repository implementation and
retrieval benchmarks. Exact pgvector casting also scans; no claim of ANN-scale
performance is made. The production Railway extension state has not been assumed.
Check it using `SELECT * FROM pg_available_extensions WHERE name='vector'` and
`SELECT extname FROM pg_extension WHERE extname='vector'`. A database owner can
later enable `CREATE EXTENSION vector WITH SCHEMA public`; the application detects
it at retrieval time.

Candidate ranking combines normalized text score, cosine score, exact standard
match, source authority, jurisdiction, publisher/manufacturer match and a small
recent-publication bonus. It is a relevance heuristic, not a probability or a
compliance score. Metadata-only authority is heavily discounted. Results are
bounded, diverse (at most two chunks per document in ordinary retrieval), and
filtered by enabled/READY state, jurisdiction and effective date. Estonia uses
Estonia/EU/IEC/international/manufacturer sources; US material requires explicit
US context. The system instruction handles authority conflicts; automated
semantic conflict detection is not implemented. Multiple retrieved versions are
flagged, but newer publication alone does not prove legal applicability.

Keyword or semantic failures generate explicit warnings. No evidence means no
citations. A failure in embeddings falls back to keyword retrieval. The model is
explicitly told when no authoritative full text is available.

## Starter content and licensing

The starter directory includes all requested IEC families, Schneider's motor
guide, TTJA installations guidance, EUR-Lex LVD/EMC/RoHS links and the EU Blue
Guide. These are **METADATA_ONLY** original scope/directory summaries. IEC links
lead to the official catalog: no particular edition or national adoption is
asserted. They cannot support clause-level compliance claims. Full manufacturer,
legal and standards libraries still need curation and authorized ingestion.

Two short adapted theory summaries attribute Tony R. Kuphaldt's *Lessons In
Electric Circuits*, [DC chapter 2](https://www.ibiblio.org/kuphaldt/electricCircuits/DC/DC_2.html)
and [AC chapter 10](https://www.ibiblio.org/kuphaldt/electricCircuits/AC/AC_10.html).
They are marked OPEN_LICENSE, retain the adaptation notice and the
[CC BY 4.0 license](https://www.ibiblio.org/kuphaldt/electricCircuits/DC/DC_A3.html).
US conductor-sizing examples were not imported. Citations expose attribution
and license notes as well as the original links.

Allowed copyright states: PUBLIC, OPEN_LICENSE, USER_PROVIDED, LICENSED,
METADATA_ONLY, RESTRICTED. Public visibility is not a redistribution license.
All manual imports require an explicit rights confirmation covering both indexing
and sending excerpts to external AI/embedding providers. Restricted material is
rejected. STANDARD full text requires LICENSED; metadata entries are limited to
2,000 characters. Owning a PDF does not automatically permit this processing.
Check the license, user count, excerpt/display permissions and provider-processing
terms with the rights holder. There is no automatic standards scraping or download.
The metadata flag is an operator assertion, not an automatic copyright detector.

The [Schneider guide](https://www.electrical-installation.org/enwiki/Asynchronous_motors),
[TTJA](https://ttja.ee/en/business-client/safety/installations-and-machines/electrical-installations),
and [EUR-Lex](https://eur-lex.europa.eu/eli/dir/2014/35/oj/eng) remain distinct
source authorities. The [2022 Blue Guide](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:52022XC0629(04))
is explanatory guidance, not binding legislation.

## Source management and ingestion

Set a random `KNOWLEDGE_ADMIN_TOKEN` of at least 32 characters on the **backend**.
Empty/short configuration disables every `/api/admin` route (404). Authorized
requests require `Authorization: Bearer …`; invalid credentials return 401.
There is no existing login/role system to reuse. This shared operator token is a
minimal admin mechanism, not a user-account/role platform. Rotate it in backend
configuration. Do not put it in `VITE_*`, URLs, browser storage or committed files.

Open the existing site's **Knowledge management** link (`/#knowledge`), unlock
with the token, and add a source. Supply metadata, permission notes and Markdown
or plain text. Headings/subheadings, paragraphs and normal-sized tables are
preserved. Oversized blocks split at line/sentence/word boundaries. The hard cap
is 50,000 input characters, 128 chunks, and 256 KiB HTTP request bytes. Token
counts are conservative character-based estimates, not provider tokenization.
Larger works should be imported as separate meaningful sections with section
paths/titles and edition metadata.

URL fields are validated HTTP(S) **citation links only**, never fetched. This
avoids SSRF and local filesystem access. PDF parsing, OCR, file upload and remote
URL extraction are extension points, not implemented features. Convert legally
available documents to reviewed Markdown/text first. Tables larger than a chunk
need manual review because repeated table headers are not synthesized.

SHA-256 plus source URL and version prevents duplicate imports. New content at
the same URL creates a separate versioned record rather than destroying history;
disable superseded records deliberately. Re-indexing uses stored content and the
current embedding configuration. It does not fetch the URL. `last_fetched_at`
therefore stays null. Pending/indexing/failed/ready status and safe error text are
visible. A failed re-index preserves previous chunks but excludes the failed
source from retrieval. Retry is explicit. Indexing is synchronous and bounded;
concurrent indexing is rejected and stale locks can be retried after five minutes.
The final chunk replacement is transactional. Disabled sources remain disabled
through re-indexing. Deleting removes chunks, but catalog-referenced documents
cannot be deleted; disable them instead.

Admin API (all under `/api/admin/knowledge`):

| Method / path | Use |
| --- | --- |
| GET `/documents?offset=0` | 50 document summaries, metadata, status and chunk counts |
| POST `/documents` | Ingest source JSON; returns document UUID |
| PATCH `/documents/{id}` | `{ "enabled": false }` or true |
| DELETE `/documents/{id}` | Delete document/chunks unless catalog-referenced |
| POST `/documents/{id}/reindex` | Re-index stored content; send `{}` |
| GET `/documents/{id}/chunks` | Inspect chunk text and embedding model, not raw vectors |
| POST `/debug` | `{ "question": "...", "problemContext": [] }` |
| GET `/products?offset=0` | Paginated catalog summaries |
| POST `/products` | Import/upsert manufacturer + part number |
| PATCH `/products/{id}` | Enable/disable catalog entry |
| POST `/compatibility` | Import/update a sourced relationship |

[Example source JSON](examples/knowledge-source.json) is original owner-checklist
content. Review its permission assertion before ingestion. Do not treat it as a
standard or manufacturer specification.

## Electrical calculations and validation

`ElectricalCalculationService` uses BigDecimal/DECIMAL128 arithmetic and returns
value, SI unit, formula, inputs and assumptions. Tested utilities include V=IR,
DC power, single-phase power with PF, balanced three-phase power with line-to-line
voltage, motor input power with efficiency, and estimated three-phase motor
current with both PF and efficiency. Display values use eight significant digits.
No guessed PF/efficiency, cable resistance or fault-level constants are supplied.
Voltage-drop and short-circuit calculations deliberately require a future valid
input/model implementation.

Extracted values retain the supplied spelling and normalized unit: kW→W, kV→V,
kA/mA→A, percentages→ratios, length→m. Multiple conflicting values are flagged.
Coil/control, explicit input/output and operating-voltage roles are distinct;
ambiguous voltage lists are not silently assigned. Extraction covers common
values and selected categories, not every field proposed for the eventual domain.

`ElectricalValidationService` returns PASS/FAIL/UNKNOWN/WARNING per rule. Missing
motor/protection inputs generate questions. A stated 9 kA fault against a 6 kA
breaking rating fails under the stated same-conditions assumption; incompatible
AC coil/DC supply also fails. These failures bypass the answer model. Protection
answers with missing inputs have a deterministic caution before the explanation.
Contactor AC switching ratings do not imply DC capability. Outdoor enclosures
require environmental/IP details. IP comparisons do not treat immersion as proof
of water-jet performance. No rule claims complete suitability.

Products use typed relational searchable fields, with separate conditional
ratings for voltage, AC/DC type, utilization category, motor power, Icu/Ics/Icn
and conditions. Extra manufacturer attributes alone use bounded JSONB. The
[product template](examples/product.json) intentionally contains no made-up
ratings. `ProductModels.ProductInput` defines the full JSON schema. Units are V,
A (including breaking/residual current), W, Hz, mm² and °C; numeric unknowns are
null, not zero. `ratings` and `standards` arrays are required (may be empty).
Example rating shape: `voltage`, `currentType`, `utilizationCategory`,
`ratedCurrent`, `motorPower`, `icu`, `ics`, `icn`, `standardNumber`, `conditions`.
Use exact documented values and state every application limitation.

Product import requires an enabled indexed manufacturer datasheet/guide with
full-text permission. No product records are seeded. Retrieval selects up to
three catalog candidates by detected type, manufacturer and explicit part number.
It evaluates documented voltage limits, exact voltage/AC-DC application ratings,
explicit breaking-capacity type, explicit pole count, coil voltage/type,
utilization category, power supply input/output checks and enclosure IP. Required
values/derating not established by extraction stay UNKNOWN/WARNING. Alphabetical
candidate limiting is a first catalog implementation, not an exhaustive optimizer.
Price, stock, checkout and purchasing are not implemented.

Compatibility JSON fields are `productId`, `relatedProductId`,
`compatibilityType`, `conditions`, `sourceDocumentId`, `verified`, `notes`.
Relationships are directional, sourced and operator-verified. Unverified or
source-disabled relationships are not supplied as confirmed compatibility.
There is no inference from matching dimensions or product names.

## Citations, debugging and security

`AiService` always supplies `knowledge/electrical-system.txt` as provider system
instructions. The input is a JSON envelope: `userQuestion` and `referenceData`.
All retrieved text/metadata is explicitly untrusted data. No document can add
system messages, tools or executable behavior. This reduces prompt injection;
LLM obedience is not a formal security guarantee. Deterministic failures and
verified citation mapping are enforced independently of generated prose.

The model cites `[K-<chunk UUID>]`. `CitationMapper` only exposes IDs actually
provided to generation, numbers them for display and removes unsupported IDs
with a warning. Returned sources include document/chunk IDs, title, publisher,
URL, section, version/edition/dates, standard, jurisdiction, authority, license
and relevance. A citation proves provenance, not that a generated sentence is
entailed by the excerpt. Uncited generated claims remain unverified.

The admin debugger returns the exact system instruction and generation input,
including query expansion, parameters, chunk contents/scores, products and checks.
It never calls the answer model; configured query embeddings may incur usage.
Ordinary users receive explanation details, not the debug prompt or retrieval
chunks. APIs use no-store; React escapes metadata and skips raw Markdown HTML.

Logs include a random request ID, detected intents, normalized numeric inputs,
a query fingerprint, retrieved document IDs/scores, calculation/check names and
provider error status. Raw user text, prompts, provider error bodies, credentials
and admin tokens are not logged or stored. Inspect actual retrieval queries in
the protected debugger. There is no persistent `chat_retrieval_log` table.

## Configuration and deployment

Keep existing Railway database references, chat AI credentials, Nginx
`API_UPSTREAM`, separate web/backend services and `S3_ENABLED=false` on Railway.
The backend migrations run at startup, not at image build time. No separate
pre-deploy command is needed. Existing public-schema data is outside Flyway's
managed schema. Deployment must use the updated backend and web images.

| Backend environment variable | Default / meaning |
| --- | --- |
| `KNOWLEDGE_ADMIN_TOKEN` | Empty: admin disabled. Minimum 32 characters. |
| `EMBEDDING_ENDPOINT` | Empty: lexical-only retrieval. Full compatible embeddings URL. |
| `EMBEDDING_API_KEY` | Empty; independent backend-only provider credential. |
| `EMBEDDING_MODEL` | Empty; set with endpoint for embeddings. |
| `EMBEDDING_DIMENSIONS` | 1536; must match provider output; 1–4096. |
| `RAG_KEYWORD_TOP_K` | 24 keyword candidates; 1–100. |
| `RAG_VECTOR_TOP_K` | 16 semantic candidates; 1–100. |
| `RAG_FINAL_CHUNKS` | 6 context chunks; 1–12. |
| `RAG_MAX_CHUNK_CHARS` | 1600; 400–3000. Re-index after changing. |
| `RAG_MAX_CONTEXT_CHARS` | 12000 serialized evidence characters; 2000–24000. |
| `RAG_MIN_RELEVANCE` | 0.15 minimum ranking score; 0.01–1. |

All are mapped through Compose and listed in `.env.example`. Generation retains
the existing `AI_*` variables. Embeddings time out after 12 seconds; generation
uses `AI_TIMEOUT` (default 60s); the browser allows 90s and Nginx 100s. Increase
these coherently if changing timeouts. Large documents should be sectioned before
importing. The public chat still uses the existing public access model; this
change adds admin authorization, not visitor accounts or a general rate limiter.

Run locally:

```sh
docker compose config --quiet
docker compose up --build -d
# http://localhost:5173 — chat and /#knowledge
cd web
npm run build
npm run lint
```

Backend checks on JDK 25: `cd backend && ./mvnw --batch-mode --no-transfer-progress verify`.
Unit/API tests use a local mock AI and need no real provider credentials. Database
tests run with `./scripts/verify-knowledge.sh`, which builds a test image and
creates a disposable PostgreSQL 18 database/network, preserving local/production
data. It rejects URLs that do not name `erro_knowledge_test` and cleans up its
containers. `RAG_TEST_POSTGRES_IMAGE` can select a pgvector-enabled test image.
The normal Docker image build skips only these explicitly opt-in database tests;
the script executes them. `node --test web/test/proxy.test.mjs` tests Nginx after
`docker compose build web`.

After Railway deployment:

1. Confirm Flyway schema v3 and successful PostgreSQL/Redis startup checks. Confirm
   S3 is skipped when disabled. Existing services/domain remain unchanged.
2. Open `https://erro.ink/healthz`; verify chat via the same-origin `/api/chat`.
3. Unlock `/#knowledge` and inspect the 20 starter sources. An unauthenticated
   GET to `/api/admin/knowledge/documents` must return 401, or 404 when disabled.
4. Test retrieval for `IEC 60947-2 breaking capacity` and the motor example;
   inspect source IDs, scope-only flags, missing data and generation context.
5. Ingest a small authorized source; verify chunk count, duplicate handling,
   disable/enable exclusion and re-indexing. Enable embeddings only after the
   keyword path is verified; re-index sources that need vectors.
6. Ask the 9 kA/6 kA breaker question and the AC-coil/DC-supply question. Confirm
   deterministic FAIL. Ask the motor example; confirm no invented PF/efficiency
   or nominal-current-only breaker choice. Verify real provider citation behavior.
7. Test a follow-up, start a new problem, and refresh; previous parameters must not
   survive the reset/refresh. Verify source details on a phone-sized viewport.

## Verification record — 2026-10-05/06

- Maven compilation and `verify` pass with local mock AI/embedding providers.
  The final suite contains 54 tests; the isolated PostgreSQL run executes all
  seven database tests as well as unit/API tests. No real provider keys are used.
- Plain PostgreSQL 18.6 and pgvector-enabled PostgreSQL both passed migration,
  ingestion, keyword/semantic retrieval and model-isolation checks. Tests verify
  unrelated existing public data survives migration and repeat startup.
- Frontend build/lint and all six Nginx proxy regression tests pass.
- Playwright exercised the real production Nginx/backend images against a local
  mock provider: desktop/mobile layout (1440/768/390/320 px), sending, loading,
  provider errors, Markdown, citations, calculations, follow-up inputs, problem
  reset and refresh clearing, admin authorization (including encoded paths),
  retrieval debugging, text ingestion, toggles and re-indexing. No local/session
  storage was created. A summary selector was narrowed to prevent homepage
  styling from affecting nested answer details.
- Backend startup verified Compose PostgreSQL, Redis and MinIO. A separate run
  with `S3_ENABLED=false` verified PostgreSQL/Redis and skipped S3, matching the
  Railway runtime configuration. Compose configuration and affected Docker
  image builds pass.

Run browser checks from the repository root after installing the frontend dev
dependencies and Chromium:

```sh
cd web
npm ci
npx playwright install chromium
cd ..
./scripts/verify-browser.sh
```

The runner builds the existing images, starts an isolated Compose project with
random local ports and fixture credentials/provider, then removes only its own
containers and volumes. It requires Docker, Node/npm, curl and browser system
libraries. `npm run test:browser` is the underlying script and requires the
runner's local fixture URL/token. Never target it at production. The normal
local Compose services/data are preserved.

Railway resources have not been changed or independently inspected. A live
DeepSeek answer and a real embedding account have not been tested. Inspect the
production database role/extension availability, deploy the reviewed migrations,
configure the admin token and verify actual citations after deployment. Semantic
conflict detection, exhaustive extraction of every proposed parameter, URL/PDF
extraction/OCR, ANN indexing for large corpora, and a populated manufacturer
catalog remain future work. The parser is deliberately conservative; the tested
rules do not establish the engineering correctness of every possible question.
